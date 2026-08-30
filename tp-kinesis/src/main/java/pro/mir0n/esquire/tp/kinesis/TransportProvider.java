/*
 *  Esquire frameworks (tm)
 *  tp-kinesis -- transport provider (Amazon Kinesis)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the Amazon Kinesis ITransportProvider, both legs. An ABSENT partition-by means
 *                   FIFO -- every record under one key, one shard, one ordered sequence -- and naming a header
 *                   opts in to spreading, which is safe only where records do not depend on one another.
 *                   poll-millis is the delivery latency (GetRecords is capped at five a second per shard, so
 *                   200ms is the floor). The stream is ensured on first use, never when a leg opens.
 */
package pro.mir0n.esquire.tp.kinesis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.OwnExcluding;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.SelectingReceiver;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.KinesisClientBuilder;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamSummaryRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.IncreaseStreamRetentionPeriodRequest;
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;
import software.amazon.awssdk.services.kinesis.model.StreamMode;
import software.amazon.awssdk.services.kinesis.model.StreamModeDetails;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Amazon Kinesis implementation of the transport-provider SPI: both legs. */
public final class TransportProvider implements ITransportProvider {

    private static final Logger devLog =
            LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.kinesis.TransportProvider");

    /** The AWS region the client works in. Absent = the SDK default chain. */
    public static final String PARAM_REGION = "region";

    /**
     * The header whose value becomes the record's partition key -- i.e. how the stream is SHARDED.
     *
     * <p><b>Absent means FIFO</b>, and that is the default on purpose: every record is written under one key,
     * so the whole stream is a single ordered sequence. Kinesis keeps order only WITHIN a partition key, and
     * each shard is read by its own thread, so any key that varies is a decision to give up total order.
     *
     * <p>Setting it is therefore opting IN to spreading, and it is only safe where the records do not depend
     * on one another. The audit bus is such a case: rows are independent, so {@code EntityID} buys per-entity
     * ordering and spreads the load. A broadcast that BUILDS something is not: a parent must be applied before
     * its child, and an update before it can be applied at all.
     *
     * <p>The cost of FIFO is the real limit of the shape: one key is one shard, which Kinesis rates at 1 MB/s
     * and 1000 records/s. A stream that must be applied in order cannot be sharded, whatever the vendor.
     */
    public static final String PARAM_PARTITION_BY = "partition-by";

    /**
     * Where a receive leg begins when it has no position of its own -- which is every start, since no
     * position is kept (see {@link KinesisConsumer}). {@code TRIM_HORIZON} (the default) reads the whole
     * retained window; {@code LATEST} takes only what arrives from now on.
     */
    public static final String PARAM_ITERATOR_TYPE = "iterator-type";

    /**
     * Milliseconds a receive leg waits after an empty read.
     *
     * <p>This is the transport's delivery LATENCY. Every consumer PULLS -- nothing is pushed into a service --
     * but an SQS receive BLOCKS on the server and comes back the moment a message arrives, while GetRecords
     * answers at once whether or not there is anything. So a Kinesis reader has to pace itself, and a record
     * written just after an empty read waits this long. That is the one place the two are not interchangeable.
     *
     * <p>The default is the floor Kinesis itself sets: {@code GetRecords} is capped at five calls a second
     * per shard, so 200ms is as fast as a shard may legally be read. Lower is not faster, only throttled.
     */
    public static final String PARAM_POLL_MILLIS = "poll-millis";

    /** Records asked for per GetRecords. */
    public static final String PARAM_LIMIT = "limit";

    /** The param prefixes naming which AWS call a key belongs to -- see SqsSupport for why AWS needs them
     *  where ActiveMQ, Kafka and Redis do not. */
    public static final String GROUP_CLIENT = "client";
    public static final String GROUP_STREAM = "stream";

    /** The bare param keys this driver owns; anything else must name its AWS call with a prefix. */
    private static final Set<String> KNOWN_PARAMS = Set.of(PARAM_REGION, PARAM_PARTITION_BY,
            PARAM_ITERATOR_TYPE, PARAM_POLL_MILLIS, PARAM_LIMIT, BusConstants.PARAM_NO_LOCAL);

    private static final Set<String> KNOWN_GROUPS = Set.of(GROUP_CLIENT, GROUP_STREAM);

    /** Stream settings are typed calls, not a verbatim attribute map, so these are read by name and any
     *  other key under {@code stream.} is refused rather than quietly ignored. */
    private static final String STREAM_RETENTION_HOURS = "RetentionPeriodHours";

    /** SDK client settings are typed too. */
    private static final String CLIENT_API_CALL_TIMEOUT = "apiCallTimeout";
    private static final String CLIENT_API_CALL_ATTEMPT_TIMEOUT = "apiCallAttemptTimeout";
    private static final String CLIENT_MAX_ATTEMPTS = "maxAttempts";

    private static final int DEFAULT_POLL_MILLIS = 200;   // the Kinesis cap: 5 GetRecords/s per shard
    private static final int DEFAULT_LIMIT = 500;

    /** How long to wait for a newly made stream to become ACTIVE before giving up. */
    private static final long STREAM_READY_TIMEOUT_MS = 30_000L;
    private static final long STREAM_POLL_MS = 500L;

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        requireKnownParams(s.params());
        KinesisClient kinesis = client(s.endpoint(), s.params(), paramGroup(s.params(), GROUP_CLIENT));
        String stream = sanitize(destination);
        String partitionBy = s.param(PARAM_PARTITION_BY, null);   // absent = FIFO (one key)

        // UNKNOWN, not UP: the stream calls above prove the control-plane worked, not that a record can be
        // written. The first put is what proves this leg, and it sets this then.
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        devLog.info("tp-kinesis: publisher opened on {} (endpoint={}, stream={}, partitionBy={})",
                destination, s.endpoint(), stream, partitionBy != null ? partitionBy : "FIFO");
        // The stream is made on the FIRST PUT, not here. Opening a leg must not need AWS to be answering:
        // a service that cannot reach it for a moment at boot has a transport that is DOWN, not a
        // configuration that is wrong, and the two must not share a fate. The config IS still checked above,
        // and a bad one still stops the service.
        return new KinesisPublisher(kinesis, stream, partitionBy, paramGroup(s.params(), GROUP_STREAM),
                s.objectMapper(), conn);
    }

    /**
     * The receive leg: a poll thread per shard. Created PAUSED like every other consumer -- KinesisConsumer
     * discovers the shards and begins reading at start().
     *
     * <p>The x-rod's subscription selector and {@code noLocal} are applied HERE, in code, by two separate
     * filters. Kinesis has no server-side filtering of any kind: every reader of a shard gets every record
     * on it. That is what makes it a broadcast without any fan-out to arrange -- and it is also why leaving
     * these out would silently deliver a consumer both what it did not ask for and its own publications.
     */
    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        requireKnownParams(s.params());
        KinesisClient kinesis = client(s.endpoint(), s.params(), paramGroup(s.params(), GROUP_CLIENT));
        String stream = sanitize(destination);

        ShardIteratorType iteratorType = ShardIteratorType.fromValue(
                s.param(PARAM_ITERATOR_TYPE, ShardIteratorType.TRIM_HORIZON.toString()));
        long pollMs = s.paramLong(PARAM_POLL_MILLIS, DEFAULT_POLL_MILLIS);
        int limit = (int) s.paramLong(PARAM_LIMIT, DEFAULT_LIMIT);

        boolean noLocal = Boolean.parseBoolean(s.param(BusConstants.PARAM_NO_LOCAL, "false"));
        String ownRodId = s.identity() != null ? s.identity().rodId() : null;
        Consumer<TransportMessage> receiver = SelectingReceiver.wrap(handler, s.selector());
        receiver = OwnExcluding.wrap(receiver, ownRodId, noLocal);

        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        devLog.info("tp-kinesis: consumer created (paused) on {} (endpoint={}, stream={}, from={}, noLocal={})",
                destination, s.endpoint(), stream, iteratorType, noLocal);
        // Same as the publisher: nothing is asked of AWS here. The stream is ensured and the shards are
        // discovered by start(), which keeps trying rather than throwing.
        return new KinesisConsumer(kinesis, stream, paramGroup(s.params(), GROUP_STREAM), receiver,
                s.objectMapper(), iteratorType, pollMs, limit, conn);
    }

    /** The sub-group of params written {@code <prefix>.<key>}, keyed by {@code <key>} alone. It lives here
     *  rather than on TransportSettings because AWS is ATTACHED: these jars are mounted beside a service image
     *  that carries its own build of the messaging framework, so a driver may only call framework API the
     *  SHIPPED image already has. */
    static Map<String, String> paramGroup(Map<String, String> params, String prefix) {
        Map<String, String> ret = new LinkedHashMap<>();
        String head = prefix + ".";
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith(head) && e.getKey().length() > head.length()) {
                ret.put(e.getKey().substring(head.length()), e.getValue());
            }
        }
        return ret;
    }

    /** Refuse a param this driver does not know -- there is no further AWS call to hand it to, and dropping
     *  it in silence is how a leg ends up running without a setting the topology says it has. */
    private static void requireKnownParams(Map<String, String> params) {
        for (String key : params.keySet()) {
            int dot = key.indexOf('.');
            boolean ok;
            if (dot > 0) {
                ok = KNOWN_GROUPS.contains(key.substring(0, dot));
            } else {
                ok = KNOWN_PARAMS.contains(key);
            }
            if (!ok) {
                throw new IllegalStateException("tp-kinesis: unknown transport param '" + key
                        + "'; expected one of " + KNOWN_PARAMS + " or a key under " + KNOWN_GROUPS);
            }
        }
    }

    /** A Kinesis client for this leg. The endpoint is set only when the topology gives one -- the LocalStack
     *  case; against real AWS it is left empty and the SDK builds it from the region. Credentials always come
     *  from the SDK default chain, so none is ever written into a file. */
    static KinesisClient client(String endpoint, Map<String, String> params, Map<String, String> clientGroup) {
        KinesisClientBuilder builder = KinesisClient.builder();
        String region = params.get(PARAM_REGION);
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region.trim()));
        }
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        if (clientGroup != null && !clientGroup.isEmpty()) {
            builder.overrideConfiguration(clientOverride(clientGroup));
        }
        return builder.build();
    }

    private static ClientOverrideConfiguration clientOverride(Map<String, String> group) {
        ClientOverrideConfiguration.Builder builder = ClientOverrideConfiguration.builder();
        for (Map.Entry<String, String> e : group.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (CLIENT_API_CALL_TIMEOUT.equals(key)) {
                builder.apiCallTimeout(Duration.ofMillis(Long.parseLong(value.trim())));
            } else if (CLIENT_API_CALL_ATTEMPT_TIMEOUT.equals(key)) {
                builder.apiCallAttemptTimeout(Duration.ofMillis(Long.parseLong(value.trim())));
            } else if (CLIENT_MAX_ATTEMPTS.equals(key)) {
                int attempts = Integer.parseInt(value.trim());
                builder.retryStrategy(b -> b.maxAttempts(attempts));
            } else {
                throw new IllegalStateException("tp-kinesis: unknown client param 'client." + key
                        + "'; the SDK client takes typed settings, not a verbatim map");
            }
        }
        return builder.build();
    }

    /** Make the stream when it is not there, and wait until it can take records. On-demand capacity, so there
     *  is no shard count to pick: the stream grows and shrinks by itself, and an idle one costs nothing to
     *  keep. A stream that already exists is left exactly as it is. */
    static void ensureStream(KinesisClient kinesis, String stream, Map<String, String> settings) {
        // ASK before creating. A receive leg calls this on every retry while it waits for a stream to come
        // back, so the common answer -- "it is already there" -- has to be cheap and quiet. Creating first and
        // catching ResourceInUse would work, but it writes a line every time round the loop.
        boolean present = describes(kinesis, stream);
        if (!present) {
            boolean made = false;
            try {
                CreateStreamRequest request = CreateStreamRequest.builder()
                        .streamName(stream)
                        .streamModeDetails(StreamModeDetails.builder().streamMode(StreamMode.ON_DEMAND).build())
                        .build();
                kinesis.createStream(request);
                made = true;
            } catch (ResourceInUseException already) {
                // somebody else made it between the ask and the create
                devLog.info("tp-kinesis: stream in use: {}", stream);
            }
            if (made) {
                awaitActive(kinesis, stream);
                devLog.info("tp-kinesis: stream created: {}", stream);
            }
            applyStreamSettings(kinesis, stream, settings);
        }
    }

    /** Whether the stream is there. A missing one is an ANSWER, not a failure; anything else is a real error
     *  and is left to the caller, which reports the leg DOWN and tries again. */
    private static boolean describes(KinesisClient kinesis, String stream) {
        boolean ret = true;
        try {
            kinesis.describeStreamSummary(DescribeStreamSummaryRequest.builder().streamName(stream).build());
        } catch (ResourceNotFoundException absent) {
            ret = false;
        }
        return ret;
    }

    /** The {@code stream.} group. Kinesis takes these as typed calls of their own, not as attributes on the
     *  create, so each is named here and an unknown one is refused. */
    private static void applyStreamSettings(KinesisClient kinesis, String stream, Map<String, String> settings) {
        if (settings != null) {
            for (Map.Entry<String, String> e : settings.entrySet()) {
                if (STREAM_RETENTION_HOURS.equals(e.getKey())) {
                    increaseRetention(kinesis, stream, Integer.parseInt(e.getValue().trim()));
                } else {
                    throw new IllegalStateException("tp-kinesis: unknown stream param 'stream." + e.getKey()
                            + "'; Kinesis takes stream settings as typed calls, not a verbatim map");
                }
            }
        }
    }

    /** Kinesis only ever RAISES retention through this call; asking for what the stream already has is an
     *  error, and one that must not stop a leg opening. */
    private static void increaseRetention(KinesisClient kinesis, String stream, int hours) {
        try {
            IncreaseStreamRetentionPeriodRequest request = IncreaseStreamRetentionPeriodRequest.builder()
                    .streamName(stream)
                    .retentionPeriodHours(hours)
                    .build();
            kinesis.increaseStreamRetentionPeriod(request);
            devLog.info("tp-kinesis: stream {} retention raised to {}h", stream, hours);
        } catch (Exception ex) {
            devLog.info("tp-kinesis: stream {} retention left as it is ({})", stream, ex.getMessage());
        }
    }

    /** A stream is CREATING for a moment after it is made, and a record sent into that window is refused. */
    private static void awaitActive(KinesisClient kinesis, String stream) {
        long deadline = System.currentTimeMillis() + STREAM_READY_TIMEOUT_MS;
        boolean active = false;
        while (!active && System.currentTimeMillis() < deadline) {
            DescribeStreamSummaryRequest request = DescribeStreamSummaryRequest.builder()
                    .streamName(stream)
                    .build();
            String status = kinesis.describeStreamSummary(request).streamDescriptionSummary()
                    .streamStatusAsString();
            if ("ACTIVE".equals(status)) {
                active = true;
            } else {
                sleepQuietly();
            }
        }
        if (!active) {
            throw new IllegalStateException("tp-kinesis: stream " + stream + " did not become ACTIVE in time");
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(STREAM_POLL_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** A Kinesis stream name takes letters, digits, hyphen, underscore and dot -- but the bus destinations are
     *  dotted and a dot reads badly in a stream name, so the same rule the queue names use is applied here:
     *  everything outside letters, digits, hyphen and underscore becomes a hyphen. */
    static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (keep) {
                out.append(c);
            } else {
                out.append('-');
            }
        }
        return out.toString();
    }

    /** The Kinesis publisher handle. The send-retry seam: {@link #encode} prepares the broker-free bag with a
     *  stable ApplMsgID minted once, {@link #dispatch} writes it as one record and THROWS on a failure. The
     *  whole bag is the record's data, as JSON -- the stream IS the log, so what is written is what is read. */
    private static final class KinesisPublisher implements TransportPublisher {

        private final KinesisClient kinesis;
        private final String stream;
        private final String partitionBy;
        private final Map<String, String> streamSettings;
        private final ObjectMapper objectMapper;
        private final AtomicReference<TransportHealth> conn;

        /** Set once the stream is known to be there. Cleared if it turns out not to be. */
        private volatile boolean streamReady;

        KinesisPublisher(KinesisClient kinesis, String stream, String partitionBy,
                         Map<String, String> streamSettings, ObjectMapper objectMapper,
                         AtomicReference<TransportHealth> conn) {
            this.kinesis        = kinesis;
            this.stream         = stream;
            this.partitionBy    = partitionBy;
            this.streamSettings = streamSettings;
            this.objectMapper   = objectMapper;
            this.conn           = conn;
        }

        @Override
        public Object encode(TransportMessage message) {
            // the broker-free prepared unit: keep a STABLE ApplMsgID (a held event's resend reuses it = dedup-able),
            // mint one only when absent. SendingTime is per physical send -> dispatch.
            Map<String, Object> props = new LinkedHashMap<>(message.headers());
            Object applMsgId = props.get(BusConstants.FIELD_APPL_MSG_ID);
            if (applMsgId == null) {
                props.put(BusConstants.FIELD_APPL_MSG_ID, UUID.randomUUID().toString());
            }
            return props;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void dispatch(Object encoded) throws Exception {
            Map<String, Object> props = (Map<String, Object>) encoded;
            props.put(BusConstants.FIELD_SENDING_TIME, Instant.now().toString());
            String key = partitionKey(props);
            SdkBytes data = SdkBytes.fromUtf8String(objectMapper.writeValueAsString(props));
            try {
                try {
                    put(key, data);
                } catch (ResourceNotFoundException gone) {
                    // The stream is gone -- removed under a running leg, or never made because AWS was not
                    // answering when this leg opened. Make it and send again.
                    devLog.warn("tp-kinesis: stream {} is not there; making it", stream);
                    streamReady = false;
                    put(key, data);
                }
                conn.set(TransportHealth.UP);
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);
                throw ex;
            }
        }

        /** One record, ensuring the stream exists the first time round. */
        private void put(String key, SdkBytes data) {
            if (!streamReady) {
                ensureStream(kinesis, stream, streamSettings);
                streamReady = true;
            }
            PutRecordRequest request = PutRecordRequest.builder()
                    .streamName(stream)
                    .partitionKey(key)
                    .data(data)
                    .build();
            kinesis.putRecord(request);
        }

        @Override
        public void accept(TransportMessage message) {
            try {
                dispatch(encode(message));
            } catch (Exception ex) {
                devLog.error("tp-kinesis: put failed on {}: {}", stream, ex.getMessage(), ex);
            }
        }

        @Override
        public TransportHealth health() {
            return conn.get();
        }

        @Override
        public void close() {
            kinesis.close();
        }

        /** Every record needs a partition key -- Kinesis has no "unpartitioned" -- so FIFO is expressed as one
         *  CONSTANT key for the whole stream. With a header named instead, the value off that header spreads the
         *  records; a message not carrying it gets a random key, which spreads it rather than refusing it, since
         *  a record is worth more written unordered than not written at all. */
        private String partitionKey(Map<String, Object> props) {
            String ret = null;
            if (partitionBy == null) {
                ret = stream;
            } else {
                Object value = props.get(partitionBy);
                if (value != null && !value.toString().isBlank()) {
                    ret = value.toString();
                } else {
                    ret = UUID.randomUUID().toString();
                }
            }
            return ret;
        }
    }
}

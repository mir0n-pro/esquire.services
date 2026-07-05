/*
 *  Esquire frameworks (tm)
 *  tp-redis -- transport provider (Redis stream)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/12/2026 mir0n  created (as RedisTransportProvider): the Redis ITransportProvider (option d). openPublisher
 *                   XADDs each TransportMessage's header bag to the destination stream (the stream IS the
 *                   append-only log), optionally approximate-trimmed to max-len. Producer-only.
 * 06/13/2026 mir0n  class-name-driven SPI: renamed to the conventional pro.mir0n.esquire.tp.redis.
 *                   TransportProvider; producer-only declared via supportsConsume()=false (no vendor constant);
 *                   builds its OWN Lettuce connection from settings.endpoint(); the redis-only max-len comes
 *                   from the provider's param group (transport.redis.max-len), read via settings.params().
 * 06/17/2026 mir0n  openPublisher returns a TransportPublisher (close() destroys the LettuceConnectionFactory)
 * 06/22/2026 mir0n  openConsumer signature returns a TransportConsumer (SPI two-phase); still producer-only --
 *                   throws UnsupportedOperationException
 * 06/22/2026 mir0n  send-outcome health on the publisher handle: no clean connection callback, so a good XADD
 *                   -> UP, a failed XADD -> DOWN (best-effort; producer-only stream).
 * 06/24/2026 mir0n  session (alive) messages routed to a separate <destination>.admin stream (streamFor; a capped
 *                   admin stream) so the append-only log stream keeps only real records; supportsBothLegs() = false
 * 06/30/2026 mir0n  RedisPublisher (extracted class) implements the send-retry seam: encode() prepares the
 *                   broker-free property bag (a stable ApplMsgID minted ONCE, absent-only), dispatch() builds the
 *                   stream record + XADDs THROWING on a failure (+ SendingTime per physical send), accept() the
 *                   best-effort path, health() / close() on the handle
 */
package pro.mir0n.esquire.tp.redis;

import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Redis-stream implementation of the transport-provider SPI. Producer-only. */
public final class TransportProvider implements ITransportProvider {

    /** Redis-only OPERATION param (transport.params.max-len): approximate stream MAXLEN; 0 = unbounded. It is an
     *  XADD option, NOT a connection param, so it is excluded from the connection URI below. */
    public static final String PARAM_MAX_LEN = "max-len";

    /** Session (alive-protocol) messages -- HeartBeat / TestRequest -- are connectivity probes, NOT log data, so
     *  they go to a SEPARATE {@code <destination>.admin} stream rather than the append-only log stream. Redis has
     *  no server-side content filter (unlike a Kafka-Connect sink SMT), so this producer-side split by stream key
     *  is the ONLY way to keep the log stream to real records (e.g. UA audit) -- a Redis-specific transport concern,
     *  not a session-layer one. NOTE (ops): the {@code .admin} stream is a throwaway liveness channel and is best
     *  EXCLUDED from any downstream logging / persistence (it carries no records). */
    public static final String ADMIN_STREAM_SUFFIX = ".admin";

    /** The {@code .admin} liveness stream is capped tight (approximate trim) -- only the latest probe matters, so
     *  it never grows. */
    private static final long ADMIN_STREAM_MAXLEN = 1L;

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.redis.TransportProvider");

    public TransportProvider() {
    }

    @Override
    public boolean supportsConsume() {
        return false;   // the stream IS the append-only log (read via XRANGE); there is no xxRod consume leg
    }

    @Override
    public boolean supportsBothLegs() {
        return false;   // XADD-only (produce-only) -- a single rod cannot also receive here, so no CLIENT role
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        long maxLen = s.paramLong(PARAM_MAX_LEN, 0L);
        StringRedisTemplate redis = buildTemplate(s.endpoint(), s.params());
        devLog.info("tp-redis: publisher opened on stream {} (endpoint={}, maxLen={})", destination, s.endpoint(), maxLen);

        // close() disposes the Lettuce connection factory built above (a DisposableBean).
        AutoCloseable closer = (redis.getConnectionFactory() instanceof DisposableBean db) ? db::destroy : () -> { };
        // health is XADD send-outcome (producer-only stream): a failed XADD -> DOWN, a good one -> UP.
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UP);
        return new RedisPublisher(redis, destination, maxLen, closer, conn);
    }

    /** The Redis-stream publisher handle. The send-retry seam: {@link #encode} prepares the BROKER-FREE unit (the
     *  property bag with a STABLE ApplMsgID minted ONCE), {@link #dispatch} builds the stream record from it and
     *  XADDs, THROWING on a failure (the retry signal) and flipping the health indicator. A held event's resend
     *  relays the SAME bag (no re-encode); the per-physical-send SendingTime is stamped in dispatch. {@link #accept}
     *  is the best-effort (retry-off) path. */
    private static final class RedisPublisher implements TransportPublisher {
        private final StringRedisTemplate redis;
        private final String destination;
        private final long maxLen;
        private final AutoCloseable closer;
        private final AtomicReference<TransportHealth> conn;

        RedisPublisher(StringRedisTemplate redis, String destination, long maxLen, AutoCloseable closer,
                       AtomicReference<TransportHealth> conn) {
            this.redis       = redis;
            this.destination = destination;
            this.maxLen      = maxLen;
            this.closer      = closer;
            this.conn        = conn;
        }

        @Override
        public Object encode(TransportMessage message) {
            // the broker-free prepared unit: keep a STABLE ApplMsgID (a held event's resend reuses it = dedup-able),
            // mint one only when absent. SendingTime is per physical send -> dispatch.
            Map<String, Object> props = new LinkedHashMap<>(message.headers());
            props.computeIfAbsent(BusConstants.FIELD_APPL_MSG_ID, k -> UUID.randomUUID().toString());
            return props;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void dispatch(Object encoded) throws Exception {
            Map<String, Object> props = (Map<String, Object>) encoded;
            Map<String, String> fields = new LinkedHashMap<>();
            props.forEach((k, v) -> {
                if (v != null) {
                    fields.put(k, v.toString());   // stream fields are strings; null fields are omitted
                }
            });
            fields.put(BusConstants.FIELD_SENDING_TIME, Instant.now().toString());

            // a session (alive) message rides to <destination>.admin, never the log stream -- the log keeps only
            // real records. A capped admin stream (latest probe only). An app message goes to the log.
            Object msgType = props.get(BusConstants.FIELD_MSG_TYPE);
            String streamKey = streamFor(destination, msgType != null ? msgType.toString() : null);
            boolean admin = !streamKey.equals(destination);

            MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(streamKey);
            try {
                if (admin) {
                    redis.opsForStream().add(record, XAddOptions.maxlen(ADMIN_STREAM_MAXLEN).approximateTrimming(true));
                } else if (maxLen > 0) {
                    redis.opsForStream().add(record, XAddOptions.maxlen(maxLen).approximateTrimming(true));
                } else {
                    redis.opsForStream().add(record);
                }
                conn.set(TransportHealth.UP);
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);
                throw ex;
            }
        }

        @Override
        public void accept(TransportMessage message) {
            try {
                dispatch(encode(message));
            } catch (Exception ex) {
                devLog.error("tp-redis: XADD failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }

        @Override
        public TransportHealth health() {
            return conn.get();
        }

        @Override
        public void close() throws Exception {
            closer.close();
        }
    }

    /** The stream a message rides by its {@code MsgType}: a session (alive) message -- HeartBeat / TestRequest --
     *  goes to the {@code <destination>.admin} liveness stream; every other message (e.g. UA audit) goes to the
     *  {@code destination} log stream. Keeps the append-only log to real records, since Redis cannot filter
     *  server-side. */
    static String streamFor(String destination, String msgType) {
        String ret;
        if (RodEvent.isSession(msgType)) {
            ret = destination + ADMIN_STREAM_SUFFIX;
        } else {
            ret = destination;
        }
        return ret;
    }

    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        throw new UnsupportedOperationException(
                "tp-redis is producer-only: the stream is the append-only log (read with XRANGE); no consumer");
    }

    /** Build a started StringRedisTemplate over a Lettuce connection to {@code endpoint}. Every leg {@code params}
     *  entry EXCEPT {@code max-len} (an XADD op) is appended to the {@code redis://} URI as a connection option,
     *  so Lettuce applies it verbatim (database / timeout / ...); the endpoint authority carries host/port/password.
     *  The audit stream owns this connection -- it is not the app's shared redis. */
    private static StringRedisTemplate buildTemplate(String endpoint, Map<String, String> params) {
        StringBuilder uri = new StringBuilder(
                endpoint.startsWith("redis://") || endpoint.startsWith("rediss://") ? endpoint : "redis://" + endpoint);
        boolean q = uri.indexOf("?") >= 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (PARAM_MAX_LEN.equals(e.getKey())) {
                continue;   // XADD option, not a connection param
            }
            uri.append(q ? '&' : '?').append(e.getKey()).append('=').append(e.getValue());
            q = true;
        }
        RedisURI r = RedisURI.create(uri.toString());
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(r.getHost(), r.getPort());
        cfg.setDatabase(r.getDatabase());
        if (r.getPassword() != null && r.getPassword().length > 0) {
            cfg.setPassword(RedisPassword.of(new String(r.getPassword())));
        }
        LettuceClientConfiguration client = LettuceClientConfiguration.builder().commandTimeout(r.getTimeout()).build();
        LettuceConnectionFactory cf = new LettuceConnectionFactory(cfg, client);
        cf.afterPropertiesSet();
        cf.start();
        StringRedisTemplate template = new StringRedisTemplate(cf);
        template.afterPropertiesSet();
        return template;
    }
}

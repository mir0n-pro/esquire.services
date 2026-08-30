/*
 *  Esquire frameworks (tm)
 *  tp-kinesis -- transport provider (Amazon Kinesis)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the Kinesis receive leg -- one poll thread per shard over GetRecords. A stream
 *                   keeps nobody position, so the leg holds its own in memory and iterator-type says where a
 *                   restart begins. A failed iterator is RETRIED, and the stream re-made if it is gone; a
 *                   shard ends only when a successful GetRecords hands back no next iterator.
 */
package pro.mir0n.esquire.tp.kinesis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The Kinesis receive leg: one poll thread per shard, reading with {@code GetRecords}.
 *
 * <p><b>Why this does not look like the SQS consumer.</b> A queue is one place to read from and the broker
 * keeps everyone's position. A Kinesis stream is several SHARDS, and it keeps nobody's position -- the reader
 * holds an iterator and is responsible for where it is. So this discovers the shards at start and runs a
 * thread on each, and the iterator lives in that thread.
 *
 * <p><b>What is NOT here: a checkpoint store.</b> The Kinesis Client Library keeps positions in a DynamoDB
 * lease table -- a second AWS service, a table per application, and its own bill. That is refused: the audit
 * bus already accepts the same loss over ActiveMQ and SNS (a broker restart drops whatever auKeep has not
 * drained). So the position lives in memory, and on a restart the leg begins where {@code iterator-type} says.
 * The default is {@code TRIM_HORIZON} -- the whole retained window, re-reading rather than silently skipping,
 * because a duplicated audit row is a smaller wrong than a missing one.
 */
public final class KinesisConsumer implements TransportConsumer {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.kinesis.KinesisConsumer");

    private static final TypeReference<Map<String, Object>> BAG = new TypeReference<>() { };

    private final KinesisClient kinesis;
    private final String stream;
    private final Map<String, String> streamSettings;
    private final Consumer<TransportMessage> handler;
    private final ObjectMapper objectMapper;
    private final ShardIteratorType iteratorType;
    private final long pollMs;
    private final int limit;
    private final AtomicReference<TransportHealth> conn;

    private volatile boolean running;
    private volatile Thread bootstrap;
    private ExecutorService pollers;

    public KinesisConsumer(KinesisClient kinesis, String stream, Map<String, String> streamSettings,
                           Consumer<TransportMessage> handler, ObjectMapper objectMapper,
                           ShardIteratorType iteratorType, long pollMs, int limit,
                           AtomicReference<TransportHealth> conn) {
        this.kinesis        = kinesis;
        this.stream         = stream;
        this.streamSettings = streamSettings;
        this.handler        = handler;
        this.objectMapper   = objectMapper;
        this.iteratorType   = iteratorType;
        this.pollMs         = pollMs;
        this.limit          = limit;
        this.conn           = conn;
    }

    /** Discover the shards and put a poll thread on each. Created PAUSED, like every other consumer leg: nothing
     *  is read until the bus starts this. */
    @Override
    public void start() {
        running = true;
        // The shards cannot be listed until the stream exists and AWS is answering, and NEITHER is guaranteed
        // at start: a service must be able to come up against a transport that is merely down. So discovery
        // runs on its own thread and keeps trying, leaving this leg DOWN until it succeeds, rather than
        // throwing and taking the service with it.
        bootstrap = new Thread(this::discoverAndPoll, "tp-kinesis." + stream + "-start");
        bootstrap.setDaemon(true);
        bootstrap.start();
    }

    /** Ensure the stream, list its shards, put a poll thread on each. Retries until it works, or close(). */
    private void discoverAndPoll() {
        List<String> shards = null;
        while (running && shards == null) {
            try {
                TransportProvider.ensureStream(kinesis, stream, streamSettings);
                shards = shardIds();
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);
                devLog.warn("tp-kinesis: cannot open {} yet ({}); trying again", stream, ex.getMessage());
                shards = null;
                idle();
            }
        }
        if (running && shards != null && !shards.isEmpty()) {
            pollers = Executors.newFixedThreadPool(shards.size(), new PollThreads(stream));
            for (String shardId : shards) {
                pollers.submit(() -> pollLoop(shardId));
            }
            devLog.info("tp-kinesis: consumer started on {} ({} shards, from {})", stream, shards.size(), iteratorType);
        }
    }

    @Override
    public TransportHealth health() {
        return conn.get();
    }

    @Override
    public void close() {
        running = false;
        if (bootstrap != null) {
            bootstrap.interrupt();
        }
        if (pollers != null) {
            pollers.shutdownNow();
            try {
                pollers.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        kinesis.close();
    }

    /** The shards of the stream. An on-demand stream is made with several, so this is never a single-shard
     *  assumption -- reading one of four is reading a quarter of the audit. */
    private List<String> shardIds() {
        List<String> ret = new ArrayList<>();
        ListShardsRequest request = ListShardsRequest.builder().streamName(stream).build();
        List<Shard> shards = kinesis.listShards(request).shards();
        for (Shard shard : shards) {
            ret.add(shard.shardId());
        }
        return ret;
    }

    /** One shard, read to its end. The iterator that comes back from each GetRecords is where the next one
     *  starts; a null one means the shard was closed by a reshard, and the thread ends there. */
    private void pollLoop(String shardId) {
        String iterator = null;
        boolean closed = false;
        while (running && !closed) {
            if (iterator == null) {
                // Null means "not held": either this thread has just begun, or the last attempt failed. It is
                // NOT a reason to stop. Reading it as one is what once ended the thread for good on a moment's
                // outage -- the leg then had nobody polling when AWS came back, and only a restart fixed it.
                // A shard is finished ONLY when a SUCCESSFUL GetRecords hands back no next iterator.
                // Make the stream if it is not there. A queue is re-made by the SQS consumer for exactly
                // this reason, and a stream needs the same: after an outage the stream is gone, and nothing
                // else will bring it back until some PRODUCER happens to write. Without this, a drained bus
                // stays DOWN for as long as the fleet is quiet, then heals only by accident.
                try {
                    TransportProvider.ensureStream(kinesis, stream, streamSettings);
                } catch (Exception ex) {
                    conn.set(TransportHealth.DOWN);
                }
                iterator = shardIterator(shardId);
                if (iterator == null) {
                    idle();
                }
            } else {
                try {
                    GetRecordsRequest request = GetRecordsRequest.builder()
                            .shardIterator(iterator)
                            .limit(limit)
                            .build();
                    GetRecordsResponse response = kinesis.getRecords(request);
                    conn.set(TransportHealth.UP);
                    for (Record record : response.records()) {
                        deliver(record);
                    }
                    iterator = response.nextShardIterator();
                    if (iterator == null) {
                        closed = true;      // a reshard closed this shard -- the one clean end
                    } else if (response.records().isEmpty()) {
                        // Kinesis answers an empty read at once and caps GetRecords at five a second per
                        // shard, so a poll with nothing in it has to wait or it becomes a hot loop against
                        // the throttle.
                        idle();
                    }
                } catch (Exception ex) {
                    conn.set(TransportHealth.DOWN);
                    if (running) {
                        devLog.error("tp-kinesis: receive failed on {} shard {}: {}",
                                stream, shardId, ex.getMessage(), ex);
                        idle();
                        iterator = null;    // take a fresh one next turn; the held one may be the stale thing
                    }
                }
            }
        }
        devLog.info("tp-kinesis: shard {} of {} no longer read", shardId, stream);
    }

    private String shardIterator(String shardId) {
        String ret = null;
        try {
            GetShardIteratorRequest request = GetShardIteratorRequest.builder()
                    .streamName(stream)
                    .shardId(shardId)
                    .shardIteratorType(iteratorType)
                    .build();
            ret = kinesis.getShardIterator(request).shardIterator();
        } catch (Exception ex) {
            conn.set(TransportHealth.DOWN);
            devLog.error("tp-kinesis: no iterator for {} shard {}: {}", stream, shardId, ex.getMessage(), ex);
        }
        return ret;
    }

    /** One record to the handler. The record data IS the header bag as JSON -- what the publisher wrote. A
     *  record that cannot be read is logged and dropped: it cannot be sent back and holding the shard on it
     *  would stop every record behind it. */
    private void deliver(Record record) {
        try {
            String body = new String(record.data().asByteArray(), StandardCharsets.UTF_8);
            Map<String, Object> headers = objectMapper.readValue(body, BAG);
            handler.accept(new TransportMessage(headers, null));
        } catch (Exception ex) {
            devLog.error("tp-kinesis: unreadable record on {} (seq {}): {}",
                    stream, record.sequenceNumber(), ex.getMessage(), ex);
        }
    }

    private void idle() {
        try {
            Thread.sleep(pollMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    /** Named threads: the thread name is a field on every log line, and "pool-3-thread-1" says nothing about
     *  which stream stopped reading. */
    private static final class PollThreads implements ThreadFactory {

        private final String stream;
        private final AtomicInteger seq = new AtomicInteger();

        PollThreads(String stream) {
            this.stream = stream;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread ret = new Thread(r, "tp-kinesis." + stream + "-" + seq.incrementAndGet());
            ret.setDaemon(true);
            return ret;
        }
    }
}

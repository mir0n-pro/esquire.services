/*
 *  Esquire frameworks (tm)
 *  tp-sqns -- transport provider (Amazon SQS / Amazon SNS)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the long-poll receive leg both providers use -- named poll threads, the delete as
 *                   the acknowledgement (sent only after the handler returned), and a queue that went away
 *                   made again on the next turn, then re-wired by the hook the leg supplies.
 */
package pro.mir0n.esquire.tp.sqns;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** The SQS receive leg: long-poll threads that decode each message body into the neutral header bag and hand
 *  it to the rod. */
public final class SqsConsumer implements TransportConsumer {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.sqns.SqsConsumer");

    private static final TypeReference<Map<String, Object>> HEADERS = new TypeReference<>() {
    };

    /** How long a poll thread waits after a receive failed, so a broken endpoint does not spin. */
    private static final long ERROR_PAUSE_MS = 1000L;

    private final SqsClient sqs;
    private final String queueName;
    private final Consumer<TransportMessage> handler;
    private final ObjectMapper objectMapper;
    private final int waitSeconds;
    private final int batchSize;
    private final int threads;
    private final AtomicReference<TransportHealth> conn;
    /** Run whenever this leg has to MAKE its queue again. SQS needs nothing; SNS re-subscribes the new queue
     *  to the topic, so a queue that came back is wired, not merely present. Null when there is nothing to do. */
    private final Consumer<String> onQueueCreated;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService pool;

    /** Cleared when the queue turns out to be gone, so the next turn of the poll loop makes it again. A leg
     *  that cannot re-establish its own queue is dead for good, which is the worst thing a transport can be. */
    private final Map<String, String> queueAttributes;
    private volatile String queueUrl;

    public SqsConsumer(SqsClient sqs, String queueName, String queueUrl, Map<String, String> queueAttributes,
                       Consumer<TransportMessage> handler,
                       ObjectMapper objectMapper, int waitSeconds, int batchSize, int threads,
                       AtomicReference<TransportHealth> conn, Consumer<String> onQueueCreated) {
        this.sqs          = sqs;
        this.queueName    = queueName;
        this.queueUrl        = queueUrl;
        this.queueAttributes = queueAttributes;
        this.handler      = handler;
        this.objectMapper = objectMapper;
        this.waitSeconds  = waitSeconds;
        this.batchSize    = batchSize;
        this.threads      = threads;
        this.conn           = conn;
        this.onQueueCreated = onQueueCreated;
        this.pool           = Executors.newFixedThreadPool(threads, new PollThreads(queueName));
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            for (int i = 0; i < threads; i++) {
                pool.submit(this::pollLoop);
            }
            devLog.info("tp-sqs: consumer started on {} (threads={}, wait={}s, batch={})",
                    queueName, threads, waitSeconds, batchSize);
        }
    }

    @Override
    public TransportHealth health() {
        return conn.get();
    }

    @Override
    public void close() {
        running.set(false);
        pool.shutdownNow();   // interrupts the long poll; the loop then sees running == false and ends
        sqs.close();
        devLog.info("tp-sqs: consumer closed on {}", queueName);
    }

    private void pollLoop() {
        while (running.get()) {
            String url = null;
            try {
                url = queue();
                ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                        .queueUrl(url)
                        .maxNumberOfMessages(batchSize)
                        .waitTimeSeconds(waitSeconds)
                        .build();
                List<Message> received = sqs.receiveMessage(request).messages();
                conn.set(TransportHealth.UP);
                for (Message message : received) {
                    deliver(url, message);
                }
            } catch (QueueDoesNotExistException gone) {
                // The queue was removed under us. Forget the URL so the next turn makes the queue again --
                // without this the leg never comes back, which is worse than any single lost message.
                if (running.get()) {
                    conn.set(TransportHealth.DOWN);
                    queueUrl = null;
                    devLog.warn("tp-sqs: queue {} is gone; making it again", queueName);
                    pause();
                }
            } catch (Exception ex) {
                // A shutdown interrupts the poll, and that throw is not a failure -- running is already false.
                if (running.get()) {
                    conn.set(TransportHealth.DOWN);
                    devLog.error("tp-sqs: receive failed on {}: {}", queueName, ex.getMessage(), ex);
                    pause();
                }
            }
        }
    }

    /** This leg's queue URL, made again when it was forgotten. CreateQueue returns the queue that is already
     *  there, so the ordinary path costs nothing. */
    private String queue() {
        String ret = queueUrl;
        if (ret == null) {
            ret = SqsSupport.createQueue(sqs, queueName, queueAttributes);
            if (onQueueCreated != null) {
                onQueueCreated.accept(ret);
            }
            queueUrl = ret;
        }
        return ret;
    }

    private void deliver(String url, Message message) {
        boolean applied = false;
        try {
            Map<String, Object> headers = objectMapper.readValue(message.body(), HEADERS);
            handler.accept(new TransportMessage(headers, null));
            applied = true;
        } catch (Exception ex) {
            devLog.error("tp-sqs: consume failed on {}: {}", queueName, ex.getMessage(), ex);
        }
        // The delete IS the acknowledgement, and it happens only once the handler returned. A message whose
        // handling failed stays hidden for the visibility timeout and is then delivered again -- the only
        // redelivery SQS offers.
        if (applied) {
            DeleteMessageRequest delete = DeleteMessageRequest.builder()
                    .queueUrl(url)
                    .receiptHandle(message.receiptHandle())
                    .build();
            try {
                sqs.deleteMessage(delete);
            } catch (Exception ex) {
                // The handler DID apply this message and only the acknowledgement failed, so it comes back
                // after the visibility timeout and is applied a second time. Name the operation that failed:
                // reporting it as a receive would send a reader looking in the wrong place.
                conn.set(TransportHealth.DOWN);
                devLog.error("tp-sqs: delete (the acknowledgement) failed on {}: {}", queueName, ex.getMessage(), ex);
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(ERROR_PAUSE_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    /** Poll threads named after the queue they read. The thread name is a field on every log line, so an
     *  anonymous pool-N-thread-M would leave the busiest threads in the service unattributable. */
    private static final class PollThreads implements ThreadFactory {

        private final String queue;
        private final AtomicInteger seq = new AtomicInteger();

        PollThreads(String queue) {
            this.queue = queue;
        }

        @Override
        public Thread newThread(Runnable body) {
            Thread ret = new Thread(body, "tp-sqs." + queue + "-" + seq.incrementAndGet());
            ret.setDaemon(true);
            return ret;
        }
    }
}

/*
 *  Esquire frameworks (tm)
 *  tp-sqns -- transport provider (Amazon SQS / Amazon SNS)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the Amazon SQS ITransportProvider. route-by turns the filter a JMS selector used
 *                   to apply into a DESTINATION -- a queue per rod-id or per slot-id; the whole header bag
 *                   rides as the message body, since SQS allows ten attributes and the bag carries twenty.
 *                   Nothing is asked of AWS when a leg opens: the queue is made on the first poll.
 */
package pro.mir0n.esquire.tp.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;
import pro.mir0n.esquire.tp.sqns.SqsConsumer;
import pro.mir0n.esquire.tp.sqns.SqsSupport;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Amazon SQS implementation of the transport-provider SPI. */
public final class TransportProvider implements ITransportProvider {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.sqs.TransportProvider");

    private static final int DEFAULT_WAIT_SECONDS = 20;   // the SQS maximum; a shorter wait only costs more calls
    private static final int DEFAULT_BATCH_SIZE   = 10;   // the SQS maximum per receive

    /** The bare param keys this driver owns; anything else must name its AWS call with a prefix. */
    private static final Set<String> KNOWN_PARAMS = Set.of(
            SqsSupport.PARAM_REGION, SqsSupport.PARAM_ROUTE_BY,
            SqsSupport.PARAM_WAIT_SECONDS, SqsSupport.PARAM_BATCH_SIZE, BusConstants.PARAM_NO_LOCAL);

    private static final Set<String> KNOWN_GROUPS = Set.of(SqsSupport.GROUP_CLIENT, SqsSupport.GROUP_QUEUE);

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        SqsSupport.requireKnownParams("tp-sqs", s.params(), KNOWN_PARAMS, KNOWN_GROUPS);
        SqsClient sqs = SqsSupport.client(s.endpoint(), s.params(), SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_CLIENT));
        String routeBy = s.param(SqsSupport.PARAM_ROUTE_BY, null);
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        devLog.info("tp-sqs: publisher opened on {} (endpoint={}, routeBy={})", destination, s.endpoint(), routeBy);
        return new SqsPublisher(sqs, destination, routeBy, SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_QUEUE),
                s.objectMapper(), conn);
    }

    /** The consumer for this leg. {@code settings.selector()} is ignored on purpose: SQS has no message
     *  selector, and the split the selector used to make is the queue this leg reads (see route-by). */
    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        SqsSupport.requireKnownParams("tp-sqs", s.params(), KNOWN_PARAMS, KNOWN_GROUPS);
        SqsClient sqs = SqsSupport.client(s.endpoint(), s.params(), SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_CLIENT));
        String routeBy = s.param(SqsSupport.PARAM_ROUTE_BY, null);
        Map<String, String> queueAttributes = SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_QUEUE);
        String queueName = SqsSupport.consumeQueueName(destination, routeBy, s.identity());

        int waitSeconds = (int) s.paramLong(SqsSupport.PARAM_WAIT_SECONDS, DEFAULT_WAIT_SECONDS);
        int batchSize = (int) s.paramLong(SqsSupport.PARAM_BATCH_SIZE, DEFAULT_BATCH_SIZE);
        int threads = s.concurrency() > 0 ? s.concurrency() : 1;

        // NOTHING is asked of AWS here -- the queue is made on the first poll instead (SqsConsumer.queue()).
        // Opening a leg must not need AWS to be answering: a service that cannot reach it for a moment at
        // boot has a transport that is DOWN, not a configuration that is wrong, and the two must not share a
        // fate. The config IS still checked above, and still fails fast.
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        devLog.info("tp-sqs: consumer created (paused) on {} (endpoint={}, routeBy={}, queue={})",
                destination, s.endpoint(), routeBy, queueName);
        // no hook: a plain SQS queue needs nothing beyond existing.
        return new SqsConsumer(sqs, queueName, null, queueAttributes, handler, s.objectMapper(),
                waitSeconds, batchSize, threads, conn, null);
    }

    /** The SQS publisher handle. The send-retry seam: {@link #encode} prepares the broker-free bag with a stable
     *  ApplMsgID minted once, {@link #dispatch} resolves the queue from the route-by value on that bag and sends,
     *  THROWING on a failure (the retry signal). The whole bag rides as the message body -- SQS allows at most
     *  ten message attributes and the bag carries about twenty. */
    private static final class SqsPublisher implements TransportPublisher {

        private final SqsClient sqs;
        private final String destination;
        private final String routeBy;
        private final Map<String, String> queueAttributes;
        private final ObjectMapper objectMapper;
        private final AtomicReference<TransportHealth> conn;
        private final Map<String, String> urls = new ConcurrentHashMap<>();

        SqsPublisher(SqsClient sqs, String destination, String routeBy, Map<String, String> queueAttributes,
                     ObjectMapper objectMapper, AtomicReference<TransportHealth> conn) {
            this.sqs             = sqs;
            this.destination     = destination;
            this.routeBy         = routeBy;
            this.queueAttributes = queueAttributes;
            this.objectMapper    = objectMapper;
            this.conn            = conn;
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
            String queueName = SqsSupport.publishQueueName(destination, routeBy, props);
            try {
                try {
                    send(queueName, props);
                } catch (QueueDoesNotExistException gone) {
                    // The queue was removed under us, and the URL we held is stale. Forget it and send once
                    // more: the resolve makes the queue again, so a vanished queue heals on this very message
                    // instead of taking the leg down for good.
                    urls.remove(queueName);
                    send(queueName, props);
                }
                conn.set(TransportHealth.UP);
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);
                throw ex;
            }
        }

        private void send(String queueName, Map<String, Object> props) throws Exception {
            String queueUrl = SqsSupport.queueUrl(sqs, queueName, urls, queueAttributes);
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(props))
                    .build();
            sqs.sendMessage(request);
        }

        @Override
        public void accept(TransportMessage message) {
            try {
                dispatch(encode(message));
            } catch (Exception ex) {
                devLog.error("tp-sqs: publish failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }

        @Override
        public TransportHealth health() {
            return conn.get();
        }

        @Override
        public void close() {
            sqs.close();
        }
    }
}

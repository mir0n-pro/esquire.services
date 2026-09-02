/*
 *  Esquire frameworks (tm)
 *  tp-sqns -- transport provider (Amazon SQS / Amazon SNS)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the Amazon SNS ITransportProvider. SNS has no receive API -- a subscription is an
 *                   address it delivers to, not a consumer that waits -- so a consuming
 *                   leg owns an SQS queue subscribed to the topic and named from its rod-id; the subscription
 *                   selector and noLocal are applied in code by the framework filters. A topic that goes away
 *                   is resolved again on both legs. Nothing is asked of AWS when a leg opens.
 */
package pro.mir0n.esquire.tp.sns;

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
import pro.mir0n.esquire.tp.sqns.SnsSupport;
import pro.mir0n.esquire.tp.sqns.SqsConsumer;
import pro.mir0n.esquire.tp.sqns.SqsSupport;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.NotFoundException;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Amazon SNS implementation of the transport-provider SPI: the fan-out leg of the entity broadcast. */
public final class TransportProvider implements ITransportProvider {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.sns.TransportProvider");

    private static final int DEFAULT_WAIT_SECONDS = 20;   // the SQS maximum on the receive leg
    private static final int DEFAULT_BATCH_SIZE   = 10;   // the SQS maximum per receive

    /** The bare param keys this driver owns; anything else must name its AWS call with a prefix. */
    private static final Set<String> KNOWN_PARAMS = Set.of(
            SqsSupport.PARAM_REGION, SqsSupport.PARAM_ROUTE_BY,
            SqsSupport.PARAM_WAIT_SECONDS, SqsSupport.PARAM_BATCH_SIZE, BusConstants.PARAM_NO_LOCAL);

    private static final Set<String> KNOWN_GROUPS = Set.of(SqsSupport.GROUP_CLIENT, SqsSupport.GROUP_QUEUE,
            SqsSupport.GROUP_TOPIC, SqsSupport.GROUP_SUBSCRIPTION);

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        SqsSupport.requireKnownParams("tp-sns", s.params(), KNOWN_PARAMS, KNOWN_GROUPS);
        SnsClient sns = SnsSupport.client(s.endpoint(), s.params(), SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_CLIENT));
        // The topic is NOT resolved here: it is made on the first publish, and again if it ever goes away.
        // Opening a leg must not need AWS to be answering -- a moment's unreachability at boot is a transport
        // that is DOWN, not a configuration that is wrong.
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        devLog.info("tp-sns: publisher opened on {} (endpoint={})", destination, s.endpoint());
        return new SnsPublisher(sns, null, destination, SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_TOPIC),
                s.objectMapper(), conn);
    }

    /**
     * The receive leg. Nothing subscribes to SNS and waits, so this rod owns a QUEUE of its own -- named from
     * its rod-id, so every instance gets the whole broadcast instead of competing for it -- subscribed to the
     * topic and drained by the shared polling consumer.
     *
     * <p>The x-rod's subscription selector and {@code noLocal} are both applied on this side, and by two
     * SEPARATE filters -- SNS offers nowhere to hang either (a subscription is an address, not a consumer),
     * and they are different questions: what this
     * consumer wants, and whether it wants back what it itself published. Every consumer brings its own
     * subscription; own-exclusion sits in front of any of them.
     */
    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        SqsSupport.requireKnownParams("tp-sns", s.params(), KNOWN_PARAMS, KNOWN_GROUPS);
        Map<String, String> clientGroup = SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_CLIENT);
        SqsClient sqs = SqsSupport.client(s.endpoint(), s.params(), clientGroup);
        SnsClient sns = SnsSupport.client(s.endpoint(), s.params(), clientGroup);

        Map<String, String> queueAttributes = SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_QUEUE);
        Map<String, String> subscriptionAttributes = SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_SUBSCRIPTION);
        String routeBy = s.param(SqsSupport.PARAM_ROUTE_BY, BusConstants.FIELD_ROD_ID);
        String queueName = SqsSupport.consumeQueueName(destination, routeBy, s.identity());

        boolean noLocal = Boolean.parseBoolean(s.param(BusConstants.PARAM_NO_LOCAL, "false"));
        String ownRodId = s.identity() != null ? s.identity().rodId() : null;

        // what a broker would do with a message selector and with noLocal -- two filters, composed, because
        // they answer two different questions and every consumer brings its own subscription.
        Consumer<TransportMessage> receiver = SelectingReceiver.wrap(handler, s.selector());
        receiver = OwnExcluding.wrap(receiver, ownRodId, noLocal);

        int waitSeconds = (int) s.paramLong(SqsSupport.PARAM_WAIT_SECONDS, DEFAULT_WAIT_SECONDS);
        int batchSize = (int) s.paramLong(SqsSupport.PARAM_BATCH_SIZE, DEFAULT_BATCH_SIZE);
        int threads = s.concurrency() > 0 ? s.concurrency() : 1;

        // The queue is made on the first poll and wired HERE, by this hook -- which is also what runs when a
        // queue had to be made again, so first wiring and re-wiring are one path rather than two. A queue that
        // came back unsubscribed would be present but deaf.
        //
        // The arn is resolved INSIDE the closure, never closed over. A queue and its topic go away together,
        // so by the time a queue needs subscribing the topic may name nothing -- and then re-subscribing 404s
        // forever while the queue is re-made every second.
        Map<String, String> topicAttributes = SqsSupport.paramGroup(s.params(), SqsSupport.GROUP_TOPIC);
        Consumer<String> rewire = url -> SnsSupport.subscribeQueue(
                sns, sqs, SnsSupport.topicArn(sns, destination, topicAttributes), url, subscriptionAttributes);

        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        devLog.info("tp-sns: consumer created (paused) on {} (queue={}, noLocal={})", destination, queueName, noLocal);
        return new SqsConsumer(sqs, queueName, null, queueAttributes, receiver, s.objectMapper(),
                waitSeconds, batchSize, threads, conn, rewire);
    }

    /** The SNS publisher handle. Same send-retry seam as the SQS one: {@link #encode} prepares the broker-free
     *  bag with a stable ApplMsgID minted once, {@link #dispatch} publishes it to the topic and THROWS on a
     *  failure. The bag rides as the message body; the rod-id rides as a message ATTRIBUTE as well, because a
     *  subscription filter reads attributes, and that filter is what gives this transport its noLocal. */
    private static final class SnsPublisher implements TransportPublisher {

        private final SnsClient sns;
        private final String destination;
        private final Map<String, String> topicAttributes;
        private final ObjectMapper objectMapper;
        private final AtomicReference<TransportHealth> conn;

        /** Null until the first publish resolves it, and null again when the topic turns out to be gone. */
        private volatile String topicArn;

        SnsPublisher(SnsClient sns, String topicArn, String destination, Map<String, String> topicAttributes,
                     ObjectMapper objectMapper, AtomicReference<TransportHealth> conn) {
            this.sns             = sns;
            this.topicArn        = topicArn;
            this.destination     = destination;
            this.topicAttributes = topicAttributes;
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
            String body = objectMapper.writeValueAsString(props);
            Object rodId = props.get(BusConstants.FIELD_ROD_ID);
            try {
                try {
                    publish(topic(), body, rodId);
                } catch (NotFoundException gone) {
                    // The TOPIC is gone, not the message. An arn names a topic that was removed and re-made
                    // under a running leg, so every later publish would fail for as long as the process lives.
                    // Forget it and resolve again -- CreateTopic returns the existing topic, or makes it.
                    devLog.warn("tp-sns: topic {} is gone; resolving it again", destination);
                    topicArn = null;
                    publish(topic(), body, rodId);
                }
                conn.set(TransportHealth.UP);
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);
                throw ex;
            }
        }

        /** The topic, made on first use. Resolving it at OPEN would have meant a leg could not be opened
         *  while AWS was unreachable -- and a service must be able to start against a transport that is
         *  merely down. CreateTopic returns the existing topic for a name already taken. */
        private String topic() {
            String ret = topicArn;
            if (ret == null) {
                ret = SnsSupport.topicArn(sns, destination, topicAttributes);
                topicArn = ret;
            }
            return ret;
        }

        private void publish(String arn, String body, Object rodId) {
            PublishRequest.Builder request = PublishRequest.builder()
                    .topicArn(arn)
                    .message(body);
            if (rodId != null) {
                request.messageAttributes(rodIdAttribute(rodId.toString()));
            }
            sns.publish(request.build());
        }

        @Override
        public void accept(TransportMessage message) {
            try {
                dispatch(encode(message));
            } catch (Exception ex) {
                devLog.error("tp-sns: publish failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }

        @Override
        public TransportHealth health() {
            return conn.get();
        }

        @Override
        public void close() {
            sns.close();
        }

        /** The publishing rod-id, as a message attribute. The bag in the body already carries it and that is
         *  what the receive filter reads; this is here so anything looking at the topic from outside -- a
         *  console, a future subscription filter -- can tell publishers apart without opening the body. */
        private static Map<String, MessageAttributeValue> rodIdAttribute(String rodId) {
            Map<String, MessageAttributeValue> ret = new LinkedHashMap<>();
            ret.put(BusConstants.FIELD_ROD_ID,
                    MessageAttributeValue.builder().dataType("String").stringValue(rodId).build());
            return ret;
        }
    }
}

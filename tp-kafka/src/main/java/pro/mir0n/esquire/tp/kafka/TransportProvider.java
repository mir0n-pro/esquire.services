/*
 *  Esquire frameworks (tm)
 *  tp-kafka -- transport provider (Kafka)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/12/2026 mir0n  created (as KafkaTransportProvider): the Kafka ITransportProvider. openPublisher sends each
 *                   TransportMessage as a JSON value keyed by TransportMessage.key (per-entity order);
 *                   openConsumer runs a programmatic ConcurrentMessageListenerContainer.
 * 06/13/2026 mir0n  class-name-driven SPI: renamed to the conventional pro.mir0n.esquire.tp.kafka.
 *                   TransportProvider; builds its OWN producer / consumer factory from settings.endpoint()
 *                   (bootstrap-servers); the kafka-only group-id comes from the provider's param group
 *                   (transport.kafka.group-id), read via settings.params().
 * 06/17/2026 mir0n  openPublisher returns a TransportPublisher (close() destroys the DefaultKafkaProducerFactory);
 *                   the clientId param + CLIENT_ID_CONFIG removed from buildTemplate / buildConsumerFactory
 * 06/22/2026 mir0n  two-phase consumer: openConsumer returns a TransportConsumer (start + close legs); the
 *                   container is created PAUSED (setAutoStartup(false), no container.start()) -- delivery waits
 *                   for the bus start() that calls the returned start leg
 * 06/22/2026 mir0n  send-outcome health on the publisher handle: Kafka has no clean connection callback, so an
 *                   acked send -> UP, a failed send (callback exception / throw) -> DOWN (best-effort).
 * 06/24/2026 mir0n  session (alive) messages routed to a separate <destination>.admin topic (topicFor); the admin
 *                   topic is created with a short retention via AdminClient (Kafka has no per-message TTL)
 * 08/26/2026 mir0n  the connection health seeds UNKNOWN, not UP -- nothing has proved the connection at open
 */
package pro.mir0n.esquire.tp.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Kafka implementation of the transport-provider SPI. Owns its own audit producer / consumer. */
public final class TransportProvider implements ITransportProvider {

    /** Kafka-only param key (transport.kafka.group-id): the consumer group id. */
    public static final String PARAM_GROUP_ID = "group-id";
    private static final String DEFAULT_GROUP_ID = "esquire-xxrod-audit";

    /** Session (alive-protocol) messages -- HeartBeat / TestRequest -- are connectivity probes, NOT log data, so
     *  they go to a SEPARATE {@code <destination>.admin} topic rather than the append-only audit log topic. The
     *  log topic then carries only real records (e.g. UA audit). The Kafka analog of the tp-redis admin stream. */
    public static final String ADMIN_TOPIC_SUFFIX = ".admin";

    /** The {@code .admin} topic is a throwaway liveness channel -- short retention so heartbeats self-purge (the
     *  "not durable" is done HERE, by the transport, when it creates the topic; Kafka has no per-message TTL). */
    private static final String ADMIN_RETENTION_MS = "60000";   // 1 minute

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.kafka.TransportProvider");

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        ObjectMapper om = s.objectMapper();
        KafkaTemplate<String, String> kafka = buildTemplate(s.endpoint(), s.params());
        ensureAdminTopic(s.endpoint(), destination + ADMIN_TOPIC_SUFFIX);   // the throwaway liveness topic (short retention)
        devLog.info("tp-kafka: publisher opened on topic {} (bootstrap={})", destination, s.endpoint());

        // close() disposes the producer factory (closes its pooled producers); the DefaultKafkaProducerFactory
        // built above is a DisposableBean.
        AutoCloseable closer = (kafka.getProducerFactory() instanceof DisposableBean db) ? db::destroy : () -> { };
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        return TransportPublisher.of(msg -> {
            try {
                String value = om.writeValueAsString(msg.headers());
                // a session (alive) message rides to <destination>.admin, never the log topic -- the log keeps
                // only real records. An app message goes to the destination log topic.
                Object msgType = msg.headers().get(BusConstants.FIELD_MSG_TYPE);
                String topic = topicFor(destination, msgType != null ? msgType.toString() : null);
                kafka.send(topic, msg.key(), value).whenComplete((res, ex) -> {
                    if (ex != null) {
                        conn.set(TransportHealth.DOWN);
                        devLog.error("tp-kafka: send failed on {} key={}: {}", topic, msg.key(), ex.getMessage(), ex);
                    } else {
                        conn.set(TransportHealth.UP);
                    }
                });
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);
                devLog.error("tp-kafka: publish failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }, closer, conn::get);
    }

    /** The topic a message rides by its {@code MsgType}: a session (alive) message -- HeartBeat / TestRequest --
     *  goes to the {@code <destination>.admin} liveness topic; every other message (e.g. UA audit) goes to the
     *  {@code destination} log topic. Keeps the append-only audit log topic to real records. */
    static String topicFor(String destination, String msgType) {
        String ret;
        if (RodEvent.isSession(msgType)) {
            ret = destination + ADMIN_TOPIC_SUFFIX;
        } else {
            ret = destination;
        }
        return ret;
    }

    /** Create the {@code .admin} liveness topic with short retention, from inside the transport (Kafka has no
     *  per-message TTL, so "not durable" is a topic-config concern set HERE). Best-effort: a TopicExistsException
     *  or any admin error is logged and ignored -- the routing still works; only the retention tidy-up is skipped. */
    private static void ensureAdminTopic(String bootstrap, String adminTopic) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        try (Admin admin = Admin.create(cfg)) {
            NewTopic topic = new NewTopic(adminTopic, 1, (short) 1).configs(Map.of(
                    TopicConfig.RETENTION_MS_CONFIG,    ADMIN_RETENTION_MS,
                    TopicConfig.SEGMENT_MS_CONFIG,      ADMIN_RETENTION_MS,   // roll segments so retention can delete them
                    TopicConfig.CLEANUP_POLICY_CONFIG,  TopicConfig.CLEANUP_POLICY_DELETE));
            admin.createTopics(List.of(topic)).all().get(10, TimeUnit.SECONDS);
            devLog.info("tp-kafka: admin topic {} ensured (retention.ms={})", adminTopic, ADMIN_RETENTION_MS);
        } catch (Exception ex) {
            devLog.info("tp-kafka: admin topic {} ensure skipped ({})", adminTopic, ex.getMessage());
        }
    }

    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        ObjectMapper om = s.objectMapper();
        String groupId = s.param(PARAM_GROUP_ID, DEFAULT_GROUP_ID);
        ConsumerFactory<String, String> consumerFactory = buildConsumerFactory(s.endpoint(), groupId, s.params());

        ContainerProperties props = new ContainerProperties(destination);
        props.setGroupId(groupId);
        props.setMessageListener((MessageListener<String, String>) record -> {
            try {
                Map<String, Object> headers = om.readValue(record.value(), new TypeReference<Map<String, Object>>() { });
                handler.accept(new TransportMessage(headers, record.key()));
            } catch (Exception ex) {
                devLog.error("tp-kafka: consume failed on {} p{}-{}: {}",
                        destination, record.partition(), record.offset(), ex.getMessage(), ex);
            }
        });

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, props);
        container.setAutoStartup(false);   // created PAUSED -- the x-rod's start() begins delivery once wired
        if (s.concurrency() > 0) {
            container.setConcurrency(s.concurrency());
        }
        devLog.info("tp-kafka: consumer created (paused) on topic {} (bootstrap={}, group={}, concurrency={})",
                destination, s.endpoint(), groupId, s.concurrency());
        return TransportConsumer.of(container::start, container::stop);
    }

    /** Build a KafkaTemplate over a String/String producer to {@code bootstrap}. The audit topic owns this
     *  producer -- it is not the app's shared kafka. Every leg {@code params} entry is a Kafka config applied
     *  verbatim (acks / linger.ms / compression.type / ...); the essentials below win so they cannot be broken. */
    private static KafkaTemplate<String, String> buildTemplate(String bootstrap, Map<String, String> params) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.putAll(params);           // any Kafka producer config verbatim -- client.id included, via transport.params.*
        cfg.remove(PARAM_GROUP_ID);   // our convention key -> the consumer maps it to group.id; not a producer config
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(cfg);
        return new KafkaTemplate<>(pf);
    }

    /** Build a String/String consumer factory to {@code bootstrap} in {@code groupId} (read from the start). Every
     *  leg {@code params} entry is a Kafka config applied verbatim (max.poll.records / ...); essentials win. */
    private static ConsumerFactory<String, String> buildConsumerFactory(String bootstrap, String groupId, Map<String, String> params) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.putAll(params);           // any Kafka consumer config verbatim -- client.id included, via transport.params.*
        cfg.remove(PARAM_GROUP_ID);   // our convention key -> mapped to GROUP_ID_CONFIG below
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(cfg);
    }
}

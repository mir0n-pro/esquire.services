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
 */
package pro.mir0n.esquire.tp.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
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
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Kafka implementation of the transport-provider SPI. Owns its own audit producer / consumer. */
public final class TransportProvider implements ITransportProvider {

    /** Kafka-only param key (transport.kafka.group-id): the consumer group id. */
    public static final String PARAM_GROUP_ID = "group-id";
    private static final String DEFAULT_GROUP_ID = "esquire-xxrod-audit";

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.kafka.TransportProvider");

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        ObjectMapper om = s.objectMapper();
        KafkaTemplate<String, String> kafka = buildTemplate(s.endpoint(), s.params());
        devLog.info("tp-kafka: publisher opened on topic {} (bootstrap={})", destination, s.endpoint());

        // close() disposes the producer factory (closes its pooled producers); the DefaultKafkaProducerFactory
        // built above is a DisposableBean.
        AutoCloseable closer = (kafka.getProducerFactory() instanceof DisposableBean db) ? db::destroy : () -> { };
        return TransportPublisher.of(msg -> {
            try {
                String value = om.writeValueAsString(msg.headers());
                kafka.send(destination, msg.key(), value).whenComplete((res, ex) -> {
                    if (ex != null) {
                        devLog.error("tp-kafka: send failed on {} key={}: {}", destination, msg.key(), ex.getMessage(), ex);
                    }
                });
            } catch (Exception ex) {
                devLog.error("tp-kafka: publish failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }, closer);
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

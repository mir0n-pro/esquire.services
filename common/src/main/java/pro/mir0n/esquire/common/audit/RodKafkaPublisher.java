/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/08/2026 mir0n  created: the x-Rod option (c) producer dispatcher over KAFKA -- a Consumer<RodEvent>
 *                   the xy-Rod feed worker calls. It serializes the event via RodEventCodec.toJson and
 *                   publishes it to the audit Kafka topic, keyed by entityId so all events for one entity
 *                   land on one partition (per-entity ordering). The standalone xxRod consumer reads the
 *                   topic and writes the *_log -- same role as the ActiveMQ bus publisher, Kafka transport.
 *                   Best-effort: the send is async; a delivery failure is logged via the callback, never
 *                   thrown back into the single feed worker.
 */
package pro.mir0n.esquire.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import pro.mir0n.esquire.common.xrod.RodEvent;

import java.util.function.Consumer;

/**
 * Publishes each committed {@link RodEvent} to the audit Kafka topic (x-Rod option c, Kafka transport).
 * Record key = {@code entityId} (partition-by-entity -> per-entity order); value = the JSON envelope from
 * {@link RodEventCodec#toJson}. The xxRod consumer reads the topic and writes the {@code *_log} tables.
 */
public final class RodKafkaPublisher implements Consumer<RodEvent> {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodKafkaPublisher.class.getName());
    private static final Logger msgLog = LoggerFactory.getLogger("msg." + RodKafkaPublisher.class.getName());

    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final ObjectMapper om;

    public RodKafkaPublisher(KafkaTemplate<String, String> kafka, String topic, ObjectMapper om) {
        this.kafka = kafka;
        this.topic = topic;
        this.om    = om;
    }

    @Override
    public void accept(RodEvent e) {
        try {
            String value = RodEventCodec.toJson(e, om);
            kafka.send(topic, e.entityId(), value).whenComplete((res, ex) -> {
                if (ex != null) {
                    devLog.error("rod-kafka: send failed for kind={}, entityId={}, subId={}: {}",
                            e.kind(), e.entityId(), e.subId(), ex.getMessage(), ex);
                }
            });
            msgLog.info("ROD | RDA | kafka | {} | {} | {} | {}", e.op(), e.kind(), e.entityId(), e.subId());
        } catch (Exception ex) {
            devLog.error("rod-kafka: publish failed for kind={}, entityId={}, subId={}: {}",
                    e.kind(), e.entityId(), e.subId(), ex.getMessage(), ex);
        }
    }
}

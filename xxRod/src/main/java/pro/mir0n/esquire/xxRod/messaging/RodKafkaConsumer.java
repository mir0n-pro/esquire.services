/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/08/2026 mir0n  created: the audit-topic intake (x-Rod option c, Kafka transport). Gated by
 *                   xxrod.transport=kafka. @KafkaListener decodes each record value into a RodEvent
 *                   (RodEventCodec.fromJson) and hands it to the director -- the same director / *_log sink
 *                   as the ActiveMQ intake, different transport.
 */
package pro.mir0n.esquire.xxRod.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.audit.AuditRod;
import pro.mir0n.esquire.common.audit.RodEventCodec;
import pro.mir0n.esquire.common.xrod.RodEvent;
import pro.mir0n.esquire.xxRod.director.IRodDirector;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "xxrod", name = "transport", havingValue = AuditRod.TRANSPORT_KAFKA)
@RequiredArgsConstructor
public class RodKafkaConsumer {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodKafkaConsumer.class.getName());
    private static final Logger msgLog = LoggerFactory.getLogger("msg." + RodKafkaConsumer.class.getName());

    private final IRodDirector director;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EsqMsgConstants.TOPIC_ROD_AUDIT, groupId = "${xxrod.kafka.group-id:esquire-xxrod-audit}")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            RodEvent event = RodEventCodec.fromJson(record.value(), objectMapper);
            MDC.put(EsqConstants.PD_REQUEST_ID,     event.requestId());
            MDC.put(EsqConstants.PD_CORRELATION_ID, event.correlationId());
            msgLog.info("ROD | RDA | {}-{} | {} | {} | {} | {}",
                    record.partition(), record.offset(), event.op(), event.kind(), event.entityId(), event.subId());
            director.accept(event);
        } catch (Exception e) {
            log.error("xxRod: audit record failed partition={} offset={}: {}",
                    record.partition(), record.offset(), e.getMessage());
            devLog.error("xxRod: audit record failed partition={} offset={}: {}",
                    record.partition(), record.offset(), e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}

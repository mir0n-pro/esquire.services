/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the audit-queue intake. Decodes each message into a RodEvent (RodEventCodec)
 *                   and hands it to the director. The transport-pluggable edge (first transport = ActiveMQ).
 * 06/08/2026 mir0n  gated by xxrod.transport=activemq (default) -- the ActiveMQ intake; the Kafka intake
 *                   (RodKafkaConsumer) is the alternative.
 */
package pro.mir0n.esquire.xxRod.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.audit.AuditRod;
import pro.mir0n.esquire.common.audit.RodEventCodec;
import pro.mir0n.esquire.common.xrod.RodEvent;
import pro.mir0n.esquire.xxRod.director.IRodDirector;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "xxrod", name = "transport",
        havingValue = AuditRod.TRANSPORT_ACTIVEMQ, matchIfMissing = true)
@RequiredArgsConstructor
public class RodAuditConsumer {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodAuditConsumer.class.getName());
    private static final Logger msgLog = LoggerFactory.getLogger("msg." + RodAuditConsumer.class.getName());

    private final IRodDirector director;
    private final ObjectMapper objectMapper;

    @JmsListener(destination = EsqMsgConstants.QUEUE_ROD_AUDIT, containerFactory = "jmsQueueListenerFactory")
    public void onMessage(Message message) {
        String applMsgId = null;
        try {
            applMsgId = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            MDC.put(EsqConstants.PD_REQUEST_ID,     message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID));
            MDC.put(EsqConstants.PD_CORRELATION_ID, message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID));
            RodEvent event = RodEventCodec.fromMessage(message, objectMapper);
            msgLog.info("ROD | RDA | {} | {} | {} | {} | {}",
                    applMsgId, message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE),
                    event.kind(), event.entityId(), event.subId());
            director.accept(event);
        } catch (Exception e) {
            log.error("xxRod: audit message failed applMsgId={}: {}", applMsgId, e.getMessage());
            devLog.error("xxRod: audit message failed applMsgId={}: {}", applMsgId, e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}

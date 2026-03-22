/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — entity broadcast topic consumer (skeleton)
 * 03/21/2026 mir0n  raw string literals replaced with EsqMsgConstants; requestId/correlationId reads added;
 *                   MDC set/clear; devLog; log.debug→devLog.debug; dual error pattern
 */

package pro.mir0n.esquire.kcMaster.messaging;

import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;

/**
 * Durable subscriber on esquire.entity.broadcast.
 * Listens for entity update events that may require Keycloak synchronization.
 *
 * Subscription name is stable — never change it once deployed.
 */
@Slf4j
@Component
public class KcEntityBroadcastConsumer {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcEntityBroadcastConsumer.class.getName());

    private static final String TOPIC         = "esquire.entity.broadcast";
    private static final String SUBSCRIPTION  = "esquire.entity.broadcast.kcmaster.primary";

    @JmsListener(
            destination      = TOPIC,
            containerFactory = "jmsDurableTopicListenerFactory",
            subscription     = SUBSCRIPTION
    )
    public void onMessage(Message message) {
        try {
            String requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            String msgType       = message.getStringProperty(EsqMsgConstants.FIELD_MSG_TYPE);
            String entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    entityKind    = message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND);
            String text          = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

            MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

            devLog.debug("kcMaster: entity broadcast received: msgType={} entityId={} entityKind={} text={}", msgType, entityId, entityKind, text);

            // todo: determine what KC action (if any) this event requires
        } catch (Exception e) {
            log.error("kcMaster: entity broadcast processing failed: {}", e.getMessage());
            devLog.error("kcMaster: entity broadcast processing failed: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}

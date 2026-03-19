/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: durable subscriber on esquire.entity.broadcast; Phase 1 logs received messages
 */
package pro.mir0n.esquire.bizTree.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;

/**
 * Durable consumer for the esquire.entity.broadcast topic.
 *
 * Phase 1: logs received messages to the dedicated entity-broadcast log file.
 * Enable via: biztree.messaging.consumer.enabled=true (default: true).
 *
 * Durable subscription:
 *   - clientId: biztree.messaging.client-id (BizTreeJmsConfig)
 *   - subscriptionName: esquire.entity.broadcast.biztree.primary (stable, do not change)
 *   - selector: BusID = 'esquire.entity' AND MsgType = 'UE'
 *   - idempotency key: ApplMsgID
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "biztree.messaging.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class EsqEntityBroadcastConsumer {

    private static final Logger broadcastLog = LoggerFactory.getLogger("entity.broadcast");

    private static final String SUBSCRIPTION_NAME =
            EsqMsgConstants.TOPIC_ENTITY_BROADCAST + ".biztree.primary";
    private static final String MSG_SELECTOR =
            EsqMsgConstants.FIELD_BUS_ID  + " = '" + EsqMsgConstants.BUS_ID_ENTITY              + "'" +
            " AND " +
            EsqMsgConstants.FIELD_MSG_TYPE + " = '" + EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS + "'";

    @JmsListener(
        destination = EsqMsgConstants.TOPIC_ENTITY_BROADCAST,
        containerFactory = "jmsDurableTopicListenerFactory",
        subscription = SUBSCRIPTION_NAME,
        selector = MSG_SELECTOR
    )
    public void onEntityBroadcast(TextMessage message) {
        String applMsgId = null;
        try {
            applMsgId    = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String serviceId  = message.getStringProperty(EsqMsgConstants.FIELD_SERVICE_ID);
            String entityId   = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    entityKind = message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND);
            String eventType  = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String body       = message.getText();

            broadcastLog.info("ENTITY | serviceId={} | kind={} | id={} | event={} | applMsgId={} | body={}",
                    serviceId, entityKind, entityId, eventType, applMsgId, body);

        } catch (JMSException e) {
            log.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage());
        }
    }
}

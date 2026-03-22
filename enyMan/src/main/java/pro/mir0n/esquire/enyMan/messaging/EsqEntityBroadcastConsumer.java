/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: consumer template for esquire.entity.broadcast; disabled by default (Phase 2)
 * 03/20/2026 mir0n  switched to properties-only transport: Message replaces TextMessage; Text via getStringProperty(); Text added to required properties validation
 * 03/21/2026 mir0n  three-tier logging: devLog added; log.debug→devLog.debug; log.warn→devLog.debug;
 *                   MDC set/clear; dual error pattern; unused imports removed
 */
package pro.mir0n.esquire.enyMan.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;

/**
 * Durable consumer template for the esquire.entity.broadcast topic.
 *
 * This is a reusable pattern for future consumers. Not active by default in phase 1.
 * Enable via: enyman.messaging.consumer.enabled=true
 *
 * Durable subscription requirements:
 *   - stable clientId set in EnyManJmsConfig (enyman.messaging.client-id)
 *   - stable subscriptionName per @JmsListener
 *   - selector uses JMS properties only (FIX-JSON field names)
 *   - idempotency: use ApplMsgID as the primary duplicate-control key
 *
 * Selector examples:
 *   EntityKind = 34
 *   EntityKind IN (34, 35, 36)
 *   BusID = 'esquire.entity' AND EntityKind = 34
 *   BusID = 'esquire.entity' AND MsgType = 'UE' AND EntityKind = 34
 *
 * Do not use JSON body fields in selectors.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "enyman.messaging.consumer.enabled", havingValue = "true", matchIfMissing = false)
public class EsqEntityBroadcastConsumer {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + EsqEntityBroadcastConsumer.class.getName());

    private static final String SUBSCRIPTION_NAME =
            EsqMsgConstants.TOPIC_ENTITY_BROADCAST + ".enyman.primary";
    private static final String MSG_SELECTOR =
            EsqMsgConstants.FIELD_BUS_ID  + " = '" + EsqMsgConstants.BUS_ID_ENTITY               + "'" +
            " AND " +
            EsqMsgConstants.FIELD_MSG_TYPE + " = '" + EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS + "'";

    @JmsListener(
        destination = EsqMsgConstants.TOPIC_ENTITY_BROADCAST,
        containerFactory = "jmsDurableTopicListenerFactory",
        subscription = SUBSCRIPTION_NAME,
        selector = MSG_SELECTOR
    )
    public void onEntityBroadcast(Message message) {
        String applMsgId = null;
        try {
            applMsgId            = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);

            MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

            // Idempotency entry point: check applMsgId against seen-messages store before processing.
            // Implement duplicate-control here (e.g., DB lookup or cache by applMsgId).

            String entityId  = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    kind      = message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND);
            String eventType = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String textJson  = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

            devLog.debug("EsqEntityBroadcastConsumer: received kind={}, id={}, event={}, applMsgId={}",
                    kind, entityId, eventType, applMsgId);

            // Business processing goes here.
            // Validate required fields before processing:
            validateRequiredProperties(message);

        } catch (JMSException e) {
            log.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage());
            devLog.error("EsqEntityBroadcastConsumer: message error applMsgId={}: {}", applMsgId, e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private void validateRequiredProperties(Message message) throws JMSException {
        assertProperty(message, EsqMsgConstants.FIELD_APPL_MSG_ID);
        assertProperty(message, EsqMsgConstants.FIELD_SCHEMA_VERSION);
        assertProperty(message, EsqMsgConstants.FIELD_BUS_ID);
        assertProperty(message, EsqMsgConstants.FIELD_MSG_TYPE);
        assertProperty(message, EsqMsgConstants.FIELD_EVENT_TYPE);
        assertProperty(message, EsqMsgConstants.FIELD_ENTITY_KIND);
        assertProperty(message, EsqMsgConstants.FIELD_ENTITY_ID);
        assertProperty(message, EsqMsgConstants.FIELD_MESSAGE_ENCODING);
        assertProperty(message, EsqMsgConstants.FIELD_TEXT);

        String busId          = message.getStringProperty(EsqMsgConstants.FIELD_BUS_ID);
        String msgType        = message.getStringProperty(EsqMsgConstants.FIELD_MSG_TYPE);
        String msgEncoding    = message.getStringProperty(EsqMsgConstants.FIELD_MESSAGE_ENCODING);
        int    schemaVersion  = message.getIntProperty(EsqMsgConstants.FIELD_SCHEMA_VERSION);

        if (!EsqMsgConstants.BUS_ID_ENTITY.equals(busId)) {
            devLog.debug("EsqEntityBroadcastConsumer: unexpected BusID={}", busId);
        }
        if (!EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS.equals(msgType)) {
            devLog.debug("EsqEntityBroadcastConsumer: unexpected MsgType={}", msgType);
        }
        if (!EsqMsgConstants.MESSAGE_ENCODING.equals(msgEncoding)) {
            devLog.debug("EsqEntityBroadcastConsumer: unexpected MessageEncoding={}", msgEncoding);
        }
        if (schemaVersion != EsqMsgConstants.SCHEMA_VERSION) {
            devLog.debug("EsqEntityBroadcastConsumer: unexpected SchemaVersion={}", schemaVersion);
        }
    }

    private void assertProperty(Message message, String name) throws JMSException {
        if (!message.propertyExists(name)) {
            log.error("EsqEntityBroadcastConsumer: required property missing: {}", name);
            devLog.error("EsqEntityBroadcastConsumer: required property missing: {}", name);
        }
    }
}

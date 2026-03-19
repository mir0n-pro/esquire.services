/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: publishes FIX-JSON envelope to esquire.entity.broadcast on entity update
 */
package pro.mir0n.esquire.enyMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.TextMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes entity state events to the esquire.entity.broadcast JMS topic.
 *
 * Protocol: FIX-JSON notation.
 * JMS property names are identical to FIX-JSON body field names.
 * Publish only after transaction commits (post-commit contract).
 *
 * Required canonical fields: ApplMsgID, SendingTime, SchemaVersion, BusID, ServiceID,
 * CtrlID, MsgType, EventType, EntityKind, EntityID, RequestID, CorrelationID,
 * MessageEncoding, Text.
 *
 * Text is body-only (Object type, not a JMS property).
 * All other fields are set as both JMS properties and body fields with identical values.
 */
@Slf4j
@Component
public class EsqEntityBroadcastPublisher {

    private final JmsTemplate jmsTopicTemplate;
    private final ObjectMapper objectMapper;

    // Sending service identifier — identifies this service in outbound messages.
    @Value("${enyman.messaging.service-id:enyMan}")
    private String serviceId;

    // Producer instance identifier — must be stable and configured per deployment.
    @Value("${enyman.messaging.ctrl-id:enyman.default}")
    private String ctrlId;

    public EsqEntityBroadcastPublisher(JmsTemplate jmsTopicTemplate, ObjectMapper objectMapper) {
        this.jmsTopicTemplate = jmsTopicTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish an entity state event.
     *
     * @param entityKind    FIX-JSON EntityKind value
     * @param entityId      FIX-JSON EntityID value
     * @param eventType     C / U / D  (EsqMsgConstants.EVENT_*)
     * @param requestId     from request context; null generates a new UUID
     * @param correlationId from correlation context; null generates a new UUID
     * @param text          lightweight payload for Text body field (may be null)
     */
    public void publish(int entityKind, String entityId, String eventType,
                        String requestId, String correlationId, Map<String, Object> text) {

        String applMsgId   = UUID.randomUUID().toString();
        String sendingTime = Instant.now().toString();
        String rid = (requestId     != null) ? requestId     : UUID.randomUUID().toString();
        String cid = (correlationId != null) ? correlationId : UUID.randomUUID().toString();

        // --- build FIX-JSON body ---
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(EsqMsgConstants.FIELD_APPL_MSG_ID,      applMsgId);
        body.put(EsqMsgConstants.FIELD_SENDING_TIME,     sendingTime);
        body.put(EsqMsgConstants.FIELD_SCHEMA_VERSION,   EsqMsgConstants.SCHEMA_VERSION);
        body.put(EsqMsgConstants.FIELD_BUS_ID,           EsqMsgConstants.BUS_ID_ENTITY);
        body.put(EsqMsgConstants.FIELD_SERVICE_ID,       serviceId);
        body.put(EsqMsgConstants.FIELD_CTRL_ID,          ctrlId);
        body.put(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS);
        body.put(EsqMsgConstants.FIELD_EVENT_TYPE,       eventType);
        body.put(EsqMsgConstants.FIELD_ENTITY_KIND,      entityKind);
        body.put(EsqMsgConstants.FIELD_ENTITY_ID,        entityId);
        body.put(EsqMsgConstants.FIELD_REQUEST_ID,       rid);
        body.put(EsqMsgConstants.FIELD_CORRELATION_ID,   cid);
        body.put(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MESSAGE_ENCODING);
        body.put(EsqMsgConstants.FIELD_TEXT,             text != null ? text : Map.of());

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("EsqEntityBroadcastPublisher: envelope serialization failed: {}", e.getMessage());
            return;
        }

        // --- send to topic with JMS properties ---
        final String finalRid = rid;
        final String finalCid = cid;
        jmsTopicTemplate.send(EsqMsgConstants.TOPIC_ENTITY_BROADCAST, session -> {
            TextMessage msg = session.createTextMessage(json);
            // JMS properties — identical to body fields (authority: JMS properties for routing)
            msg.setStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID,      applMsgId);
            msg.setStringProperty(EsqMsgConstants.FIELD_SENDING_TIME,     sendingTime);
            msg.setIntProperty   (EsqMsgConstants.FIELD_SCHEMA_VERSION,   EsqMsgConstants.SCHEMA_VERSION);
            msg.setStringProperty(EsqMsgConstants.FIELD_BUS_ID,           EsqMsgConstants.BUS_ID_ENTITY);
            msg.setStringProperty(EsqMsgConstants.FIELD_SERVICE_ID,       serviceId);
            msg.setStringProperty(EsqMsgConstants.FIELD_CTRL_ID,          ctrlId);
            msg.setStringProperty(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS);
            msg.setStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE,       eventType);
            msg.setIntProperty   (EsqMsgConstants.FIELD_ENTITY_KIND,      entityKind);
            msg.setStringProperty(EsqMsgConstants.FIELD_ENTITY_ID,        entityId);
            msg.setStringProperty(EsqMsgConstants.FIELD_REQUEST_ID,       finalRid);
            msg.setStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID,   finalCid);
            msg.setStringProperty(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MESSAGE_ENCODING);
            // Text is body-only (Object type, not a JMS property)
            return msg;
        });

        log.info("EsqEntityBroadcastPublisher: published kind={}, id={}, event={}, applMsgId={}",
                entityKind, entityId, eventType, applMsgId);
    }
}

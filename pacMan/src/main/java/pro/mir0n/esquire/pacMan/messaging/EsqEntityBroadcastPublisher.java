/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: publishes FIX-JSON envelope to esquire.entity.broadcast on account update
 * 03/20/2026 mir0n  switched to properties-only transport: Message (no body); Text as JSON string property
 *                   service-id: removed inline constant fallback; config-only via @Value
 */
package pro.mir0n.esquire.pacMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes entity state events to the esquire.entity.broadcast JMS topic.
 *
 * Protocol: FIX-JSON notation, properties-only (no message body).
 * All 14 canonical fields are set as JMS properties.
 * Text is a JMS string property containing a JSON-serialized entity state snapshot:
 *   {"id":"...", "kind":N [,"name":"..."] [,"desc":"..."]}
 * EntityID and EntityKind are also set as discrete properties for selector use.
 * Publish only after transaction commits (post-commit contract).
 */
@Slf4j
@Component
public class EsqEntityBroadcastPublisher {

    private final JmsTemplate jmsTopicTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pacman.messaging.service-id}")
    private String serviceId;

    @Value("${pacman.messaging.ctrl-id:pacman.default}")
    private String ctrlId;

    public EsqEntityBroadcastPublisher(JmsTemplate jmsTopicTemplate, ObjectMapper objectMapper) {
        this.jmsTopicTemplate = jmsTopicTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(int entityKind, String entityId, String eventType,
                        String requestId, String correlationId, Map<String, Object> text) {

        String applMsgId   = UUID.randomUUID().toString();
        String sendingTime = Instant.now().toString();
        String rid = (requestId     != null) ? requestId     : UUID.randomUUID().toString();
        String cid = (correlationId != null) ? correlationId : UUID.randomUUID().toString();

        String textJson;
        try {
            textJson = objectMapper.writeValueAsString(text != null ? text : Map.of());
        } catch (Exception e) {
            log.error("EsqEntityBroadcastPublisher: text serialization failed: {}", e.getMessage());
            return;
        }

        // --- send as properties-only message (no body) ---
        final String finalRid  = rid;
        final String finalCid  = cid;
        final String finalText = textJson;
        jmsTopicTemplate.send(EsqMsgConstants.TOPIC_ENTITY_BROADCAST, session -> {
            Message msg = session.createMessage();
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
            msg.setStringProperty(EsqMsgConstants.FIELD_TEXT,             finalText);
            return msg;
        });

        log.debug("EsqEntityBroadcastPublisher: published kind={}, id={}, event={}, applMsgId={}",
                entityKind, entityId, eventType, applMsgId);
    }
}

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
 * 03/21/2026 mir0n  three-tier logging: msgLog/devLog added; props map migrated to LinkedHashMap+Utils.setProps;
 *                   dual-mode ENTITY msg audit; console echo log.info; final variable copies removed
 */
package pro.mir0n.esquire.pacMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;

import pro.mir0n.esquire.messaging.jms.Utils;

import java.time.Instant;
import java.util.LinkedHashMap;
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

    private static final org.slf4j.Logger msgLog = LoggerFactory.getLogger("msg." + EsqEntityBroadcastPublisher.class.getName());
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + EsqEntityBroadcastPublisher.class.getName());

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
            devLog.error("EsqEntityBroadcastPublisher: text serialization failed: {}", e.getMessage(), e);
            return;
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,      applMsgId);
        props.put(EsqMsgConstants.FIELD_SENDING_TIME,     sendingTime);
        props.put(EsqMsgConstants.FIELD_SCHEMA_VERSION,   EsqMsgConstants.SCHEMA_VERSION);
        props.put(EsqMsgConstants.FIELD_BUS_ID,           EsqMsgConstants.BUS_ID_ENTITY);
        props.put(EsqMsgConstants.FIELD_SERVICE_ID,       serviceId);
        props.put(EsqMsgConstants.FIELD_CTRL_ID,          ctrlId);
        props.put(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS);
        props.put(EsqMsgConstants.FIELD_EVENT_TYPE,       eventType);
        props.put(EsqMsgConstants.FIELD_ENTITY_KIND,      entityKind);
        props.put(EsqMsgConstants.FIELD_ENTITY_ID,        entityId);
        props.put(EsqMsgConstants.FIELD_REQUEST_ID,       rid);
        props.put(EsqMsgConstants.FIELD_CORRELATION_ID,   cid);
        props.put(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MESSAGE_ENCODING);
        props.put(EsqMsgConstants.FIELD_TEXT,             textJson);

        jmsTopicTemplate.send(EsqMsgConstants.TOPIC_ENTITY_BROADCAST, session -> {
            Message msg = session.createMessage();
            Utils.setProps(msg, props);
            return msg;
        });

        if (msgLog.isDebugEnabled()) {
            msgLog.info("ENTITY | UE | {}", Utils.formatProps(props));
        } else {
            msgLog.info("ENTITY | UE | {} | {} | {} | {} | {} | {}",
                    applMsgId, eventType, entityKind, entityId, rid, cid);
        }
        log.info("ENTITY | UE | {} | {} | {} | {} | {} | {}",
                applMsgId, eventType, entityKind, entityId);
    }
}

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/06/2026 mir0n  created: publishes EVENT_UPDATE_PATH URQ to kcMaster on entity move
 */
package pro.mir0n.esquire.enyMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import jakarta.jms.Session;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes EVENT_UPDATE_PATH ("X") URQ messages to esquire.kc.request after a USR entity move.
 * kcMaster picks these up and updates esq_rootpath in KeyCloak.
 *
 * Fire-and-forget: DB transaction already committed; publish failure is logged but not rethrown.
 */
@Slf4j
@Component
public class KcRequestPublisher {

    private static final org.slf4j.Logger msgLog = LoggerFactory.getLogger("msg." + KcRequestPublisher.class.getName());
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcRequestPublisher.class.getName());

    @Value("${enyman.messaging.ctrl-id:enyman.default}")
    private String ctrlId;

    private final JmsTemplate jmsQueueTemplate;
    private final ObjectMapper objectMapper;

    public KcRequestPublisher(@Qualifier("jmsQueueTemplate") JmsTemplate jmsQueueTemplate,
                              ObjectMapper objectMapper) {
        this.jmsQueueTemplate = jmsQueueTemplate;
        this.objectMapper     = objectMapper;
    }

    public void publishPathUpdate(String entityId, int entityKind, String newPath,
                                  String requestId, String correlationId) {
        String mid       = UUID.randomUUID().toString();
        String testReqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id",   entityId);
            body.put("kind", entityKind);
            body.put("path", newPath);
            String text = objectMapper.writeValueAsString(body);

            Map<String, Object> props = new LinkedHashMap<>();
            props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,      mid);
            props.put(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_REQUEST);
            props.put(EsqMsgConstants.FIELD_EVENT_TYPE,       EsqMsgConstants.EVENT_UPDATE_PATH);
            props.put(EsqMsgConstants.FIELD_ENTITY_KIND,      entityKind);
            props.put(EsqMsgConstants.FIELD_ENTITY_ID,        entityId);
            props.put(EsqMsgConstants.FIELD_CTRL_ID,          ctrlId);
            props.put(EsqMsgConstants.FIELD_REQUEST_ID,       requestId);
            props.put(EsqMsgConstants.FIELD_CORRELATION_ID,   correlationId);
            props.put(EsqMsgConstants.FIELD_TEST_REQ_ID,      testReqId);
            props.put(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MSG_ENCODING_JSON);
            props.put(EsqMsgConstants.FIELD_TEXT,             text);

            jmsQueueTemplate.send(EsqMsgConstants.QUEUE_KC_REQUEST, (Session session) -> {
                Message msg = session.createMessage();
                Utils.setProps(msg, props);
                return msg;
            });

            if (msgLog.isDebugEnabled()) {
                msgLog.info("KC | URQ | {}", Utils.formatProps(props));
            } else {
                msgLog.info("KC | URQ | {} | {} | {} | {} | {} | {} | {} | {}",
                        mid, EsqMsgConstants.EVENT_UPDATE_PATH, entityKind, entityId,
                        ctrlId, requestId, correlationId, testReqId);
            }
            log.info("KC | URQ | {} | {} | {} | {} | {} | {}",
                    mid, EsqMsgConstants.EVENT_UPDATE_PATH, entityKind, entityId, ctrlId, testReqId);

        } catch (Exception e) {
            log.error("enyMan: failed to publish KC URQ path update: entityId={}, error={}", entityId, e.getMessage());
            devLog.error("enyMan: failed to publish KC URQ path update: entityId={}, kind={}, requestId={}, correlationId={}, error={}",
                    entityId, entityKind, requestId, correlationId, e.getMessage(), e);
        }
    }
}

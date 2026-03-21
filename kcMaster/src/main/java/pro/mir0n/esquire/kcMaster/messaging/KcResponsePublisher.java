/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — publishes URS (success) and URR (request reject) to esquire.kc.response
 *                   whole message logged via LinkedHashMap props; URR carries RFC 9457 Error header
 */

package pro.mir0n.esquire.kcMaster.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import jakarta.jms.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KcResponsePublisher {

    private static final org.slf4j.Logger kcAudit = LoggerFactory.getLogger("kc.audit");

    private final JmsTemplate jmsQueueTemplate;
    private final ObjectMapper objectMapper;

    public void publishSuccess(String entityId, String command,
                               String ctrlId, String requestId, String correlationId, String testReqId) {
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,   UUID.randomUUID().toString());
            props.put(EsqMsgConstants.FIELD_MSG_TYPE,       EsqMsgConstants.MSG_TYPE_RESPONSE);
            props.put(EsqMsgConstants.FIELD_EVENT_TYPE,     command);
            props.put(EsqMsgConstants.FIELD_ENTITY_KIND,    EsqConstants.KIND_ACCESS_PROFILE);
            props.put(EsqMsgConstants.FIELD_ENTITY_ID,      entityId);
            props.put(EsqMsgConstants.FIELD_CTRL_ID,        ctrlId);
            props.put(EsqMsgConstants.FIELD_REQUEST_ID,     requestId);
            props.put(EsqMsgConstants.FIELD_CORRELATION_ID, correlationId);
            props.put(EsqMsgConstants.FIELD_TEST_REQ_ID,    testReqId);

            jmsQueueTemplate.send(EsqMsgConstants.QUEUE_KC_RESPONSE, (Session session) -> {
                Message msg = session.createMessage();
                Utils.setProps(msg, props);
                return msg;
            });

            kcAudit.info("KC | URS | {}", Utils.formatProps(props));
        } catch (Exception e) {
            log.error("kcMaster: failed to publish URS: entityId={} error={}", entityId, e.getMessage(), e);
        }
    }

    public void publishFailure(String entityId, String command, String loginId,
                               String errorCode, String errorMessage,
                               String ctrlId, String requestId, String correlationId, String testReqId,
                               String requestText) {
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("type",   "about:blank");
            error.put("title",  errorCode);
            error.put("status", 500);
            error.put("detail", errorMessage);
            String errorJson = objectMapper.writeValueAsString(error);

            Map<String, Object> props = new LinkedHashMap<>();
            props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,   UUID.randomUUID().toString());
            props.put(EsqMsgConstants.FIELD_MSG_TYPE,       EsqMsgConstants.MSG_TYPE_REJECT);
            props.put(EsqMsgConstants.FIELD_EVENT_TYPE,     command);
            props.put(EsqMsgConstants.FIELD_ENTITY_KIND,    EsqConstants.KIND_ACCESS_PROFILE);
            props.put(EsqMsgConstants.FIELD_ENTITY_ID,      entityId);
            props.put(EsqMsgConstants.FIELD_CTRL_ID,        ctrlId);
            props.put(EsqMsgConstants.FIELD_REQUEST_ID,     requestId);
            props.put(EsqMsgConstants.FIELD_CORRELATION_ID, correlationId);
            props.put(EsqMsgConstants.FIELD_TEST_REQ_ID,    testReqId);
            if (requestText != null) {
                props.put(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MESSAGE_ENCODING);
                props.put(EsqMsgConstants.FIELD_TEXT,             requestText);
            }
            props.put(EsqMsgConstants.FIELD_ERROR,          errorJson);

            jmsQueueTemplate.send(EsqMsgConstants.QUEUE_KC_RESPONSE, (Session session) -> {
                Message msg = session.createMessage();
                Utils.setProps(msg, props);
                return msg;
            });

            kcAudit.info("KC | URR | {}", Utils.formatProps(props));
        } catch (Exception e) {
            log.error("kcMaster: failed to publish URR: entityId={} error={}", entityId, e.getMessage(), e);
        }
    }

}

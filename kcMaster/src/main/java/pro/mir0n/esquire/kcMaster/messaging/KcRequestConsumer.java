/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URQ consumer; deserializes Text, dispatches to KcRequestHandler, publishes URS
 * 03/21/2026 mir0n  three-tier logging: kcAudit→msgLog/devLog; dual-mode URQ audit; MDC from message;
 *                   applMsgId read; dual error pattern with full context
 * 04/06/2026 mir0n  entityKind read from FIELD_ENTITY_KIND; forwarded to publishSuccess/publishFailure
 */

package pro.mir0n.esquire.kcMaster.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

@Slf4j
@Component
@RequiredArgsConstructor
public class KcRequestConsumer {

    private static final org.slf4j.Logger msgLog = LoggerFactory.getLogger("msg." + KcRequestConsumer.class.getName());
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcRequestConsumer.class.getName());

    private final KcRequestHandler handler;
    private final KcResponsePublisher publisher;
    private final ObjectMapper objectMapper;

    @JmsListener(
            destination      = EsqMsgConstants.QUEUE_KC_REQUEST,
            containerFactory = "jmsQueueListenerFactory"
    )
    public void onMessage(Message message) {
        String applMsgId     = null;
        String entityId      = null;
        int    entityKind    = 0;
        String command       = null;
        String ctrlId        = null;
        String requestId     = null;
        String correlationId = null;
        String testReqId     = null;
        String text          = null;

        try {
            applMsgId     = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            entityKind    = message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND);
            command       = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            ctrlId        = message.getStringProperty(EsqMsgConstants.FIELD_CTRL_ID);
            requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            testReqId     = message.getStringProperty(EsqMsgConstants.FIELD_TEST_REQ_ID);
            text          = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

            MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

            if (msgLog.isDebugEnabled()) {
                msgLog.info("KC | URQ | {}", Utils.formatProps(message));
            } else {
                msgLog.info("KC | URQ | {} | {} | {} | {} | {} | {} | {} | {}",
                        applMsgId, command, EsqConstants.KIND_ACCESS_PROFILE, entityId,
                        ctrlId, requestId, correlationId, testReqId);
            }
            log.info("KC | URQ | {} | {} | {} | {} | {} | {}",
                    applMsgId, command, EsqConstants.KIND_ACCESS_PROFILE, entityId,
                    ctrlId, testReqId); //xxx: requestId, correlationId are in MDC

            KcSyncRequest req = objectMapper.readValue(text, KcSyncRequest.class);
            handler.handle(command, req, correlationId, requestId);

            publisher.publishSuccess(entityId, entityKind, command, ctrlId, requestId, correlationId, testReqId);

        } catch (Exception e) {
            log.error("kcMaster: URQ processing failed: entityId={}, command={}, ctrlId={}, error={}", entityId, command, ctrlId, e.getMessage());
            devLog.error("kcMaster: URQ processing failed: entityId={}, command={}, ctrlId={}, requestId={}, correlationId={}, error={}", entityId, command, ctrlId, requestId, correlationId, e.getMessage(), e);
            publisher.publishFailure(
                    entityId,
                    entityKind,
                    command,
                    null,
                    "KC_SYNC_ERROR",
                    e.getMessage(),
                    ctrlId,
                    requestId,
                    correlationId,
                    testReqId,
                    text
            );
        } finally {
            MDC.clear();
        }
    }

}

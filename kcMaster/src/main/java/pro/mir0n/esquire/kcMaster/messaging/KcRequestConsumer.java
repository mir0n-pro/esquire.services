/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URQ consumer; deserializes Text, dispatches to KcRequestHandler, publishes URS
 */

package pro.mir0n.esquire.kcMaster.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

@Slf4j
@Component
@RequiredArgsConstructor
public class KcRequestConsumer {

    private static final org.slf4j.Logger kcAudit = LoggerFactory.getLogger("kc.audit");

    private final KcRequestHandler handler;
    private final KcResponsePublisher publisher;
    private final ObjectMapper objectMapper;

    @JmsListener(
            destination      = EsqMsgConstants.QUEUE_KC_REQUEST,
            containerFactory = "jmsQueueListenerFactory"
    )
    public void onMessage(Message message) {
        String entityId      = null;
        String command       = null;
        String ctrlId        = null;
        String requestId     = null;
        String correlationId = null;
        String testReqId     = null;
        String text          = null;

        try {
            entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            command       = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            ctrlId        = message.getStringProperty(EsqMsgConstants.FIELD_CTRL_ID);
            requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            testReqId     = message.getStringProperty(EsqMsgConstants.FIELD_TEST_REQ_ID);
            text          = message.getStringProperty(EsqMsgConstants.FIELD_TEXT);

            kcAudit.info("KC | URQ | {}", Utils.formatProps(message));

            KcSyncRequest req = objectMapper.readValue(text, KcSyncRequest.class);
            handler.handle(command, req, correlationId, requestId);

            publisher.publishSuccess(entityId, command, ctrlId, requestId, correlationId, testReqId);

        } catch (Exception e) {
            log.error("kcMaster: URQ processing failed: entityId={} command={} error={}", entityId, command, e.getMessage(), e);
            publisher.publishFailure(
                    entityId,
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
        }
    }

}

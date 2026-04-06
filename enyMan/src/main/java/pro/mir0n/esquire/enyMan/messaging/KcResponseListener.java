/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/06/2026 mir0n  created: KC response listener; logs URS/URR outcomes for reconciliation
 */
package pro.mir0n.esquire.enyMan.messaging;

import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

/**
 * Listens for KC sync responses (URS/URR) on esquire.kc.response.
 * Selector limits delivery to messages produced by this enyMan instance (CtrlID match).
 */
@Slf4j
@Component
public class KcResponseListener {

    private static final org.slf4j.Logger msgLog = LoggerFactory.getLogger("msg." + KcResponseListener.class.getName());
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcResponseListener.class.getName());

    @JmsListener(
            destination      = EsqMsgConstants.QUEUE_KC_RESPONSE,
            containerFactory = "jmsQueueListenerFactory",
            selector         = "CtrlID = '${enyman.messaging.ctrl-id}'"
    )
    public void onResponse(Message message) {
        try {
            String msgType       = message.getStringProperty(EsqMsgConstants.FIELD_MSG_TYPE);
            String applMsgId     = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String command       = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
            int    entityKind    = message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND);
            String ctrlId        = message.getStringProperty(EsqMsgConstants.FIELD_CTRL_ID);
            String requestId     = message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID);
            String correlationId = message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID);
            String testReqId     = message.getStringProperty(EsqMsgConstants.FIELD_TEST_REQ_ID);

            MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);

            String tag = EsqMsgConstants.MSG_TYPE_REJECT.equals(msgType) ? "URR" : "URS";
            if (msgLog.isDebugEnabled()) {
                msgLog.info("KC | {} | {}", tag, Utils.formatProps(message));
            } else {
                msgLog.info("KC | {} | {} | {} | {} | {} | {} | {} | {} | {}",
                        tag, applMsgId, command, entityKind, entityId,
                        ctrlId, requestId, correlationId, testReqId);
            }
            log.info("KC | {} | {} | {} | {} | {} | {} | {}",
                    tag, applMsgId, command, entityKind, entityId, ctrlId, testReqId);

            // todo: correlate by testReqId; update sync status / reconciliation record

        } catch (Exception e) {
            log.error("enyMan: failed to process KC response: {}", e.getMessage());
            devLog.error("enyMan: failed to process KC response: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}

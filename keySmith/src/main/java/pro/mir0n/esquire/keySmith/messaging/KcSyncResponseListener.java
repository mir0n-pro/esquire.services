/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URS/URR response listener; logs KC sync outcome for reconciliation
 * 03/21/2026 mir0n  JMS selector added: CtrlID = '${keysmith.messaging.ctrl-id}' — filters responses
 *                   to this instance only; full field reads, MDC, three-tier logging, dual-mode msg audit
 */

package pro.mir0n.esquire.keySmith.messaging;

import jakarta.jms.Message;
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
public class KcSyncResponseListener {

    private static final org.slf4j.Logger msgLog = LoggerFactory.getLogger("msg." + KcSyncResponseListener.class.getName());
    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KcSyncResponseListener.class.getName());

    @JmsListener(
            destination      = EsqMsgConstants.QUEUE_KC_RESPONSE,
            containerFactory = "jmsQueueListenerFactory",
            selector         = "CtrlID = '${keysmith.messaging.ctrl-id}'"
    )
    public void onResponse(Message message) {
        try {
            String msgType       = message.getStringProperty(EsqMsgConstants.FIELD_MSG_TYPE);
            String applMsgId     = message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID);
            String command       = message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE);
            String entityId      = message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID);
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
                        tag, applMsgId, command, EsqConstants.KIND_ACCESS_PROFILE, entityId,
                        ctrlId, requestId, correlationId, testReqId);
            }
            log.info("KC | {} | {} | {} | {} | {} | {} | {}",
                    tag, applMsgId, command, EsqConstants.KIND_ACCESS_PROFILE, entityId, ctrlId, testReqId);

            // todo: correlate by testReqId; update sync status / reconciliation record
        } catch (Exception e) {
            log.error("keySmith: failed to process KC response: {}", e.getMessage());
            devLog.error("keySmith: failed to process KC response: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

}

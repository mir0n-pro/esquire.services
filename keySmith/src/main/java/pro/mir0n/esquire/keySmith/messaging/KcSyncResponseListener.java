/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URS/URR response listener; logs KC sync outcome for reconciliation
 */

package pro.mir0n.esquire.keySmith.messaging;

import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.jms.Utils;

@Slf4j
@Component
public class KcSyncResponseListener {

    private static final org.slf4j.Logger kcSync = LoggerFactory.getLogger("kc.sync");

    @JmsListener(
            destination      = EsqMsgConstants.QUEUE_KC_RESPONSE,
            containerFactory = "jmsQueueListenerFactory"
    )
    public void onResponse(Message message) {
        try {
            String msgType = message.getStringProperty(EsqMsgConstants.FIELD_MSG_TYPE);
            String props   = Utils.formatProps(message);

            if (EsqMsgConstants.MSG_TYPE_REJECT.equals(msgType)) {
                kcSync.info("KC | URR | {}", props);
            } else {
                kcSync.info("KC | URS | {}", props);
            }

            // todo: correlate by testReqId; update sync status / reconciliation record
        } catch (Exception e) {
            log.error("keySmith: failed to process KC response: {}", e.getMessage(), e);
        }
    }

}

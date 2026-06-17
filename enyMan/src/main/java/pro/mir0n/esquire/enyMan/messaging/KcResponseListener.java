/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/06/2026 mir0n  created: KC response listener; logs URS/URR outcomes for reconciliation
 * 06/14/2026 mir0n  bus-oriented: consumes the {esquire.kc, kc-response} catalog leg via a receive x-Rod with a
 *                   RodID = '<this instance>' selector (only this enyMan's responses). URS vs URR is the msg-type.
 * 06/14/2026 mir0n  the x-Rod is opened by the shared XRodManager (one frontend, common to every service); the
 *                   manager owns the receive pool + the transport consumer + start/stop. The class is just the
 *                   worker (onResponse) plus the one constructor line.
 * 06/15/2026 mir0n  net: the @JmsListener method (CtrlID selector, JMS Message property reads) is gone;
 *                   ctor registers onResponse(RodEvent) via XRodManager.consumer(BUS_KEY_KC, CLIENT); the
 *                   worker reads requestId/correlationId/msgType off the RodEvent (URS vs URR).
 */
package pro.mir0n.esquire.enyMan.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

/** Receives KC sync responses (URS/URR) for this enyMan instance off the {esquire.kc, kc-response} leg. */
@Slf4j
@Component
public class KcResponseListener {

    public KcResponseListener(XRodManager rods) {
        // selectByRodId: only the responses tagged with THIS instance's rod-id reach us.
        rods.consumer(EsqMsgConstants.BUS_KEY_KC, Role.CLIENT, this::onResponse);
    }

    void onResponse(RodEvent e) {
        MDC.put(EsqConstants.PD_REQUEST_ID, e.requestId());
        MDC.put(EsqConstants.PD_CORRELATION_ID, e.correlationId());
        try {
            // msgType is the authoritative URS/URR tag (no need to inspect the body).
            log.info("KC | {} | {} | {} | {} | {}", e.msgType(), e.opCode(), e.kind(), e.entityId(), e.requestId());
            // todo: correlate by requestId; update sync status / reconciliation record
        } finally {
            MDC.clear();
        }
    }
}

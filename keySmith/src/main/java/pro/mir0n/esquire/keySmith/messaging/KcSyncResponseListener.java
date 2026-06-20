/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URS/URR response listener; logs KC sync outcome for reconciliation
 * 06/14/2026 mir0n  bus-oriented: consumes the {esquire.kc, kc-response} catalog leg via a receive x-Rod with a
 *                   RodID = '<this instance>' selector (only this keySmith's responses). URS vs URR is the msg-type.
 * 06/14/2026 mir0n  the x-Rod is opened by the shared XRodManager; the manager owns the receive pool + transport
 *                   consumer + start/stop. The class is just the worker (onResponse) plus the constructor line.
 * 06/15/2026 mir0n  consumes via the shared XRodManager (ctor: rods.consumer(BUS_KEY_KC, Role.CLIENT,
 *                   this::onResponse)); onResponse(RodEvent) reads the typed envelope (msgType is the
 *                   authoritative URS/URR tag). Dropped the @JmsListener / Message field reads and the
 *                   msgLog dual-mode logging.
 */

package pro.mir0n.esquire.keySmith.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

/** Receives KC sync responses (URS/URR) for this keySmith instance off the {esquire.kc, kc-response} leg. */
@Slf4j
@Component
public class KcSyncResponseListener {

    public KcSyncResponseListener(XRodManager rods) {
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

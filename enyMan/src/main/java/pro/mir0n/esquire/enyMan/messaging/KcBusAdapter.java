/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created (was KcRequestPublisher + the response worker): the enyMan end of the kc bus (CLIENT)
 *                   -- one adapter, both legs, on the single kc-CLIENT rod (from the facade). publishPathUpdate()
 *                   transmits an EVENT_UPDATE_PATH URQ to kcMaster after a USR move; onResponse() handles the
 *                   URS/URR reply for this instance (the rod-id selector isolates it).
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 */
package pro.mir0n.esquire.enyMan.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The enyMan end of the kc bus (CLIENT role): one rod, both legs. {@link #publishPathUpdate} transmits an
 * EVENT_UPDATE_PATH URQ to kcMaster after a USR entity move (fire-and-forget; the tx already committed);
 * {@link #onResponse} receives the URS/URR reply for THIS enyMan instance (the rod-id selector isolates it).
 */
@Slf4j
@Component
public class KcBusAdapter {

    private final IXRod rod;

    public KcBusAdapter() {
        // kc CLIENT: transmit UPDATE_PATH requests + receive URS/URR responses (rod-id selector), on one rod.
        this.rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_KC);
        this.rod.setWorker(this::onResponse);   // role-support: throws if the rod has no receive leg
        this.rod.transmit(null);                // role-support: probe -- throws if the rod has no transmit leg
    }

    public void publishPathUpdate(String entityId, int entityKind, String newPath,
                                  String requestId, String correlationId) {
        // guarantee a non-null tracking id (the former testReqId; it rides as the requestId on the wire).
        String reqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id",   entityId);
            body.put("kind", entityKind);
            body.put("path", newPath);
            RodEvent e = new RodEvent(RodEvent.Op.UPDATE_PATH, entityKind, entityId, null,
                    System.currentTimeMillis(), correlationId, reqId, null, null, BusConstants.MSG_TYPE_REQUEST, body);
            rod.transmit(e);
            log.info("KC | URQ | {} | {} | {} | {}", BusConstants.EVENT_UPDATE_PATH, entityKind, entityId, reqId);
        } catch (Exception ex) {
            // fire-and-forget: the move tx already committed, so a publish failure is logged for reconciliation.
            log.error("enyMan: failed to publish URQ: entityId={}, requestId={}, error={}", entityId, reqId, ex.getMessage());
        }
    }

    /** Receive the KC sync response (URS/URR) for this enyMan instance off the kc-response leg. */
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

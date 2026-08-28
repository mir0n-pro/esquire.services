/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created: the keySmith end of the kc bus (CLIENT) -- one adapter, both legs. Merges the former
 *                   KcSyncPublisher (transmit URQ) + KcSyncResponseListener (receive URS/URR) onto the single
 *                   kc-CLIENT rod (from the facade). publish() builds the URQ from the access profile + roles and
 *                   transmits it; onResponse() handles the reply tagged with this instance's rod-id.
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 * 07/15/2026 mir0n  v1.2.11 T11 -- the kc-bus receive worker (onResponse) stamps MDC via
 *                   EsqContextHolder.applyMessage(event) and clears in a finally (I10)
 * 08/11/2026 mir0n  v1.2.12 -- the RodEvent constructor call carries a null change number: a KeyCloak
 *                   request leg reports none
 * 08/12/2026 mir0n  v1.2.13 -- implements IIdentityGateway: postRequest(RodEvent) transmits, postMessage is skipped,
 *                   start()/stop() take the kc leg (was the constructor)
 */
package pro.mir0n.esquire.keySmith.messaging;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.identity.IIdentityGateway;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.function.Consumer;

/**
 * The keySmith end of the kc bus (CLIENT role): one rod, both legs, and keySmith's identity gateway when the
 * provider is another service. {@link #post} transmits the URQ to kcMaster after the DB transaction commits;
 * {@link #onResponse} receives the URS/URR reply for THIS keySmith instance (the rod-id selector isolates it).
 *
 * <p>The process wires it as the {@link IIdentityGateway}, so nothing in keySmith's service layer knows a bus
 * is involved. The event it is handed is the event that goes on the wire -- there is nothing to translate.
 */
@Slf4j
public class KcBusAdapter implements IIdentityGateway {

    private IXRod rod;
    private volatile Consumer<RodEvent> resultHandler;

    /** Takes the kc leg: transmit URQ requests + receive URS/URR responses (rod-id selector), on one rod. */
    @Override
    public void start() {
        this.rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_KC);
        this.rod.setWorker(this::onResponse);   // role-support: throws if the rod has no receive leg
        this.rod.transmit(null);                // role-support: probe -- throws if the rod has no transmit leg
    }

    /** Nothing to close: the rod belongs to the messaging bus, which closes it at context close. */
    @Override
    public void stop() {
    }

    /** Skipped: on the bus, kcMaster subscribes to the entity broadcasts itself. */
    @Override
    public void postMessage(RodEvent event) {
    }

    @Override
    public void setResultHandler(Consumer<RodEvent> handler) {
        this.resultHandler = handler;
    }

    /** Transmits the URQ to kcMaster. The caller's transaction has already committed. */
    @Override
    public void postRequest(RodEvent event) {
        try {
            rod.transmit(event);
            log.info("KC | URQ | {} | {} | {} | {}", event.opCode(), event.kind(), event.entityId(), event.requestId());
        } catch (Exception e) {
            // DB already committed -- message failure logged for reconciliation
            log.error("keySmith: failed to publish URQ: entityId={}, command={}, requestId={}, error={}",
                    event.entityId(), event.opCode(), event.requestId(), e.getMessage());
        }
    }

    /** Receive the KC sync response (URS/URR) for this keySmith instance off the kc-response leg. */
    void onResponse(RodEvent e) {
        EsqContextHolder.applyMessage(e);
        try {
            // msgType is the authoritative URS/URR tag (no need to inspect the body).
            log.info("KC | {} | {} | {} | {} | {}", e.msgType(), e.opCode(), e.kind(), e.entityId(), e.requestId());
            Consumer<RodEvent> handler = this.resultHandler;
            if (handler != null) {
                handler.accept(e);
            }
            // todo: correlate by requestId; update sync status / reconciliation record
        } finally {
            EsqContextHolder.clear();
        }
    }
}

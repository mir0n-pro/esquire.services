/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created (was EsqEntityBroadcastPublisher): the enyMan end of the entity bus (SERVER) -- the
 *                   transmit leg onto the entity-broadcast TOPIC (rod from the facade). publish() builds a
 *                   RodEvent (msg-type UE) and transmits it post-commit.
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 * 06/27/2026 mir0n  one entity rod (BUS_KEY_ENTITY, role CLIENT) now runs BOTH legs on a shared connection:
 *                   transmit (publish) + receive. onPeerCreate(sink) sets a broker subscription selector
 *                   (EventType = 'C') so the receive leg forwards a PEER instance's CREATE to the move-queue
 *                   reconcile intake; the slot's noLocal drops THIS instance's own publications -- closes the
 *                   cross-instance race-8b gap the per-instance inMove() left open
 * 07/23/2026 mir0n  v1.2.11 -- forwardPeerCreate carries the create's OWN cid/rid onto the CreateReconcileItem
 *                   (the path-fix reissue stays correlated to the create it repairs, no leftover-worker-MDC reliance)
 * 08/11/2026 mir0n  v1.2.12 -- publish() takes the change number onto the event header and prints it on the
 *                   UE line; the parameter doc states which counter each event type carries
 */
package pro.mir0n.esquire.enyMan.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.enyMan.queue.CreateReconcileItem;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.MessagingBus;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The enyMan end of the entity bus, on ONE rod ({@code BUS_KEY_ENTITY}, role CLIENT) that runs both legs over a
 * single shared connection: a transmit leg onto the entity-broadcast TOPIC and a receive leg off the same topic.
 *
 * <p>Transmit ({@link #publish}): builds a {@link RodEvent} (the event-type code -> Op, the snapshot -> body,
 * msg-type UE) and sends it out the leg, only after the transaction commits (post-commit contract).
 *
 * <p>Receive ({@link #onPeerCreate}): forwards a PEER enyMan instance's CREATE to the move queue's reconcile
 * intake, so the MOVING instance reconciles a create another instance made during the move -- closes the
 * cross-instance race-8b gap the per-instance {@code inMove()} left open. The move queue depends on this adapter
 * (transmit), so this adapter must NOT depend back on it: the queue pushes its sink in via {@code onPeerCreate}.
 */
@Slf4j
@Component
public class EntityBusAdapter {

    private final IXRod rod;

    public EntityBusAdapter() {
        // One entity rod, both legs on one shared connection. Probe the transmit leg now (throws if absent).
        this.rod = MessagingBus.getInstance().getXRod(EsqConstants.BUS_KEY_ENTITY);
        this.rod.transmit(null);   // role-support: probe -- throws if the rod has no transmit leg
    }

    /**
     * Publish an entity state event.
     *
     * @param entityKind    FIX-JSON EntityKind value
     * @param entityId      FIX-JSON EntityID value
     * @param eventType     C / U / D / X (BusConstants.EVENT_*)
     * @param requestId     from request context; may be null
     * @param correlationId from correlation context; may be null
     * @param text          entity state snapshot (may be null)
     * @param changeNo      the change number this event is reporting, or null when none is known.
     *                      WHICH counter it is follows the event type: C / U / D carry the ENTITY row's
     *                      number, X (path) carries the PATH row's number. They are separate counters --
     *                      each unique within the entity id, neither comparable with the other -- so a
     *                      receiver must guard a path event against a path number and an entity event
     *                      against an entity number.
     */
    public void publish(int entityKind, String entityId, String eventType,
                        String requestId, String correlationId, Map<String, Object> text, Long changeNo) {
        RodEvent e = new RodEvent(RodEvent.opFromCode(eventType), entityKind, entityId, null, changeNo,
                System.currentTimeMillis(), correlationId, requestId, null, null,
                BusConstants.MSG_TYPE_ENTITY_BROADCASTS, text != null ? text : Map.of());
        rod.transmit(e);
        log.info("ENTITY | UE | {} | {} | {} | {} | cn={}", eventType, entityKind, entityId, correlationId, changeNo);
    }

    /**
     * Bind the receive leg's worker: forward a PEER enyMan instance's CREATE to {@code sink} (the move queue's
     * reconcile intake). Called once by the move queue after it is ready (it depends on this adapter, so this
     * adapter cannot depend back on it -- the queue pushes its sink in here instead).
     *
     * <p>Narrowing is at the BROKER via a subscription selector ({@link IXRod#setWorker(String,
     * java.util.function.Consumer)}): only enyMan creates entities, so every CREATE on the entity bus is an
     * enyMan create -- the selector needs only the op ({@code EventType = 'C'}). Own-exclusion is the transport's
     * {@code noLocal} on the entity slot: publisher and consumer share one connection, so the broker drops THIS
     * instance's OWN publications -- only a PEER instance's creates arrive (own creates ride the local
     * {@code submitReconcileIfInMove} path).
     */
    public void onPeerCreate(Consumer<CreateReconcileItem> sink) {
        String subscription = BusConstants.FIELD_EVENT_TYPE + " = '" + BusConstants.EVENT_CREATE + "'";
        rod.setWorker(subscription, e -> forwardPeerCreate(e, sink));
        log.info("ENTITY-RX wired: rodId={}, subscription=[{}] (peer CREATEs -> move queue)", rod.rodId(), subscription);
    }

    // Each delivered event is already narrowed by the broker selector (CREATE op) and the transport noLocal (not
    // self); extract the parent context PLUS the create's own cid/rid, and hand them to the reconcile sink so the
    // path-fix runs -- and reissues -- under the create's identity (no reliance on leftover worker MDC).
    private void forwardPeerCreate(RodEvent e, Consumer<CreateReconcileItem> sink) {
        if (e != null) {
            Map<String, Object> body = e.body();
            String parentId = (body != null && body.get(EsqConstants.TEXT_PARENT_ID) instanceof String s) ? s : null;
            String path     = (body != null && body.get(EsqConstants.TEXT_PATH)      instanceof String s) ? s : null;
            sink.accept(new CreateReconcileItem(e.entityId(), e.kind(), parentId, path,
                    e.correlationId(), e.requestId()));
        }
    }
}

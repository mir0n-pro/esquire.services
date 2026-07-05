/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: the x-rod SESSION-sublayer interface + producer extension point, in the engine
 *                   package so the x-rod depends on THIS, not on any concrete sublayer (the implementations live in
 *                   the .sublayer sub-package, built in via SessionSublayerFactory). A session sublayer is an
 *                   event-driven collaborator the feed (tx) worker drives as a message passes -- NOT in the send
 *                   path (the worker owns encode + dispatch); it marks its own state and reacts, never sends. The
 *                   hooks: beforeSend / onSendSuccess / onSendError / onReceiveSessn / tick / health / start /
 *                   shutdown. The alive keepalive and the producer send-retry are the members today; a receive-side
 *                   sublayer is future work.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

/**
 * A composable (sub)layer of the x-rod SESSION -- a stateful collaborator the engine ({@link AXRod}) drives, with
 * NO thread of its own. The concrete sublayers (the alive keepalive, the producer send-retry, and future patterns)
 * live in the {@code sublayer} sub-package and are built in via a factory, so the engine never depends on a
 * specific implementation.
 *
 * <p>A sublayer is NOT in the send workflow -- the feed (tx) worker owns the actual send (encode + dispatch). A
 * sublayer is an event-driven HANDLER: the worker calls its hooks as a message passes (the alive marks, the
 * send-retry decision) and it reacts on its own state, never sending. This is the DEFINED EXTENSION POINT for
 * further resilience patterns (circuit breaker, bulkhead, per-message timeout, the receive-side layers).
 *
 * <p>{@link #tick()} is the one hook the engine drives generically: a step fired on every idle pass (the ~1s
 * MessagingBus ticker), so a sublayer runs its cadence off the shared ticker with NO thread of its own (the
 * send-retry re-send, the alive heartbeat). Default no-op for a sublayer that needs no maintenance.
 */
public interface ISessionSublayer {

    /** Idle-driven maintenance step, fired by the rod's {@code idle()} hook on every MessagingBus tick (~1s).
     *  Every sublayer implements it (the engine ticks them all) -- the send-retry re-send, the alive heartbeat. */
    void tick();

    default void start() {
    }
    default void shutdown() {
    }
    default void beforeSend(RodEvent ev) {
    }
    default void onSendSuccess(RodEvent ev){
    }
    default Object onSendError(RodEvent ev, Object msg, Throwable error) {
        return null;
    }
    default void onReceiveSessn(RodEvent ev){
    }
    default TransportHealth health() {
        return TransportHealth.UP;
    }
}
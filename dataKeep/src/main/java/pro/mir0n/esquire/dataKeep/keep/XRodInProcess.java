/*
 *  Esquire frameworks (tm)
 *  esquire-dataKeep
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/18/2026 mir0n  created: the GENERIC in-process relay. An AXRod whose transmit feed loops straight into its
 *                   OWN worker pool, which runs the worker the frontend hands to start() -- no transport, no codec,
 *                   no bus consumer/publisher. This is the piece base XRod does NOT provide for a producer leg: it
 *                   STARTS the worker pool. Resolved by rod-class like any x-rod (a leg names it as the in-process
 *                   sink); XRodManager.consumer(busKey, role, worker) passes the applier as that worker.
 */
package pro.mir0n.esquire.dataKeep.keep;

import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.impl.AXRod;

import java.util.function.Consumer;

/** A generic in-process x-rod: {@code transmit(event)} feeds the event into the engine's own worker pool, which
 *  runs the {@code worker} (the keep's applier). No transport leg. It starts the pool a bare transmit-only
 *  producer leg lacks. */
public final class XRodInProcess extends AXRod {

    @Override
    public void start(String name, Logger devLog, Consumer<RodEvent> worker) {
        // in-process: outbound loops the feed into the receive pool, which runs the worker (the applier). No transport.
        startEngine(name, devLog, this::receive, worker);
    }
}

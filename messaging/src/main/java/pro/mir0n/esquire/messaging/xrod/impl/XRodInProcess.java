/*
 *  Esquire frameworks (tm)
 *  common library
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
 * 06/22/2026 mir0n  moved to messaging.xrod.impl (was dataKeep.keep). start(name,devLog,worker) split into
 *                   init(name,devLog) (buildEngine -- the feed loops into the pool) + start() (inherited
 *                   runEngine); the worker is set via setWorker; no longer final.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import org.slf4j.Logger;

/** A generic in-process x-rod: {@code transmit(event)} feeds the event into the engine's own worker pool, which
 *  runs the {@code worker} (set via {@code setWorker}). No transport leg. It starts the pool a bare transmit-only
 *  producer leg lacks. */
public class XRodInProcess extends AXRod {

    @Override
    public void init(String name, Logger devLog) {
        // in-process: the feed loops into the receive pool, which applies the live worker. No transport; the
        // service submits into the loop via transmit(). The worker is set/reset via setWorker.
        // buildEngine CREATEs the (idle) feed + pool; start() (inherited AXRod.start -> runEngine) sets them
        // running -- there is no transport leg, so no consumer to start.
        buildEngine(name, devLog, this::receive, this::applyWorker);
    }
}

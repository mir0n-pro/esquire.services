/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: a non-sending x-Rod pod -- it log.info()s each event's full content to the leg's
 *                   msg-audit (led by a directive read from its own x-rod.info sub-block, a XRodInfoParams)
 *                   instead of transmitting. A dry-run / kill-switch rod: it composes the default XRod transmit
 *                   machinery (post -> commit -> stamp feed) but swaps the transport for a log line.
 * 06/17/2026 mir0n  dropped the composed inner XRod -- log-only directly: transmit() / receive() log the event
 *                   line (no feed / pool / transport); usesOutboundTransport() / bindInbound() removed
 * 06/22/2026 mir0n  start(name,devLog,worker) split into setWorker (no-op) + init (the log-line setup) + start
 *                   (no-op); import Role/XRodParams from messaging.catalog, IXRod/RodEvent from messaging.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.function.Consumer;

/** A non-sending x-rod: it log.info()s each event (full content, led by a directive) instead of transmitting.
 *  No transport, no feed, no pool -- a dry-run / kill-switch leg. */
public final class XRodInfo implements IXRod {

    private static final String DEFAULT_DIR = "Skipped";
    /** This x-rod's OWN named param sub-block under the leg's x-rod (x-rod.info). */
    public static final String PARAM = "info";

    private String dir;                      // the directive logged in the dir slot (from x-rod.info)
    private BusIdentity identity;            // names the leg -> this x-rod's msg-audit logger
    private Logger msgLog;                   // msg.<bus-id>.<slot-id>; null = no msg-audit

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        // PREPARE: read this x-rod's OWN info sub-block (the directive) + keep the leg identity for THIS x-rod's log.
        XRodInfoParams p = params != null ? params.sub(PARAM, XRodInfoParams.class) : null;
        this.dir      = p != null ? p.dirOr(DEFAULT_DIR) : DEFAULT_DIR;
        this.identity = params != null
                ? new BusIdentity(params.busId(), params.slotId(), params.rodId()) : null;
    }

    @Override
    public void setWorker(Consumer<RodEvent> worker) {
        // log-only x-rod: no receive worker
    }

    @Override
    public void init(String name, Logger devLog) {
        this.msgLog = identity != null && identity.busId() != null
                ? LoggerFactory.getLogger("msg." + identity.busId() + "." + identity.slotId())
                : null;
        if (devLog != null) {
            devLog.info("x-rod-info[{}]: log-only (directive={}) -- events are logged, never sent", name, dir);
        }
    }

    @Override
    public void start() {
        // log-only: no threads, no transport -- transmit()/receive() log inline.
    }

    @Override
    public void transmit(RodEvent event) {
        logInfo(event);
    }

    @Override
    public void receive(RodEvent event) {
        logInfo(event);   // log-only: a received event is logged just like a transmitted one
    }

    @Override
    public void shutdown() {
        // log-only: nothing to stop.
    }

    /** Log-only "send": the full event content, led by the directive in the dir slot (TX|RX's place). */
    private void logInfo(RodEvent e) {
        if (msgLog != null && msgLog.isInfoEnabled()) {
            msgLog.info(describe(dir, e));
        }
    }

    /** The logged line: {@code <directive> | <msgType> | <op> | <kind> | <entityId> | <subId> | <rodId> |
     *  <requestId> | <uid> | <correlationId> | <actionTime> | <body>} -- the whole RodEvent. */
    static String describe(String dir, RodEvent e) {
        return dir + " | " + e.msgType() + " | " + e.opCode() + " | " + e.kind() + " | " + e.entityId()
                + " | " + e.subId() + " | " + e.rodId() + " | " + e.requestId() + " | " + e.uid()
                + " | " + e.correlationId() + " | " + e.actionTime() + " | " + e.body();
    }
}

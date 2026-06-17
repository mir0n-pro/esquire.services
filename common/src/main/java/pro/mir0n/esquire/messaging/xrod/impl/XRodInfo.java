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
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

import java.util.Map;
import java.util.function.Consumer;

/** A non-sending x-rod: it log.info()s each event (full content, led by a directive) instead of transmitting. */
public final class XRodInfo implements IXRod {

    private static final String DEFAULT_DIR = "Skipped";
    /** This x-rod's OWN named param sub-block under the leg's x-rod (x-rod.info). */
    public static final String PARAM = "info";

    private final XRod inner = new XRod();   // reuses the transmit machinery (feed + post-commit stamp)
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
        // the inner runs IN-PROCESS (no codec) with NO leg identity (busId/slotId stripped) -> no inner TX/RX
        // msg-audit; each committed event is handed to logInfo, which logs exactly ONE rich line on THIS x-rod's log.
        inner.configure(params != null ? params.withBus(null, null, null) : null, role, null);
    }

    @Override
    public void start(String name, Logger devLog, Consumer<RodEvent> worker) {
        this.msgLog = identity != null && identity.busId() != null
                ? LoggerFactory.getLogger("msg." + identity.busId() + "." + identity.slotId())
                : null;
        inner.start(name, devLog, this::logInfo);
        if (devLog != null) {
            devLog.info("x-rod-info[{}]: log-only (directive={}) -- events are logged, never sent", name, dir);
        }
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

    @Override
    public boolean isEnabled() {
        return inner.isEnabled();
    }

    @Override
    public boolean usesOutboundTransport() {
        return false;   // log-only: it logs each event instead of sending, no transport leg
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source, String msgType) {
        inner.post(op, kind, entityId, subId, source, msgType);
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, String msgType) {
        inner.post(op, kind, entityId, subId, msgType);
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body, String msgType) {
        inner.post(op, kind, entityId, subId, body, msgType);
    }

    @Override
    public void transmit(RodEvent event) {
        inner.transmit(event);
    }

    @Override
    public void submit(RodEvent event) {
        inner.submit(event);
    }

    @Override
    public void bindInbound(AutoCloseable inbound) {
        inner.bindInbound(inbound);
    }

    @Override
    public void shutdown() {
        inner.shutdown();
    }
}

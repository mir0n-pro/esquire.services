/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/30/2026 mir0n  created: the x-rod MESSAGE-AUDIT module -- a null-safe wrapper over the per-leg
 *                   msg.<bus-id>.<slot-id> logger. Built from the leg identity (no logger when the leg has no
 *                   bus-id), it centralises the audit-logger construction + the standard leg-trace / transmit-error
 *                   line format so the engine (AXRod), the log-only rod (XRodInfo), and the session sublayers
 *                   (send-retry) do not each repeat it. Raw info/warn passthroughs cover a custom line.
 * 08/11/2026 mir0n  v1.2.12 -- changeNo added to the TX/RX leg trace, printed as "-" when the producer
 *                   supplied none
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.BusIdentity;

/** The x-rod message-audit: a null-safe wrapper over the per-leg {@code msg.<bus-id>.<slot-id>} appender. Built from
 *  the leg {@link BusIdentity} -- a leg with no bus-id (a test / disabled / in-process leg) gets no logger and every
 *  method is a no-op. Centralises the audit-logger construction + the standard line format; the raw {@link #info} /
 *  {@link #warn} passthroughs let a caller log a custom line (the full-event dump, the send-retry trail). */
public final class MsgAudit {

    private final Logger log;   // msg.<bus-id>.<slot-id>; null = no bus identity -> no audit

    public MsgAudit(BusIdentity identity) {
        this.log = identity != null && identity.busId() != null
                ? LoggerFactory.getLogger("msg." + identity.busId() + "." + identity.slotId())
                : null;
    }

    /** The standard leg trace:
     *  {@code <dir> | msgType | op | kind | entityId | subId | changeNo | rodId | requestId}. A SESSION
     *  (alive-protocol) event is gated at DEBUG so heartbeat noise can be silenced separately; an application event
     *  at INFO. The change number prints as "-" when the producer supplied none (session messages, and any event
     *  with no row behind it) -- absent is NOT zero. */
    public void log(String dir, RodEvent e) {
        if (log != null
                && ((e.isSession() && log.isDebugEnabled())
                 || (!e.isSession() && log.isInfoEnabled()))) {
            log.info("{} | {} | {} | {} | {} | {} | {} | {} | {}",
                    dir, e.msgType(), e.opCode(), e.kind(), e.entityId(), e.subId(),
                    e.changeNo() != null ? e.changeNo() : "-", e.rodId(), e.requestId());
        }
    }

    /** A transmit error: the {@code TX-ERR} line plus the cause (a failed dispatch). A SESSION event is gated at
     *  DEBUG (same as {@link #log}); an application event at WARN. */
    public void err(RodEvent e, Throwable error) {
        if (log != null
                && ((e.isSession() && log.isDebugEnabled())
                 || (!e.isSession() && log.isWarnEnabled()))) {
            log.warn("TX-ERR | {} | {} | {} | {} | {} | {} | {} | {}",
                    e.msgType(), e.opCode(), e.kind(), e.entityId(), e.subId(), e.rodId(), e.requestId(),
                    error != null ? error.toString() : "");
        }
    }

    /** Raw info passthrough (null-guarded) -- for a custom line (e.g. the full-event dump). */
    public void info(String format, Object... args) {
        if (log != null && log.isInfoEnabled()) {
            log.info(format, args);
        }
    }

    /** Raw warn passthrough (null-guarded) -- for a custom line (e.g. the send-retry hold / drop). */
    public void warn(String format, Object... args) {
        if (log != null && log.isWarnEnabled()) {
            log.warn(format, args);
        }
    }
}

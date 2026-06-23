/*
 *  Esquire frameworks (tm)
 *  esquire-audit
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/17/2026 mir0n  created: bridges the audit module's flows onto the messaging bus. post() buffers each change
 *                   in the caller's transaction; after commit it stamps ONE actionTime, snapshots the request
 *                   context (correlation / request / uid), builds the RodEvent and transmit()s it on the audit
 *                   x-rod. With no active transaction it transmits immediately. The x-rod stays pure transmit /
 *                   receive -- the transactional ordering and the entity->event build live HERE (lifted out of XRod).
 * 06/22/2026 mir0n  IXRod / RodEvent imports moved to messaging.xrod; the audit x-rod passed in is the one the
 *                   facade builds (MessagingBus.getXRod).
 */
package pro.mir0n.esquire.audit;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The audit module's bridge onto the messaging bus: services call {@code post(...)} inside their write
 * transaction, and the bridge relays a {@link RodEvent} on the audit x-rod -- but only AFTER the
 * transaction commits, so a rolled-back change is never relayed. With no active transaction it transmits
 * immediately. The bus x-rod itself knows nothing of transactions or entities; this is the audit module
 * using the bus for its needs.
 */
public final class AuditBusBridge {

    private final IXRod xrod;
    private final ThreadLocal<List<Entry>> buffer = new ThreadLocal<>();

    public AuditBusBridge(IXRod xrod) {
        this.xrod = xrod;
    }

    /** Whether audit is on (the audit x-rod is a real leg, not the OFF one). Callers guard expensive
     *  audit-body assembly on this; {@link #post} is itself a no-op when disabled. */
    public boolean isEnabled() {
        return xrod.isEnabled();
    }

    /** Post an entity / param row directly: its fields are mapped into the event body (skipped for a DELETE). */
    public void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source) {
        if (xrod.isEnabled()) {
            Map<String, Object> body = null;
            if (source != null && op != RodEvent.Op.DELETE) {
                body = new HashMap<>();
                source.fillMap(body);
            }
            post(op, kind, entityId, subId, body);
        }
    }

    /** Post with no body (e.g. a DELETE). */
    public void post(RodEvent.Op op, int kind, String entityId, String subId) {
        post(op, kind, entityId, subId, (Map<String, Object>) null);
    }

    /** Post a pre-mapped body. Buffers in the active transaction (flushed after commit); transmits immediately
     *  when no transaction is active. The event always carries msg-type {@code MSG_TYPE_AUDIT}. */
    public void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body) {
        if (xrod.isEnabled()) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                xrod.transmit(new RodEvent(op, kind, entityId, subId, System.currentTimeMillis(),
                        crl(), req(), uid(), null, EsqMsgConstants.MSG_TYPE_AUDIT, normalizeBody(op, body)));
            } else {
                List<Entry> buf = buffer.get();
                if (buf == null) {
                    buf = new ArrayList<>();
                    buffer.set(buf);
                    TransactionSynchronizationManager.registerSynchronization(new FlushOnCommit());
                }
                buf.add(new Entry(op, kind, entityId, subId, normalizeBody(op, body)));
            }
        }
    }

    private static String crl() {
        EsqRequestContext ctx = RequestContextUtils.getContext();
        return ctx != null ? ctx.correlationId() : null;
    }

    private static String req() {
        EsqRequestContext ctx = RequestContextUtils.getContext();
        return ctx != null ? ctx.requestId() : null;
    }

    private static String uid() {
        EsqRequestContext ctx = RequestContextUtils.getContext();
        return ctx != null ? ctx.uid() : null;
    }

    private static Map<String, Object> normalizeBody(RodEvent.Op op, Map<String, Object> body) {
        return (op == RodEvent.Op.DELETE) ? Map.of() : (body != null ? body : Map.of());
    }

    /** One buffered change in the current transaction (one entry per posted entity), flushed after commit. */
    private record Entry(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body) { }

    /** After the caller's transaction commits: stamp one actionTime, snapshot the request context, and transmit
     *  the buffered events OUT of the transaction. Cleared on completion (commit OR rollback). */
    private final class FlushOnCommit implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            List<Entry> buf = buffer.get();
            if (buf != null && !buf.isEmpty()) {
                long actionTime = System.currentTimeMillis();
                String crl = crl();
                String req = req();
                String uid = uid();
                for (Entry e : buf) {
                    xrod.transmit(new RodEvent(e.op(), e.kind(), e.entityId(), e.subId(), actionTime,
                            crl, req, uid, null, EsqMsgConstants.MSG_TYPE_AUDIT, e.body()));
                }
            }
        }

        @Override
        public void afterCompletion(int status) {
            buffer.remove();   // never leak the buffer onto a pooled thread
        }
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: the xy-Rod -- the producer feed. post() at the write site buffers a row
 *                   change in the CURRENT transaction (one event per entity per request -- NO coalescing;
 *                   redelivery dedup is the repository's ON CONFLICT (Postgres) / MERGE (Oracle)). A
 *                   TransactionSynchronization stamps ONE commit-time actionTime + snapshots the audit
 *                   triple (crl/req/uid) from EsqRequestContext, builds the RodEvents AFTER commit, and
 *                   feeds them through a single-worker BoundedQueueRig to the dispatcher -- (b) the
 *                   in-process xx-Rod pool, (c)/(d) the bus. Disabled (option 0) -> post() is a cheap
 *                   no-op (and the gate the DELETE-enumeration path checks via isEnabled()).
 */
package pro.mir0n.esquire.common.xrod;

import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.utils.concurrent.BoundedQueueRig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The xy-Rod producer feed -- one instance per asset-updating service.
 *
 * <p>Write sites call {@link #post} once per entity they change; the events are held in a per-transaction
 * list and emitted only AFTER the entity transaction commits (so a rollback emits nothing, and the audit
 * write never sits inside -- or can fail -- the business transaction). At commit the list is stamped with
 * one {@code actionTime}, the audit triple is snapshotted from the unified {@link EsqRequestContext}, and
 * the events are fed -- OUT of the entity transaction -- through a single-worker {@link BoundedQueueRig}
 * to the {@code dispatcher}: {@code XXRod::submit} for (b), a bus send for (c)/(d).
 *
 * <p>No in-memory coalescing: the contract is one update per entity per request, and duplicate delivery
 * (the (c) bus) is settled by the repository's {@code ON CONFLICT} / {@code MERGE} on the
 * {@code (crl_id, entity_id, kind, sub_id)} key. Single worker on the feed is required for the bus path
 * (a JMS session is not thread-safe) and harmless for the in-process path.
 */
public final class XYRod {

    private final Consumer<RodEvent> dispatcher;
    private volatile boolean enabled;
    private final BoundedQueueRig<RodEvent> feed;
    private final int feedCapacity;

    private String name = "xy-rod";
    private Logger devLog;

    /** Per-transaction list of pending changes (one entry per posted entity), flushed after commit. */
    private final ThreadLocal<List<Entry>> buffer = new ThreadLocal<>();

    /**
     * @param dispatcher   what the single feed worker does with each committed event ((b) xx-Rod
     *                     submit; (c)/(d) bus send).
     * @param enabled      option switch: false -> post() is a no-op (option 0).
     * @param feedCapacity bounded backlog of the single-worker feed queue.
     */
    public XYRod(Consumer<RodEvent> dispatcher, boolean enabled, int feedCapacity) {
        this.dispatcher   = dispatcher;
        this.enabled      = enabled;
        this.feedCapacity = Math.max(1, feedCapacity);
        this.feed         = new BoundedQueueRig<>(ev -> this.dispatcher.accept(ev));
    }

    public void start(String name, Logger devLogger) {
        this.name   = name;
        this.devLog = devLogger;
        feed.init(name, devLogger, feedCapacity);
        feed.start();
        feed.setProcessing(true);
        if (devLog != null) {
            devLog.info("xy-rod[{}]: started (enabled={})", name, enabled);
        }
    }

    public void shutdown() {
        feed.shutdown();
    }

    /** Option gate. Disabled -> post() is a no-op; the DELETE-enumeration path also checks this. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Buffer a row change in the current transaction (or feed it immediately if there is no active
     * transaction synchronization). No-op when disabled.
     *
     * @param op        CREATE / UPDATE / DELETE
     * @param kind      the (sub)asset kind (the registry key)
     * @param entityId  the owning entity id
     * @param subId     row discriminator, or null when (entityId, kind) is unique
     * @param body      the full committed row (CREATE/UPDATE); ignored for DELETE (id + kind ride the header)
     */
    /**
     * Post a row change, taking the source object directly: it fills its own body via {@link IMappable}.
     * Builds the body only when enabled (no wasted work otherwise) and only for CREATE/UPDATE -- DELETE
     * carries id + kind in the header, so a null/ignored source yields an empty body. This keeps the field
     * knowledge inside the entity and the x-Rod free of any domain field names.
     */
    public void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source) {
        if (enabled) {
            Map<String, Object> body = null;
            if (source != null && op != RodEvent.Op.DELETE) {
                body = new HashMap<>();
                source.fillMap(body);
            }
            post(op, kind, entityId, subId, body);
        }
    }

    /** Post with no body -- id + kind ride the header. Used for DELETE. */
    public void post(RodEvent.Op op, int kind, String entityId, String subId) {
        post(op, kind, entityId, subId, (Map<String, Object>) null);
    }

    public void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body) {
        if (enabled) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                // No transaction -> feed one event now (best effort).
                feed.put(build(op, kind, entityId, subId, normalizeBody(op, body), System.currentTimeMillis()));
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

    private RodEvent build(RodEvent.Op op, int kind, String entityId, String subId,
                           Map<String, Object> body, long actionTime) {
        EsqRequestContext ctx = RequestContextUtils.getContext();
        String crl = (ctx != null) ? ctx.correlationId() : null;
        String req = (ctx != null) ? ctx.requestId()     : null;
        String uid = (ctx != null) ? ctx.uid()           : null;
        RodEvent ret = new RodEvent(op, kind, entityId, subId, actionTime, crl, req, uid, body);
        return ret;
    }

    private static Map<String, Object> normalizeBody(RodEvent.Op op, Map<String, Object> body) {
        Map<String, Object> ret;
        if (op == RodEvent.Op.DELETE) {
            ret = Map.of();
        } else {
            ret = (body != null) ? body : Map.of();
        }
        return ret;
    }

    /** One buffered change (op + body for an entity in the current transaction). */
    private record Entry(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body) { }

    /** After the entity transaction commits: stamp one actionTime, snapshot the audit triple, and feed
     *  the buffered events OUT of the transaction. Cleared on completion (commit OR rollback). */
    private final class FlushOnCommit implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            List<Entry> buf = buffer.get();
            if (buf != null && !buf.isEmpty()) {
                long actionTime = System.currentTimeMillis();
                EsqRequestContext ctx = RequestContextUtils.getContext();
                String crl = (ctx != null) ? ctx.correlationId() : null;
                String req = (ctx != null) ? ctx.requestId()     : null;
                String uid = (ctx != null) ? ctx.uid()           : null;
                for (Entry e : buf) {
                    feed.put(new RodEvent(e.op(), e.kind(), e.entityId(), e.subId(), actionTime, crl, req, uid, e.body()));
                }
            }
        }

        @Override
        public void afterCompletion(int status) {
            buffer.remove();   // never leak the buffer onto a pooled thread
        }
    }
}

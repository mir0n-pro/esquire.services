/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: handler-dispatch hub (v1.2.5 Taijitu refactor Step 1).
 *                   Extracted from EsqEntityBroadcastConsumer's inline handler map.
 *                   Currently owned by BizTreeDirectorLegacy. In Step 3 each
 *                   Monad embeds its own hub instance so per-monad cache
 *                   repositories carry per-monad handlers.
 * 06/02/2026 mir0n  dispatch(): skip path now logs devLog.warn (no handler / null textNode) split
 *                   into two guarded branches, instead of a single silent no-op.
 * 06/23/2026 mir0n  EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- dispatch() counts esq.biz.tree.handler.dispatch.total (tags event, kind,
 *                   outcome = handled|no-handler|no-payload|failed) in a finally; the body is unchanged. The
 *                   FAILED value is the point: the catch here SWALLOWS the handler exception, so a handler that
 *                   blows up leaves the cache silently stale while the bus still counts the message as received --
 *                   nothing else anywhere reports that the tree did not change
 * 08/11/2026 mir0n  v1.2.12 -- dispatch() takes the event's change number and guards on it: a path event
 *                   against the node's stored path number, every other event against its entity number;
 *                   unknown or unseen applies unguarded, and the applied number is stamped back. Takes
 *                   IBizTreeCacheRepository for those reads
 */
package pro.mir0n.esquire.bizTree.access;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.CreateAcctHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.CreateOrgHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.CreateUsrHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.DeleteEntityHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.MoveAcctHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.MoveOrgHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.MoveUsrHandler;
import pro.mir0n.esquire.bizTree.messaging.handler.UpdateEntityHandler;
import pro.mir0n.esquire.messaging.BusConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-cache-instance dispatch hub for entity-broadcast events. Holds one
 * IBizTreeEventHandler per (eventType, kindBits) key and routes the parsed
 * event to the right handler.
 *
 * In Step 1 (Taijitu migration), a single hub instance lives inside
 * BizTreeDirectorLegacy and points at the legacy IBizTreeCacheRepository.
 * In Step 3 each Monad embeds its own hub instance pointing at that monad's
 * own cache repository, so Yang and Yin each apply events to their own H2
 * table without sharing handler state.
 */
@Slf4j
public final class MessageHandlerHub {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + MessageHandlerHub.class.getName());

    /** Map key: event type + kindBits (ORG=1, USR=2, ACCT=4). */
    private record HandlerKey(String eventType, int kindBits) {}

    private final Map<HandlerKey, IBizTreeEventHandler> handlers;
    /** Needed by the freshness guard (v1.2.12): reads the node's stored change numbers, stamps the applied one. */
    private final IBizTreeCacheRepository cacheRepository;

    public MessageHandlerHub(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
        UpdateEntityHandler updateHandler = new UpdateEntityHandler(cacheRepository);
        DeleteEntityHandler deleteHandler = new DeleteEntityHandler(cacheRepository);

        Map<HandlerKey, IBizTreeEventHandler> m = new HashMap<>();
        m.put(new HandlerKey(BusConstants.EVENT_UPDATE,      1), updateHandler);                       // ORG
        m.put(new HandlerKey(BusConstants.EVENT_UPDATE,      2), updateHandler);                       // USR
        m.put(new HandlerKey(BusConstants.EVENT_UPDATE,      4), updateHandler);                       // ACCT
        m.put(new HandlerKey(BusConstants.EVENT_CREATE,      1), new CreateOrgHandler(cacheRepository));
        m.put(new HandlerKey(BusConstants.EVENT_CREATE,      2), new CreateUsrHandler(cacheRepository));
        m.put(new HandlerKey(BusConstants.EVENT_CREATE,      4), new CreateAcctHandler(cacheRepository));
        m.put(new HandlerKey(BusConstants.EVENT_DELETE,      1), deleteHandler);                       // ORG
        m.put(new HandlerKey(BusConstants.EVENT_DELETE,      2), deleteHandler);                       // USR
        m.put(new HandlerKey(BusConstants.EVENT_DELETE,      4), deleteHandler);                       // ACCT
        m.put(new HandlerKey(BusConstants.EVENT_UPDATE_PATH, 1), new MoveOrgHandler(cacheRepository));
        m.put(new HandlerKey(BusConstants.EVENT_UPDATE_PATH, 2), new MoveUsrHandler(cacheRepository));
        m.put(new HandlerKey(BusConstants.EVENT_UPDATE_PATH, 4), new MoveAcctHandler(cacheRepository));
        this.handlers = Map.copyOf(m);
    }

    /**
     * Dispatch one entity-broadcast event to its registered handler.
     * Skipped messages (no handler / null textNode) are logged via devLog.warn so a flood of
     * unexpected events is visible -- prior to v1.2.6 Goal 3 instrumentation this path was a
     * silent no-op which hid possible mis-routing or schema drift between publisher and consumer.
     */
    /**
     * Dispatch, guarding on the change number (v1.2.12).
     *
     * <p>WHICH number is compared follows the event type, and this is the one place that has to know it:
     * a PATH event (X) carries the PATH row's number and is compared against the node's stored path
     * number; every other event carries the ENTITY row's number and is compared against the stored entity
     * number. The two are separate per-entity counters and are never comparable with each other -- see
     * {@code BusConstants.FIELD_CHANGE_NO}, which spells the exception out. Compare across them and a
     * moved subtree silently stays half-repathed.
     *
     * <p>A null {@code changeNo} means the producer sent none: apply unguarded, because "unknown" must
     * never be read as "old". Same for a node the cache has never seen.
     *
     * <p>Read-then-write is safe without locking: the monad worker is single-threaded, and a batch runs
     * in one cache transaction.
     */
    public void dispatch(String eventType, String entityId, int entityKind, JsonNode textNode, Long changeNo) {
        // esq.biz.tree.handler.dispatch.total (O1/T8 phase C): what the CACHE did with a broadcast, which the bus
        // meters cannot know. messaging.receive.total says the message arrived; this says whether it was applied,
        // skipped for want of a handler, skipped for want of a payload, or FAILED. The failure case matters most:
        // the catch below swallows the exception, so a handler that blows up leaves the cache silently stale --
        // the broadcast counts as received, and nothing else anywhere says the tree did not change.
        // Tags bounded: event is the BusConstants event set, kind the EsqObjectKind code, outcome one of four.
        String outcome = "handled";
        try {
            EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(entityKind);
            int kindBits = 0;
            if (eek.isAcct()) kindBits += 4;
            if (eek.isUsr())  kindBits += 2;
            if (eek.isOrg())  kindBits += 1;

            IBizTreeEventHandler handler = handlers.get(new HandlerKey(eventType, kindBits));
            if (handler == null) {
                outcome = "no-handler";
                devLog.warn("MessageHandlerHub: SKIP no handler for eventType={} entityKind={} (kindBits={}) entityId={}",
                        eventType, entityKind, kindBits, entityId);
                return;
            }
            if (textNode == null) {
                outcome = "no-payload";
                devLog.warn("MessageHandlerHub: SKIP null textNode for eventType={} entityKind={} entityId={}",
                        eventType, entityKind, entityId);
                return;
            }
            boolean isPath = BusConstants.EVENT_UPDATE_PATH.equals(eventType);
            Long entityPk = parseEntityPk(entityId);
            if (changeNo != null && entityPk != null && isStale(entityPk, isPath, changeNo)) {
                outcome = "stale";
                devLog.debug("MessageHandlerHub: SKIP stale eventType={} entityKind={} entityId={} changeNo={}",
                        eventType, entityKind, entityId, changeNo);
                return;
            }
            try {
                handler.handle(entityId, entityKind, textNode);
                if (changeNo != null && entityPk != null) {
                    // Stamp AFTER the handler: a CREATE has no row to stamp until its handler made one.
                    if (isPath) {
                        cacheRepository.stampPathChangeNo(entityPk, changeNo);
                    } else {
                        cacheRepository.stampEntityChangeNo(entityPk, changeNo);
                    }
                }
            } catch (Exception ex) {
                outcome = "failed";
                log.error("MessageHandlerHub: handler failed eventType={} kind={} entityId={}: {}",
                        eventType, entityKind, entityId, ex.getMessage());
                devLog.error("MessageHandlerHub: handler failed eventType={} kind={} entityId={}: {}",
                        eventType, entityKind, entityId, ex.getMessage(), ex);
            }
        } finally {
            EsqBizMeters.count("esq.biz.tree.handler.dispatch.total",
                    "event", String.valueOf(eventType), "kind", String.valueOf(entityKind), "outcome", outcome);
        }
    }

    /** True when the cache already holds this event's change (or a later one) -- a redelivery or an
     *  out-of-order arrival. Unknown stored number = never stale: applying twice beats losing a change. */
    private boolean isStale(long entityPk, boolean isPath, long changeNo) {
        boolean ret = false;
        Long[] stored = cacheRepository.findChangeNumbers(entityPk);
        if (stored != null) {
            Long current = isPath ? stored[1] : stored[0];
            ret = current != null && changeNo <= current;
        }
        return ret;
    }

    /** The entity id as a cache key, or null when it is not a plain number (nothing to guard against). */
    private static Long parseEntityPk(String entityId) {
        Long ret = null;
        if (entityId != null) {
            try {
                ret = Long.valueOf(entityId);
            } catch (NumberFormatException ignored) {
                ret = null;   // not a numeric entity id -- apply unguarded
            }
        }
        return ret;
    }
}

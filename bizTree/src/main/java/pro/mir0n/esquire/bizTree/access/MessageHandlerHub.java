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
 */
package pro.mir0n.esquire.bizTree.access;

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

    public MessageHandlerHub(IBizTreeCacheRepository cacheRepository) {
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
    public void dispatch(String eventType, String entityId, int entityKind, JsonNode textNode) {
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(entityKind);
        int kindBits = 0;
        if (eek.isAcct()) kindBits += 4;
        if (eek.isUsr())  kindBits += 2;
        if (eek.isOrg())  kindBits += 1;

        IBizTreeEventHandler handler = handlers.get(new HandlerKey(eventType, kindBits));
        if (handler == null) {
            devLog.warn("MessageHandlerHub: SKIP no handler for eventType={} entityKind={} (kindBits={}) entityId={}",
                    eventType, entityKind, kindBits, entityId);
            return;
        }
        if (textNode == null) {
            devLog.warn("MessageHandlerHub: SKIP null textNode for eventType={} entityKind={} entityId={}",
                    eventType, entityKind, entityId);
            return;
        }
        try {
            handler.handle(entityId, entityKind, textNode);
        } catch (Exception ex) {
            log.error("MessageHandlerHub: handler failed eventType={} kind={} entityId={}: {}",
                    eventType, entityKind, entityId, ex.getMessage());
            devLog.error("MessageHandlerHub: handler failed eventType={} kind={} entityId={}: {}",
                    eventType, entityKind, entityId, ex.getMessage(), ex);
        }
    }
}

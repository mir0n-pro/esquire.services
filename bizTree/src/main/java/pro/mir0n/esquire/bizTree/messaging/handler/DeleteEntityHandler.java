/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/26/2026 mir0n  created: handles DELETE events for ORG/USR/ACCT kinds
 */
package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;

/** Handles DELETE entity events for ORG, USR, and ACCT kinds. */
public class DeleteEntityHandler implements IBizTreeEventHandler {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + DeleteEntityHandler.class.getName());

    private final IBizTreeCacheRepository cacheRepository;

    public DeleteEntityHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        long pk = Long.parseLong(entityId);
        cacheRepository.deleteNodes(pk);
        devLog.debug("DeleteEntityHandler: deleted cache nodes for entityId={} kind={}", entityId, entityKind);
    }
}

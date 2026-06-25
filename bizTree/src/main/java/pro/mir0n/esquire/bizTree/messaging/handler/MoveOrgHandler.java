/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/02/2026 mir0n  created: handles ORG entity MOVE (UPDATE_PATH) events
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 */
package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.common.EsqConstants;

/** Handles UPDATE_PATH (X) events for ORG kinds. */
public class MoveOrgHandler implements IBizTreeEventHandler {

    private final IBizTreeCacheRepository cacheRepository;

    public MoveOrgHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        String newEntityPath = textNode.path(EsqConstants.TEXT_PATH).asText(null);
        if (newEntityPath == null) {
            return;
        }
        cacheRepository.moveOrgNode(Long.parseLong(entityId), newEntityPath);
    }
}

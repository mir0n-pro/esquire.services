/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/25/2026 mir0n  created: handles CREATE events for ORG kinds
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 */
package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.common.EsqConstants;

/** Handles CREATE entity events for ORG kinds. */
public class CreateOrgHandler implements IBizTreeEventHandler {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + CreateOrgHandler.class.getName());

    private final IBizTreeCacheRepository cacheRepository;

    public CreateOrgHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        String parentId   = textNode.has(EsqConstants.TEXT_PARENT_ID) && !textNode.get(EsqConstants.TEXT_PARENT_ID).isNull()
                            ? textNode.get(EsqConstants.TEXT_PARENT_ID).asText() : null;
        String entityPath = textNode.has(EsqConstants.TEXT_PATH) && !textNode.get(EsqConstants.TEXT_PATH).isNull()
                            ? textNode.get(EsqConstants.TEXT_PATH).asText() : null;
        if (parentId != null && entityPath != null) {
            long   pk   = Long.parseLong(entityId);
            String name = textNode.has(EsqConstants.TEXT_NAME) && !textNode.get(EsqConstants.TEXT_NAME).isNull()
                          ? textNode.get(EsqConstants.TEXT_NAME).asText() : "";
            String desc = textNode.has(EsqConstants.TEXT_DESC) && !textNode.get(EsqConstants.TEXT_DESC).isNull()
                          ? textNode.get(EsqConstants.TEXT_DESC).asText() : null;
            cacheRepository.insertOrgNodes(pk, entityKind, name, desc, parentId, entityPath);
        }
    }
}

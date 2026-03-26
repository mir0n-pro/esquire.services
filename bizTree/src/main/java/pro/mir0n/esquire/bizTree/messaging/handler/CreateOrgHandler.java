/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/25/2026 mir0n  created: handles CREATE events for ORG kinds
 */
package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.common.EsqMsgConstants;

/** Handles CREATE entity events for ORG kinds. */
public class CreateOrgHandler implements IBizTreeEventHandler {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + CreateOrgHandler.class.getName());

    private final IBizTreeCacheRepository cacheRepository;

    public CreateOrgHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        String parentId   = textNode.has(EsqMsgConstants.TEXT_PARENT_ID) && !textNode.get(EsqMsgConstants.TEXT_PARENT_ID).isNull()
                            ? textNode.get(EsqMsgConstants.TEXT_PARENT_ID).asText() : null;
        String entityPath = textNode.has(EsqMsgConstants.TEXT_PATH) && !textNode.get(EsqMsgConstants.TEXT_PATH).isNull()
                            ? textNode.get(EsqMsgConstants.TEXT_PATH).asText() : null;
        if (parentId != null && entityPath != null) {
            long   pk   = Long.parseLong(entityId);
            String name = textNode.has(EsqMsgConstants.TEXT_NAME) && !textNode.get(EsqMsgConstants.TEXT_NAME).isNull()
                          ? textNode.get(EsqMsgConstants.TEXT_NAME).asText() : "";
            String desc = textNode.has(EsqMsgConstants.TEXT_DESC) && !textNode.get(EsqMsgConstants.TEXT_DESC).isNull()
                          ? textNode.get(EsqMsgConstants.TEXT_DESC).asText() : null;
            cacheRepository.insertOrgNodes(pk, entityKind, name, desc, parentId, entityPath);
        }
    }
}

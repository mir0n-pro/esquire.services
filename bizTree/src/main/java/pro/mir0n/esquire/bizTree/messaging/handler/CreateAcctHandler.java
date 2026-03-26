/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/25/2026 mir0n  created: handles CREATE events for ACCT kinds
 */
package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.bizTree.BizTreeConstants;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.common.EsqMsgConstants;

/** Handles CREATE entity events for ACCT kinds. Inserts main node under user and shortcut under org's FOLDER_ACCOUNT. */
public class CreateAcctHandler implements IBizTreeEventHandler {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + CreateAcctHandler.class.getName());

    private final IBizTreeCacheRepository cacheRepository;

    public CreateAcctHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        String parentId   = textNode.has(EsqMsgConstants.TEXT_PARENT_ID) && !textNode.get(EsqMsgConstants.TEXT_PARENT_ID).isNull()
                            ? textNode.get(EsqMsgConstants.TEXT_PARENT_ID).asText() : null;
        String entityPath = textNode.has(EsqMsgConstants.TEXT_PATH) && !textNode.get(EsqMsgConstants.TEXT_PATH).isNull()
                            ? textNode.get(EsqMsgConstants.TEXT_PATH).asText() : null;
        if (parentId == null || entityPath == null) return;

        long   pk         = Long.parseLong(entityId);
        long   usrPk      = Long.parseLong(parentId);
        String name       = textNode.has(EsqMsgConstants.TEXT_NAME) && !textNode.get(EsqMsgConstants.TEXT_NAME).isNull()
                            ? textNode.get(EsqMsgConstants.TEXT_NAME).asText() : "";
        String desc       = textNode.has(EsqMsgConstants.TEXT_DESC) && !textNode.get(EsqMsgConstants.TEXT_DESC).isNull()
                            ? textNode.get(EsqMsgConstants.TEXT_DESC).asText() : null;
        boolean hasStatus = textNode.has(EsqMsgConstants.TEXT_STATUS) && !textNode.get(EsqMsgConstants.TEXT_STATUS).isNull();
        int     statusCode = hasStatus ? BizTreeConstants.decodeStatus(textNode.get(EsqMsgConstants.TEXT_STATUS).asText()) : BizTreeConstants.STATUS_OK;

        cacheRepository.insertAcctNode(pk, entityKind, name, desc, usrPk, entityPath, statusCode);
    }
}

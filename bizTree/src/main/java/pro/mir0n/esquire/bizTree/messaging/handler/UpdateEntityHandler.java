/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/25/2026 mir0n  created: handles UPDATE events for ORG/USR/ACCT; decodeStatus() moved from consumer
 */
package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.bizTree.BizTreeConstants;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.common.EsqMsgConstants;

/** Handles UPDATE entity events for ORG, USR, and ACCT kinds. */
public class UpdateEntityHandler implements IBizTreeEventHandler {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + UpdateEntityHandler.class.getName());

    private final IBizTreeCacheRepository cacheRepository;

    public UpdateEntityHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        boolean hasName    = textNode.has(EsqMsgConstants.TEXT_NAME)    && !textNode.get(EsqMsgConstants.TEXT_NAME).isNull();
        boolean hasDesc    = textNode.has(EsqMsgConstants.TEXT_DESC);  // can be null
        boolean hasStatus  = textNode.has(EsqMsgConstants.TEXT_STATUS)  && !textNode.get(EsqMsgConstants.TEXT_STATUS).isNull();   // acc_status (pacMan/ACCT)
        boolean hasDeleted = textNode.has(EsqMsgConstants.TEXT_DELETED) && !textNode.get(EsqMsgConstants.TEXT_DELETED).isNull();  // usr_deleted_flg (enyMan/USR)
        if (hasName || hasDesc || hasStatus || hasDeleted) {
            long    pk         = Long.parseLong(entityId);
            String  name       = hasName    ? textNode.get(EsqMsgConstants.TEXT_NAME).asText() : null;
            String  desc       = hasDesc    ? (textNode.get(EsqMsgConstants.TEXT_DESC).isNull() ? null : textNode.get(EsqMsgConstants.TEXT_DESC).asText()) : IBizTreeCacheRepository.SKIP;
            Integer statusCode = null;
            if (hasStatus) {
                statusCode = BizTreeConstants.decodeStatus(textNode.get(EsqMsgConstants.TEXT_STATUS).asText());
            } else if (hasDeleted) {
                statusCode = BizTreeConstants.decodeStatus(textNode.get(EsqMsgConstants.TEXT_DELETED).asText());
            }
            cacheRepository.updateNode(pk, name, desc, statusCode);
        }
    }
}

package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.common.EsqMsgConstants;

/** Handles UPDATE_PATH (X) events for ORG kinds. */
public class MoveOrgHandler implements IBizTreeEventHandler {

    private final IBizTreeCacheRepository cacheRepository;

    public MoveOrgHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        String newEntityPath = textNode.path(EsqMsgConstants.TEXT_PATH).asText(null);
        if (newEntityPath == null) {
            return;
        }
        cacheRepository.moveOrgNode(Long.parseLong(entityId), newEntityPath);
    }
}

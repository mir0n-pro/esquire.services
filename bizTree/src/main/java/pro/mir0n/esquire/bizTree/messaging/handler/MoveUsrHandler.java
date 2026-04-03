package pro.mir0n.esquire.bizTree.messaging.handler;

import com.fasterxml.jackson.databind.JsonNode;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.messaging.IBizTreeEventHandler;
import pro.mir0n.esquire.common.EsqMsgConstants;

/** Handles UPDATE_PATH (X) events for USR kinds. */
public class MoveUsrHandler implements IBizTreeEventHandler {

    private final IBizTreeCacheRepository cacheRepository;

    public MoveUsrHandler(IBizTreeCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public void handle(String entityId, int entityKind, JsonNode textNode) throws Exception {
        String newEntityPath = textNode.path(EsqMsgConstants.TEXT_PATH).asText(null);
        if (newEntityPath == null) {
            return;
        }
        int normalizedKind = (int) Math.floor((double) entityKind / 2) * 2;
        cacheRepository.moveUsrNode(Long.parseLong(entityId), normalizedKind, newEntityPath);
    }
}

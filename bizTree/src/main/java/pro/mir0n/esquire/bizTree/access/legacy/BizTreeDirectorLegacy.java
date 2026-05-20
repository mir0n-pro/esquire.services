/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: legacy pass-through implementation of IBizTreeDirector
 *                   (v1.2.5 Taijitu refactor Step 1). Delegates reads to the existing
 *                   IBizTreeService and events to a single MessageHandlerHub. Carries
 *                   @Component today; in Step 3 the annotation moves to the Taijitu
 *                   wrapper and this class becomes inert (kept in tree for emergency
 *                   switch-back per feedback-invisible-refactor).
 */
package pro.mir0n.esquire.bizTree.access.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.bizTree.access.MessageHandlerHub;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheLoader;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;

import java.util.List;

/**
 * Step 1 Director: thin pass-through to the legacy mechanics. Reads go to
 * the existing IBizTreeService; events go to a single MessageHandlerHub that
 * owns the handler map extracted out of EsqEntityBroadcastConsumer.
 *
 * No behaviour change versus the pre-refactor cache layer: same service,
 * same handlers, same cache repository.
 *
 * NOT a Spring @Component. The active IBizTreeDirector implementation is
 * declared explicitly in pro.mir0n.esquire.bizTree.access.BizTreeDirectorConfig
 * via a @Bean factory method. To swap implementations (Step 3): comment the
 * line that creates this class in BizTreeDirectorConfig and uncomment the
 * line that creates the Taijitu. Single-file edit, no annotation moves.
 */
@Slf4j
public class BizTreeDirectorLegacy implements IBizTreeDirector {

    private final IBizTreeService    bizTreeService;
    private final BizTreeCacheLoader cacheLoader;
    private final MessageHandlerHub  handlerHub;

    public BizTreeDirectorLegacy(IBizTreeService bizTreeService,
                                 BizTreeCacheLoader cacheLoader,
                                 IBizTreeCacheRepository cacheRepository) {
        this.bizTreeService = bizTreeService;
        this.cacheLoader    = cacheLoader;
        this.handlerHub     = new MessageHandlerHub(cacheRepository);
    }

    /* --- Lifecycle ------------------------------------------------------- */

    @Override
    public void bootstrap() {
        // Legacy workflow: synchronous one-shot load (what the old
        // BizTreeCacheLoader ApplicationReadyEvent listener used to do).
        // Events arriving during this load are applied on arrival by the
        // consumer with no buffering -- this is exactly the cache-load race
        // that the yang/taijitu directors close.
        log.info("BizTreeDirectorLegacy: bootstrap -- loading cache (synchronous, no event gating)");
        cacheLoader.load();
        log.info("BizTreeDirectorLegacy: bootstrap -- cache loaded");
    }

    /* --- Read surface ---------------------------------------------------- */

    @Override
    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid) {
        List<EsqTreeNode> ret = bizTreeService.esquire(id, skip, take, rootPath, uid);
        return ret;
    }

    @Override
    public List<String> esquirePath(String id, String rootPath) {
        List<String> ret = bizTreeService.esquirePath(id, rootPath);
        return ret;
    }

    @Override
    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid) {
        EsqTreeNode ret = bizTreeService.esquireEntityNode(kind, id, name, rootPath, uid);
        return ret;
    }

    @Override
    public List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid) {
        List<EsqTreeNode> ret = bizTreeService.esquireSubtree(id, rootPath, uid);
        return ret;
    }

    /* --- Event surface --------------------------------------------------- */

    @Override
    public void onEntityBroadcast(String eventType, String entityId, int entityKind, JsonNode textNode) {
        handlerHub.dispatch(eventType, entityId, entityKind, textNode);
    }
}

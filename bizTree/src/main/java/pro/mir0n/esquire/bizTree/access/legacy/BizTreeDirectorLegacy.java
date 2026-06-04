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
 * 05/20/2026 mir0n  generalization: implements IBizTreeDirector extends ITaijituRig --
 *                   onEntityBroadcast takes the 7 raw fields and parses textJson inline (legacy
 *                   has no worker thread); added ObjectMapper ctor arg + no-op shutdown().
 * 05/22/2026 mir0n  bootstrap() renamed start() (ITaijituRig lifecycle).
 * 05/23/2026 mir0n  added isReady() -- a volatile ready flag set true after the synchronous load
 *                   (the readiness gate); sweepAsync() inherits the ITaijituRig no-op (single-pass).
 * 06/04/2026 mir0n  read methods drop rootPath / uid params; read them via RequestContextUtils and
 *                   forward to IBizTreeService (which keeps its params)
 */
package pro.mir0n.esquire.bizTree.access.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
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

    private static final Logger devLog = LoggerFactory.getLogger("develop." + BizTreeDirectorLegacy.class.getName());

    private final IBizTreeService    bizTreeService;
    private final BizTreeCacheLoader cacheLoader;
    private final MessageHandlerHub  handlerHub;
    private final ObjectMapper       objectMapper;
    private volatile boolean         ready = false;   // flips true after the synchronous load (readiness gate)

    public BizTreeDirectorLegacy(IBizTreeService bizTreeService,
                                 BizTreeCacheLoader cacheLoader,
                                 IBizTreeCacheRepository cacheRepository,
                                 ObjectMapper objectMapper) {
        this.bizTreeService = bizTreeService;
        this.cacheLoader    = cacheLoader;
        this.handlerHub     = new MessageHandlerHub(cacheRepository);
        this.objectMapper   = objectMapper;
    }

    /* --- Lifecycle ------------------------------------------------------- */

    @Override
    public void start() {
        // Legacy workflow: synchronous one-shot load (what the old
        // BizTreeCacheLoader ApplicationReadyEvent listener used to do).
        // Events arriving during this load are applied on arrival by the
        // consumer with no buffering -- this is exactly the cache-load race
        // that the yang/taijitu directors close.
        log.info("BizTreeDirectorLegacy: start -- loading cache (synchronous, no event gating)");
        cacheLoader.load();
        ready = true;
        log.info("BizTreeDirectorLegacy: start -- cache loaded");
    }

    @Override
    public void shutdown() {
        // Nothing to stop: legacy has no worker thread.
    }

    /** Ready once the synchronous load has completed. */
    @Override
    public boolean isReady() {
        return ready;
    }

    // sweepAsync(): inherits the ITaijituRig no-op default -- legacy is single-pass, no night-watch.

    /* --- Read surface ---------------------------------------------------- */

    @Override
    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take) {
        List<EsqTreeNode> ret = bizTreeService.esquire(id, skip, take, RequestContextUtils.getRootPath(), RequestContextUtils.getUid());
        return ret;
    }

    @Override
    public List<String> esquirePath(String id) {
        List<String> ret = bizTreeService.esquirePath(id, RequestContextUtils.getRootPath());
        return ret;
    }

    @Override
    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name) {
        EsqTreeNode ret = bizTreeService.esquireEntityNode(kind, id, name, RequestContextUtils.getRootPath(), RequestContextUtils.getUid());
        return ret;
    }

    @Override
    public List<EsqTreeNode> esquireSubtree(String id) {
        List<EsqTreeNode> ret = bizTreeService.esquireSubtree(id, RequestContextUtils.getRootPath(), RequestContextUtils.getUid());
        return ret;
    }

    /* --- Event surface --------------------------------------------------- */

    @Override
    public void onEntityBroadcast(String eventType, String entityId, int entityKind,
                                  String requestId, String correlationId,
                                  String messageEncoding, String text) {
        // Legacy has no worker thread, so it parses on the caller (JMS) thread before dispatch.
        JsonNode textNode = null;
        if (text != null) {
            try {
                textNode = objectMapper.readTree(text);
            } catch (Exception parseEx) {
                log.error("BizTreeDirectorLegacy: textJson parse failed id={}: {}", entityId, parseEx.getMessage());
                devLog.error("BizTreeDirectorLegacy: textJson parse failed id={}: {}", entityId, parseEx.getMessage(), parseEx);
                return;
            }
        }
        handlerHub.dispatch(eventType, entityId, entityKind, textNode);
    }
}

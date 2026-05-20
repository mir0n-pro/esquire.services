/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: single-monad IBizTreeDirector implementation
 *                   (v1.2.5 Taijitu refactor Step 2). The "active half" -- a pure
 *                   role-router over one MonadY, which is the full cache-access object
 *                   (owns reads + writes); this director just forwards. For the full
 *                   Taijitu the equivalent router picks the active of two MonadYY.
 */
package pro.mir0n.esquire.bizTree.access.yang;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.bizTree.taijitu.IMonad;
import pro.mir0n.esquire.bizTree.taijitu.IMonadCommand;

import java.util.List;

/**
 * Pure role-router over a single {@link MonadY}. The monad is the full
 * cache-access object (reads, writes, lifecycle); this director only forwards
 * IBizTreeDirector calls to it. There is no Yin, no night-watch -- that is the
 * full Taijitu (two MonadYY, the router picking the active one).
 *
 * Wired in BizTreeDirectorConfig; bootstrap() fired once by BizTreeBootstrapRunner.
 *
 * See: services/doc/Esquire.BizTree.md "Migration plan".
 */
@Slf4j
public final class BizTreeDirectorYang implements IBizTreeDirector {

    private final IMonad monad;

    public BizTreeDirectorYang(IMonad monad) {
        this.monad = monad;
    }

    /* --- Lifecycle ------------------------------------------------------- */

    @Override
    public void bootstrap() {
        monad.start();
        monad.setQueueEnabled(true);   // events buffer from here on
        monad.submit(new IMonadCommand.Init());
        log.info("BizTreeDirectorYang: bootstrap issued (INIT queued, queue enabled)");
    }

    /* --- Read surface (forwarded to the monad) --------------------------- */

    @Override
    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid) {
        return monad.esquire(id, skip, take, rootPath, uid);
    }

    @Override
    public List<String> esquirePath(String id, String rootPath) {
        return monad.esquirePath(id, rootPath);
    }

    @Override
    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid) {
        return monad.esquireEntityNode(kind, id, name, rootPath, uid);
    }

    @Override
    public List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid) {
        return monad.esquireSubtree(id, rootPath, uid);
    }

    /* --- Event surface (forwarded to the monad) -------------------------- */

    @Override
    public void onEntityBroadcast(String eventType, String entityId, int entityKind, JsonNode textNode) {
        boolean accepted = monad.offer(eventType, entityId, entityKind, textNode);
        if (!accepted) {
            log.warn("BizTreeDirectorYang: event not accepted (status={}): type={} id={} kind={}",
                    monad.status(), eventType, entityId, entityKind);
        }
    }
}

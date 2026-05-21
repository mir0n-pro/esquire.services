/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: single-monad IBizTreeDirector implementation (v1.2.5 Taijitu Step 2).
 * 05/20/2026 mir0n  generalization: extends the common ATaijituRigY (which controls the monad and
 *                   drives the processing gate); this class only supplies the MonadY it controls
 *                   and adds the bizTree REST reads (routed to that monad). bootstrap / shutdown /
 *                   onEntityBroadcast / onStarted / onResult are inherited from ATaijituRigY.
 */
package pro.mir0n.esquire.bizTree.access.yang;

import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.bizTree.taijitu.MonadY;
import pro.mir0n.utils.taijitu.ATaijituRigY;

import java.util.List;

/**
 * The bizTree director for the single-monad (Yang) mode. The Taijitu control logic lives in
 * the common {@link ATaijituRigY}; here we just hand it the {@link MonadY} it controls and add
 * the domain reads, which delegate to that monad (gated on LOADED). For the full Taijitu the
 * equivalent router will pick the active of two monads.
 *
 * Wired in BizTreeDirectorConfig; bootstrap() fired once by BizTreeBootstrapRunner.
 */
public final class BizTreeDirectorYang extends ATaijituRigY implements IBizTreeDirector {

    private final MonadY monad;

    public BizTreeDirectorYang(MonadY monad) {
        super(monad);
        this.monad = monad;
    }

    /* --- Read surface (forwarded to the active monad) -------------------- */

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
}

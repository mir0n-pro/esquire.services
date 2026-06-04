/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/22/2026 mir0n  created: the bizTree director for the full Taijitu (two-monad) mode. The control
 *                   logic lives in the common ATaijituRig (two equal monads, gateFor per monad, swap);
 *                   here we hand it the two Monads it controls and add the domain reads, routed to the
 *                   current serving monad (yang()). Dummy night-watch for now -- shadow idle.
 * 06/04/2026 mir0n  read methods drop rootPath / uid params; read them via RequestContextUtils and
 *                   forward to the serving monad (IBizTreeService keeps its params)
 */
package pro.mir0n.esquire.bizTree.access.taijitu;

import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.bizTree.taijitu.Monad;
import pro.mir0n.utils.taijitu.ATaijituRig;

import java.util.List;

/**
 * The bizTree director for the full Taijitu mode: two equal {@link Monad}s behind the common
 * {@link ATaijituRig}. Reads delegate to the CURRENT serving monad ({@code yang()}), which can swap
 * during a night-watch promotion. Wired in BizTreeDirectorConfig; start() fired once by
 * BizTreeBootstrapRunner.
 */
public final class BizTreeDirectorTaijitu extends ATaijituRig implements IBizTreeDirector {

    public BizTreeDirectorTaijitu(Monad monad, Monad danom) {
        super(monad, danom);
    }

    /** The current serving monad (the read target; follows a swap). */
    private Monad serving() {
        return (Monad) yang();
    }

    /* --- Read surface (forwarded to the serving monad) ------------------- */

    @Override
    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take) {
        return serving().esquire(id, skip, take, RequestContextUtils.getRootPath(), RequestContextUtils.getUid());
    }

    @Override
    public List<String> esquirePath(String id) {
        return serving().esquirePath(id, RequestContextUtils.getRootPath());
    }

    @Override
    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name) {
        return serving().esquireEntityNode(kind, id, name, RequestContextUtils.getRootPath(), RequestContextUtils.getUid());
    }

    @Override
    public List<EsqTreeNode> esquireSubtree(String id) {
        return serving().esquireSubtree(id, RequestContextUtils.getRootPath(), RequestContextUtils.getUid());
    }
}

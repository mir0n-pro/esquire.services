/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: Director slot interface (v1.2.5 Taijitu refactor Step 1).
 *                   AccessPoint calls into this; today's @Component is BizTreeDirectorLegacy
 *                   (pass-through to existing IBizTreeService + MessageHandlerHub).
 *                   In Step 3 the Taijitu replaces the legacy impl behind the same interface.
 * 05/20/2026 mir0n  generalization: extends common ITaijituRig (bootstrap / shutdown /
 *                   onEntityBroadcast) and adds only the bizTree-specific REST reads.
 */
package pro.mir0n.esquire.bizTree.access;

import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.utils.taijitu.ITaijituRig;

import java.util.List;

/**
 * The bizTree director: the common {@link ITaijituRig} control face (bootstrap, shutdown,
 * onEntityBroadcast) plus the bizTree-specific read surface (routed to the active monad).
 * Implementations: BizTreeDirectorYang (extends the common ATaijituRigY) and the legacy
 * pass-through.
 *
 * See: services/doc/Esquire.BizTree.md -- "Migration plan" section.
 */
public interface IBizTreeDirector extends ITaijituRig {

    /* --- Read surface (bizTree-specific; mirror IBizTreeService 1:1) ----- */

    List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid);
    List<String>      esquirePath(String id, String rootPath);
    EsqTreeNode       esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid);
    List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid);
}

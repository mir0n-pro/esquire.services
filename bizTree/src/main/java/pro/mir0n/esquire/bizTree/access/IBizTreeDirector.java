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
 * 05/22/2026 mir0n  javadoc: implementations are BizTreeDirectorTaijitu + legacy (yang removed).
 * 06/04/2026 mir0n  read surface: rootPath + uid params removed from esquire / esquirePath /
 *                   esquireEntityNode / esquireSubtree (impls read them from the request context)
 * 06/15/2026 mir0n  added default onRodEvent(RodEvent): unpacks the RodEvent off the entity-broadcast
 *                   bus onto the generic onEntityBroadcast intake (body rides already parsed, no re-parse)
 * 06/22/2026 mir0n  import update: RodEvent moved to messaging.xrod (was messaging)
 */
package pro.mir0n.esquire.bizTree.access;

import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.utils.taijitu.ITaijituRig;

import java.util.List;

/**
 * The bizTree director: the common {@link ITaijituRig} control face (bootstrap, shutdown,
 * onEntityBroadcast) plus the bizTree-specific read surface (routed to the active monad).
 * Implementations: BizTreeDirectorTaijitu (extends the common ATaijituRig) and the legacy
 * pass-through.
 *
 * See: services/doc/Esquire.BizTree.md -- "Migration plan" section.
 */
public interface IBizTreeDirector extends ITaijituRig {

    /**
     * Receive one {@link RodEvent} off the entity-broadcast bus -- the esquire substrate face the
     * receive x-Rod hands events to. Unpacks the event onto the generic {@link ITaijituRig#onEntityBroadcast}
     * intake (the generic taijitu never sees the esquire {@code RodEvent}); the {@code body} rides already
     * parsed, so the monad applies it with no re-parse. The same substrate will later serve the KC bus.
     */
    default void onRodEvent(RodEvent e) {
        onEntityBroadcast(e.opCode(), e.entityId(), e.kind(), e.requestId(), e.correlationId(), e.body());
    }

    /* --- Read surface (bizTree-specific) -- uid / rootPath come from the unified per-request
       context (RequestContextUtils), read here and forwarded to IBizTreeService. ------------ */

    List<EsqTreeNode> esquire(String id, Integer skip, Integer take);
    List<String>      esquirePath(String id);
    EsqTreeNode       esquireEntityNode(Integer kind, String id, String name);
    List<EsqTreeNode> esquireSubtree(String id);
}

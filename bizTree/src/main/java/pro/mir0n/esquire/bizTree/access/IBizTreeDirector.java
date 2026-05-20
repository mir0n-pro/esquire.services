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
 */
package pro.mir0n.esquire.bizTree.access;

import com.fasterxml.jackson.databind.JsonNode;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;

import java.util.List;

/**
 * The "Director slot" -- single point of entry for everything the AccessPoint
 * needs to do against the cache. Today's implementation is a pass-through to
 * the existing service + handler dispatch (BizTreeDirectorLegacy); after the
 * Step 3 swap it becomes the Taijitu (Supreme Ultimate Cache).
 *
 * Read methods mirror IBizTreeService 1:1; event method covers what the
 * entity-broadcast consumer used to dispatch inline.
 *
 * See: services/doc/Esquire.BizTree.md -- "Migration plan" section.
 */
public interface IBizTreeDirector {

    /* --- Lifecycle ------------------------------------------------------- */

    /**
     * Bring the cache to a serving state. Called exactly once by
     * {@code BizTreeBootstrapRunner} on ApplicationReadyEvent. Each director
     * owns its own bootstrap workflow:
     *   - legacy  -- loads the cache synchronously (the old auto-fire load).
     *   - yang    -- starts the monad, opens the queue, submits INIT.
     *   - taijitu -- same as yang for the active monad, plus schedules night-watch.
     * This is the single, uniform "actual workflow" entry; the active
     * implementation is chosen in BizTreeDirectorConfig.
     */
    void bootstrap();

    /* --- Read surface ---------------------------------------------------- */

    List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid);
    List<String>      esquirePath(String id, String rootPath);
    EsqTreeNode       esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid);
    List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid);

    /* --- Event surface --------------------------------------------------- */

    /**
     * Apply one entity-broadcast event to the cache. AccessPoint extracts the
     * message properties + parses the textJson body once, then calls in here.
     * Implementations dispatch via the (eventType, kindBits) handler map.
     */
    void onEntityBroadcast(String eventType, String entityId, int entityKind, JsonNode textNode);
}

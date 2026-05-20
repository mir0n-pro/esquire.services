/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: monad public contract (v1.2.5 Taijitu refactor Step 2).
 *                   Spans the full cache-access surface: lifecycle, queue entry,
 *                   monitor/control, reads (REST), and listener registration. MonadY
 *                   implements it; MonadYY (Step 3) inherits it unchanged -- Yin adds
 *                   nothing to this interface, it's the same face with a shadow inside.
 */
package pro.mir0n.esquire.bizTree.taijitu;

import com.fasterxml.jackson.databind.JsonNode;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;

import java.util.List;

/**
 * The public contract of a cache monad -- the full cache-access object the
 * director routes to. One interface, four concerns:
 *
 *   lifecycle         -- start / stop
 *   queue entry       -- submit (command) / offer (event)
 *   monitor & control -- setQueueEnabled / status / queueDepth
 *   reads (REST)      -- esquire / esquirePath / esquireEntityNode / esquireSubtree
 *   listeners         -- setErrorListener / setCmdResponseListener
 *
 * Implemented by {@link MonadY}; {@link MonadY}'s subclass MonadYY (Step 3)
 * inherits it without additions -- the Yin shadow lives behind the same face.
 */
public interface IMonad {

    /* lifecycle */
    void start();
    void stop();

    /* queue entry */
    void    submit(IMonadCommand command);
    boolean offer(String eventType, String entityId, int entityKind, JsonNode textNode);

    /* monitor & control */
    void        setQueueEnabled(boolean enabled);
    MonadStatus status();
    int         queueDepth();

    /* reads -- full cache-access object (gated on LOADED) */
    List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid);
    List<String>      esquirePath(String id, String rootPath);
    EsqTreeNode       esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid);
    List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid);

    /* listeners */
    void setErrorListener(IErrorListener listener);
    void setCmdResponseListener(ICmdResponseListener listener);
}

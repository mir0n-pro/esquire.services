/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: the active (yang) cache monad -- bizTree's concrete extension of the
 *                   common Taijitu AMonadY (v1.2.5 generalization). AMonadY owns the Taijitu logic
 *                   (queue, status, command execution, gate); MonadY adds the actual cache access:
 *                   loadCache() (BizTreeCacheLoader), handleMessage() (parse text -> dispatch via
 *                   the event hub, off the JMS thread, with MDC), and the REST reads (gated on
 *                   status()==LOADED). NON-final: MonadYY (dark side) extends to add Yin routines.
 * 05/22/2026 mir0n  requireLoaded() calls id() (AMonadY.monadId() renamed id() on IMonad).
 */
package pro.mir0n.esquire.bizTree.taijitu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.access.CacheNotReadyException;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheLoader;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.utils.taijitu.AMonadY;
import pro.mir0n.utils.taijitu.MonadCmd;
import pro.mir0n.utils.taijitu.MonadStatus;
import pro.mir0n.utils.taijitu.QueueItem;

import java.util.List;

/**
 * bizTree's concrete cache monad. Extends the common {@link AMonadY} (which owns the Taijitu
 * mechanics) and implements the single {@code process(QueueItem)} hook plus the REST reads:
 *   - command work : LOAD -> {@link BizTreeCacheLoader#load()} (CLEAR / CHECKSUM TBD).
 *   - message work : parse the raw text (off the JMS thread, MDC-tagged) and apply it via the
 *                    event hub (MessageHandlerHub).
 *   - reads (esquire ...) : served from the read backend once the cache is LOADED.
 */
public class MonadY extends AMonadY {

    private final BizTreeCacheLoader cacheLoader;
    private final IEventSink         eventHub;
    private final IBizTreeService    readBackend;
    private final ObjectMapper       objectMapper;

    public MonadY(String monadId,
                  int queueCapacity,
                  BizTreeCacheLoader cacheLoader,
                  IEventSink eventHub,
                  IBizTreeService readBackend,
                  ObjectMapper objectMapper) {
        super(monadId, queueCapacity);
        this.cacheLoader  = cacheLoader;
        this.eventHub     = eventHub;
        this.readBackend  = readBackend;
        this.objectMapper = objectMapper;
    }

    /* ====================================================================
     * process(QueueItem) -- the cache work (command + message)
     * ==================================================================== */

    @Override
    protected void _processItem(QueueItem item) {
        if (item.eventType() == MonadCmd.CMD) {
            if (MonadCmd.LOAD == item.entityId()) {
                cacheLoader.load();
                // no cancel() yet
            } else if (MonadCmd.CLEAR == item.entityId()) {
                //cache.clear();
            } else if (MonadCmd.CHECKSUM == item.entityId()) {
                //cache.checksum();  -- how to return the checksum?
            }
        } else {   // message
            // Tag the worker thread with the originating ids, parse here (off the JMS thread),
            // then apply via the event hub.
            putMdc(item);
            try {
                JsonNode textNode = parse(item);
                eventHub.apply(item.eventType(), item.entityId(), item.entityKind(), textNode);
            } finally {
                clearMdc();
            }
        }
    }

    private JsonNode parse(QueueItem item) {
        if (item.text() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(item.text());
        } catch (Exception e) {
            throw new IllegalStateException("textJson parse failed (id=" + item.entityId() + ")", e);
        }
    }

    private static void putMdc(QueueItem item) {
        if (item.requestId() != null)     MDC.put(EsqConstants.PD_REQUEST_ID,     item.requestId());
        if (item.correlationId() != null) MDC.put(EsqConstants.PD_CORRELATION_ID, item.correlationId());
    }

    private static void clearMdc() {
        MDC.remove(EsqConstants.PD_REQUEST_ID);
        MDC.remove(EsqConstants.PD_CORRELATION_ID);
    }

    /* ====================================================================
     * REST reads (full cache-access object; gated on LOADED)
     * ==================================================================== */

    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid) {
        requireLoaded();
        return readBackend.esquire(id, skip, take, rootPath, uid);
    }

    public List<String> esquirePath(String id, String rootPath) {
        requireLoaded();
        return readBackend.esquirePath(id, rootPath);
    }

    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid) {
        requireLoaded();
        return readBackend.esquireEntityNode(kind, id, name, rootPath, uid);
    }

    public List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid) {
        requireLoaded();
        return readBackend.esquireSubtree(id, rootPath, uid);
    }

    private void requireLoaded() {
        if (status() != MonadStatus.LOADED) {
            throw new CacheNotReadyException("monad=" + id() + " status=" + status());
        }
    }

}

/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/22/2026 mir0n  created: the dark-side concrete cache monad -- bizTree's extension of the common
 *                   AMonad (AMonadY + off-worker CHECKSUM). Cache work: LOAD via
 *                   BizTreeCacheLoader, message apply via the event hub, REST reads gated on LOADED;
 *                   CHECKSUM digest is a stub for now (real digest at step 6). Two equal instances
 *                   ("monad" + "danom") sit behind BizTreeDirectorTaijitu.
 * 05/23/2026 mir0n  cache work landed: ctor takes IBizTreeCacheRepository; CLEAR -> cacheRepository.clear()
 *                   (TRUNCATE); CHECKSUM off-worker in _processItemCancellable -> prepareCancelable +
 *                   executeQuery (order-independent MD5), registering PrepareStatementCancelable so a
 *                   sweep timeout aborts the query; try-with-resources on CancelableStatement on every path.
 * 06/02/2026 mir0n  _processItems(List) override: applies a batch of events in ONE cache transaction via
 *                   the injected TransactionTemplate (cacheTx); a null cacheTx falls back to one-by-one
 * 06/15/2026 mir0n  message branch applies the already-parsed item.body() via valueToTree; removed the
 *                   private parse(QueueItem) helper that did readTree(item.text())
 * 07/09/2026 mir0n  v1.2.11 -- the H2 apply runs inside EsqAsyncTrace.continueIn(item.traceparent(),
 *                   item.correlationId(), "cache apply", ...)
 */
package pro.mir0n.esquire.bizTree.taijitu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.backend.o11y.EsqAsyncTrace;
import pro.mir0n.esquire.bizTree.access.CacheNotReadyException;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheLoader;
import pro.mir0n.esquire.bizTree.cache.CancelableStatement;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.utils.taijitu.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * bizTree's dark-side concrete cache monad. Extends the common {@link AMonad} (the bright
 * {@code AMonadY} plus the off-worker CHECKSUM dispatch) and supplies the single
 * {@code _processItem(QueueItem)} hook plus the REST reads:
 *   - command work : LOAD -> {@link BizTreeCacheLoader#load()}; CLEAR / CHECKSUM -> the cache repository
 *     (clear = TRUNCATE; checksum = order-independent MD5 over the table, off the worker).
 *   - message work : parse the raw text (off the JMS thread, MDC-tagged) and apply via the event hub.
 *   - reads (esquire ...) : served from the read backend once the cache is LOADED.
 */
public class Monad extends AMonad {

    private final BizTreeCacheLoader      cacheLoader;
    private final IBizTreeCacheRepository cacheRepository;
    private final IEventSink              eventHub;
    private final IBizTreeService         readBackend;
    private final ObjectMapper            objectMapper;
    private final TransactionTemplate     cacheTx;   // wraps an event BATCH in one cache transaction

    public Monad(String monadId,
                 int queueCapacity,
                 BizTreeCacheLoader cacheLoader,
                 IBizTreeCacheRepository cacheRepository,
                 IEventSink eventHub,
                 IBizTreeService readBackend,
                 ObjectMapper objectMapper,
                 TransactionTemplate cacheTx) {
        super(monadId, queueCapacity);
        this.cacheLoader     = cacheLoader;
        this.cacheRepository = cacheRepository;
        this.eventHub        = eventHub;
        this.readBackend     = readBackend;
        this.objectMapper    = objectMapper;
        this.cacheTx         = cacheTx;
    }

    /* ====================================================================
     * _processItem(QueueItem) -- the cache work (command + message)
     * ==================================================================== */

    @Override
    protected String _processItem(QueueItem item) {
        String ret = null;
        if (item.eventType() == MonadCmd.CMD) {
            if (MonadCmd.LOAD == item.entityId()) {
                cacheLoader.load();
            } else if (MonadCmd.CLEAR == item.entityId()) {
                cacheRepository.clear();
            }
            // CHECKSUM is off-worker -- see _processItemCancellable
        } else {   // message
            putMdc(item);
            try {
                JsonNode textNode = (item.body() == null) ? null : objectMapper.valueToTree(item.body());
                // The H2 apply runs on THIS monad worker thread (off the bus-receive thread). Continue the
                // request's trace from the item's traceparent so the cache apply nests under the "receive from
                // <bus>" span (O2/T3). null traceparent / tracing off -> runs plain.
                EsqAsyncTrace.continueIn(item.traceparent(), item.correlationId(), "cache apply", () ->
                        eventHub.apply(item.eventType(), item.entityId(), item.entityKind(), textNode));
            } finally {
                clearMdc();
            }
        }
        return ret;
    }

    /**
     * Bulk apply: run the whole batch of events in ONE cache transaction. Each {@link #_processItem}
     * applies its event through the same cacheJdbcTemplate, which joins the thread-bound connection,
     * so the batch commits once instead of once-per-event -- the throughput win under a flood of
     * move broadcasts. If any event fails the whole batch rolls back and the throwable propagates to
     * the rig (its list error listener stops the bulk); the night-watch sweep heals the gap.
     */
    @Override
    protected void _processItems(List<QueueItem> events) {
        if (cacheTx == null) {
            super._processItems(events);   // no transaction seam -> fall back to one-by-one
            return;
        }
        cacheTx.executeWithoutResult(status -> {
            for (int i = 0; i < events.size(); i++) {
                _processItem(events.get(i));
            }
        });
    }

    private class PrepareStatementCancelable implements ICancelable {
        private volatile PreparedStatement ps;   // volatile: cancel() reads it on a different thread than setPs(null) disarms it
        private PrepareStatementCancelable(PreparedStatement ps) {
            this.ps = ps;
        }
        public void setPs(PreparedStatement ps) {
            this.ps = ps;
        }

        @Override
        public void cancel() {
            try {
                if (ps != null) ps.cancel();
            } catch (Throwable ignore) {
                // best-effort; query() surfaces the abort as a SQLException
            }
        }
    }
    @Override
    protected String _processItemCancellable(ICmdResponseListener listener, QueueItem item) {
        String ret = null;
        String command = item.entityId();                            // generic: the query name = the command
        // try-with-resources on the holder closes BOTH the statement and its connection on every path.
        try (CancelableStatement q = cacheRepository.prepareCancelable(command)) {
            PreparedStatement ps = q.statement();
            PrepareStatementCancelable psc = new PrepareStatementCancelable(ps);
            listener.onStarted(command, psc);    // register cancel: a timed-out sweep aborts the query
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ret = rs.getString(1);
                }
            } finally {
                psc.setPs(null);                 // disarm before the holder closes the statement
            }
        } catch (SQLException e) {
            throw new IllegalStateException(command + " query failed", e);
        }
        return ret;
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
     * REST reads (gated on LOADED)
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

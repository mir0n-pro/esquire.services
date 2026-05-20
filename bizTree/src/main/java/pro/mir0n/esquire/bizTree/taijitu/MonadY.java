/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: the active (yang) monad -- full cache-access object
 *                   (v1.2.5 Taijitu refactor Step 2). API front + single worker over
 *                   one FIFO queue with two gates; commands -> MonadCmdHub, events ->
 *                   MessageHandlerHub (eventHub via IEventSink), reads -> read backend.
 *                   Implements IMonad with IErrorListener + ICmdResponseListener
 *                   (defaults LoggingErrorListener / NOOP, replaceable); worker loop
 *                   survives recoverable faults (catch Exception -> error listener +
 *                   FAIL the command), InterruptedException = clean shutdown, Error
 *                   propagates. NON-final: MonadYY (Step 3) extends for the Yin routines.
 */
package pro.mir0n.esquire.bizTree.taijitu;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.access.CacheNotReadyException;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The active (yang) cache monad: a single FIFO queue, a single worker thread,
 * two gates, a small state machine -- and the full cache-access surface
 * (reads + writes). The owning director is a pure router that forwards to it.
 *
 * Layers:
 *   - API front (this class' public methods): queue entry, monitor + control,
 *     reads, listener registration.
 *   - internal (worker thread + the two domain hubs): commands -> MonadCmdHub,
 *     events -> IEventSink (production = MessageHandlerHub::dispatch).
 *
 * Worker resilience: the per-item processing is wrapped so one poisoned item
 * (e.g. an NPE on a malformed event) cannot silently kill the worker thread.
 * Recoverable Exceptions are routed to the IErrorListener and the worker keeps
 * running; InterruptedException is a clean shutdown signal; fatal Errors
 * (OOM etc.) are left to propagate.
 */
@Slf4j
public class MonadY implements IMonad {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + MonadY.class.getName());

    private final String                    name;
    private final BlockingQueue<IQueueItem> queue;
    private final MonadCmdHub               cmdHub;
    private final IEventSink                eventHub;
    private final IBizTreeService           readBackend;

    private volatile boolean     queueEnabled      = false;
    private volatile boolean     processingEnabled = false;
    private volatile MonadStatus status            = MonadStatus.IDLE;

    private volatile IErrorListener       errorListener;
    private volatile ICmdResponseListener cmdResponseListener = ICmdResponseListener.NOOP;

    private final ReentrantLock gate        = new ReentrantLock();
    private final Condition     gateChanged = gate.newCondition();

    private volatile boolean running = false;
    private Thread worker;

    public MonadY(String name,
                  int queueCapacity,
                  ICacheLoad cacheLoad,
                  IEventSink eventHub,
                  IBizTreeService readBackend) {
        this.name          = name;
        this.queue         = new ArrayBlockingQueue<>(queueCapacity);
        this.eventHub      = eventHub;
        this.readBackend   = readBackend;
        this.errorListener = new LoggingErrorListener(name);
        this.cmdHub        = new MonadCmdHub(cacheLoad, this);
    }

    /* ====================================================================
     * API front -- lifecycle
     * ==================================================================== */

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::workerLoop, "monad-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        signalGate();
        if (worker != null) {
            worker.interrupt();
        }
    }

    /* ====================================================================
     * API front -- queue entry
     * ==================================================================== */

    @Override
    public void submit(IMonadCommand command) {
        put(new IQueueItem.Cmd(command));
    }

    @Override
    public boolean offer(String eventType, String entityId, int entityKind, JsonNode textNode) {
        if (!queueEnabled) {
            return false;
        }
        boolean ret = queue.offer(new IQueueItem.Event(eventType, entityId, entityKind, textNode));
        if (!ret) {
            log.warn("monad[{}]: queue full ({}), event dropped: type={} id={} kind={}",
                    name, queue.size(), eventType, entityId, entityKind);
        }
        return ret;
    }

    /* ====================================================================
     * API front -- monitor & control
     * ==================================================================== */

    @Override
    public void setQueueEnabled(boolean v) {
        this.queueEnabled = v;
    }

    public void setProcessingEnabled(boolean v) {
        this.processingEnabled = v;
        signalGate();
    }

    public boolean      isQueueEnabled()      { return queueEnabled; }
    public boolean      isProcessingEnabled() { return processingEnabled; }
    @Override public MonadStatus status()     { return status; }
    @Override public int         queueDepth() { return queue.size(); }

    /* ====================================================================
     * API front -- listeners
     * ==================================================================== */

    @Override
    public void setErrorListener(IErrorListener listener) {
        this.errorListener = (listener == null) ? new LoggingErrorListener(name) : listener;
    }

    @Override
    public void setCmdResponseListener(ICmdResponseListener listener) {
        this.cmdResponseListener = (listener == null) ? ICmdResponseListener.NOOP : listener;
    }

    /* ====================================================================
     * API front -- reads (full cache-access object; gated on LOADED)
     * ==================================================================== */

    @Override
    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid) {
        requireLoaded();
        return readBackend.esquire(id, skip, take, rootPath, uid);
    }

    @Override
    public List<String> esquirePath(String id, String rootPath) {
        requireLoaded();
        return readBackend.esquirePath(id, rootPath);
    }

    @Override
    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid) {
        requireLoaded();
        return readBackend.esquireEntityNode(kind, id, name, rootPath, uid);
    }

    @Override
    public List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid) {
        requireLoaded();
        return readBackend.esquireSubtree(id, rootPath, uid);
    }

    private void requireLoaded() {
        if (status != MonadStatus.LOADED) {
            throw new CacheNotReadyException("monad=" + name + " status=" + status);
        }
    }

    /* ====================================================================
     * internal -- worker thread + gate
     * ==================================================================== */

    private void workerLoop() {
        while (running) {
            IQueueItem item;
            try {
                item = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // clean shutdown signal
                if (!running) {
                    break;
                }
                continue;
            }

            try {
                processItem(item);
            } catch (Exception e) {
                // Recoverable fault (NPE etc.): keep the worker alive, surface it.
                errorListener.onError("processing " + describe(item), e);
                if (item instanceof IQueueItem.Cmd c) {
                    setStatusInternal(MonadStatus.FAILED);
                    cmdResponseListener.onResult(c.command(), MonadStatus.FAILED);
                }
            }
            // java.lang.Error (OOM, StackOverflow, ...) is intentionally NOT caught
            // -- it propagates and ends the worker, which is correct for a fatal fault.
        }
    }

    private void processItem(IQueueItem item) {
        if (item instanceof IQueueItem.Cmd c) {
            cmdHub.handle(c.command());
        } else if (item instanceof IQueueItem.Event e) {
            if (awaitProcessing()) {
                eventHub.apply(e.eventType(), e.entityId(), e.entityKind(), e.textNode());
            }
            // awaitProcessing()==false: stopping or IDLE/FAILED -> event dropped
        }
    }

    private static String describe(IQueueItem item) {
        if (item instanceof IQueueItem.Cmd c) {
            return "command " + c.command().getClass().getSimpleName();
        }
        if (item instanceof IQueueItem.Event e) {
            return "event " + e.eventType() + " id=" + e.entityId() + " kind=" + e.entityKind();
        }
        return "unknown item";
    }

    /**
     * Block the worker until events may be applied. Returns true to apply,
     * false to drop the held event. Drops when stopping, or when status is
     * IDLE/FAILED (no load in flight that would ever open the gate). Waits
     * only while LOADING.
     */
    private boolean awaitProcessing() {
        gate.lock();
        try {
            while (running && !processingEnabled) {
                if (status == MonadStatus.FAILED || status == MonadStatus.IDLE) {
                    return false;
                }
                gateChanged.await();
            }
            return running && processingEnabled;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            gate.unlock();
        }
    }

    private void put(IQueueItem item) {
        try {
            queue.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void signalGate() {
        gate.lock();
        try {
            gateChanged.signalAll();
        } finally {
            gate.unlock();
        }
    }

    /* ====================================================================
     * internal -- driven by MonadCmdHub (same package; no control interface)
     * ==================================================================== */

    String name() {
        return name;
    }

    IErrorListener errorListener() {
        return errorListener;
    }

    ICmdResponseListener cmdResponseListener() {
        return cmdResponseListener;
    }

    void setStatusInternal(MonadStatus s) {
        this.status = s;
        signalGate();
    }

    void dropBufferedEventsInternal() {
        queue.removeIf(qi -> qi instanceof IQueueItem.Event);
    }
}

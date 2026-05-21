/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: abstract cache monad -- the generalized Taijitu monad mechanics
 *                   lifted out of bizTree. Owns a BoundedQueueRig over QueueItem, the status
 *                   machine, the queue gate, and command EXECUTION (LOAD/CLEAR/CHECKSUM). It is
 *                   bean-blind and REST-free: the actual cache work is one abstract hook the
 *                   subclass fills -- _processItem(QueueItem). The monad executes commands and
 *                   fires onStarted/onResult to its ICmdResponseListener; the controlling
 *                   director (ATaijituRigY) decides the processing gate.
 */
package pro.mir0n.utils.taijitu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.utils.concurrent.BoundedQueueRig;
import pro.mir0n.utils.concurrent.IQueueRig;

/**
 * The controlled cache monad. A single FIFO queue ({@link QueueItem}) drained by a single
 * worker, a small status machine, and two gates (accept / process) -- bean-blind.
 *
 * Layers:
 *   - public API (used by the director): lifecycle, queue entry, control, status, listeners.
 *   - command execution: the dispatcher runs LOAD/CLEAR/CHECKSUM "in Taijitu terms" (status +
 *     onStarted/onResult notifications); the actual cache work is delegated to {@link #_processItem}.
 *   - message handling: events also go to {@link #_processItem} (the subclass branches on CMD).
 *
 * Only LOAD/CLEAR change status; a message or CHECKSUM fault is traced by the rig's error
 * listener and does NOT change status. The monad never touches the processing gate during a
 * command -- it only EXECUTES and NOTIFIES; the director (an {@link ICmdResponseListener})
 * drives the gate off the callbacks.
 *
 * Subclasses add the actual cache access (and any domain reads): see bizTree MonadY.
 */
public abstract class AMonadY {

    private static final Logger log    = LoggerFactory.getLogger(AMonadY.class);
    private static final Logger devLog = LoggerFactory.getLogger("develop." + AMonadY.class.getName());

    /** No-op cancel handle for now (real JDBC cancel lands with the night-watch CHECKSUM). */
    private static final ICancelable NOOP_CANCEL = () -> { };

    private final String               name;          // monad INSTANCE id (e.g. "monad") -- never the role
    private final int                  queueCapacity;
    private final IQueueRig<QueueItem> rig;            // queue + worker thread machinery (the Rig)

    private volatile boolean     queueEnabled = false;
    private volatile MonadStatus status       = MonadStatus.IDLE;

    //private volatile IErrorListener       errorListener;
    private volatile ICmdResponseListener cmdResponseListener = ICmdResponseListener.NOOP;

    private volatile boolean started = false;

    protected AMonadY(String monadId, int queueCapacity) {
        this.name          = monadId;
        this.queueCapacity = queueCapacity;
        this.rig           = new BoundedQueueRig<>(this::processItem);
    }

    /* ==================================================================== */
    /* Abstract hook -- the actual cache work the subclass fills            */
    /* ==================================================================== */

    /**
     * Do the actual work for one queue item, on the worker thread: for a command
     * (eventType == CMD) run the cache-side of LOAD / CLEAR / CHECKSUM; otherwise apply the
     * message to the cache. A command whose work fails must throw (the monad goes FAILED).
     */
    protected abstract void _processItem(QueueItem item);

    /* ==================================================================== */
    /* Public API -- lifecycle                                              */
    /* ==================================================================== */

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        rig.init(name, devLog, queueCapacity);
        // We do NOT override the rig's error listener: any uncaught worker fault (a message
        // that throws, a CHECKSUM fault) is traced there and does NOT change monad status.
        // Only a failed LOAD flips status -- handled (and caught) in handleCommand below.
        rig.start();   // worker parks immediately -- processing starts OFF
    }

    public synchronized void stop() {
        rig.shutdown();
    }

    /* ==================================================================== */
    /* Public API -- queue entry                                            */
    /* ==================================================================== */

    public void submit(String commandId) {
        // Commands ride the same queue as events. No user requestId; correlationId is a cheap
        // synthesized tracking id.
        String correlationId = MonadCmd.CMD + "." + commandId + "." + name + "." + System.currentTimeMillis();
        rig.put(new QueueItem(MonadCmd.CMD, commandId, 0, null, correlationId, null, null));
    }

    public boolean offer(QueueItem item) {
        if (!queueEnabled) {
            return false;
        }
        rig.put(item);   // blocking put on the bounded rig -- backpressure if full
        return true;
    }

    /* ==================================================================== */
    /* Public API -- monitor & control (the director toggles these)         */
    /* ==================================================================== */

    public void setQueueEnabled(boolean enabled)      { this.queueEnabled = enabled; }
    public void setProcessingEnabled(boolean enabled) { rig.setProcessing(enabled); }
    public void clearQueue()                          { rig.clear(); }
    public MonadStatus status()                       { return status; }
    public int         queueDepth()                   { return rig.size(); }

    /* ==================================================================== */
    /* Public API -- listeners                                              */
    /* ==================================================================== */

    public void setCmdResponseListener(ICmdResponseListener listener) {
        this.cmdResponseListener = (listener == null) ? ICmdResponseListener.NOOP : listener;
    }

    /** Monad instance id (never the role). */
    public String monadId() {
        return name;
    }

    /* ==================================================================== */
    /* internal -- worker callback + command execution                     */
    /* ==================================================================== */

    private void processItem(QueueItem item) {
        if (MonadCmd.CMD == item.eventType()) {   // interned marker: command
            handleCommand(item);
        } else {
            _processItem(item);                        // message -> subclass cache work
        }
    }

    private void handleCommand(QueueItem item) {
        // Tell the director the command has begun; it disables processing pre-emptively.
        cmdResponseListener.onStarted(item.entityId(), NOOP_CANCEL);
        if (MonadCmd.LOAD == item.entityId()) {
            setStatusInternal(MonadStatus.LOADING);
            log.info("monad[{}]: LOAD -- loading", name);
            try {
                _processItem(item);
                setStatusInternal(MonadStatus.LOADED);
                log.info("monad[{}]: LOAD -- loaded", name);
                cmdResponseListener.onResult(MonadCmd.LOAD, MonadStatus.LOADED);   // director enables processing
            } catch (Throwable e) {
                setStatusInternal(MonadStatus.FAILED);
                devLog.error("monad[{}]: LOAD -- failed", name, e);   // errors -> develop only (route to console via appender if wanted)
                cmdResponseListener.onResult(MonadCmd.LOAD, MonadStatus.FAILED);   // director clears the queue
            }
        } else if (MonadCmd.CLEAR == item.entityId()) {
            setQueueEnabled(false);
            _processItem(item);
            setStatusInternal(MonadStatus.IDLE);
            log.info("monad[{}]: CLEAR -- idle", name);
            cmdResponseListener.onResult(MonadCmd.CLEAR, MonadStatus.IDLE);    // director disables + clears
        } else if (MonadCmd.CHECKSUM == item.entityId()) {
            devLog.debug("monad[{}]: CHECKSUM stub (no-op until Yin lands)", name);
        } else {
            devLog.warn("monad[{}]: unknown command '{}' -- ignored", name, item.entityId());
        }
    }

    private void setStatusInternal(MonadStatus s) {
        this.status = s;
    }
}

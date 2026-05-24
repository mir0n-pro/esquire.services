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
 * 05/22/2026 mir0n  synchronous command: added doCommand(cmd, enableQueue, timeoutMs) -- posts the
 *                   command and BLOCKS on an inner CommandGate monitor until the worker signals
 *                   (timeoutMs<=0 waits indefinitely; a positive timeout cancels the registered
 *                   cancelable + grace-waits). CommandGate is the ICmdResponseListener the worker
 *                   notifies, forwarding to the rig listener + waking doCommand. handleCommand now
 *                   NOTIFIES via the gate (CLEAR in try/finally -> ALWAYS IDLE + notify, so
 *                   doCommand never hangs). implements IMonad; instance loggers via getClass().
 * 05/22/2026 mir0n  dark-enabling: onResult + CommandGate carry a result String (doCommand returns
 *                   it -- notifyComplete uses the result, else status.name()); _processItem returns
 *                   String; handleCommand + commandGate + log/devLog made protected so the dark
 *                   AMonad can override (CHECKSUM dispatch); the CHECKSUM stub branch removed from
 *                   handleCommand (AMonad handles it).
 * 05/23/2026 mir0n  submit(commandId) -> submitCommand(commandId, enableQueue): clears the gate, posts
 *                   the command, and opens the accept-gate when enableQueue. doCommand split into
 *                   submitCommand + the new resultCommand(timeoutMs) (block on the gate, cancel the
 *                   registered cancelable + grace-wait on a positive timeout). Removed dead NOOP_CANCEL;
 *                   unknown-command log demoted devLog.warn -> devLog.debug.
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
public abstract class AMonadY implements IMonad {

    // instance loggers bound to the concrete subclass (getClass()), so log lines show e.g. MonadY
    protected final Logger log    = LoggerFactory.getLogger(getClass());
    protected final Logger devLog = LoggerFactory.getLogger("develop." + getClass().getName());

    /** After cancelling a timed-out command, wait this long for the worker to report the failure. */
    private static final long CANCEL_GRACE_MS = 1000L;

    private final String               name;          // monad INSTANCE id (e.g. "monad") -- never the role
    private final int                  queueCapacity;
    private final IQueueRig<QueueItem> rig;            // queue + worker thread machinery (the Rig)

    private volatile boolean     queueEnabled = false;
    private volatile MonadStatus status       = MonadStatus.IDLE;

    private volatile ICmdResponseListener rigCmdResponseListener = null;

    private volatile boolean started = false;
    /** Monitor + result slot for a synchronous {@link #doCommand}. */
    protected final CommandGate commandGate = new CommandGate(); // we reuse the same instance: we cannot run more than one command simultaneously

    protected AMonadY(String monadId, int queueCapacity) {
        this.name          = monadId;
        this.queueCapacity = queueCapacity;
        this.rig           = new BoundedQueueRig<>(this::processItem);
    }

    /* ==================================================================== */
    /* Abstract hook -- the actual cache work the subclass fills            */
    /* ==================================================================== */

    /**
     * Do the actual work for one queue item, on the worker thread: for a command (eventType == CMD)
     * run the cache-side of LOAD / CLEAR; otherwise apply the message to the cache. A command whose
     * work fails must throw (the monad goes FAILED). The returned String is a command's result
     * (e.g. a digest); LOAD / CLEAR / messages return value is ignored.
     */
    protected abstract String _processItem(QueueItem item);

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

    public synchronized void shutdown() {
        rig.shutdown();
    }

    /* ==================================================================== */
    /* Public API -- queue entry                                            */
    /* ==================================================================== */

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

    public void setQueueEnabled(boolean enabled) {
        this.queueEnabled = enabled;
    }
    public void setProcessingEnabled(boolean enabled) {
        rig.setProcessing(enabled);
    }
    public void queueClear() {
        rig.clear();
    }
    public MonadStatus status() {
        return status;
    }
    public int queueSize() {
        return rig.size();
    }

    /* ==================================================================== */
    /* Public API -- listeners                                              */
    /* ==================================================================== */

    public void setCmdResponseListener(ICmdResponseListener listener) {
        this.rigCmdResponseListener = listener;
    }

    /** Monad instance id (never the role). */
    public String id() {
        return name;
    }

    /* ==================================================================== */
    /* Public API -- synchronous command                                    */
    /* ==================================================================== */

    public void submitCommand(String commandId, boolean enableQueue) {
        // Commands ride the same queue as events. No user requestId; correlationId is a cheap
        // synthesized tracking id.
        commandGate.clear();
        String correlationId = MonadCmd.CMD + "." + commandId + "." + name + "." + System.currentTimeMillis();
        rig.put(new QueueItem(MonadCmd.CMD, commandId, 0, null, correlationId, null, null));
        if (enableQueue) {
            setQueueEnabled(true);        // accept events (they buffer behind the LOAD)
        }
    }

    /**
     * Issue a command and BLOCK until the worker completes it, returning its RESULT -- the status
     * for LOAD/CLEAR, the digest for CHECKSUM, etc. {@code timeoutMs <= 0} waits INDEFINITELY (LOAD
     * must complete -- no cache, no service); a positive timeout returns {@code null} if it elapses
     * first (the attempt failed -- the caller sleeps and retries). One command in flight at a time
     * (commands serialize through the single worker). The worker must be processing for it to run.
     */
    public String doCommand(String cmd, boolean enableQueue,  long timeoutMs) {
        submitCommand(cmd, enableQueue);
        return resultCommand(timeoutMs);
    }

    public String resultCommand(long timeoutMs) {
        String result = null;
        ICancelable cancelable = null;
        try {
            if (timeoutMs <= 0) {
                synchronized (commandGate) {
                    while (commandGate.result == null) { //avoid JVM spurious wakeup
                        commandGate.wait(); // wait [forever] until completed (LOAD)
                    }
                    result = commandGate.result;
                }
            } else {
                synchronized (commandGate) {
                    if (commandGate.result == null) {
                        commandGate.wait(timeoutMs);
                    }
                    result = commandGate.result;
                    cancelable = commandGate.cancelable;
                }

                if (result == null // TIMEOUT
                        && cancelable != null) {
                    cancelable.cancel();
                    synchronized (commandGate) {
                        commandGate.wait(CANCEL_GRACE_MS);
                        result = commandGate.result;
                    }
                }
            }
        } catch (InterruptedException e) {
            // xxx: we doCommand from the director thread,
            // within non-interactable routines: like bootstrap,
            // well we can report about; this max that we can do
            result = "INTERRUPTED";
            //Thread.currentThread().interrupt();
        }
        return result == null ? "TIMEDOUT" : result; //TBD: FAILED?
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

    //TBD: we need to get back to the method: do not like it so far;
    //     a command handle must be abstract, generic
    protected void handleCommand(QueueItem item) {
        if (MonadCmd.LOAD == item.entityId()) {
            setStatusInternal(MonadStatus.LOADING);
            log.info("monad[{}]: LOAD -- loading", name);
            try {
                commandGate.onStarted(item.entityId(), null);   // director enables processing
                _processItem(item);
                setStatusInternal(MonadStatus.LOADED);
                log.info("monad[{}]: LOAD -- loaded", name);
                commandGate.onResult(MonadCmd.LOAD, MonadStatus.LOADED, null);   // director enables processing
            } catch (Throwable e) {
                setStatusInternal(MonadStatus.FAILED);
                devLog.error("monad[{}]: LOAD -- failed", name, e);   // errors -> develop only (route to console via appender if wanted)
                commandGate.onResult(MonadCmd.LOAD, MonadStatus.FAILED, null);   // director clears the queue
            }
        } else if (MonadCmd.CLEAR == item.entityId()) {
            commandGate.onStarted(item.entityId(), null);   // director enables processing
            try {
                _processItem(item);
            } catch (Throwable e) {
                devLog.error("monad[{}]: CLEAR -- failed", name, e);   // best-effort wipe; we end IDLE regardless
            } finally {
                setStatusInternal(MonadStatus.IDLE);                   // CLEAR ALWAYS ends IDLE
                log.info("monad[{}]: CLEAR -- idle", name);
                commandGate.onResult(MonadCmd.CLEAR, MonadStatus.IDLE, null);   // always notify -> doCommand never hangs
            }
        } else {
            devLog.debug("monad[{}]: unknown command '{}' -- ignored", name, item.entityId());
        }
    }

    private void setStatusInternal(MonadStatus s) {
        this.status = s;
    }

    /** Monitor + result slot for one synchronous command. */
    private class CommandGate implements ICmdResponseListener{
        private String      result;       // guarded by 'this'; null until the worker completes (status name, digest, ...)
        private ICancelable cancelable;   // guarded by 'this'; set by the running command if cancelable (e.g. H2 stmt)

        //xxx: not needed to be synchronized: it runs out of concurrent commands
        protected void clear() {
            result = null;
            cancelable = null;
        };

        private synchronized void setCancelable(ICancelable cancelable) {
            this.cancelable = cancelable;
        }

        private synchronized void notifyComplete(String result) {
            this.result = result;
            this.notifyAll();
        }

        //listener reactions
        @Override
        public void onStarted(String commandId, ICancelable cancelable) {
            if (cancelable != null) {
                setCancelable(cancelable);
            }
            if (rigCmdResponseListener != null) {
                rigCmdResponseListener.onStarted(commandId, cancelable);
            }
        }

        @Override
        public void onResult(String commandId, MonadStatus status, String result) {
            if (rigCmdResponseListener != null) {
                rigCmdResponseListener.onResult(commandId, status, result);
            }
            //TBD: or better above?
            notifyComplete(result == null? status.name():result);
        }

    }



}

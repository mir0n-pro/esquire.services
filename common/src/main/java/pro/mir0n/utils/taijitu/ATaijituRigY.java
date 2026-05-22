/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: abstract Taijitu director -- the generalized controller over the
 *                   cache monad(s). Holds the active AMonadY (room for the Yin next), runs the
 *                   standard bootstrap, and DRIVES the processing gate off the monad's command
 *                   callbacks (disable on LOAD start, enable on LOADED, clear on FAILED). The
 *                   monad executes + notifies; the director decides the gate. A consumer's
 *                   director extends this and adds its domain reads (routed to the active monad).
 * 05/22/2026 mir0n  synchronous bootstrap: bootstrap() renamed start(); the active monad held as
 *                   AtomicReference<IMonad>. start() is a retry loop -- clearMonad(m) then
 *                   doCommand(LOAD) until LOADED, else sleep + retry. clearMonad(IMonad) = empty
 *                   the square (disable queue, queueClear, enable processing, doCommand(CLEAR)).
 *                   ctor registers the director as the monad's command listener; onStarted/onResult
 *                   then drive the per-command gate-flag policy. instance loggers via getClass().
 */
package pro.mir0n.utils.taijitu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract director: implements {@link ITaijituRig} (the control face) and
 * {@link ICmdResponseListener}. The ctor registers it as the monad's command listener, so its
 * onStarted/onResult callbacks drive the per-command gate-flag policy. Bean-blind -- no domain reads.
 *
 * start() is SYNCHRONOUS: it issues LOAD via {@link IMonad#doCommand} and BLOCKS until the load
 * completes, retrying (clearMonad + LOAD, with a sleep) until LOADED.
 */
public abstract class ATaijituRigY implements ITaijituRig, ICmdResponseListener {

    // instance loggers bound to the concrete subclass (getClass()), so log lines show e.g. BizTreeDirectorYang
    private final Logger log    = LoggerFactory.getLogger(getClass());
    private final Logger devLog = LoggerFactory.getLogger("develop." + getClass().getName());

    /** The active monad. (Room for a second, passive monad when the dark side lands.) */
    protected AtomicReference<IMonad> yangMonad = new AtomicReference<>();

    /** Sleep between failed LOAD attempts (configurable; the load must eventually succeed). */
    protected long retryDelayMs = 5000L;

    protected ATaijituRigY(IMonad yang) {
        yangMonad.set(yang);
        yang.setCmdResponseListener(this);
    }

    /* --- Lifecycle ------------------------------------------------------- */

    @Override
    public void start() {
        IMonad active = yang();
        active.start();
        int attempt = 0;
        while (true) {
            attempt++;
            clearMonad(active);                  // empty the square first -- a clean slate every attempt (table + queue)
            active.setProcessingEnabled(true);   // the worker should be able to receive the LOAD command
            String result = active.doCommand(MonadCmd.LOAD, true, 0);   // BLOCK until the load completes (must complete)
            if (MonadStatus.LOADED.name().equals(result)) {
                log.info("{}: bootstrap LOADED on attempt {} -- serving", getClass().getSimpleName(), attempt);
                return;                          // queue + processing enabled -> serving
            }
            log.info("{}: bootstrap LOAD={} on attempt {} -- retrying in {}ms",
                    getClass().getSimpleName(), result, attempt, retryDelayMs);
            if (!sleepBeforeRetry()) {
                return;                          // interrupted -> abandon bootstrap
            }
        }
    }

    /** Sleep between LOAD attempts; returns false if interrupted (abandon bootstrap). */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Empty the square: block incoming, purge buffered events, then run CLEAR to wipe the table --
     * a clean slate. All queue/flag control here on the director thread; the worker only clears the
     * table (in CLEAR's _processItem). {@code onResult(CLEAR)} parks the monad (queue + processing off).
     */
    protected String clearMonad(IMonad m) {
        m.setQueueEnabled(false);                       // 1. block incoming
        m.queueClear();                                 // 2. move everybody out (purge, right after disable)
        m.setProcessingEnabled(true);                   // 3. the worker can run CLEAR
        return m.doCommand(MonadCmd.CLEAR, false, 0);   // 4. wipe the table (worker); don't reopen the queue, wait
    }

    @Override
    public void shutdown() {
yang().shutdown();
    }

    /* --- Event intake ---------------------------------------------------- */

    @Override
    public void onEntityBroadcast(String eventType, String entityId, int entityKind,
                                  String requestId, String correlationId,
                                  String messageEncoding, String text) {
        QueueItem item = new QueueItem(eventType, entityId, entityKind,
                requestId, correlationId, messageEncoding, text);
        boolean accepted = yang().offer(item);
        if (!accepted) {
            devLog.debug("{}: event not accepted (status={}): type={} id={} kind={}",
                    getClass().getSimpleName(), yang().status(), eventType, entityId, entityKind);
        }
    }

    /* --- Command lifecycle (the director is the monad's command listener) --- */
    // The monad notifies these on the worker thread (synchronously with the command); they own the
    // per-command gate-flag policy. queueClear stays a director-thread routine (clearMonad), not here.

    @Override
    public void onStarted(String commandId, ICancelable cancelable) {
        //contract: the inner worker cannot control the queue flags;
        if (MonadCmd.LOAD == commandId) {
            yang().setProcessingEnabled(false);  // hold: don't drain events until the load completes
                                                 // (queue-enable is owned by doCommand, at submit time)
        }
    }

    @Override
    public void onResult(String commandId, MonadStatus status) {
        //contract: the inner worker cannot control the queue flags;
        //          it is out of its context
        if (MonadCmd.LOAD == commandId) {
            if (status == MonadStatus.LOADED) {
                yang().setProcessingEnabled(true);    // load ok -> drain buffered events, to be sure!!!
            } else if (status == MonadStatus.FAILED) {
                yang().setQueueEnabled(false);          // synchronously with an inner worker
                yang().setProcessingEnabled(false);  // synchronously with an inner worker
            }
        } else if (MonadCmd.CLEAR.equals(commandId)) {
            yang().setQueueEnabled(false);       // synchronously with an inner worker
            yang().setProcessingEnabled(false);  // synchronously with an inner worker
        }
    }

    /** The active monad, for subclasses that route domain reads to it. */
    protected IMonad yang() {
        return yangMonad.get();
    }
}

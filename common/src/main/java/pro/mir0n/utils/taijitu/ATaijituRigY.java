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
 * 05/22/2026 mir0n  no longer implements ICmdResponseListener; gateFor(IMonad) builds a per-monad
 *                   listener (registered in start(), was the ctor self-registration) so a multi-monad
 *                   director can tell its monads apart. onResult is 3-arg (result String). log/devLog protected.
 * 05/23/2026 mir0n  added isReady() -- true once the serving monad is LOADED (the readiness gate).
 * 06/15/2026 mir0n  pass(...) event-intake signature changed: the raw (messageEncoding, text) pair replaced
 *                   by a single already-parsed body Map<String,Object>, forwarded into the body-map QueueItem.
 */
package pro.mir0n.utils.taijitu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract director: implements {@link ITaijituRig} (the control face). It does NOT listen to its
 * monad's commands itself -- {@link #gateFor} builds a per-monad {@link ICmdResponseListener} that
 * drives that monad's processing gate (disable during LOAD, enable on LOADED, park on FAILED/CLEAR),
 * and start() registers it. Bean-blind -- no domain reads.
 *
 * start() is SYNCHRONOUS: it issues LOAD via {@link IMonad#doCommand} and BLOCKS until the load
 * completes, retrying (clearMonad + LOAD, with a sleep) until LOADED.
 */
public abstract class ATaijituRigY implements ITaijituRig {

    // instance loggers bound to the concrete subclass (getClass()), so log lines show e.g. BizTreeDirectorYang
    protected final Logger log    = LoggerFactory.getLogger(getClass());
    protected final Logger devLog = LoggerFactory.getLogger("develop." + getClass().getName());

    /** The active monad. (Room for a second, passive monad when the dark side lands.) */
    protected AtomicReference<IMonad> yangMonad = new AtomicReference<>();

    /** Sleep between failed LOAD attempts (configurable; the load must eventually succeed). */
    protected long retryDelayMs = 5000L;

    protected ATaijituRigY(IMonad yang) {
        yangMonad.set(yang);
    }

    /* --- Lifecycle ------------------------------------------------------- */

    @Override
    public void start() {
        IMonad active = yang();
        active.setCmdResponseListener(gateFor(active));
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
                                  String requestId, String correlationId, java.util.Map<String, Object> body) {
        QueueItem item = new QueueItem(eventType, entityId, entityKind, requestId, correlationId, body);
        boolean accepted = yang().offer(item);
        if (!accepted) {
            devLog.debug("{}: event not accepted (status={}): type={} id={} kind={}",
                    getClass().getSimpleName(), yang().status(), eventType, entityId, entityKind);
        }
    }

    /** Ready once the serving monad is LOADED (false during the bootstrap load). */
    @Override
    public boolean isReady() {
        return yang().status() == MonadStatus.LOADED;
    }

    /* --- Per-monad command-gate listener (built by gateFor) --------------- */
    // gateFor(m) is the ICmdResponseListener the monad notifies on the worker thread (synchronously
    // with the command); it owns that monad's per-command gate-flag policy. queueClear stays a
    // director-thread routine (clearMonad), not here.

    /** The active monad, for subclasses that route domain reads to it. */
    protected IMonad yang() {
        return yangMonad.get();
    }


    protected ICmdResponseListener gateFor(IMonad m) {
        return new ICmdResponseListener() {
            @Override
            public void onStarted(String commandId, ICancelable cancelable) {
                if (MonadCmd.LOAD.equals(commandId)) {
                    m.setProcessingEnabled(false);   // hold events until the load result is known
                }
            }
            @Override
            public void onResult(String commandId, MonadStatus status, String result) {
                if (MonadCmd.LOAD.equals(commandId)) {
                    if (status == MonadStatus.LOADED) {
                        m.setProcessingEnabled(true);    // load ok -> drain buffered events
                    } else if (status == MonadStatus.FAILED) {
                        m.setQueueEnabled(false);          // synchronously with an inner worker
                        m.setProcessingEnabled(false);  // synchronously with an inner worker
                        //m.queueClear();
                    }
                } else if (MonadCmd.CLEAR.equals(commandId)) {
                    m.setQueueEnabled(false);       // synchronously with an inner worker
                    m.setProcessingEnabled(false);  // synchronously with an inner worker
                    //m.queueClear();
                }
            }
        };
    }

}

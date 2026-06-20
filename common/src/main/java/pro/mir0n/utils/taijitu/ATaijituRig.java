/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/21/2026 mir0n  created: the DARK-side director -- extends the bright ATaijituRigY (single
 *                   active monad) to the full Taijitu: two equal AMonad instances (serving +
 *                   shadow), a swappable role pair (the double-buffer pointer flip), per-monad
 *                   gate driving, event fanout to the shadow during a sweep, and off-queue
 *                   CHECKSUM collection. Reads route to the serving monad. (Night-watch sweep
 *                   orchestration lands next.)
 * 05/23/2026 mir0n  night-watch sweep orchestration: a single daemon ScheduledExecutorService re-arms
 *                   each sweep sweepIntervalMs AFTER the previous one ends (sweeping guard, one at a
 *                   time). sweep() loads the shadow fresh, posts CHECKSUM to both legs, collects each
 *                   within sweepTimeoutMs (resultCommand), screens FAILED (checksumFailed -> abandon),
 *                   compares digests, reacts per onMismatch (LOG / SWAP swapYinYang / TERMINATE), and
 *                   clears the shadow back to idle. sweepAsync() + sweepGuarded() back the REST force-
 *                   sweep. Cadence/policy configurable: sweepIntervalMs / sweepTimeoutMs / onMismatch + setters.
 * 06/15/2026 mir0n  pass(...) event-intake signature changed: the raw (messageEncoding, text) pair replaced
 *                   by a single already-parsed body Map<String,Object>, forwarded into the body-map QueueItem.
 */
package pro.mir0n.utils.taijitu;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The full Taijitu director: two equal {@link AMonad}s behind one director. {@code serving}
 * (yang role) answers reads and receives every event; {@code shadow} (yin role) is idle until a
 * night-watch sweep loads it fresh from the source of truth, fans events to it, CHECKSUMs both,
 * and promotes-or-discards via {@link #swapYinYang()}. Bright {@link ATaijituRigY} drives ONE active
 * monad; this adds the second monad and the swap.
 *
 * The two monads each get their OWN gate-driving listener (the bright single-active onStarted/
 * onResult cannot tell the monads apart), so this director does not register itself as their
 * {@link ICmdResponseListener}.
 */
public abstract class ATaijituRig extends ATaijituRigY {

    protected AtomicReference<IMonad> yinMonad = new AtomicReference<>();

    /** Night-watch trigger (daemon thread); fires the next sweep() sweepIntervalMs AFTER the last one ends. */
    private final ScheduledExecutorService nightWatchExec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "night-watch");
                t.setDaemon(true);
                return t;
            });
    /** Delay between the end of one sweep and the start of the next (configurable); short enough to watch live. */
    protected volatile long sweepIntervalMs = 10_000L;
    /** Per-leg CHECKSUM deadline within a sweep (configurable). */
    protected volatile long sweepTimeoutMs = 10_000L;
    /** Guard: one sweep at a time (scheduled or REST-forced). */
    private final AtomicBoolean sweeping = new AtomicBoolean(false);
    /** What to do when the two monads' checksums disagree (configurable). */
    protected volatile MismatchAction onMismatch = MismatchAction.LOG;

    protected ATaijituRig(IMonad monad, IMonad domad) {
        super(monad);   // the inherited bright 'active' seeds to the initial serving monad
        yinMonad.set(domad);
    }

    /** Set the checksum-mismatch reaction (null -> LOG). */
    public void setOnMismatch(MismatchAction action) {
        this.onMismatch = (action == null) ? MismatchAction.LOG : action;
    }

    /** Set the sweep cadence: delay between the end of one sweep and the start of the next (ms). Apply before start(). */
    public void setSweepIntervalMs(long sweepIntervalMs) {
        this.sweepIntervalMs = sweepIntervalMs;
    }

    /** Set the per-leg CHECKSUM deadline within a sweep (ms). */
    public void setSweepTimeoutMs(long sweepTimeoutMs) {
        this.sweepTimeoutMs = sweepTimeoutMs;
    }

    /* --- Lifecycle ------------------------------------------------------- */
    protected IMonad yin() {
        return yinMonad.get();
    }

    //xxx: swapYinYang: intentionally full swap is not atomic:
    //     most important is swap of yang side; and it is atomic and goes first:
    //     yin leg goes second: there is no concurrency on dark side:
    //     after the swap: next dark command will be from rig - CLEAR - will follow swapYinYang
    //
    protected void swapYinYang() {
        yinMonad.set(yangMonad.getAndSet(yinMonad.get()));

    }
    @Override
    public void start() {
        IMonad shadow  = yin();
        shadow.setCmdResponseListener(gateFor(shadow));
        shadow.start();
        super.start(); //xxx: runs bootstrap
        nightWatchExec.scheduleWithFixedDelay(this::sweepGuarded, sweepIntervalMs, sweepIntervalMs, TimeUnit.MILLISECONDS);
        log.info("{}: start -- both monads up (serving={}, shadow={}); night-watch every {}ms",
                getClass().getSimpleName(), yang().id(), shadow.id(), sweepIntervalMs);
    }

    @Override
    public void shutdown() {
        nightWatchExec.shutdownNow();
        super.shutdown();
        yin().shutdown();
    }


    /* --- Event intake ---------------------------------------------------- */

    @Override
    public void onEntityBroadcast(String eventType, String entityId, int entityKind,
                                  String requestId, String correlationId, java.util.Map<String, Object> body) {
        QueueItem item = new QueueItem(eventType, entityId, entityKind, requestId, correlationId, body);
        yang().offer(item);
        yin().offer(item);
    }

    /* --- Night-watch sweep ------------------------------------------------ */

    /**
     * REST force-sweep: dispatch the guarded sweep onto the night-watch thread and return at once, so
     * the HTTP request is not held for a full load + checksum. Serialized with the periodic sweep on
     * the single-thread executor; the {@code sweeping} guard drops it if one is already in progress.
     */
    @Override
    public void sweepAsync() {
        nightWatchExec.execute(this::sweepGuarded);
    }

    /**
     * Scheduler / async-trigger entry: swallow any throw so an unexpected fault can't cancel the
     * periodic watch (scheduleWithFixedDelay suppresses all future runs once a task throws).
     */
    private void sweepGuarded() {
        try {
            sweep();
        } catch (Throwable t) {
            log.error("{}: night-watch sweep threw -- schedule preserved", getClass().getSimpleName());
            devLog.error("{}: night-watch sweep threw -- schedule preserved", getClass().getSimpleName(), t);
        }
    }

    /**
     * The synchronous night-watch sweep: load the shadow fresh, CHECKSUM both legs (posted to both,
     * collected within one deadline), and react to drift per onMismatch; the shadow is cleared back
     * to idle after. Guarded so only one runs at a time. Run via {@link #sweepGuarded()} by the
     * scheduler and the async REST trigger ({@link #sweepAsync()}); also called directly by tests.
     * NOT on the director interface -- external callers trigger via {@link #sweepAsync()}.
     */
    public void sweep() {
        if (!sweeping.compareAndSet(false, true)) {
            devLog.debug("{}: sweep skipped -- one already in progress", getClass().getSimpleName());
            return;
        }
        try {
            IMonad bright = yang();
            IMonad shadow  = yin();

            //xxx: we skip clearMonad intentionally: there is no way to get yin dirty!
            //clearMonad(shadow);
            shadow.setProcessingEnabled(true);                   // kick: worker can dequeue the LOAD command
            String loaded = shadow.doCommand(MonadCmd.LOAD, true, 0);     // single attempt; events buffer behind the load

            if (MonadStatus.LOADED.name().equals(loaded)) {
                bright.submitCommand(MonadCmd.CHECKSUM, false);                          // post; the worker runs it and signals the gate
                shadow.submitCommand(MonadCmd.CHECKSUM, false);                          // post; the worker runs it and signals the gate
                String brightDigest = bright.resultCommand(sweepTimeoutMs); // how it works; internal threads will collect the result in parallel
                String shadowDigest = shadow.resultCommand(sweepTimeoutMs);   //here we just wait for the result from each thead; noe needs to have double legged structure
                if (checksumFailed(brightDigest) || checksumFailed(shadowDigest)) {
                    log.error("{}: sweep -- checksum FAILED (bright={}, shadow={}) -- inconclusive, retry next sweep",
                            getClass().getSimpleName(), brightDigest, shadowDigest);
                } else if (brightDigest.equals(shadowDigest)) {
                    log.info("{}: sweep -- checksums match ({})", getClass().getSimpleName(), brightDigest);
                } else {
                    switch (onMismatch) {
                        case LOG -> {
                            log.error("{}: CHECKSUM MISMATCH bright={} shadow={} -- LOG (keep serving)",
                                    getClass().getSimpleName(), brightDigest, shadowDigest);
                        }
                        case SWAP -> {
                            log.error("{}: CHECKSUM MISMATCH bright={} shadow={} -- SWAP",
                                    getClass().getSimpleName(), brightDigest, shadowDigest);
                            swapYinYang();
                        }
                        case TERMINATE -> {
                            log.error("{}: CHECKSUM MISMATCH bright={} shadow={} -- TERMINATE",
                                    getClass().getSimpleName(), brightDigest, shadowDigest);
                            System.exit(1);
                        }
                    }
                }
            } else {
                log.error("{}: sweep -- shadow {} load={} -- abandon", getClass().getSimpleName(), shadow.id(), loaded);
            }
        } finally {
            clearMonad(yin()); //keep current yin() in idle
            sweeping.set(false);
        }
    }

    /**
     * A timed-out checksum is cancelled and reported as FAILED, so FAILED is the only inconclusive
     * outcome to screen out before comparing -- a FAILED leg is not evidence of drift, so the sweep
     * abandons (never a match, never a reaction) and retries next tick.
     */
    private static boolean checksumFailed(String checksumResult) {
        return checksumResult == null || MonadStatus.FAILED.name().equals(checksumResult);
    }
}

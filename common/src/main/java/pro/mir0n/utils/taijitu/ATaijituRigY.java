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
 */
package pro.mir0n.utils.taijitu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract director: implements {@link ITaijituRig} (the control face) and
 * {@link ICmdResponseListener} (it listens to its monad's command lifecycle and drives the
 * processing gate). Bean-blind -- no domain reads here.
 *
 * The gate dance is the whole point of the framework, so it lives here once and every cache
 * gets race-safe bootstrap for free:
 *   bootstrap  -> place LOAD, enable queue + processing (worker picks up LOAD)
 *   onStarted  -> LOAD has begun: disable processing (hold events until the result is known)
 *   onResult   -> LOADED: enable processing (drain buffered events); FAILED: clear the queue
 */
public abstract class ATaijituRigY implements ITaijituRig, ICmdResponseListener {

    private static final Logger log    = LoggerFactory.getLogger(ATaijituRigY.class);
    private static final Logger devLog = LoggerFactory.getLogger("develop." + ATaijituRigY.class.getName());

    /** The active monad. (Room for a second, passive monad when the dark side lands.) */
    protected final AMonadY active;

    protected ATaijituRigY(AMonadY active) {
        this.active = active;
    }

    /* --- Lifecycle ------------------------------------------------------- */

    @Override
    public void bootstrap() {
        active.setCmdResponseListener(this);
        active.start();
        active.submit(MonadCmd.LOAD);        // queued; processing still OFF, so not run yet
        active.setQueueEnabled(true);        // accept events (they buffer on the queue)
        active.setProcessingEnabled(true);   // worker picks up LOAD first; onStarted then disables processing
        log.info("{}: bootstrap issued (LOAD queued, queue + processing enabled)", getClass().getSimpleName());
    }

    @Override
    public void shutdown() {
        active.stop();
    }

    /* --- Event intake ---------------------------------------------------- */

    @Override
    public void onEntityBroadcast(String eventType, String entityId, int entityKind,
                                  String requestId, String correlationId,
                                  String messageEncoding, String text) {
        QueueItem item = new QueueItem(eventType, entityId, entityKind,
                requestId, correlationId, messageEncoding, text);
        boolean accepted = active.offer(item);
        if (!accepted) {
            devLog.warn("{}: event not accepted (status={}): type={} id={} kind={}",
                    getClass().getSimpleName(), active.status(), eventType, entityId, entityKind);
        }
    }

    /* --- Command lifecycle (drives the processing gate) ------------------ */

    @Override
    public void onStarted(String commandId, ICancelable cancelable) {
        if (MonadCmd.LOAD.equals(commandId)) {
            active.setProcessingEnabled(false);   // hold events until the load result is known
        }
    }

    @Override
    public void onResult(String commandId, MonadStatus status) {
        if (MonadCmd.LOAD.equals(commandId)) {
            if (status == MonadStatus.LOADED) {
                active.setProcessingEnabled(true);    // load ok -> drain buffered events
            } else if (status == MonadStatus.FAILED) {
                active.setQueueEnabled(false);        // load failed -> stop accepting (no producer backpressure)
                active.clearQueue();                  // and discard what was buffered (still all in the queue)
            }
        } else if (MonadCmd.CLEAR.equals(commandId)) {
            active.setProcessingEnabled(false);
            active.clearQueue();
        }
    }

    /** The active monad, for subclasses that route domain reads to it. */
    protected AMonadY active() {
        return active;
    }
}

/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: command-result listener seam (generalized from bizTree.taijitu).
 *                   The director<->monad collaboration: the monad notifies when a command starts
 *                   (with a cancel handle) and resolves (status); the director drives the
 *                   processing gate off these callbacks.
 */
package pro.mir0n.utils.taijitu;

/**
 * Notified by a monad about the lifecycle of a control command (id = one of {@link MonadCmd}):
 *   - {@link #onStarted} -- the worker has begun the command; carries an {@link ICancelable}.
 *   - {@link #onResult}  -- the command resolved; carries the resulting {@link MonadStatus}.
 *
 * The director ({@link ATaijituRigY}) implements this to drive the processing gate
 * (disable on start, enable on LOADED, clear on FAILED).
 */
public interface ICmdResponseListener {

    void onStarted(String commandId, ICancelable cancelable);

    void onResult(String commandId, MonadStatus status);

    /** No-op default for monads with no controller attached. */
    ICmdResponseListener NOOP = new ICmdResponseListener() {
        @Override public void onStarted(String commandId, ICancelable cancelable) { }
        @Override public void onResult(String commandId, MonadStatus status)      { }
    };
}

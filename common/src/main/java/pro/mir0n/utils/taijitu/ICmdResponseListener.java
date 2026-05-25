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
 * 05/22/2026 mir0n  onResult gains a result String (status name for LOAD/CLEAR, digest for CHECKSUM).
 */
package pro.mir0n.utils.taijitu;

/**
 * Notified by a monad about the lifecycle of a control command (id = one of {@link MonadCmd}):
 *   - {@link #onStarted} -- the worker has begun the command; carries an {@link ICancelable}.
 *   - {@link #onResult}  -- the command resolved; carries the {@link MonadStatus} and the result
 *                           string (the status name for LOAD/CLEAR, the digest for CHECKSUM).
 *
 * The director's {@code gateFor(m)} builds one of these per monad to drive that monad's processing
 * gate (disable on LOAD start, enable on LOADED, park on FAILED/CLEAR).
 */
public interface ICmdResponseListener {

    void onStarted(String commandId, ICancelable cancelable);

    void onResult(String commandId, MonadStatus status, String result);

    /** No-op default for monads with no controller attached. */
    ICmdResponseListener NOOP = new ICmdResponseListener() {
        @Override public void onStarted(String commandId, ICancelable cancelable) { }
        @Override public void onResult(String commandId, MonadStatus status, String result)      { }
    };
}

/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: command-result listener seam (v1.2.5 Taijitu refactor Step 2).
 *                   The taijitu<->monad collaboration: monad notifies the director when a
 *                   command starts (with a cancel handle) and when it resolves (status).
 *                   Default: NOOP. The full Taijitu uses this to drive night-watch.
 */
package pro.mir0n.esquire.bizTree.taijitu;

/**
 * Notified by a monad about the lifecycle of a control command:
 *   - {@link #onStarted} -- the worker has begun the command; carries an
 *     {@link ICancelable} the director can use to abort it on timeout.
 *   - {@link #onResult}  -- the command resolved; carries the resulting status
 *     (e.g. INIT -> LOADED / FAILED, CLEAN -> IDLE).
 *
 * A single-monad director (Yang) can leave this at {@link #NOOP}; the full
 * Taijitu installs a real listener to sequence the night-watch sweep
 * (INIT-passive -> CHECKSUM-both -> compare -> swap).
 */
public interface ICmdResponseListener {

    void onStarted(IMonadCommand command, ICancelable cancelable);

    void onResult(IMonadCommand command, MonadStatus status);

    /** No-op default for directors that don't react to command lifecycle. */
    ICmdResponseListener NOOP = new ICmdResponseListener() {
        @Override public void onStarted(IMonadCommand command, ICancelable cancelable) { }
        @Override public void onResult(IMonadCommand command, MonadStatus status)      { }
    };
}

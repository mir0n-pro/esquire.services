/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: in-flight command abort handle (v1.2.5 Taijitu refactor Step 2).
 *                   Handed to ICmdResponseListener.onStarted; the director calls cancel()
 *                   on timeout to abort the running command. Real body (JDBC
 *                   Statement.cancel for a long CHECKSUM) lands with Yin / night-watch.
 */
package pro.mir0n.esquire.bizTree.taijitu;

/**
 * Abort handle for a command that is currently executing on the monad worker.
 * The director receives one via {@link ICmdResponseListener#onStarted} and
 * calls {@link #cancel()} if the command exceeds its timeout.
 */
@FunctionalInterface
public interface ICancelable {
    void cancel();
}

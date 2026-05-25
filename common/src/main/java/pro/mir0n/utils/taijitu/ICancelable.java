/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: in-flight command abort handle (generalized from bizTree.taijitu).
 */
package pro.mir0n.utils.taijitu;

/**
 * Abort handle for a command currently executing on the monad worker. The director
 * receives one via {@link ICmdResponseListener#onStarted} and calls {@link #cancel()}
 * if the command exceeds its timeout. (Real body lands with the night-watch CHECKSUM.)
 */
@FunctionalInterface
public interface ICancelable {
    void cancel();
}

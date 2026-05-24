/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/21/2026 mir0n  created: the DARK-side monad -- extends the bright AMonadY (queue, status, gate,
 *                   LOAD/CLEAR) with the one capability the Taijitu adds: an off-queue CHECKSUM.
 *                   handleCommand intercepts CHECKSUM and runs it on a SEPARATE thread (checksumExec,
 *                   not the worker) via the abstract _processItemCancellable hook; the digest is
 *                   reported through the 3-arg onResult. CHECKSUM never touches status or the queue.
 * 05/23/2026 mir0n  removed unused Logger imports; javadoc: a timed-out checksum is aborted via the
 *                   cancelable the subclass registers through listener.onStarted (the seam is wired).
 */
package pro.mir0n.utils.taijitu;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dark-side cache monad: a bright {@link AMonadY} plus an off-queue CHECKSUM. {@code handleCommand}
 * intercepts a CHECKSUM command and runs it on a SEPARATE thread (its own {@code checksumExec}, not
 * the queue worker) via the abstract {@link #_processItemCancellable}; the digest is reported back
 * through the 3-arg {@code onResult} (so {@code doCommand(CHECKSUM)} returns it). CHECKSUM never
 * touches status or the queue; a timed-out checksum is aborted via the cancelable the subclass
 * registers through {@code listener.onStarted}.
 *
 * Two equal instances of a concrete subclass (e.g. bizTree {@code Monad}) sit behind one
 * {@link ATaijituRig}; the night-watch loads the shadow fresh, checksums both, and promotes-or-discards.
 */
public abstract class AMonad extends AMonadY {

    private final ExecutorService checksumExec;   // off-queue: CHECKSUM runs here, not on the worker

    protected AMonad(String monadId, int queueCapacity) {
        super(monadId, queueCapacity);
        this.checksumExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "checksum-" + monadId);
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public synchronized void shutdown() {
        super.shutdown();
        checksumExec.shutdownNow();
    }

    @Override
    protected void handleCommand(QueueItem item) {
        if (MonadCmd.CHECKSUM == item.entityId()) {
            checksumExec.execute(new ChecksumWorker(commandGate, item));
        } else {
            super.handleCommand(item);
        }
    }


    /**
     * Compute the CHECKSUM digest off the worker thread (runs on checksumExec). Must be side-effect
     * free and return a NON-null digest (a null collapses to the status name at the gate -- which
     * would let two empty legs falsely match). Register the in-flight cancelable via
     * {@code listener.onStarted} so a timed-out checksum aborts the running query (e.g. bizTree's
     * Monad registers the executing PreparedStatement).
     */
    protected abstract String _processItemCancellable(ICmdResponseListener listener, QueueItem item);

    private class ChecksumWorker implements Runnable {
        private final ICmdResponseListener listener;
        private final QueueItem item;
        private ChecksumWorker(ICmdResponseListener listener, QueueItem item) {
            this.listener = listener;
            this.item = item;
       }
       public void run() {
           try {
               String res = _processItemCancellable(listener, item);
               log.info("monad[{}]: CHECKSUM -- completed", id());
               listener.onResult(MonadCmd.CHECKSUM, MonadStatus.IDLE, res);
           } catch (Throwable e) {
               devLog.error("monad[{}]: CHECKSUM -- failed",id(), e);   // errors -> develop only (route to console via appender if wanted)
               listener.onResult(MonadCmd.CHECKSUM, MonadStatus.FAILED, null);
           }

       }
    }
}

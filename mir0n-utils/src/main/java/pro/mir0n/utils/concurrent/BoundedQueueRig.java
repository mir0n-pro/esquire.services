/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: bounded active-object queue rig -- a fixed-capacity FIFO drained
 *                   by a single daemon worker, with a processing gate. While processing is OFF
 *                   the worker leaves the queue UNTOUCHED (parks before any dequeue); items sit
 *                   on the queue until processing is enabled. A recoverable Throwable from the
 *                   worker is routed to the IErrorListener and the worker keeps running;
 *                   InterruptedException is a shutdown signal. clear() bulk-drops queued items
 *                   (the only removal other than normal processing). Lifted from bizTree MonadY.
 * 06/02/2026 mir0n  added tryPut(E) -- non-blocking offer; returns false when stopped or at
 *                   capacity instead of waiting or dropping silently.
 * 06/02/2026 mir0n  bulk drain for an IQueueListWorker: at backlog > setBulkThreshold (default 10) the
 *                   whole deque is handed over as one ArrayList; the worker's returned remainder is
 *                   re-queued to the front, a thrown bulk is routed to the IListErrorListener (null = stop).
 *                   run / process flags now read through an ISignaler; LoggingErrorListener is an IListErrorListener
 */
package pro.mir0n.utils.concurrent;

import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded {@link IQueueRig}: a fixed-capacity FIFO queue drained by one daemon
 * worker thread, gated by a processing flag. The worker is supplied at
 * construction; {@link #init} sizes the queue and names the thread.
 *
 * Processing gate (the key property): while processing is OFF the worker parks
 * BEFORE dequeuing -- queued items are left UNTOUCHED on the queue, in order,
 * and {@link #size()} still counts them. Producers may keep {@link #put}-ing
 * (blocking only when full). When processing is turned ON the worker drains the
 * queue. {@link #clear()} is the only way (besides normal processing) to remove
 * items -- a bulk drop used when buffered work must be discarded.
 *
 * Worker resilience: each {@code worker.process(item)} runs outside the lock and
 * is wrapped so one poisoned item cannot kill the thread -- a {@link Throwable}
 * is routed to the {@link IErrorListener} (if set) or logged, and the worker
 * continues. {@link InterruptedException} from the wait is a shutdown signal.
 *
 * @param <E> queue element type
 */
public class BoundedQueueRig<E> implements IQueueRig<E> {

    private final IQueueWorker<E>     worker;
    private final IQueueListWorker<E> listWorker;   // non-null iff worker is an IQueueListWorker

    private String name = "queue-rig";
    private Logger devLog;
    private int    capacity;

    private final ArrayDeque<E>   deque = new ArrayDeque<>();
    private final AtomicInteger   count = new AtomicInteger(0);   // lock-free snapshot for size()
    private IErrorListener<E>      errorListener = new LoggingErrorListener();

    private final ReentrantLock lock      = new ReentrantLock();
    private final Condition     notFull   = lock.newCondition();   // signalled when space frees
    private final Condition     available = lock.newCondition();   // signalled when (processing && non-empty) may hold

    private volatile boolean running    = false;
    private volatile boolean processing = false;   // gate; volatile so the signaler reads it lock-free
    private Thread thread;

    /** Rig run/process state as an {@link ISignaler} -- the single source of truth read by the
     *  worker loop / put paths here AND handed to a list worker, so its bulk sees exactly the same
     *  running/processing view the rig acts on. The setters (setProcessing/start/shutdown) own the
     *  flags; everything that READS them goes through this. */
    private final ISignaler signaler = new ISignaler() {
        @Override public boolean isRunning()    { return running; }
        @Override public boolean isProcessing() { return processing; }
    };

    /** Backlog size above which a list worker is handed the whole queue as ONE bulk; at or below
     *  it the rig drains one-by-one. Only consulted when the worker is an {@link IQueueListWorker}.
     *  Internal tuning. */
    public static final int DEFAULT_BULK_THRESHOLD = 10;
    private volatile int bulkThreshold = DEFAULT_BULK_THRESHOLD;

    @Override
    public void setBulkThreshold(int n) {
        this.bulkThreshold = n;
    }

    /** Upper bound on any single condition wait. put() drops the item if the queue stays
     *  full this long (so a producer never hangs forever while processing is paused); the
     *  worker simply re-checks its loop condition on timeout (a missed-signal safety net). */
    public static final long DEFAULT_AWAIT_TIMEOUT_MS = 10_000L;
    private volatile long awaitTimeoutMs = DEFAULT_AWAIT_TIMEOUT_MS;

    public void setAwaitTimeoutMs(long ms) {
        this.awaitTimeoutMs = ms;
    }

    public BoundedQueueRig(IQueueWorker<E> worker) {
        this.worker     = worker;
        this.listWorker = (worker instanceof IQueueListWorker) ? (IQueueListWorker<E>) worker : null;
    }

    @Override
    public void init(String name, Logger devLogger, int capacity) {
        this.name     = name;
        this.devLog   = devLogger;
        this.capacity = capacity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setErrorListener(IErrorListener listener) {
        this.errorListener = listener;
    }

    @Override
    public void setProcessing(boolean enabled) {
        lock.lock();
        try {
            processing = enabled;
            if (enabled) {
                available.signalAll();   // wake the worker to drain
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            deque.clear();
            count.set(0);
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, name);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void shutdown() {
        running = false;
        lock.lock();
        try {
            available.signalAll();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void put(E item) {
        lock.lock();
        try {
            long nanos = TimeUnit.MILLISECONDS.toNanos(awaitTimeoutMs);
            while (signaler.isRunning() && deque.size() >= capacity) {
                if (nanos <= 0L) {
                    // Full for longer than the timeout (e.g. processing paused) -- drop, don't hang.
                    if (devLog != null) {
                        devLog.warn("queue-rig[{}]: put timed out after {}ms (full, size={}) -- item dropped: {}",
                                name, awaitTimeoutMs, deque.size(), item);
                    }
                    return;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            if (!signaler.isRunning()) {
                return;
            }
            deque.addLast(item);
            count.incrementAndGet();
            if (signaler.isProcessing()) {
                available.signal();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Non-blocking offer: enqueue the item if there is room, otherwise return false immediately.
     * Unlike {@link #put}, this never waits on the queue and never drops silently -- the caller
     * gets a definitive yes/no and can take corrective action (counter rollback, retry, error).
     * v1.2.6 Goal 3: the enyMan move-queue needs this so that submitMove can decrement its
     * "move in progress" counter when capacity is exhausted, instead of leaking it.
     */
    @Override
    public boolean tryPut(E item) {
        lock.lock();
        try {
            if (!signaler.isRunning() || deque.size() >= capacity) {
                return false;
            }
            deque.addLast(item);
            count.incrementAndGet();
            if (signaler.isProcessing()) {
                available.signal();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        return count.get();   // lock-free snapshot (eventually consistent; fine for monitoring)
    }

    private void loop() {
        while (signaler.isRunning()) {
            E            single = null;
            ArrayList<E> bulk   = null;
            lock.lock();
            try {
                // Park while the gate is OFF or the queue is empty. Items are left UNTOUCHED
                // on the queue -- nothing is dequeued until processing is enabled.
                while (signaler.isRunning() && (!signaler.isProcessing() || deque.isEmpty())) {
                    // Timed wait: a missed signal can never park the worker forever -- it
                    // re-checks the condition at least every awaitTimeoutMs.
                    available.await(awaitTimeoutMs, TimeUnit.MILLISECONDS);
                }
                if (signaler.isRunning()) {
                    if (listWorker != null && deque.size() > bulkThreshold) {
                        // Overloaded -> hand the WHOLE backlog to the list worker in one shot.
                        bulk = new ArrayList<>(deque);
                        deque.clear();
                        count.set(0);
                        notFull.signalAll();
                    } else {
                        single = deque.pollFirst();
                        count.decrementAndGet();
                        notFull.signal();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }

            if (bulk != null) {
                processBulk(bulk);
            } else if (single != null) {
                processSingle(single);
            }
            // else: shutting down / interrupted -> the outer while re-checks isRunning()
        }
    }

    /** Drain ONE item; a recoverable Throwable is surfaced to the error listener and the worker
     *  keeps running -- the original single-item contract. */
    private void processSingle(E item) {
        try {
            worker.process(item);
        } catch (Throwable t) {
            if (errorListener != null) {
                errorListener.onError(t, item);
            } else if (devLog != null) {
                devLog.error("queue-rig[{}]: worker error on {}", name, item, t);
            }
        }
    }

    /**
     * Drain a BULK through the list worker. The worker returns the items it did NOT process
     * (the signaler told it to stop) -- those are re-queued to the front so order is preserved.
     * If the worker THROWS, the full bulk is routed to the {@link IListErrorListener}, which returns
     * the items to continue with, or null = stop the bulk (remainder abandoned -- same bypass spirit
     * as the single-item path). A plain {@link IErrorListener} (no list variant) just stops + logs.
     */
    private void processBulk(ArrayList<E> bulk) {
        ArrayList<E> work = bulk;
        while (signaler.isRunning() && work != null && !work.isEmpty()) {
            List<E> remaining;
            try {
                remaining = listWorker.process(work, signaler);
            } catch (Throwable t) {
                List<E> cont = null;
                if (errorListener instanceof IListErrorListener) {
                    @SuppressWarnings("unchecked")
                    IListErrorListener<E> listListener = (IListErrorListener<E>) errorListener;
                    cont = listListener.onError(t, work);
                } else if (devLog != null) {
                    devLog.error("queue-rig[{}]: list worker error on bulk of {} items", name, work.size(), t);
                }
                if (cont == null || cont.isEmpty()) {
                    return;   // stop the bulk -- remainder abandoned
                }
                work = (cont instanceof ArrayList) ? (ArrayList<E>) cont : new ArrayList<>(cont);
                continue;     // re-run the worker on the continuation the listener handed back
            }
            // Clean return: the remainder is what the signaler made the worker skip -- re-queue it.
            if (remaining != null && !remaining.isEmpty()) {
                requeueFront(remaining);
            }
            return;
        }
    }

    /** Push unprocessed items back to the HEAD of the queue, preserving FIFO order, so a bulk the
     *  worker stopped early resumes from where it left off once processing is re-enabled. */
    private void requeueFront(List<E> items) {
        lock.lock();
        try {
            for (int i = items.size() - 1; i >= 0; i--) {
                deque.addFirst(items.get(i));
            }
            count.addAndGet(items.size());
            if (signaler.isProcessing()) {
                available.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Default listener: logs and, for a bulk fault, stops the bulk (returns null). */
    private class LoggingErrorListener implements IListErrorListener<E> {
        @Override
        public E onError(Throwable error, E element) {
            if (devLog != null) {
                devLog.error("queue-rig[{}]: worker error on {}", name, element, error);
            }
            return element;
        }
        @Override
        public List<E> onError(Throwable error, ArrayList<E> items) {
            if (devLog != null) {
                devLog.error("queue-rig[{}]: list worker error on bulk of {} items -- stopping bulk",
                        name, items.size(), error);
            }
            return null;   // stop the bulk
        }
    }
}

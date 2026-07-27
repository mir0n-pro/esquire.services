/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/01/2026 mir0n  created: a bounded worker pool with three thread models -- the x-rod receive/apply legs (and
 *                   any pooled component) ask for one via create(), and submit work through submit() which applies
 *                   the bound. Lifts the 3-way pool construction + the acquire/execute/release out of AXRod so the
 *                   worker-pool policy lives in ONE testable place, beside BoundedQueueRig (the single-worker feed).
 */
package pro.mir0n.utils.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A bounded worker pool with three thread models. Built via {@link #create}; work is handed in through
 * {@link #submit}, which applies the concurrency bound (acquire a permit, run on the pool, release on completion).
 *
 * <ul>
 *   <li>{@link Mode#PLATFORM} -- a fixed pool of {@code size} REUSED platform (OS / "metal") threads. The pre-VT
 *       default.</li>
 *   <li>{@link Mode#VIRTUAL} -- a fixed pool of {@code size} REUSED virtual threads (the SAME shape as PLATFORM,
 *       just cheaper metal: the workers ride a shared carrier pool and unmount off it while parked).</li>
 *   <li>{@link Mode#VIRTUAL_PER_TASK} -- a NEW virtual thread PER task (run-n-exit, the idiomatic VT firehose);
 *       {@code size} is the OPTIONAL concurrency cap: {@code >0} bounds the simultaneous workers, {@code 0} =
 *       uncapped.</li>
 * </ul>
 *
 * <p>{@code PLATFORM} / {@code VIRTUAL} are a FIXED pool, so they require {@code size >= 1} -- {@code size 0} is
 * rejected (a fixed pool cannot have zero workers). {@code VIRTUAL_PER_TASK} accepts {@code size 0} (uncapped).
 */
public final class WorkerPool {

    /** The worker thread model. {@link #of} parses the {@code mode} config token. */
    public enum Mode {
        PLATFORM, VIRTUAL, VIRTUAL_PER_TASK;

        /** Parse the config token ({@code platform} | {@code virtual} | {@code virtual-per-task}); a blank or
         *  unrecognized value falls back to {@link #PLATFORM} (the safe, pre-VT default). */
        public static Mode of(String token) {
            Mode ret = PLATFORM;
            if (token != null) {
                ret = switch (token.trim().toLowerCase()) {
                    case "virtual" -> VIRTUAL;
                    case "virtual-per-task" -> VIRTUAL_PER_TASK;
                    default -> PLATFORM;
                };
            }
            return ret;
        }
    }

    private final ExecutorService pool;
    private final Semaphore permits;   // null ONLY for VIRTUAL_PER_TASK uncapped (size 0)

    private WorkerPool(ExecutorService pool, Semaphore permits) {
        this.pool = pool;
        this.permits = permits;
    }

    /** Build a pool. {@code name} names the worker threads ({@code name-N}). PLATFORM / VIRTUAL build a FIXED pool
     *  of {@code size} reused workers (size {@code >= 1} required). VIRTUAL_PER_TASK builds a virtual-thread-per-task
     *  executor bounded by {@code size} (0 = uncapped). */
    public static WorkerPool create(String name, int size, Mode mode) {
        ExecutorService pool;
        Semaphore permits;
        if (mode == Mode.VIRTUAL_PER_TASK) {
            // RUN-N-EXIT: a virtual thread per task; the optional cap is the Semaphore (size 0 -> no bound).
            ThreadFactory vf = Thread.ofVirtual().name(name + "-", 0).factory();
            pool = Executors.newThreadPerTaskExecutor(vf);
            permits = size > 0 ? new Semaphore(size) : null;
        } else {
            // RUN-INFINITE: a FIXED pool of size REUSED long-lived workers (platform or virtual per the factory);
            // the Semaphore(size) caps concurrency + back-pressures. A fixed pool needs at least one worker.
            if (size < 1) {
                throw new IllegalArgumentException("WorkerPool[" + name + "]: mode " + name(mode)
                        + " needs size >= 1 (a fixed pool cannot have 0 workers); got size=" + size);
            }
            ThreadFactory tf = mode == Mode.VIRTUAL
                    ? Thread.ofVirtual().name(name + "-", 0).factory()
                    : Thread.ofPlatform().daemon(true).name(name + "-", 0).factory();
            pool = Executors.newFixedThreadPool(size, tf);
            permits = new Semaphore(size);
        }
        return new WorkerPool(pool, permits);
    }

    /** Submit bounded work: acquire a permit (unless uncapped), run {@code work} on the pool, release on completion.
     *  Returns {@code false} if interrupted while acquiring, or the pool rejected it (during shutdown) -- the caller
     *  can then log / drop. {@code work} owns its own error handling; a throw from it does not leak a permit. */
    public boolean submit(Runnable work) {
        boolean accepted = permits == null;   // uncapped -> proceed without a permit
        if (!accepted) {
            try {
                permits.acquire();
                accepted = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (accepted) {
            try {
                pool.execute(() -> {
                    try {
                        work.run();
                    } finally {
                        if (permits != null) {
                            permits.release();
                        }
                    }
                });
            } catch (RejectedExecutionException rex) {
                if (permits != null) {
                    permits.release();
                }
                accepted = false;   // the pool was shut down between the check and execute -- caller drops it
            }
        }
        return accepted;
    }

    /** Wind the pool down: stop taking work, await in-flight tasks up to {@code awaitSeconds}, then force. */
    public void shutdown(long awaitSeconds) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    /** The number of workers currently permitted to run (the cap); a diagnostic. {@code -1} = uncapped. */
    public int capacity() {
        return permits != null ? permits.availablePermits() : -1;
    }

    private static String name(Mode m) {
        return switch (m) {
            case PLATFORM -> "platform";
            case VIRTUAL -> "virtual";
            case VIRTUAL_PER_TASK -> "virtual-per-task";
        };
    }
}

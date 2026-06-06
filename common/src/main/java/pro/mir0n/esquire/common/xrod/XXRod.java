/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: the xx-Rod -- nothing but a bounded worker pool that is CALLED. (b) the
 *                   xy-Rod queue worker calls submit(); (c) the bus consumer calls submit(). It owns NO
 *                   queue of its own -- the xy-Rod BoundedQueueRig is the one and only queue. submit()
 *                   applies the event on a worker thread, with concurrency bounded to poolSize by a
 *                   Semaphore; when poolSize applies are in flight submit() blocks, backpressuring the
 *                   caller (the backlog stays upstream in the xy-Rod queue). Each event maps to its
 *                   RodRepository by kind. poolSize SHOULD be <= the log-DB connection-pool size. Pool
 *                   size + virtual-thread use are the two configurable parameters.
 */
package pro.mir0n.esquire.common.xrod;

import org.slf4j.Logger;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * The xx-Rod: a concurrency-bounded worker pool with NO queue. {@link #submit(RodEvent)} -- called by
 * the xy-Rod worker (b) or the bus consumer (c) -- acquires one of {@code poolSize} permits (blocking,
 * so it backpressures the caller when saturated) and runs the apply on a worker thread (platform daemon,
 * or virtual when {@code useVirtualThreads}). No second buffer: the upstream xy-Rod queue holds the
 * backlog.
 *
 * <p>A worker resolves the {@link RodRepository} for the event's kind via the {@link RodRepositoryRegistry}
 * and applies it. Resilience: a missing repository is logged and skipped; a {@link Throwable} from
 * {@code apply()} is caught and logged; exactly-once across redelivery is the {@code *_log}
 * {@code ON CONFLICT} / {@code MERGE}'s job, not the pool's.
 */
public final class XXRod {

    private final RodRepositoryRegistry registry;
    private final int     poolSize;
    private final boolean useVirtualThreads;
    private final Semaphore permits;

    private String name = "xx-rod";
    private Logger devLog;
    private ThreadFactory factory;
    private volatile boolean running = false;

    /**
     * @param registry          kind -> repository map (populated by the owning service at startup).
     * @param poolSize          max concurrent applies (>= 1). SHOULD be <= the log-DB connection-pool size.
     * @param useVirtualThreads true -> virtual-thread workers; false -> platform daemon threads.
     */
    public XXRod(RodRepositoryRegistry registry, int poolSize, boolean useVirtualThreads) {
        this.registry          = registry;
        this.poolSize          = Math.max(1, poolSize);
        this.useVirtualThreads = useVirtualThreads;
        this.permits           = new Semaphore(this.poolSize);
    }

    public synchronized void start(String name, Logger devLogger) {
        if (!running) {
            this.name    = name;
            this.devLog  = devLogger;
            this.factory = useVirtualThreads
                    ? Thread.ofVirtual().name(name + "-", 0).factory()
                    : Thread.ofPlatform().daemon(true).name(name + "-", 0).factory();
            this.running = true;
            if (devLog != null) {
                devLog.info("xx-rod[{}]: started (maxConcurrent={}, {})",
                        name, poolSize, useVirtualThreads ? "virtual" : "platform");
            }
        }
    }

    /** Apply the event on a worker, bounded to poolSize concurrent. Blocks the caller when saturated
     *  (backpressure) -- there is NO buffer here; the upstream xy-Rod queue holds the backlog. */
    public void submit(RodEvent event) {
        if (!running) {
            throw new IllegalStateException("xx-rod[" + name + "]: submit before start() / after shutdown()");
        }
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (acquired) {
            factory.newThread(() -> {
                try {
                    dispatch(event);
                } finally {
                    permits.release();
                }
            }).start();
        }
    }

    private void dispatch(RodEvent event) {
        try {
            IRodRepository repo = registry.repositoryFor(event.kind());
            if (repo == null) {
                if (devLog != null) {
                    devLog.warn("xx-rod[{}]: no IRodRepository for kind={} (entityId={}) -- skipped",
                            name, event.kind(), event.entityId());
                }
            } else {
                repo.apply(event);
            }
        } catch (Throwable t) {
            if (devLog != null) {
                devLog.error("xx-rod[{}]: apply failed for kind={}, entityId={}, subId={}: {}",
                        name, event.kind(), event.entityId(), event.subId(), t.getMessage(), t);
            }
        }
    }

    public synchronized void shutdown() {
        running = false;   // in-flight applies finish; new submits are rejected
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/17/2026 mir0n  created: the abstract x-Rod transceiver ENGINE -- the feed (a BoundedQueueRig transmit leg)
 *                   plus the Semaphore-bounded reused worker pool (the receive leg), the msg-audit, and their
 *                   lifecycle. A subclass decides what flows: XRod wires a transport publisher/consumer, XRodLogDb
 *                   loops the feed into an in-process *_log applier. Extracted so a sink reuses the engine by
 *                   EXTENDING it, not by composing an inner XRod.
 * 06/22/2026 mir0n  two-phase engine: startEngine -> buildEngine (CREATE the pool/feed idle, running stays false)
 *                   + runEngine (RUN the pool/feed); start() runs runEngine. setWorker(worker) added (the live
 *                   receive callback, set/reset after init); throws if the rod has NO receive pool. transmit()
 *                   throws if the rod has NO transmit feed and ignores a null event (the publisher-leg probe).
 *                   import Role/XRodParams from messaging.catalog and IXRod/RodEvent from messaging.
 * 06/23/2026 mir0n  alive session field: sendOut marks the alive send-attempt/sent/failed; receive intercepts a
 *                   session message (handled internally, not forwarded) + marks an app receive; runEngine seeds the
 *                   session; idle() drives session.tick()
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.utils.concurrent.BoundedQueueRig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The x-Rod transceiver engine, shared by every x-rod that has a feed and/or a worker pool. Two legs:
 * <ul>
 *   <li>TRANSMIT -- {@link #transmit} puts a pre-built event on the {@code feed} (a {@link BoundedQueueRig} of
 *       depth {@code feed-capacity}); its single worker {@code sendOut} logs the {@code TX} trace and hands the
 *       event to the {@code outbound}.</li>
 *   <li>RECEIVE -- {@link #receive} acquires a {@code Semaphore(pool-size)} permit, logs {@code RX}, and runs the
 *       pool worker on the reused pool (a fixed platform pool, or one virtual thread per task).</li>
 * </ul>
 * A subclass implements {@link #init} -- it decides the {@code outbound} and the pool {@code worker} (and may
 * raise {@link #poolSize}), then calls {@link #buildEngine} to CREATE the legs (idle). {@link #start} (this class)
 * then RUNs them via {@link #runEngine}. {@link #configure} reads the leg identity and the engine knobs;
 * subclasses override it to read their own params (calling {@code super} first).
 */
public abstract class AXRod implements IXRod {

    protected static final int DEFAULT_FEED_CAPACITY = 4096;
    protected static final int DEFAULT_POOL_SIZE     = 4;
    private static final long  SHUTDOWN_AWAIT_SECONDS = 5;   // how long shutdown() waits for in-flight tasks to drain

    // --- the leg config + identity (read in configure) ---
    protected XRodParams params;        // the leg config (transport + knobs); subclasses read their own sub-blocks
    protected BusIdentity identity;     // bus-id / slot-id / rod-id -- names the msg-audit logger
    protected int poolSize;             // worker-pool size (a subclass may raise it before buildEngine, e.g. async publish)

    // --- transmit leg (the feed) ---
    private Consumer<RodEvent> outbound;   // where the feed worker hands each event; null = no transmit leg
    private int feedCapacity;
    private BoundedQueueRig<RodEvent> feed;

    // --- the bounded worker pool (the receive leg; also runs the in-process writer / the async publisher) ---
    private Consumer<RodEvent> poolWorker; // the job the pool runs; null = no pool leg
    private boolean useVirtualThreads;
    private Semaphore permits;
    private ExecutorService pool;          // platform: a reused fixed pool of poolSize threads; virtual: one per task
    private volatile boolean running = false;
    // the receive worker (the listener's callback). The receive leg/listener is created at init() and is LIVE
    // from then; setWorker sets/RESETS this anytime -- a received event does nothing while it is null.
    protected volatile Consumer<RodEvent> worker;

    // --- the alive-protocol session (heartbeat / health), set by a transport rod at init; null = no session
    //     (an in-process / disabled rod has no bus leg to keep alive) ---
    protected AliveSession session;

    // --- message-audit (msgLog on both legs) ---
    private Logger msgLog;              // msg.<bus-id>.<slot-id>; null = no msg-audit

    private String name = "x-rod";
    private Logger devLog;

    /** Fail-fast helper for {@link #validate}: throw a clear, leg-identified error when a required param is absent. */
    protected static void require(boolean present, String whatMissing, XRodParams params) {
        if (!present) {
            throw new IllegalStateException("x-rod leg bus-id=" + (params != null ? params.busId() : null)
                    + " slot-id=" + (params != null ? params.slotId() : null) + ": missing required " + whatMissing);
        }
    }

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        // PREPARE the engine: identity + the feed/pool knobs. Subclasses override to ALSO read their own params.
        this.params            = params;
        this.identity          = params != null
                ? new BusIdentity(params.busId(), params.slotId(), params.rodId()) : null;
        this.feedCapacity      = params != null ? Math.max(1, params.feedCapacityOr(DEFAULT_FEED_CAPACITY)) : DEFAULT_FEED_CAPACITY;
        this.poolSize          = params != null ? Math.max(1, params.poolSizeOr(DEFAULT_POOL_SIZE)) : DEFAULT_POOL_SIZE;
        this.useVirtualThreads = params != null && params.virtualThreadsOrFalse();
    }

    @Override
    public void setWorker(Consumer<RodEvent> worker) {
        // role-support check: a rod with NO receive leg (init built no pool -- a single-node SERVER, a transmit-only
        // role) cannot take a worker. Fail fast at wire-up rather than silently never delivering. The OFF x-rod
        // (XRodDisabled) overrides this to a no-op, so an explicitly-disabled slot is exempt.
        if (pool == null) {
            throw new IllegalStateException("x-rod[" + name + "]: setWorker -- this rod has NO receive leg "
                    + "(its role does not consume); a consumer adapter is wired to a non-consuming bus");
        }
        this.worker = worker;   // set/RESET the live receive callback; while null, received events are dropped
    }

    /** The receive pool's job for a LISTENING x-rod: apply the CURRENT worker (set via {@link #setWorker}); do
     *  nothing while none is set. The receive leg/listener is created at init() and is live from then, but it
     *  idles until a worker is wired -- so {@code setWorker} can be set/reset at any point after init(). */
    protected void applyWorker(RodEvent event) {
        Consumer<RodEvent> w = worker;
        if (w != null) {
            w.accept(event);
        }
    }

    /** BUILD the engine (init phase, no traffic): CREATE the receive pool (idle -- {@code running} stays false)
     *  and ALLOCATE the transmit feed (not yet pumping). The pool is built first so an in-process feed can
     *  {@link #receive} as soon as the engine runs. {@code outbound} null = no transmit leg; {@code worker}
     *  null = no pool leg. {@link #runEngine} (the start phase) sets it all in motion. */
    protected synchronized void buildEngine(String name, Logger devLog, Consumer<RodEvent> outbound, Consumer<RodEvent> worker) {
        this.name       = name;
        this.devLog     = devLog;
        this.outbound   = outbound;
        this.poolWorker = worker;
        this.msgLog = identity != null && identity.busId() != null
                ? LoggerFactory.getLogger("msg." + identity.busId() + "." + identity.slotId())
                : null;

        if (poolWorker != null) {
            ThreadFactory tf = useVirtualThreads
                    ? Thread.ofVirtual().name(name + "-", 0).factory()
                    : Thread.ofPlatform().daemon(true).name(name + "-", 0).factory();
            // platform: a REUSED fixed pool of poolSize threads (no thread-per-message churn); virtual: one
            // virtual thread per task. Either way the Semaphore(poolSize) caps concurrency + back-pressures.
            this.pool = useVirtualThreads
                    ? Executors.newThreadPerTaskExecutor(tf)
                    : Executors.newFixedThreadPool(poolSize, tf);
            this.permits = new Semaphore(poolSize);
            // running stays FALSE until runEngine() -- a receive before start is dropped, not run.
        }
        if (outbound != null) {
            this.feed = new BoundedQueueRig<>(this::sendOut);
            feed.init(name, devLog, feedCapacity);   // allocate; do NOT pump until runEngine()
        }
        if (devLog != null) {
            devLog.info("x-rod[{}]: built (transmit={}, pool={}, poolSize={}, {})",
                    name, outbound != null, poolWorker != null, poolSize,
                    useVirtualThreads ? "virtual" : "platform");
        }
    }

    /** RUN the engine (start phase, facade-driven): mark the receive pool live and start the transmit feed
     *  pumping. Idempotent-safe via the synchronized lifecycle; a subclass ({@link XRod}) overrides {@link #start}
     *  to ALSO begin transport delivery after calling {@code super.start()}. */
    protected synchronized void runEngine() {
        if (pool != null) {
            this.running = true;
        }
        if (feed != null) {
            feed.start();
            feed.setProcessing(true);
        }
        if (session != null) {
            session.start();   // seed the timestamps + start the heartbeat cadence (producing legs)
        }
        if (devLog != null) {
            devLog.info("x-rod[{}]: started (transmit={}, pool={})", name, feed != null, poolWorker != null);
        }
    }

    /** RUN the x-rod: start the engine threads. A transport-backed x-rod ({@link XRod}) overrides this to begin
     *  transport delivery too (calling {@code super.start()} first). */
    @Override
    public synchronized void start() {
        runEngine();
    }

    /** Periodic maintenance pass (fired by the MessagingBus idle ticker): drive the alive-protocol heartbeat
     *  cadence step. No-op when this rod runs no session (in-process / disabled). The hook to add any future
     *  per-rod housekeeping. */
    @Override
    public void idle() {
        if (session != null) {
            session.tick();
        }
    }

    /** Wind the engine down: reject new receives, stop the feed, then drain the pool (await in-flight applies /
     *  async publishes) so a subclass can safely close the transport AFTER -- it overrides to close its transport /
     *  datasource by calling {@code super.shutdown()} first. */
    @Override
    public synchronized void shutdown() {
        running = false;
        if (feed != null) {
            feed.shutdown();
        }
        if (pool != null) {
            pool.shutdown();   // stop taking new work
            try {
                // let in-flight tasks (a receive apply, or a pooled-async publish) DRAIN before the caller closes
                // the transport -- otherwise a send could hit an already-closed publisher.
                if (!pool.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    if (devLog != null) {
                        devLog.warn("x-rod[{}]: receive pool did not drain within {}s -- forcing", name, SHUTDOWN_AWAIT_SECONDS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }
        }
    }

    // --------------------------------------------------------------------- transmit leg

    @Override
    public void transmit(RodEvent event) {
        // role-support check: a rod with NO transmit leg (init built no feed -- a single-node CLIENT, a
        // receive-only role) cannot transmit. A publisher adapter PROBES this at wire-up with transmit(null):
        // no transmit leg -> throw; the leg exists -> the null is ignored. The OFF x-rod (XRodDisabled) overrides
        // this to a no-op, so an explicitly-disabled slot is exempt.
        if (feed == null) {
            throw new IllegalStateException("x-rod[" + name + "]: transmit -- this rod has NO transmit leg "
                    + "(its role does not produce); a publisher adapter is wired to a non-producing bus");
        }
        if (event != null) {
            feed.put(event);   // the feed worker (sendOut) logs the TX msg-audit, then hands to the outbound
        }
        // event == null is the publisher-leg probe -- the leg exists, so just ignore it.
    }

    /** Transmit-leg send point (the feed worker): reset the alive cadence, log the msg-audit, hand the event to
     *  the outbound, and mark the producer leg's liveness (or its failure) for the alive protocol. */
    private void sendOut(RodEvent e) {
        if (session != null) {
            session.markSendAttempt();
        }
        logMsg("TX", e);
        try {
            outbound.accept(e);
            if (session != null) {
                session.markSent();
            }
        } catch (RuntimeException ex) {
            if (session != null) {
                session.markSendFailed();
            }
            throw ex;
        }
    }

    /** Message-audit on a leg: {@code msg.<bus-id>.<slot-id>} (the msg appender). null logger = disabled. */
    private void logMsg(String dir, RodEvent e) {
        if (msgLog != null && msgLog.isInfoEnabled()) {
            msgLog.info("{} | {} | {} | {} | {} | {} | {} | {}",
                    dir, e.msgType(), e.opCode(), e.kind(), e.entityId(), e.subId(), e.rodId(), e.requestId());
        }
    }

    // --------------------------------------------------------------------- receive leg

    @Override
    public void receive(RodEvent event) {
        if (pool == null) {
            // no receive leg wired at all (a producer-only x-rod) -- a genuine misuse.
            throw new IllegalStateException("x-rod[" + name + "]: receive but no receive leg is wired");
        } else if (!running) {
            // before start (pool built, not yet running) or during/after shutdown: a delivery with no live pool.
            // Drop it -- do NOT throw (the transport consumer is paused until start, so this is the teardown edge).
            if (devLog != null) {
                devLog.info("x-rod[{}]: receive while not running (before start / during shutdown) -- dropping (kind={}, entityId={})",
                        name, event.kind(), event.entityId());
            }
        } else if (session != null && event.isSession()) {
            // a SESSION (alive) message -- handled internally by the session layer, NEVER forwarded to the app worker.
            session.receivedSession(event);
        } else {
            if (session != null) {
                session.markReceived();   // an application receive advances the consumer leg's liveness
            }
            boolean acquired = false;
            try {
                permits.acquire();
                acquired = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (acquired) {
                logMsg("RX", event);
                try {
                    pool.execute(() -> {
                        try {
                            poolWorker.accept(event);
                        } catch (Throwable t) {
                            if (devLog != null) {
                                devLog.error("x-rod[{}]: receive worker failed for kind={}, entityId={}, subId={}: {}",
                                        name, event.kind(), event.entityId(), event.subId(), t.getMessage(), t);
                            }
                        } finally {
                            permits.release();
                        }
                    });
                } catch (RejectedExecutionException rex) {
                    // the pool was shut down between the running-check and execute (the narrow shutdown race) --
                    // drop + free the permit, rather than leak it or throw during teardown.
                    permits.release();
                    if (devLog != null) {
                        devLog.info("x-rod[{}]: receive rejected during shutdown -- dropping (kind={}, entityId={})",
                                name, event.kind(), event.entityId());
                    }
                }
            }
        }
    }
}

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
 * 06/27/2026 mir0n  name / devLog made protected (XRodRR reads them); rodId() override returns identity.rodId()
 *                   (null when the rod has no identity)
 * 06/30/2026 mir0n  the feed (tx) worker OWNS the send: send() stamps the ApplMsgID once, runs beforeSend, then
 *                   sendInProcess (a non-transport outbound) or sendOut (encode once + the dispatch loop). The
 *                   alive session field is replaced by a session-sublayer list (installSessionStack via
 *                   SessionSublayerFactory); the worker fans the hooks out -- beforeSend / onSendSuccess /
 *                   onSendError(ev, enc, Throwable) / onReceiveSessn / sessionHealth -- and idle() ticks them. The
 *                   raw msgLog is replaced by the MsgAudit module (TX / TX-ERR with the cause / RX); publisher /
 *                   outbound / feed / sendSublayers made protected
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.xrod.RodPublisher;
import pro.mir0n.esquire.messaging.xrod.impl.sublayer.SessionSublayerFactory;
import pro.mir0n.utils.concurrent.BoundedQueueRig;

import java.util.List;
import java.util.UUID;
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
 *       depth {@code feed-capacity}); its single worker ({@code send}) OWNS the send -- encode once + dispatch --
 *       driving the session-sublayer hooks and logging the {@code TX} / {@code TX-ERR} msg-audit at the outcome.</li>
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
    protected Consumer<RodEvent> outbound;      // the raw outbound: the transport publisher, this::receive (pooled async), or an in-process sink
    protected RodPublisher publisher;           // the transport leg when 'outbound' is a RodPublisher (encode-once + throwing dispatch); null otherwise
    protected List<ISessionSublayer> sendSublayers = List.of();   // the session sublayers (built by the factory); ticked on idle()
    private int feedCapacity;
    protected BoundedQueueRig<RodEvent> feed;

    // --- the bounded worker pool (the receive leg; also runs the in-process writer / the async publisher) ---
    private Consumer<RodEvent> poolWorker; // the job the pool runs; null = no pool leg
    private boolean useVirtualThreads;
    private Semaphore permits;
    private ExecutorService pool;          // platform: a reused fixed pool of poolSize threads; virtual: one per task
    private volatile boolean running = false;
    // the receive worker (the listener's callback). The receive leg/listener is created at init() and is LIVE
    // from then; setWorker sets/RESETS this anytime -- a received event does nothing while it is null.
    protected volatile Consumer<RodEvent> worker;

    // --- the x-rod-level in/out (TX/RX) message-audit (the msg.<bus-id>.<slot-id> channel) -- the MsgAudit module;
    //     a no-op until buildEngine installs one built from the leg identity ---
    private MsgAudit msgAudit = new MsgAudit(null);

    protected String name = "x-rod";
    protected Logger devLog;

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

    @Override
    public String rodId() {
        return identity != null ? identity.rodId() : null;
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
        this.poolWorker = worker;
        this.msgAudit = new MsgAudit(identity);

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
        // the transmit-leg send materials the feed worker drives directly (no send chain): the raw outbound, and --
        // when it is a transport leg -- the RodPublisher whose encode-once + throwing dispatch the send loop runs.
        this.outbound  = outbound;
        this.publisher = (outbound instanceof RodPublisher rp) ? rp : null;
        if (outbound != null) {
            this.feed = new BoundedQueueRig<>(this::send);
            feed.init(name, devLog, feedCapacity);   // allocate; do NOT pump until runEngine()
        }
        if (devLog != null) {
            devLog.info("x-rod[{}]: built (transmit={}, pool={}, poolSize={}, {})",
                    name, outbound != null, poolWorker != null, poolSize,
                    useVirtualThreads ? "virtual" : "platform");
        }
    }

    /** Install the producer session-sublayer stack around the transmit leg -- the producer-side EXTENSION POINT.
     *  The {@link SessionSublayerFactory} BUILDS the stack per leg config: the alive keepalive ({@code
     *  keepAliveEnabled} = a producing leg; broadcast here, R&R in {@link XRodRR} which overrides this with its
     *  role) and the send-retry policy. A transport rod calls this AFTER {@link #buildEngine} (the feed must exist
     *  -- the alive heartbeat is PUT on it). The feed worker then calls the stack's hooks directly; no sublayer is
     *  in the send path. Send-retry needs a transport-backed leg (a {@link RodPublisher} outbound, whose
     *  encode-once + throwing dispatch the send loop drives); an in-process / non-transport outbound gets no
     *  send-retry. */
    protected void installSessionStack(boolean keepAliveEnabled) {
        this.sendSublayers = SessionSublayerFactory.build(params, publisher,
                feed, identity, devLog, keepAliveEnabled, null);
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
        for (ISessionSublayer sublayer : sendSublayers) {
            sublayer.start();
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

    /** Periodic maintenance pass (fired by the MessagingBus idle ticker, ~1s): drive the session sublayers that
     *  run off the tick rather than a thread of their own -- the alive-protocol heartbeat cadence and the
     *  send-retry re-send of a held event. No-op for the sublayers this rod does not run. The hook for any future
     *  per-rod housekeeping. */
    @Override
    public void idle() {
        for (ISessionSublayer sublayer : sendSublayers) {
            // the session sublayers run their cadence off this tick: the alive session emits a heartbeat when the
            // leg is idle; send-retry re-sends a held event when its backoff elapses.
            sublayer.tick();
        }
    }

    /** Wind the engine down: reject new receives, stop the feed, then drain the pool (await in-flight applies /
     *  async publishes) so a subclass can safely close the transport AFTER -- it overrides to close its transport /
     *  datasource by calling {@code super.shutdown()} first. */
    @Override
    public synchronized void shutdown() {
        running = false;
        for (ISessionSublayer sublayer : sendSublayers) {
            sublayer.shutdown();
        }
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

    /** Transmit-leg send point (the feed/tx worker -- the ONLY sender): stamp the dedup id, then OWN the send --
     *  encode once + dispatch -- calling the session-sublayer hooks as the message passes (the alive marks, the
     *  send-retry decision). The TX msg-audit is logged at the OUTCOME ({@link #onSendSuccess} sent / {@link
     *  #onSendError} failed), not here -- so the trace shows the send result. The sublayers never send; they react. */
    private void send(RodEvent e) {
        // stamp the stable wire dedup id ONCE (ApplMsgID): a held event's resends reuse this SAME id (so a consumer
        // can dedup), instead of the transport minting a fresh one per physical send. The send-retry hold keeps the
        // stamped event, so every re-send carries it; an event that already carries one (a decoded relay) keeps it.
        RodEvent ev = e.applMsgId() != null ? e : e.withApplMsgId(UUID.randomUUID().toString());
        beforeSend(ev);
        if (publisher == null) {
            sendInProcess(ev);           // a non-transport outbound (in-process sink, or the pooled-async this::receive)
        } else {
            sendOut(ev);           // an application event with send-retry on
        }
    }

    /** Encode the event to the transport's concrete send unit ONCE; null on an (unexpected) encode failure (logged
     *  -- there is nothing to dispatch). */
    private Object encode(RodEvent ev) {
        Object ret;
        try {
            ret = publisher.encode(ev);
        } catch (Exception ex) {
            ret = null;
            if (devLog != null && devLog.isWarnEnabled()) {
                devLog.warn("x-rod[{}]: encode failed -- dropping -- kind={}, entityId={}: {}",
                        name, ev.kind(), ev.entityId(), ex.toString());
            }
        }
        return ret;
    }
    /** The transport send loop (the feed/tx worker): encode once, then dispatch the SAME unit, driving the
     *  session-sublayer hooks at the outcome -- {@link #onSendSuccess} on a landing, {@link #onSendError} on a
     *  throw. An application event is re-dispatched while send-retry HOLDS the worker across the backoff (the
     *  back-pressure) and hands back the unit; a SESSION event is one-shot (send-retry skips it, so onSendError
     *  returns null and the loop ends). The loop also ends on the cap (drop) or a shutdown interrupt. */
    private void sendOut(RodEvent ev) {
        Object enc = encode(ev);
        if (enc != null) {
            boolean done = false;
            while (!done) {
                try {
                    publisher.dispatch(enc);
                    onSendSuccess(ev);
                    done = true;
                } catch (Throwable ex) {
                    Object next = onSendError(ev, enc, ex);
                    if (next == null) {
                        done = true;   // dropped (cap) or interrupted (shutdown)
                    } else {
                        enc = next;
                    }
                }
            }
        }
    }

    /** A non-transport outbound (an in-process sink, or the pooled-async {@code this::receive} enqueue): hand the
     *  event straight to it and mark the producer leg. No encode / dispatch / retry -- there is no transport unit. */
    private void sendInProcess(RodEvent ev) {
        try {
            outbound.accept(ev);
            onSendSuccess(ev);
        } catch (RuntimeException ex) {
            onSendError(ev, null, ex);
            throw ex;
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
        } else if (event.isSession()) {
            // a SESSION (alive) message -- handled internally by the session layer, NEVER forwarded to the app worker.
            onReceiveSessn(event);
            msgAudit.log("RX", event);
        } else {
            boolean acquired = false;
            try {
                permits.acquire();
                acquired = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (acquired) {
                msgAudit.log("RX", event);
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

    // --------------------------------------------------------------------- session-sublayer hooks (from the tx worker)

    /** Before an attempt: reset each sublayer's per-send state (the alive cadence gate). */
    protected void beforeSend(RodEvent ev) {
        for (ISessionSublayer sublayer : sendSublayers) {
            sublayer.beforeSend(ev);
        }
    }

    /** A send LANDED: log the TX msg-audit (one line per delivered message), then notify each sublayer (alive marks
     *  the producer leg sent, send-retry clears any hold). */
    protected void onSendSuccess(RodEvent ev) {
        msgAudit.log("TX", ev);
        for (ISessionSublayer sublayer : sendSublayers) {
            sublayer.onSendSuccess(ev);
        }
    }

    /** A send THREW: log the TX-ERR msg-audit (with the cause), then let EVERY sublayer react (alive marks failed,
     *  send-retry decides) -- the non-null return is the encoded unit to re-dispatch (send-retry's); null = stop. */
    protected Object onSendError(RodEvent ev, Object msg, Throwable error) {
        msgAudit.err(ev, error);
        Object result = null;
        for (ISessionSublayer sublayer : sendSublayers) {
            Object r = sublayer.onSendError(ev, msg, error);
            if (r != null) {
                result = r;
            }
        }
        return result;
    }

    /** An arriving SESSION (alive-protocol) message: run each sublayer's receive-side handler (an R&R SERVER echo). */
    protected void onReceiveSessn(RodEvent ev) {
        for (ISessionSublayer sublayer : sendSublayers) {
            sublayer.onReceiveSessn(ev);
        }
    }

    /** This leg's session health: the worst across the sublayers (the alive metric; the rest read UNKNOWN-benign). */
    protected TransportHealth sessionHealth() {
        TransportHealth ret = TransportHealth.UP;
        for (ISessionSublayer sublayer : sendSublayers) {
            ret = TransportHealth.worst(ret, sublayer.health());
        }
        return ret;
    }

}

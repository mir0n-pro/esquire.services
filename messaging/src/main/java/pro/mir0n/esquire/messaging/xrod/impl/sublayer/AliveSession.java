/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/23/2026 mir0n  created: the x-rod ALIVE-PROTOCOL session collaborator -- the FIX-style HeartBeat / TestRequest
 *                   keepalive lifted out of the x-rod so the rod classes stay compact. Owns the leg timestamps
 *                   (producer / consumer + last-send-attempt), the timestamp-age health (producer leg only -- the
 *                   Quick&Dirty first run ignores the consumer leg), and the internal dispatch of an arriving
 *                   session message. The rod supplies the keep-alive factory, the transmit, and the
 *                   session-message handler. NO own thread: the heartbeat cadence step ({@link #tick}) is driven by
 *                   the rod's idle() maintenance hook, fired by ONE MessagingBus-level idle ticker per service.
 * 06/30/2026 mir0n  moved to the .sublayer sub-package; now an ISessionSublayer -- an event-driven session sublayer
 *                   the feed (tx) worker drives (beforeSend / onSendSuccess / onSendError(ev, msg, Throwable) / tick
 *                   / health); the keep-alive is PUT on the feed (IQueueRig) instead of a transmit callback; ctor
 *                   takes the feed + BusIdentity
 * 07/23/2026 mir0n  v1.2.11 -- comment: the tick() heartbeat uses a BLOCKING put (not tryPut) -- it fires only when
 *                   the leg is idle, so the feed is drained/empty and put() returns at once; never drops a heartbeat
 */
package pro.mir0n.esquire.messaging.xrod.impl.sublayer;

import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.xrod.impl.ISessionSublayer;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The x-rod session layer: a FIX-style alive protocol on a HeartBeat / TestRequest pair, handled internally (never
 * bypassed to the application worker). It gives the x-rod a transport-agnostic health signal by TIMESTAMP AGE. An
 * event-driven {@link ISessionSublayer}: the feed (tx) worker marks its producer-leg liveness through the send
 * hooks, and the session sends nothing of its own except a keep-alive it PUTS on the feed.
 * <ul>
 *   <li>{@code producerTs} -- the producer leg's liveness (last successful send); {@code lastSendAttempt} --
 *       reset before every send, the heartbeat cadence GATE (real traffic suppresses heartbeats, which only fill
 *       gaps).</li>
 *   <li>{@link #tick} -- one heartbeat cadence step, driven by the rod's {@code idle()} maintenance hook (which
 *       a single MessagingBus-level idle ticker fires on every rod). NO thread of its own. When the producing leg
 *       ({@code keepAliveEnabled}) has been idle for {@code heartbeat-interval}, it PUTS a {@link #keepAliveEvent}
 *       on the feed (an unsolicited HeartBeat here; a TestRequest for an R&R CLIENT -- see {@link AliveSessionRR})
 *       and the feed worker sends it like any event. The global tick is just the polling resolution; the per-leg
 *       interval governs the rate.</li>
 *   <li>Health: {@code now - producerTs > alive-timeout} -> DOWN (else UP); a send failure flips DOWN at once when
 *       {@code alive-fail-fast} is on ({@link #onSendError}). The QUICK&DIRTY first run reads the PRODUCER leg only
 *       -- a non-producing ({@code keepAliveEnabled} false) leg is not measured and reads UP.</li>
 *   <li>An arriving session message runs {@link #onReceiveSessn} -- a no-op here; {@link AliveSessionRR} overrides
 *       it so an R&R SERVER echoes a TestRequest back as a HeartBeat.</li>
 * </ul>
 */
public class AliveSession implements ISessionSublayer {

    protected final long heartbeatIntervalMs;
    protected final long aliveTimeoutMs;
    protected final boolean failFastOnSendError;

    protected final boolean keepAliveEnabled;
    protected final IQueueRig<RodEvent> feed;         // the transmit feed -- a keep-alive is PUT here, the feed worker sends it
    protected final BusIdentity identity;             // the leg identity (bus-id / slot-id / rod-id) -- for unification
    protected final Logger devLog;
    protected final LongSupplier clock;               // wall clock in production; a test seam injects a controllable one

    protected volatile long producerTs;       // last successful send (the producer leg's liveness)
    protected volatile long lastSendAttempt;  // reset before every send -- the heartbeat cadence gate
    protected volatile boolean sendBroken;    // a send failed and fail-fast is on -> DOWN until the next success

    public AliveSession(IQueueRig<RodEvent> feed, long heartbeatIntervalMs, long aliveTimeoutMs, boolean failFastOnSendError,
                        boolean keepAliveEnabled, BusIdentity identity, Logger devLog) {
        this(feed, heartbeatIntervalMs, aliveTimeoutMs, failFastOnSendError, keepAliveEnabled, identity, devLog,
                System::currentTimeMillis);
    }

    /** Package-private test seam: an injectable clock makes the timestamp-age health + the cadence gate
     *  deterministic (a unit test advances logical time and calls {@link #tick} without sleeping). */
    AliveSession(IQueueRig<RodEvent> feed, long heartbeatIntervalMs, long aliveTimeoutMs, boolean failFastOnSendError,
                 boolean keepAliveEnabled, BusIdentity identity, Logger devLog,
                 LongSupplier clock) {
        this.feed                = feed;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.aliveTimeoutMs      = aliveTimeoutMs;
        this.failFastOnSendError = failFastOnSendError;
        this.keepAliveEnabled    = keepAliveEnabled;
        this.identity            = identity;
        this.devLog              = devLog;
        this.clock               = clock;
    }

    // --------------------------------------------------------------------- health

    /** The leg health by timestamp age -- the PRODUCER leg only (the Q&D first run ignores the consumer leg). A
     *  non-producing (receive-only) leg is not measured and reads UP. A producing leg: DOWN on a fail-fast send
     *  error, else UP while a successful send landed within {@code alive-timeout}, else DOWN. */
    public TransportHealth health() {
        TransportHealth ret = TransportHealth.UP;
        if (keepAliveEnabled) {
            if (sendBroken) {
                ret = TransportHealth.DOWN;
            } else if (now() - producerTs <= aliveTimeoutMs) {
                ret = TransportHealth.UP;
            } else {
                ret = TransportHealth.DOWN;
            }
        }
        return ret;
    }

    // --------------------------------------------------------------------- lifecycle + cadence

    /** Seed the timestamps to now (a freshly started leg reads UP until {@code alive-timeout} with no activity).
     *  No thread is started here -- the rod's idle() hook drives {@link #tick}. */
    public void start() {
        long t = now();
        producerTs      = t;
        lastSendAttempt = t;
        sendBroken      = false;
    }

    protected String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /** The keep-alive this leg emits when idle: an unsolicited HeartBeat. {@link AliveSessionRR} overrides it
     *  -- a TestRequest for an R&R CLIENT, and nothing at all for a SERVER. A null means no keep-alive. */
    protected RodEvent keepAliveEvent() {
        return RodEvent.heartbeat(newCorrelationId(), null, null);
    }

    /** One heartbeat cadence step (called from the rod's idle() maintenance hook). If this is a producing leg
     *  that has been idle for a full {@code heartbeat-interval}, PUT a keep-alive on the feed -- the feed worker
     *  sends it on the normal path, so {@link #beforeSend} / {@link #onSendSuccess} run there. A non-producing leg
     *  is a no-op. */
    public void tick() {
        if (keepAliveEnabled) {
            try {
                if (now() - lastSendAttempt >= heartbeatIntervalMs) {
                    RodEvent ka = keepAliveEvent();
                    if (ka != null) {
                        // A BLOCKING put is correct here (not tryPut). The heartbeat fires ONLY when this leg has
                        // been idle for heartbeat-interval, and beforeSend() resets lastSendAttempt on EVERY send --
                        // including every send-retry RE-DISPATCH (the worker re-runs the send path while holding a
                        // unit). So while the feed is backing up (broker down, worker re-dispatching) the gate stays
                        // fresh and NO heartbeat fires; when a heartbeat DOES fire the leg is genuinely idle, the
                        // worker has drained the feed, and it is EMPTY -- so put() returns at once. Feed-full and
                        // heartbeat-fires are mutually exclusive; there is nothing to guard against here.
                        feed.put(ka);
                    }
                }
            } catch (Throwable ex) {
                // a maintenance step must NEVER escape: an uncaught Throwable from a scheduleAtFixedRate task
                // SILENTLY cancels all further runs -- one rod must not be able to kill the service's idle loop.
                if (devLog != null) {
                    devLog.error("alive[{}]: heartbeat tick failed: {}", identity.rodId(), ex.toString());
                }
            }
        }
    }

    // --------------------------------------------------------------------- send-side hooks (from the feed worker)

    /** Reset the cadence gate before a send attempt (any message type) -- real traffic defers the next heartbeat. */
    @Override
    public void beforeSend(RodEvent ev) {
        lastSendAttempt = now();
    }

    /** A send SUCCEEDED -> advance the producer leg's liveness and clear any fail-fast DOWN. */
    @Override
    public void onSendSuccess(RodEvent ev) {
        producerTs = now();
        sendBroken = false;
    }

    /** A send FAILED -> flip DOWN at once when fail-fast is on; otherwise the producer timestamp simply stops
     *  advancing and the leg ages out to DOWN at {@code alive-timeout}. The alive session has no retry opinion, so
     *  it returns null (it never decides the re-dispatch unit). */
    @Override
    public Object onSendError(RodEvent ev, Object msg, Throwable error) {
        if (failFastOnSendError) {
            sendBroken = true;
        }
        return null;
    }

    private long now() {
        return clock.getAsLong();
    }
}

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
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The x-rod session layer: a FIX-style alive protocol on a HeartBeat / TestRequest pair, running ABOVE the
 * transport and handled internally (never bypassed to the application worker). It gives the x-rod a
 * transport-agnostic health signal by TIMESTAMP AGE.
 * <ul>
 *   <li>{@code legTimestamp} -- the producer leg (last successful send) and the consumer leg (last receive);
 *       {@code lastSendAttempt} -- reset before every send, the heartbeat cadence GATE (real traffic suppresses
 *       heartbeats, which only fill gaps).</li>
 *   <li>{@link #tick} -- one heartbeat cadence step, driven by the rod's {@code idle()} maintenance hook (which
 *       a single MessagingBus-level idle ticker fires on every rod). NO thread of its own. When the producing leg
 *       has been idle for {@code heartbeat-interval}, it emits a keep-alive (the rod's {@link #keepAlive}
 *       supplier: an unsolicited HeartBeat, or a TestRequest for an R&R CLIENT). The global tick is just the
 *       polling resolution; the per-leg interval governs the actual rate.</li>
 *   <li>Health: {@code now - producerTimestamp > alive-timeout} -> DOWN (else UP); a send exception flips DOWN
 *       at once when {@code alive-fail-fast} is on. The QUICK&DIRTY first run reads the PRODUCER leg only -- a
 *       non-producing (receive-only) leg is not measured and reads UP (the consumer leg is ignored).</li>
 *   <li>An arriving session message updates the consumer timestamp and runs the rod's internal handler
 *       ({@link #onSessionMsg} -- an R&R SERVER echoes a TestRequest back as a HeartBeat).</li>
 * </ul>
 */
public final class AliveSession {

    private final long heartbeatIntervalMs;
    private final long aliveTimeoutMs;
    private final boolean failFastOnSendError;

    private final Supplier<RodEvent> keepAlive;     // the idle keep-alive to emit; null = a non-producing leg (no cadence)
    private final Consumer<RodEvent> emit;          // the rod's transmit (the keep-alive rides the normal send path)
    private final Consumer<RodEvent> onSessionMsg;  // the rod's internal handler for an arriving session msg; may be null
    private final String name;
    private final Logger devLog;
    private final LongSupplier clock;               // wall clock in production; a test seam injects a controllable one

    private volatile long producerTs;       // last successful send (the producer leg's liveness)
    private volatile long consumerTs;        // last receive (the consumer leg's liveness -- tracked, ignored in the Q&D health)
    private volatile long lastSendAttempt;  // reset before every send -- the heartbeat cadence gate
    private volatile boolean sendBroken;    // a send failed and fail-fast is on -> DOWN until the next success

    public AliveSession(long heartbeatIntervalMs, long aliveTimeoutMs, boolean failFastOnSendError,
                        Supplier<RodEvent> keepAlive, Consumer<RodEvent> emit, Consumer<RodEvent> onSessionMsg,
                        String name, Logger devLog) {
        this(heartbeatIntervalMs, aliveTimeoutMs, failFastOnSendError, keepAlive, emit, onSessionMsg, name, devLog,
                System::currentTimeMillis);
    }

    /** Package-private test seam: an injectable clock makes the timestamp-age health + the cadence gate
     *  deterministic (a unit test advances logical time and calls {@link #tick} without sleeping). */
    AliveSession(long heartbeatIntervalMs, long aliveTimeoutMs, boolean failFastOnSendError,
                 Supplier<RodEvent> keepAlive, Consumer<RodEvent> emit, Consumer<RodEvent> onSessionMsg,
                 String name, Logger devLog, LongSupplier clock) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.aliveTimeoutMs      = aliveTimeoutMs;
        this.failFastOnSendError = failFastOnSendError;
        this.keepAlive           = keepAlive;
        this.emit                = emit;
        this.onSessionMsg        = onSessionMsg;
        this.name                = name;
        this.devLog              = devLog;
        this.clock               = clock;
    }

    // --------------------------------------------------------------------- send-side hooks (from AXRod.sendOut)

    /** Reset the cadence gate before a send attempt (any message type) -- real traffic defers the next heartbeat. */
    public void markSendAttempt() {
        lastSendAttempt = now();
    }

    /** A send SUCCEEDED -> advance the producer leg's liveness and clear any fail-fast DOWN. */
    public void markSent() {
        producerTs = now();
        sendBroken = false;
    }

    /** A send FAILED -> flip DOWN at once when fail-fast is on; otherwise the producer timestamp simply stops
     *  advancing and the leg ages out to DOWN at {@code alive-timeout}. */
    public void markSendFailed() {
        if (failFastOnSendError) {
            sendBroken = true;
        }
    }

    // --------------------------------------------------------------------- receive-side hooks (from AXRod.receive)

    /** An application message arrived -> advance the consumer leg's liveness (tracked; ignored in the Q&D health). */
    public void markReceived() {
        consumerTs = now();
    }

    /** A SESSION message arrived -> advance the consumer leg and run the rod's internal handler (an R&R SERVER
     *  echoes a TestRequest back as a HeartBeat). The message is NOT forwarded to the application worker. */
    public void receivedSession(RodEvent e) {
        consumerTs = now();
        if (onSessionMsg != null) {
            onSessionMsg.accept(e);
        }
    }

    // --------------------------------------------------------------------- health

    /** The leg health by timestamp age -- the PRODUCER leg only (the Q&D first run ignores the consumer leg). A
     *  non-producing (receive-only) leg is not measured and reads UP. A producing leg: DOWN on a fail-fast send
     *  error, else UP while a successful send landed within {@code alive-timeout}, else DOWN. */
    public TransportHealth health() {
        TransportHealth ret;
        if (keepAlive == null) {
            ret = TransportHealth.UP;
        } else if (sendBroken) {
            ret = TransportHealth.DOWN;
        } else if (now() - producerTs <= aliveTimeoutMs) {
            ret = TransportHealth.UP;
        } else {
            ret = TransportHealth.DOWN;
        }
        return ret;
    }

    // --------------------------------------------------------------------- lifecycle + cadence

    /** Seed the timestamps to now (a freshly started leg reads UP until {@code alive-timeout} with no activity).
     *  No thread is started here -- the rod's idle() hook drives {@link #tick}. */
    public void start() {
        long t = now();
        producerTs      = t;
        consumerTs      = t;
        lastSendAttempt = t;
        sendBroken      = false;
    }

    /** One heartbeat cadence step (called from the rod's idle() maintenance hook). If this is a producing leg
     *  that has been idle for a full {@code heartbeat-interval}, emit a keep-alive. The emit rides the rod's
     *  normal send path, so {@link #markSendAttempt} / {@link #markSent} run there. A non-producing leg is a
     *  no-op. */
    public void tick() {
        try {
            if (keepAlive != null && now() - lastSendAttempt >= heartbeatIntervalMs) {
                RodEvent ka = keepAlive.get();
                if (ka != null) {
                    emit.accept(ka);
                }
            }
        } catch (Throwable ex) {
            // a maintenance step must NEVER escape: an uncaught Throwable from a scheduleAtFixedRate task
            // SILENTLY cancels all further runs -- one rod must not be able to kill the service's idle loop.
            if (devLog != null) {
                devLog.error("alive[{}]: heartbeat tick failed: {}", name, ex.toString());
            }
        }
    }

    private long now() {
        return clock.getAsLong();
    }
}

/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: the producer SEND-RETRY sublayer (an ISessionSublayer) -- the one messaging-path
 *                   resilience pattern of v1.2.10, event-driven with NO send of its own. The feed (tx) worker owns
 *                   the send; on a dispatch throw it calls onSendError, which marks the holds map (keyed by the
 *                   stable ApplMsgID) and reacts: past the optional max-attempts cap DROP (return null), else HOLD
 *                   the worker thread over the backoff ladder (a monitor wait released by tick() -- the ~1s idle
 *                   hook, no own thread) and return the SAME encoded unit for the worker to re-dispatch. Holding
 *                   the single worker is the back-pressure; onSendSuccess clears the hold. A SESSION (heartbeat)
 *                   event is never retried. The retry trail logs to the msg-audit (MsgAudit). health() reads DOWN
 *                   while any request is held -- the broker-down signal on a send-retry-only leg (no alive protocol).
 */
package pro.mir0n.esquire.messaging.xrod.impl.sublayer;

import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.xrod.impl.ISessionSublayer;
import pro.mir0n.esquire.messaging.xrod.impl.MsgAudit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The producer send-retry sublayer (a {@link ISessionSublayer}, sibling of {@link AliveSession}): an event-driven
 * handler with NO send of its own. The feed (tx) worker owns the send -- it encodes ONCE and dispatches; on a
 * dispatch throw it calls {@link #onSendError}, on success {@link #onSendSuccess}. This sublayer only marks its
 * {@code holds} map and reacts; it never encodes, dispatches, or touches the transport.
 *
 * <ul>
 *   <li>{@link #onSendError} (a failure): bump the attempt for this message; past the optional {@code maxAttempts} cap
 *       DROP it (return {@code null} -> the worker stops); otherwise HOLD on the worker thread until the backoff
 *       ladder step elapses (a monitor wait released by {@link #tick}) and return the SAME encoded unit so the
 *       worker re-dispatches it (no re-encode). Holding the single feed worker is the back-pressure: it stops
 *       dequeuing, the bounded feed fills, producers block. A cap of 1 drops the first failure without ever holding.
 *       The per-message state is keyed by the stable {@code ApplMsgID}, so a worker POOL (W&gt;1) reuses it
 *       unchanged -- each worker holds its own message. A SESSION (heartbeat) event is NEVER retried -- skipped here
 *       (and the worker one-shots it anyway).</li>
 *   <li>{@link #onSendSuccess}: the next dispatch landed -> clear the hold (recovery).</li>
 *   <li>{@link #tick} (the rod's {@code idle()} hook, the ~1s ticker -- NO own thread): wake a held worker whose
 *       backoff has elapsed.</li>
 *   <li>{@link #health}: DOWN while any request is HELD (a send is stuck), else UP -- the broker-down signal for a
 *       leg that runs send-retry without the alive protocol.</li>
 * </ul>
 */
public final class SendRetrySublayer implements ISessionSublayer {

    private static final long DEFAULT_BACKOFF_MS = 1000L;    // backoff when none is configured (1s)
    private static final long WAIT_SAFETY_MS     = 10_000L;  // bounded hold-wait: a missed-signal safety net (~10x the
                                                             // ~1s idle tick); tick() is the primary release

    private final long[] backoffMs;         // the backoff ladder (ms); the last step repeats for further attempts
    private final int maxAttempts;          // 0 = retry until recovery (block); >0 = drop after this many (fallback)
    private final MsgAudit msgAudit;        // the x-rod msg-audit (msg.<bus-id>.<slot-id>), built from the identity
    private final BusIdentity identity;     // the leg identity -- for identity.rodId() in the retry trail
    private final LongSupplier clock;       // wall clock in production; a test seam injects a controllable one

    private final Object lock = new Object();
    private final Map<String, Hold> holds = new HashMap<>();   // ApplMsgID -> hold state; GUARDED by lock

    /** Per held message: the attempts so far and when the next re-dispatch is due. */
    private static final class Hold {
        int  attempts;       // failed attempts on this message (1 = the initial dispatch failed)
        long nextRetryAt;    // wall time the next re-dispatch is due
    }

    public SendRetrySublayer(String backoffSpec, int maxAttempts, BusIdentity identity) {
        this(backoffSpec, maxAttempts, identity, System::currentTimeMillis);
    }

    /** Package-private test seam: an injectable clock makes the backoff ladder deterministic (a unit test advances
     *  logical time and calls {@link #tick} without sleeping). */
    SendRetrySublayer(String backoffSpec, int maxAttempts, BusIdentity identity, LongSupplier clock) {
        this.backoffMs   = parseBackoff(backoffSpec);
        this.maxAttempts = Math.max(0, maxAttempts);
        this.identity     = identity;
        this.msgAudit     = new MsgAudit(identity);
        this.clock       = clock;
    }

    /** The failure hook (the feed/tx worker calls it after a dispatch threw): mark the map and react. Skip a
     *  SESSION event (never retried -> {@code null}). Past the cap DROP (return {@code null}). Else HOLD this
     *  worker thread until the backoff elapses (a monitor wait released by {@link #tick}) and return {@code enc}
     *  so the worker re-dispatches the SAME unit. */
    public Object onSendError(RodEvent ev, Object enc, Throwable error) {
        Object ret;
        if (ev.isSession()) {
            ret = null;   // a SESSION event (heartbeat) is NEVER retried -- best-effort; skip explicitly
        } else {
            String msgId = ev.applMsgId();
            //TODO: optimize lock scope
            synchronized (lock) {
                Hold h = holds.computeIfAbsent(msgId, k -> new Hold());
                h.attempts++;
                if (maxAttempts > 0 && h.attempts >= maxAttempts) {
                    holds.remove(msgId);
                    drop(h.attempts, ev);
                    ret = null;
                } else {
                    long backoff = backoffFor(h.attempts);
                    h.nextRetryAt = clock.getAsLong() + backoff;
                    held(h.attempts, backoff, ev);
                    boolean interrupted = false;
                    while (!interrupted && holds.containsKey(msgId) && clock.getAsLong() < h.nextRetryAt) {
                        try {
                            // released by tick() once the backoff elapses (the primary path); the bounded wait is a
                            // missed-signal safety net (~10x the ~1s idle tick) so a delayed/starved tick can never
                            // hang the held worker -- it re-checks the clock. Mirrors BoundedQueueRig's await-timeout.
                            lock.wait(WAIT_SAFETY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            interrupted = true;
                        }
                    }
                    if (interrupted) {
                        holds.remove(msgId);
                        ret = null;        // shutdown / interrupt -> give up so the worker can exit
                    } else {
                        ret = enc;         // backoff elapsed -> re-dispatch the same unit
                    }
                }
            }
        }
        return ret;
    }

    /** The success hook (the worker's dispatch landed): clear the hold; note the recovery when the message had
     *  been held. */
    public void onSendSuccess(RodEvent ev) {
        Hold h;
        synchronized (lock) {
            h = holds.remove(ev.applMsgId());
        }
        if (h != null) {
            msgAudit.info("send-retry[{}]: send recovered after {} attempt{} -- kind={}, entityId={}",
                    identity.rodId(), h.attempts, h.attempts == 1 ? "" : "s", ev.kind(), ev.entityId());
        }
    }

    /** The idle maintenance step (the rod's idle() hook -- no own thread): wake any held worker whose backoff has
     *  elapsed so it re-dispatches. */
    @Override
    public void tick() {
        synchronized (lock) {
            long now = clock.getAsLong();
            boolean due = false;
            for (Hold h : holds.values()) {
                if (now >= h.nextRetryAt) {
                    due = true;
                }
            }
            if (due) {
                lock.notifyAll();
            }
        }
    }

    /** The leg's send-retry health: DOWN while any request is HELD (a send is stuck -- the broker is down or
     *  unreachable), else UP. The engine folds it into the leg's session health, so a send-retry-only leg (no
     *  alive protocol) still reads DOWN through a broker outage -- and depools on k8s readiness. */
    @Override
    public TransportHealth health() {
        TransportHealth ret = heldCount() > 0 ? TransportHealth.DOWN : TransportHealth.UP;
        return ret;
    }

    /** Log that a failed send is held for the backoff. */
    private void held(int attempts, long backoffMs, RodEvent event) {
        msgAudit.warn("send-retry[{}]: send failed (transport DOWN) -- holding {} (attempt {}, next retry in {}ms) -- kind={}, entityId={}",
                identity.rodId(), event.applMsgId(), attempts, backoffMs, event.kind(), event.entityId());
    }

    /** Give up on the held event (the fallback): note it and let the worker move on. */
    private void drop(int afterAttempts, RodEvent event) {
        msgAudit.warn("send-retry[{}]: send failed after {} attempts -- DROPPING {} -- kind={}, entityId={}",
                identity.rodId(), afterAttempts, event.applMsgId(), event.kind(), event.entityId());
    }

    /** The number of messages currently held (test seam: lets a test observe a parked worker). */
    int heldCount() {
        synchronized (lock) {
            return holds.size();
        }
    }

    /** The backoff for the n-th attempt (1-based): the n-th ladder step, clamped to the last (so the final step
     *  repeats for every further attempt). */
    private long backoffFor(int attempt) {
        int idx = Math.min(attempt - 1, backoffMs.length - 1);
        return backoffMs[idx];
    }

    /** Parse a backoff ladder spec -- a comma-separated list of SECONDS (fractions allowed, e.g. "1,2,5,5.5") --
     *  into a millisecond ladder. Blank / unparsable yields a single 1s step. */
    private static long[] parseBackoff(String spec) {
        long[] ret;
        List<Long> steps = new ArrayList<>();
        if (spec != null && !spec.isBlank()) {
            for (String part : spec.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    steps.add((long) (Double.parseDouble(t) * 1000.0));
                }
            }
        }
        if (steps.isEmpty()) {
            ret = new long[] { DEFAULT_BACKOFF_MS };
        } else {
            ret = new long[steps.size()];
            for (int i = 0; i < steps.size(); i++) {
                ret[i] = steps.get(i);
            }
        }
        return ret;
    }
}
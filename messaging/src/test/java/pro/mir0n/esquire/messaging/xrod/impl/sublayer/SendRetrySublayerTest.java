/*
 *  Esquire frameworks (tm)
 *  messaging library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: SendRetrySublayer tests -- event-driven (clock-driven, no sleep). A healthy send
 *                   passes straight through (no hold, feed not paused). A send the transport reports DOWN is HELD
 *                   and the feed PAUSED; tick() re-sends only once the backoff step has elapsed; on recovery the
 *                   hold clears and the feed resumes; with a max-attempts cap the held event is DROPPED after that
 *                   many tries and the feed resumes (a cap of 1 drops the first failure without ever pausing).
 *                   Health + the feed gate are observed via scripted suppliers and an injected clock.
 */
package pro.mir0n.esquire.messaging.xrod.impl.sublayer;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Send-retry is the {@code onSendError} / {@code onSendSuccess} POLICY -- an event-driven handler, NOT a sender. The
 * feed (tx) worker owns the encode + dispatch; it calls {@link SendRetrySublayer#onSendError} after a dispatch throw
 * and {@link SendRetrySublayer#onSendSuccess} after one lands. On a failure {@code onSendError} HOLDS the worker
 * thread across the backoff (a monitor wait released by {@link SendRetrySublayer#tick()}) and returns the SAME
 * encoded unit to re-dispatch, or {@code null} to drop (the cap). A positive backoff parks the worker, so those
 * cases drive {@code onSendError} on a separate thread and release it by advancing the injected clock and ticking.
 * (A session event is never retried -- the feed worker skips it before ever calling here.)
 */
class SendRetrySublayerTest {

    private static final Object ENC = new Object();   // the encoded unit the feed worker holds (opaque to the sublayer)
    private static final Throwable ERR = new RuntimeException("transport down");   // the dispatch failure passed to onSendError
    private static final BusIdentity ID = new BusIdentity("bus", "slot", "rod");    // the leg identity

    private static RodEvent event() {
        // the feed worker stamps the ApplMsgID before the hooks run, so give the test event one (onSendError keys
        // its holds map on ev.applMsgId()).
        return new RodEvent(RodEvent.Op.UPDATE, 2, "e1", null, 0L, null, null, null, Map.of()).withApplMsgId("m1");
    }

    private static void awaitHeld(SendRetrySublayer s) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (s.heldCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(2);
        }
        assertThat(s.heldCount()).isEqualTo(1);
    }

    @Test
    void firstFailure_holdsThenReturnsEncOnTick() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0);
        SendRetrySublayer s = new SendRetrySublayer("1", 0, ID, clock::get);
        RodEvent e = event();
        Object[] back = new Object[1];

        Thread worker = new Thread(() -> back[0] = s.onSendError(e, ENC, ERR), "tx-worker");
        worker.start();

        awaitHeld(s);                            // failure recorded -> worker parked on the 1s backoff
        clock.set(1000);
        s.tick();                                // backoff elapsed -> release the worker
        worker.join(2000);

        assertThat(back[0]).isSameAs(ENC);       // returns the SAME unit to re-dispatch (no re-encode)
        assertThat(s.heldCount()).isEqualTo(1);  // the hold persists until success / drop
        s.onSendSuccess(e);                      // the re-dispatch landed
        assertThat(s.heldCount()).isZero();      // hold cleared on recovery
    }

    @Test
    void backoffNotElapsed_keepsHolding() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0);
        SendRetrySublayer s = new SendRetrySublayer("5", 0, ID, clock::get);
        RodEvent e = event();
        Object[] back = new Object[]{ "pending" };

        Thread worker = new Thread(() -> back[0] = s.onSendError(e, ENC, ERR), "tx-worker");
        worker.start();

        awaitHeld(s);                            // parked on the 5s backoff
        clock.set(4999);
        s.tick();                                // 5s not elapsed -> re-checks and re-waits
        Thread.sleep(30);
        assertThat(back[0]).isEqualTo("pending");   // not returned yet
        assertThat(s.heldCount()).isEqualTo(1);

        clock.set(5000);
        s.tick();                                // elapsed -> returns the unit
        worker.join(2000);
        assertThat(back[0]).isSameAs(ENC);
    }

    @Test
    void capReached_dropsAndReturnsNull() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0);
        SendRetrySublayer s = new SendRetrySublayer("1", 2, ID, clock::get);
        RodEvent e = event();

        // attempt 1 -> hold -> release -> returns ENC (the worker re-dispatches)
        Object[] first = new Object[1];
        Thread w1 = new Thread(() -> first[0] = s.onSendError(e, ENC, ERR), "tx-1");
        w1.start();
        awaitHeld(s);
        clock.set(1000);
        s.tick();
        w1.join(2000);
        assertThat(first[0]).isSameAs(ENC);

        // attempt 2 -> hits the cap of 2 -> DROP (null), no hold
        Object back = s.onSendError(e, ENC, ERR);
        assertThat(back).isNull();
        assertThat(s.heldCount()).isZero();
    }

    @Test
    void capOne_dropsFirstFailure_neverHolds() {
        AtomicLong clock = new AtomicLong(0);
        SendRetrySublayer s = new SendRetrySublayer("1", 1, ID, clock::get);

        Object back = s.onSendError(event(), ENC, ERR);   // cap 1 -> the first failure is terminal, no hold

        assertThat(back).isNull();
        assertThat(s.heldCount()).isZero();
    }

    @Test
    void sessionEvent_neverRetried_returnsNullWithoutHolding() {
        AtomicLong clock = new AtomicLong(0);
        SendRetrySublayer s = new SendRetrySublayer("5", 0, ID, clock::get);

        Object back = s.onSendError(RodEvent.heartbeat("c", null, null), ENC, ERR);   // admin (TR/HB) -> never held

        assertThat(back).isNull();
        assertThat(s.heldCount()).isZero();
    }

    @Test
    void health_downWhileHeld_upWhenClear() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0);
        SendRetrySublayer s = new SendRetrySublayer("1", 0, ID, clock::get);
        RodEvent e = event();

        assertThat(s.health()).isEqualTo(TransportHealth.UP);    // nothing held -> UP

        Thread worker = new Thread(() -> s.onSendError(e, ENC, ERR), "tx-worker");
        worker.start();
        awaitHeld(s);
        assertThat(s.health()).isEqualTo(TransportHealth.DOWN);  // a request is HELD (send stuck) -> DOWN

        clock.set(1000);
        s.tick();
        worker.join(2000);
        s.onSendSuccess(e);                                      // the re-dispatch landed -> hold cleared
        assertThat(s.health()).isEqualTo(TransportHealth.UP);    // back to UP
    }
}

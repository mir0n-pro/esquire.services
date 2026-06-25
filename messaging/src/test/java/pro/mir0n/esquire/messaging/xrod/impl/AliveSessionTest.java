/*
 *  Esquire frameworks (tm)
 *  messaging library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/23/2026 mir0n  created: AliveSession tests -- timestamp-age health (clock-driven, deterministic) for a
 *                   producing leg (UP within alive-timeout, DOWN past it), the fail-fast vs timeout send-error
 *                   paths, a receive-only leg (always UP, Q&D), the cadence step tick() (emits a keep-alive when
 *                   idle, suppressed by recent send activity -- driven externally, no own thread), and the
 *                   internal session-message dispatch.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AliveSessionTest {

    /** A clock-driven session (heartbeat 1000ms, alive-timeout 3000ms): seed via start(), then drive logical time,
     *  the mark hooks, and tick() by hand -- the cadence has no thread of its own. */
    private static AliveSession session(AtomicLong clock, boolean failFast, Supplier<RodEvent> keepAlive,
                                        Consumer<RodEvent> emit, Consumer<RodEvent> onSession) {
        return new AliveSession(1000L, 3000L, failFast, keepAlive, emit, onSession, "test", null, clock::get);
    }

    // ----------------------------------------------------------------- timestamp-age health

    @Test
    void producingLeg_upWithinTimeout_downPastIt_refreshedOnSend() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, false, () -> null, e -> { }, e -> { });
        s.start();   // seed producerTs = 0

        clock.set(2999);
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // within the 3000ms timeout
        clock.set(3001);
        assertThat(s.health()).isEqualTo(TransportHealth.DOWN);   // aged out -- no successful send in time
        s.markSent();                                             // a successful send at t=3001
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // producer leg refreshed
    }

    @Test
    void failFast_sendError_downImmediately_clearedOnNextSend() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, true, () -> null, e -> { }, e -> { });
        s.start();

        assertThat(s.health()).isEqualTo(TransportHealth.UP);
        s.markSendFailed();
        assertThat(s.health()).isEqualTo(TransportHealth.DOWN);   // immediate (well inside the timeout window)
        s.markSent();
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // cleared on the next success
    }

    @Test
    void noFailFast_sendError_staysUpUntilTimeout() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, false, () -> null, e -> { }, e -> { });
        s.start();

        s.markSendFailed();          // fail-fast off -> no immediate DOWN
        clock.set(1000);
        assertThat(s.health()).isEqualTo(TransportHealth.UP);
        clock.set(3001);
        assertThat(s.health()).isEqualTo(TransportHealth.DOWN);   // ages out via the timeout instead
    }

    @Test
    void receiveOnlyLeg_isAlwaysUp() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, true, null, e -> { }, e -> { });   // keepAlive null = no producer leg
        s.start();

        clock.set(10_000_000L);   // far past any timeout
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // the consumer leg is ignored in the Q&D
    }

    @Test
    void receivedSession_advancesAndRunsTheHandler() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> handled = new ArrayList<>();
        AliveSession s = session(clock, true, () -> null, e -> { }, handled::add);
        s.start();

        RodEvent tr = RodEvent.testRequest("corr", "client.0");
        s.receivedSession(tr);
        assertThat(handled).containsExactly(tr);   // the internal handler (an R&R SERVER echo) ran
    }

    // ----------------------------------------------------------------- cadence (tick, clock-driven, no thread)

    @Test
    void tick_emitsAKeepAliveOnceIdleAFullInterval() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSession s = session(clock, false, () -> RodEvent.heartbeat("c", null, null), emitted::add, e -> { });
        s.start();   // lastSendAttempt = 0

        clock.set(999);
        s.tick();
        assertThat(emitted).isEmpty();              // not idle a full interval yet
        clock.set(1000);
        s.tick();
        assertThat(emitted).hasSize(1);             // idle >= heartbeat-interval -> emit
        assertThat(emitted.get(0).isSession()).isTrue();
    }

    @Test
    void tick_suppressedByRecentSendActivity() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSession s = session(clock, false, () -> RodEvent.heartbeat("c", null, null), emitted::add, e -> { });
        s.start();

        clock.set(500);
        s.markSendAttempt();         // real traffic at t=500 resets the cadence gate
        clock.set(1000);
        s.tick();
        assertThat(emitted).isEmpty();   // only 500ms since the last send -> suppressed
        clock.set(1500);
        s.tick();
        assertThat(emitted).hasSize(1);  // now a full interval idle -> emit
    }

    @Test
    void tick_nonProducingLeg_neverEmits() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSession s = session(clock, false, null, emitted::add, e -> { });   // keepAlive null
        s.start();

        clock.set(10_000L);
        s.tick();
        assertThat(emitted).isEmpty();   // a receive-only leg has no cadence
    }
}

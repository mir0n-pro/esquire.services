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
package pro.mir0n.esquire.messaging.xrod.impl.sublayer;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.o11y.IRodMeters;
import pro.mir0n.esquire.messaging.o11y.IRodObserver;
import pro.mir0n.esquire.messaging.o11y.IRodTracer;
import pro.mir0n.esquire.messaging.o11y.RodObserverHolder;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AliveSessionTest {

    /** A dummy event for the send-side hooks -- beforeSend / onSendSuccess / onSendError ignore the event content. */
    private static final RodEvent EV = RodEvent.heartbeat("ev", null, null);

    private static final BusIdentity ID        = new BusIdentity("bus", "slot", "rod");
    private static final BusIdentity SERVER_ID = new BusIdentity("bus", "slot", "server.0");
    private static final BusIdentity CLIENT_ID = new BusIdentity("bus", "slot", "client.0");

    /** A minimal feed: the alive session PUTs a keep-alive on it; the test reads the captured list as "emitted". */
    private static final class CaptureRig implements IQueueRig<RodEvent> {
        private final List<RodEvent> puts;

        CaptureRig(List<RodEvent> puts) {
            this.puts = puts;
        }

        @Override public void init(String name, Logger devLogger, int capacity) { }
        @Override public void setErrorListener(IQueueRig.IErrorListener listener) { }
        @Override public void setProcessing(boolean enabled) { }
        @Override public void start() { }
        @Override public void shutdown() { }
        @Override public void put(RodEvent item) { puts.add(item); }
        @Override public int size() { return 0; }
        @Override public void clear() { }
    }

    /** A clock-driven session (heartbeat 1000ms, alive-timeout 3000ms): seed via start(), then drive logical time,
     *  the send hooks, and tick() by hand -- the cadence has no thread of its own. {@code keepAliveEnabled} = a
     *  producing leg (health measured + heartbeats emitted); a keep-alive is PUT on the feed ({@code emitted}). */
    private static AliveSession session(AtomicLong clock, boolean failFast, boolean keepAliveEnabled,
                                        List<RodEvent> emitted) {
        return new AliveSession(new CaptureRig(emitted), 1000L, 3000L, failFast, keepAliveEnabled, ID, null,
                clock::get);
    }

    // ----------------------------------------------------------------- timestamp-age health

    @Test
    void producingLeg_upWithinTimeout_downPastIt_refreshedOnSend() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, false, true, new ArrayList<>());
        s.start();   // seed producerTs = 0

        clock.set(2999);
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // within the 3000ms timeout
        clock.set(3001);
        assertThat(s.health()).isEqualTo(TransportHealth.DOWN);   // aged out -- no successful send in time
        s.onSendSuccess(EV);                                      // a successful send at t=3001
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // producer leg refreshed
    }

    @Test
    void failFast_sendError_downImmediately_clearedOnNextSend() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, true, true, new ArrayList<>());
        s.start();

        assertThat(s.health()).isEqualTo(TransportHealth.UP);
        s.onSendError(EV, null, null);
        assertThat(s.health()).isEqualTo(TransportHealth.DOWN);   // immediate (well inside the timeout window)
        s.onSendSuccess(EV);
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // cleared on the next success
    }

    @Test
    void noFailFast_sendError_staysUpUntilTimeout() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, false, true, new ArrayList<>());
        s.start();

        s.onSendError(EV, null, null);     // fail-fast off -> no immediate DOWN
        clock.set(1000);
        assertThat(s.health()).isEqualTo(TransportHealth.UP);
        clock.set(3001);
        assertThat(s.health()).isEqualTo(TransportHealth.DOWN);   // ages out via the timeout instead
    }

    @Test
    void receiveOnlyLeg_isAlwaysUp() {
        AtomicLong clock = new AtomicLong(0);
        AliveSession s = session(clock, true, false, new ArrayList<>());   // keepAliveEnabled false = no producer leg
        s.start();

        clock.set(10_000_000L);   // far past any timeout
        assertThat(s.health()).isEqualTo(TransportHealth.UP);     // the consumer leg is ignored in the Q&D
    }

    // ----------------------------------------------------------------- R&R session message (AliveSessionRR)

    @Test
    void rrServer_echoesTestRequestAsHeartbeat() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.SERVER, 1000L, 3000L, true, true,
                SERVER_ID, null, clock::get);

        s.onReceiveSessn(RodEvent.testRequest("corr", "client.0"));   // a SERVER answers a TestRequest with a HeartBeat

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).isSession()).isTrue();
        assertThat(emitted.get(0).correlationId()).isEqualTo("corr");   // echoes the requester's correlation
    }

    @Test
    void rrClient_ignoresAReceivedSessionMessage() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.CLIENT, 1000L, 3000L, true, true,
                CLIENT_ID, null, clock::get);

        s.onReceiveSessn(RodEvent.heartbeat("corr", null, null));   // a CLIENT's received HeartBeat is liveness only

        assertThat(emitted).isEmpty();   // no echo
    }

    @Test
    void rrServer_keepAliveIsAnUnsolicitedHeartbeat() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.SERVER, 1000L, 3000L, true, true,
                SERVER_ID, null, clock::get);
        s.start();

        clock.set(1000);
        s.tick();   // idle a full interval -> emit the keep-alive

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).msgType()).isEqualTo(BusConstants.MSG_TYPE_HEARTBEAT);   // SERVER -> HeartBeat
    }

    @Test
    void rrClient_keepAliveIsATestRequest() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.CLIENT, 1000L, 3000L, true, true,
                CLIENT_ID, null, clock::get);
        s.start();

        clock.set(1000);
        s.tick();

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).msgType()).isEqualTo(BusConstants.MSG_TYPE_TEST_REQUEST);   // CLIENT -> TestRequest
        assertThat(emitted.get(0).rodId()).isEqualTo("client.0");   // its own rod-id rides for the reply route
    }

    // ------------------------------------------------- R&R liveness round-trip TRACE (msg-bus-alive-trace on)

    /** A stub tracer standing in for the host application's OTel implementation: alive-trace ON, it mints a fixed
     *  trace id and returns a traceparent built from it, recording whether the send was opened as a ROOT trace. */
    private static final class StubTracer implements IRodTracer {
        static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
        final List<String> labels = new ArrayList<>();
        final List<Boolean> asRoots = new ArrayList<>();

        @Override public String outbound(String correlationId, String busId, String slotId, String ownRodId) {
            return null;
        }
        @Override public void inbound(String traceparent, String correlationId, String busId, String slotId,
                                      String fromRodId, String ownRodId, Runnable worker) {
            worker.run();
        }
        @Override public boolean aliveTrace() {
            return true;   // the host opted in (esquire.tracing.msg-bus-alive-trace)
        }
        @Override public String newTraceId() {
            return TRACE_ID;
        }
        @Override public String aliveOutbound(String correlationId, String busId, String label, String ownRodId,
                                              boolean asRoot) {
            labels.add(label);
            asRoots.add(asRoot);
            return "00-" + correlationId + "-b7ad6b7169203331-01";
        }
        @Override public void aliveInbound(String traceparent, String correlationId, String busId, String label,
                                           String fromRodId, String ownRodId, Runnable worker) {
            worker.run();
        }
    }

    /** Run {@code body} with a stub tracer registered (its aliveTrace() is on); always restore the static holder. */
    private static void withAliveTrace(StubTracer tracer, Runnable body) {
        RodObserverHolder.setObserver(IRodObserver.of(tracer, IRodMeters.NOOP));
        try {
            body.run();
        } finally {
            RodObserverHolder.setObserver(null);
        }
    }

    @Test
    void rrClient_tracedTestRequest_takesItsCorrelationIdFromTheTracerAndRidesARootTraceparent() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        StubTracer tracer = new StubTracer();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.CLIENT, 1000L, 3000L, true, true,
                CLIENT_ID, null, clock::get);
        s.start();

        withAliveTrace(tracer, () -> {
            clock.set(1000);
            s.tick();   // idle a full interval -> emit the traced TestRequest
        });

        assertThat(emitted).hasSize(1);
        RodEvent ev = emitted.get(0);
        assertThat(ev.msgType()).isEqualTo(BusConstants.MSG_TYPE_TEST_REQUEST);
        assertThat(ev.correlationId()).isEqualTo(StubTracer.TRACE_ID);   // the bus mints no trace id of its own
        assertThat(ev.traceparent()).isEqualTo("00-" + StubTracer.TRACE_ID + "-b7ad6b7169203331-01");
        assertThat(tracer.labels).containsExactly("TestRequest");
        assertThat(tracer.asRoots).containsExactly(true);   // no current span off the cadence -> a ROOT trace
    }

    @Test
    void rrServer_tracedTestRequest_repliesWithAHeartbeatNestedUnderTheReceive() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        StubTracer tracer = new StubTracer();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.SERVER, 1000L, 3000L, true, true,
                SERVER_ID, null, clock::get);

        withAliveTrace(tracer, () ->
                s.onReceiveSessn(RodEvent.testRequest("corr", "client.0")
                        .withTraceparent("00-corr-aaaaaaaaaaaaaaaa-01")));

        assertThat(emitted).hasSize(1);
        RodEvent hb = emitted.get(0);
        assertThat(hb.msgType()).isEqualTo(BusConstants.MSG_TYPE_HEARTBEAT);
        assertThat(hb.correlationId()).isEqualTo("corr");                       // the requester's id is echoed
        assertThat(hb.traceparent()).isEqualTo("00-corr-b7ad6b7169203331-01");  // stamped for the reply leg
        assertThat(tracer.labels).containsExactly("HeartBeat");
        assertThat(tracer.asRoots).containsExactly(false);   // sent inside the receive span -> nested, not root
    }

    @Test
    void rrServer_untracedTestRequest_repliesWithAPlainHeartbeat() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        StubTracer tracer = new StubTracer();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.SERVER, 1000L, 3000L, true, true,
                SERVER_ID, null, clock::get);

        // alive-trace is ON, but the arriving TestRequest carries no traceparent (an untraced peer)
        withAliveTrace(tracer, () -> s.onReceiveSessn(RodEvent.testRequest("corr", "client.0")));

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).traceparent()).isNull();   // nothing to nest under -> no stamp
        assertThat(tracer.labels).isEmpty();
    }

    @Test
    void rrClient_aliveTraceOff_keepsItsOwnCorrelationIdAndNoTraceparent() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSessionRR s = new AliveSessionRR(new CaptureRig(emitted), Role.CLIENT, 1000L, 3000L, true, true,
                CLIENT_ID, null, clock::get);
        s.start();

        clock.set(1000);
        s.tick();   // NOOP tracer -> aliveTrace() false -> the ordinary, untraced probe

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).traceparent()).isNull();
        assertThat(emitted.get(0).correlationId()).isNotEqualTo(StubTracer.TRACE_ID);
    }

    // ----------------------------------------------------------------- cadence (tick, clock-driven, no thread)

    @Test
    void tick_emitsAKeepAliveOnceIdleAFullInterval() {
        AtomicLong clock = new AtomicLong(0);
        List<RodEvent> emitted = new ArrayList<>();
        AliveSession s = session(clock, false, true, emitted);
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
        AliveSession s = session(clock, false, true, emitted);
        s.start();

        clock.set(500);
        s.beforeSend(EV);            // real traffic at t=500 resets the cadence gate
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
        AliveSession s = session(clock, false, false, emitted);   // keepAliveEnabled false
        s.start();

        clock.set(10_000L);
        s.tick();
        assertThat(emitted).isEmpty();   // a receive-only leg has no cadence
    }
}

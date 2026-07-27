package pro.mir0n.esquire.messaging.o11y;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

// The observability-OFF contract for the ONE bus-hop observer seam: when no observer is registered the bus pays
// nothing -- the tracer view stamps no traceparent (null), runs the worker plain, mints no trace id, and leaves
// the RR liveness round-trip untraced; the meters view is a no-op. The holder defaults to (and null-restores)
// IRodObserver.NOOP, and exposes the one registered observer as both its tracer() and meters() views.
class RodObserverHolderTest {

    @Test
    void noopObserver_traceIsFree_metersAreNoOp() {
        boolean[] ran = {false};

        assertThat(IRodObserver.NOOP.outbound("cid", "audit-c", "slot", "enyman.0")).isNull();
        assertThat(IRodObserver.NOOP.newTraceId()).isNull();
        assertThat(IRodObserver.NOOP.aliveTrace()).isFalse();
        IRodObserver.NOOP.inbound("00-cid-span-01", "cid", "audit-c", "slot", "enyman.0", "aukeep.0",
                () -> ran[0] = true);
        assertThat(ran[0]).isTrue();

        // meters side: no-ops, must not throw
        IRodObserver.NOOP.sent("audit-c", "slot", "MoveCmd");
        IRodObserver.NOOP.sendDuration("audit-c", "slot", "MoveCmd", 1234L);
        IRodObserver.NOOP.received("audit-c", "slot", "MoveCmd");
        IRodObserver.NOOP.error("audit-c", "slot", "MoveCmd", "send");
        IRodObserver.NOOP.retryBackoff("audit-c", 1000L);
        IRodObserver.NOOP.retryDropped("audit-c", "MoveCmd");
        IRodObserver.NOOP.registerFeedDepth("audit-c", "slot", () -> 0);
        IRodObserver.NOOP.registerRetryHeld("audit-c", "slot", () -> 0);
    }

    @Test
    void holder_defaultsToNoop() {
        // no registrar ran (unit context) -> the holder yields the NOOP observer, not null
        assertThat(RodObserverHolder.observer()).isNotNull();
        assertThat(RodObserverHolder.tracer()).isNotNull();
        assertThat(RodObserverHolder.meters()).isNotNull();
        assertThat(RodObserverHolder.tracer().outbound("cid", "audit-c", "slot", "enyman.0")).isNull();
        assertThat(RodObserverHolder.tracer().aliveTrace()).isFalse();
    }

    @Test
    void holder_setObserver_bothViewsHonoredThenRestored() {
        boolean[] outboundCalled = {false};
        boolean[] sentCalled = {false};
        IRodTracer stubTracer = new IRodTracer() {
            @Override public String outbound(String correlationId, String busId, String slotId, String ownRodId) {
                outboundCalled[0] = true;
                return "stamped";
            }
            @Override public boolean aliveTrace() {
                return true;
            }
            @Override public String newTraceId() {
                return "0123456789abcdef0123456789abcdef";
            }
            @Override public void inbound(String traceparent, String correlationId, String busId, String slotId,
                                          String fromRodId, String ownRodId, Runnable worker) {
                worker.run();
            }
            @Override public String aliveOutbound(String correlationId, String busId, String label, String ownRodId,
                                                  boolean asRoot) {
                return null;
            }
            @Override public void aliveInbound(String traceparent, String correlationId, String busId, String label,
                                               String fromRodId, String ownRodId, Runnable worker) {
                worker.run();
            }
        };
        IRodMeters stubMeters = new IRodMeters() {
            @Override public void sent(String busId, String slotId, String msgType) {
                sentCalled[0] = true;
            }
            @Override public void sendDuration(String busId, String slotId, String msgType, long nanos) {
            }
            @Override public void received(String busId, String slotId, String msgType) {
            }
            @Override public void error(String busId, String slotId, String msgType, String leg) {
            }
            @Override public void retryBackoff(String busId, long backoffMs) {
            }
            @Override public void retryDropped(String busId, String msgType) {
            }
            @Override public void registerFeedDepth(String busId, String slotId, java.util.function.IntSupplier depth) {
            }
            @Override public void registerRetryHeld(String busId, String slotId, java.util.function.IntSupplier held) {
            }
        };
        try {
            RodObserverHolder.setObserver(IRodObserver.of(stubTracer, stubMeters));
            assertThat(RodObserverHolder.tracer().outbound("cid", "audit-c", "slot", "enyman.0")).isEqualTo("stamped");
            assertThat(RodObserverHolder.tracer().aliveTrace()).isTrue();
            assertThat(outboundCalled[0]).isTrue();
            RodObserverHolder.meters().sent("audit-c", "slot", "MoveCmd");
            assertThat(sentCalled[0]).isTrue();
        } finally {
            RodObserverHolder.setObserver(null); // restore for other tests sharing this static holder
        }
    }

    @Test
    void holder_nullRestoresNoop() {
        RodObserverHolder.setObserver(null);
        assertThat(RodObserverHolder.observer()).isSameAs(IRodObserver.NOOP);
    }

    // The registrar-vs-bus-start ordering tripwire (I11): a feed-depth gauge registered against NOOP is silent when
    // observability is off (setObserver never runs), but if the observer is installed LATE -- meaning the bus
    // started before its registrar -- setObserver reports it on the develop channel at ERROR.
    @Test
    void feedDepthAgainstNoop_thenLateObserver_logsErrorOnce() throws Exception {
        Logger devLog = (Logger) LoggerFactory.getLogger(
                "develop." + RodObserverHolder.class.getName());
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        devLog.addAppender(appender);
        IRodObserver stub = IRodObserver.of(new NoopTracer(), new NoopMeters());
        try {
            resetFeedDepthLatch();

            // Correct ordering: observer installed with NO prior NOOP feed-depth -> silent.
            RodObserverHolder.setObserver(stub);
            assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.ERROR);

            // The race: a feed-depth registered against NOOP, THEN the observer arrives late -> ERROR.
            RodObserverHolder.noteFeedDepthAgainstNoop();
            RodObserverHolder.setObserver(stub);
            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.ERROR
                            && e.getFormattedMessage().contains("registered against NOOP"));
        } finally {
            devLog.detachAppender(appender);
            resetFeedDepthLatch();
            RodObserverHolder.setObserver(null); // restore for other tests sharing this static holder
        }
    }

    private static void resetFeedDepthLatch() throws Exception {
        Field f = RodObserverHolder.class.getDeclaredField("feedDepthAgainstNoop");
        f.setAccessible(true);
        f.setBoolean(null, false);
    }

    // Minimal no-op views so the test can build a real (non-NOOP) observer without a mocking framework.
    private static final class NoopTracer implements IRodTracer {
        @Override public String outbound(String c, String b, String s, String o) { return null; }
        @Override public boolean aliveTrace() { return false; }
        @Override public String newTraceId() { return null; }
        @Override public void inbound(String tp, String c, String b, String s, String f, String o, Runnable w) { w.run(); }
        @Override public String aliveOutbound(String c, String b, String l, String o, boolean r) { return null; }
        @Override public void aliveInbound(String tp, String c, String b, String l, String f, String o, Runnable w) { w.run(); }
    }
    private static final class NoopMeters implements IRodMeters {
        @Override public void sent(String b, String s, String m) { }
        @Override public void sendDuration(String b, String s, String m, long n) { }
        @Override public void received(String b, String s, String m) { }
        @Override public void error(String b, String s, String m, String leg) { }
        @Override public void retryBackoff(String b, long ms) { }
        @Override public void retryDropped(String b, String m) { }
        @Override public void registerFeedDepth(String b, String s, java.util.function.IntSupplier d) { }
        @Override public void registerRetryHeld(String b, String s, java.util.function.IntSupplier h) { }
    }
}

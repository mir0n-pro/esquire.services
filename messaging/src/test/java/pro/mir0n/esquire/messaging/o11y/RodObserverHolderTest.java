package pro.mir0n.esquire.messaging.o11y;

import org.junit.jupiter.api.Test;

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
}

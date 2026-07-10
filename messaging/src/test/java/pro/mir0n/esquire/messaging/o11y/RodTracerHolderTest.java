package pro.mir0n.esquire.messaging.o11y;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// The tracing-OFF contract for the bus-hop seam: when no tracer is registered the bus pays nothing --
// outbound stamps no traceparent (null), inbound just runs the worker with no span, no trace id is minted,
// and the RR liveness round-trip stays untraced.
class RodTracerHolderTest {

    @Test
    void noopTracer_outboundReturnsNull_inboundRunsWorker() {
        boolean[] ran = {false};

        String traceparent = IRodTracer.NOOP.outbound("cid", "audit-c", "slot", "enyman.0");
        assertThat(IRodTracer.NOOP.newTraceId()).isNull();   // no tracer -> the bus keeps its own correlation id
        assertThat(IRodTracer.NOOP.aliveTrace()).isFalse();  // opt-in RR liveness round-trip is off
        IRodTracer.NOOP.inbound("00-cid-span-01", "cid", "audit-c", "slot", "enyman.0", "aukeep.0",
                () -> ran[0] = true);

        assertThat(traceparent).isNull();
        assertThat(ran[0]).isTrue();
    }

    @Test
    void rodTracerHolder_defaultsToNoop() {
        // no registrar ran (unit context) -> the holder yields the NOOP tracer, not null
        assertThat(RodTracerHolder.tracer()).isNotNull();
        assertThat(RodTracerHolder.tracer().outbound("cid", "audit-c", "slot", "enyman.0")).isNull();
        assertThat(RodTracerHolder.tracer().aliveTrace()).isFalse();
    }

    @Test
    void rodTracerHolder_setTracer_isHonoredThenRestored() {
        boolean[] outboundCalled = {false};
        IRodTracer stub = new IRodTracer() {
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
        try {
            RodTracerHolder.setTracer(stub);
            assertThat(RodTracerHolder.tracer().outbound("cid", "audit-c", "slot", "enyman.0")).isEqualTo("stamped");
            assertThat(RodTracerHolder.tracer().aliveTrace()).isTrue();   // the switch rides on the tracer
            assertThat(outboundCalled[0]).isTrue();
        } finally {
            RodTracerHolder.setTracer(IRodTracer.NOOP); // restore for other tests sharing this static holder
        }
    }

    @Test
    void rodTracerHolder_nullRestoresNoop() {
        RodTracerHolder.setTracer(null);
        assertThat(RodTracerHolder.tracer()).isSameAs(IRodTracer.NOOP);
    }
}

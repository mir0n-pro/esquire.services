package pro.mir0n.esquire.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit coverage for the gateway edge propagator (v1.2.11 O2/T3): the inbound trace id is ALWAYS the settled
// correlation id; a genuine upstream traceparent supplies the parent span id + flag, else a fresh root gets a
// RANDOM span id (decoupled from the trace id -- #6) and the sampling decision from the real sampler (#1).
class CorrelationPropagatorTest {

    private static final String CORRELATION = "0af7651916cd43dd8448eb211c80319c"; // valid W3C 32-hex, kept as-is
    private static final String WIRE_SPAN = "b7ad6b7169203331";                   // 16-hex parent span on the wire

    private static final Propagator.Getter<Map<String, String>> GETTER = Map::get;

    // Builds the propagator over a mock Tracer (capturing the parent context it constructs) and the given sampler.
    private static CorrelationPropagatorConfig.CorrelationPropagator propagator(
            Sampler sampler, TraceContext.Builder ctxBuilder) {
        Tracer tracer = mock(Tracer.class);
        when(tracer.traceContextBuilder()).thenReturn(ctxBuilder);
        when(ctxBuilder.build()).thenReturn(mock(TraceContext.class));
        Span.Builder spanBuilder = mock(Span.Builder.class);
        when(tracer.spanBuilder()).thenReturn(spanBuilder);
        when(spanBuilder.setParent(org.mockito.ArgumentMatchers.any())).thenReturn(spanBuilder);
        return new CorrelationPropagatorConfig.CorrelationPropagator(tracer, sampler);
    }

    @Test
    void fields_declaresOnlyTraceparent() {
        // listing the correlation headers here would make instrumentation strip them downstream
        assertThat(propagator(Sampler.alwaysOn(), mock(TraceContext.Builder.class, RETURNS_SELF)).fields())
                .containsExactly("traceparent");
    }

    @Test
    void inject_writesW3cTraceparentFromCurrentContext_sampled() {
        TraceContext ctx = mock(TraceContext.class);
        when(ctx.traceId()).thenReturn(CORRELATION);
        when(ctx.spanId()).thenReturn(WIRE_SPAN);
        when(ctx.sampled()).thenReturn(Boolean.TRUE);
        Map<String, String> carrier = new HashMap<>();

        propagator(Sampler.alwaysOn(), mock(TraceContext.Builder.class, RETURNS_SELF))
                .inject(ctx, carrier, Map::put);

        assertThat(carrier).containsEntry("traceparent", "00-" + CORRELATION + "-" + WIRE_SPAN + "-01");
    }

    @Test
    void inject_unsampledFlag() {
        TraceContext ctx = mock(TraceContext.class);
        when(ctx.traceId()).thenReturn(CORRELATION);
        when(ctx.spanId()).thenReturn(WIRE_SPAN);
        when(ctx.sampled()).thenReturn(Boolean.FALSE);
        Map<String, String> carrier = new HashMap<>();

        propagator(Sampler.alwaysOn(), mock(TraceContext.Builder.class, RETURNS_SELF))
                .inject(ctx, carrier, Map::put);

        assertThat(carrier.get("traceparent")).endsWith("-00");
    }

    @Test
    void extract_matchingUpstreamTraceparent_keepsWireSpanIdAndFlag() {
        TraceContext.Builder ctxBuilder = mock(TraceContext.Builder.class, RETURNS_SELF);
        Map<String, String> carrier = new HashMap<>();
        carrier.put("Esq-Correlation-ID", CORRELATION);
        carrier.put("traceparent", "00-" + CORRELATION + "-" + WIRE_SPAN + "-01"); // trace id agrees

        propagator(Sampler.alwaysOff(), ctxBuilder).extract(carrier, GETTER); // sampler must NOT be consulted

        verify(ctxBuilder).traceId(CORRELATION);
        verify(ctxBuilder).spanId(WIRE_SPAN);       // real parent kept from the wire
        verify(ctxBuilder).sampled(true);           // wire flag honored, not the alwaysOff sampler
    }

    @Test
    void extract_freshRoot_randomSpanId_notDerivedFromTraceId_samplerDecides() {
        TraceContext.Builder ctxBuilder = mock(TraceContext.Builder.class, RETURNS_SELF);
        Map<String, String> carrier = new HashMap<>();
        carrier.put("Esq-Correlation-ID", CORRELATION); // no traceparent -> fresh root

        propagator(Sampler.alwaysOn(), ctxBuilder).extract(carrier, GETTER);

        verify(ctxBuilder).traceId(CORRELATION);
        ArgumentCaptor<String> spanId = ArgumentCaptor.forClass(String.class);
        verify(ctxBuilder).spanId(spanId.capture());
        assertThat(spanId.getValue()).matches("[0-9a-f]{16}");                        // valid span id
        assertThat(spanId.getValue()).isNotEqualTo("0000000000000000");              // non-zero (no all-zero hole)
        assertThat(spanId.getValue()).isNotEqualTo(CORRELATION.substring(0, 16));    // NOT sliced from the trace id
        verify(ctxBuilder).sampled(true);                                            // alwaysOn -> sampled
    }

    @Test
    void extract_freshRoot_samplerDrops_sampledFalse() {
        TraceContext.Builder ctxBuilder = mock(TraceContext.Builder.class, RETURNS_SELF);
        Map<String, String> carrier = new HashMap<>();
        carrier.put("Esq-Correlation-ID", CORRELATION);

        propagator(Sampler.alwaysOff(), ctxBuilder).extract(carrier, GETTER);

        verify(ctxBuilder).sampled(false); // decision comes from the sampler, not a hardcoded true
    }

    @Test
    void extract_mismatchedUpstreamTraceparent_treatedAsRoot() {
        TraceContext.Builder ctxBuilder = mock(TraceContext.Builder.class, RETURNS_SELF);
        Map<String, String> carrier = new HashMap<>();
        carrier.put("Esq-Correlation-ID", CORRELATION);
        carrier.put("traceparent", "00-ffffffffffffffffffffffffffffffff-" + WIRE_SPAN + "-01"); // trace id differs

        propagator(Sampler.alwaysOn(), ctxBuilder).extract(carrier, GETTER);

        verify(ctxBuilder).traceId(CORRELATION);          // authoritative
        ArgumentCaptor<String> spanId = ArgumentCaptor.forClass(String.class);
        verify(ctxBuilder).spanId(spanId.capture());
        assertThat(spanId.getValue()).isNotEqualTo(WIRE_SPAN); // NOT the mismatched wire parent -> a fresh root
    }
}

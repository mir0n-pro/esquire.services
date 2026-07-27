package pro.mir0n.esquire.backend.o11y;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Unit coverage for the shared W3C trace-context helpers (v1.2.11 O2/T3). Pure string logic -- the trace id
// is 32 hex, the span id is 16 hex, and the trace id is ALWAYS the correlationId (authoritative).
class W3CTraceContextTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c"; // 32 hex
    private static final String SPAN_ID = "b7ad6b7169203331";                  // 16 hex

    @Test
    void isTraceId_acceptsThirtyTwoLowercaseHexNonZero() {
        assertThat(W3CTraceContext.isTraceId(TRACE_ID)).isTrue();
    }

    @Test
    void isTraceId_rejectsWrongLengthUppercaseNonHexAllZeroAndNull() {
        assertThat(W3CTraceContext.isTraceId(null)).isFalse();
        assertThat(W3CTraceContext.isTraceId("")).isFalse();
        assertThat(W3CTraceContext.isTraceId("0af7")).isFalse();                                  // too short
        assertThat(W3CTraceContext.isTraceId(TRACE_ID + "0")).isFalse();                          // too long
        assertThat(W3CTraceContext.isTraceId("0AF7651916CD43DD8448EB211C80319C")).isFalse();      // uppercase
        assertThat(W3CTraceContext.isTraceId("0af7651916cd43dd8448eb211c80319g")).isFalse();      // non-hex 'g'
        assertThat(W3CTraceContext.isTraceId("00000000000000000000000000000000")).isFalse();      // all zero
    }

    @Test
    void isSpanId_acceptsSixteenLowercaseHexNonZero() {
        assertThat(W3CTraceContext.isSpanId(SPAN_ID)).isTrue();
    }

    @Test
    void isSpanId_rejectsWrongLengthNonHexAllZeroAndNull() {
        assertThat(W3CTraceContext.isSpanId(null)).isFalse();
        assertThat(W3CTraceContext.isSpanId("b7ad6b71")).isFalse();          // too short
        assertThat(W3CTraceContext.isSpanId(TRACE_ID)).isFalse();            // 32 hex is not a span id
        assertThat(W3CTraceContext.isSpanId("b7ad6b716920333z")).isFalse();  // non-hex 'z'
        assertThat(W3CTraceContext.isSpanId("0000000000000000")).isFalse();  // all zero
    }

    @Test
    void build_composesTraceparentWithCorrelationIdAsTraceIdAndSpanFlags() {
        SpanContext sc = SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());

        // the trace id in the wire is the correlationId, NOT the span's own trace id
        String result = W3CTraceContext.build("11111111111111111111111111111111", sc);

        assertThat(result).isEqualTo("00-11111111111111111111111111111111-" + SPAN_ID + "-01");
    }

    @Test
    void build_carriesUnsampledFlag() {
        SpanContext sc = SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());

        String result = W3CTraceContext.build(TRACE_ID, sc);

        assertThat(result).endsWith("-00");
    }

    @Test
    void remoteParent_rebuildsParentWithTraceIdForcedToCorrelationId() {
        // incoming traceparent carries a DIFFERENT trace id; the parent must be forced to the correlationId
        String incoming = "00-ffffffffffffffffffffffffffffffff-" + SPAN_ID + "-01";

        SpanContext parent = W3CTraceContext.remoteParent(incoming, TRACE_ID);

        assertThat(parent).isNotNull();
        assertThat(parent.getTraceId()).isEqualTo(TRACE_ID);           // forced to correlationId
        assertThat(parent.getSpanId()).isEqualTo(SPAN_ID);             // parent span id kept from the wire
        assertThat(parent.isSampled()).isTrue();
        assertThat(parent.isRemote()).isTrue();
    }

    @Test
    void remoteParent_nullWhenCorrelationIdInvalid() {
        String incoming = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

        assertThat(W3CTraceContext.remoteParent(incoming, "not-a-trace-id")).isNull();
    }

    @Test
    void remoteParent_nullWhenTraceparentMissingOrHasInvalidSpanId() {
        assertThat(W3CTraceContext.remoteParent(null, TRACE_ID)).isNull();
        assertThat(W3CTraceContext.remoteParent("00-" + TRACE_ID + "-0000000000000000-01", TRACE_ID)).isNull();
    }

    @Test
    void remoteParent_unsampledFlagPreserved() {
        String incoming = "00-" + TRACE_ID + "-" + SPAN_ID + "-00";

        SpanContext parent = W3CTraceContext.remoteParent(incoming, TRACE_ID);

        assertThat(parent).isNotNull();
        assertThat(parent.isSampled()).isFalse();
    }
}

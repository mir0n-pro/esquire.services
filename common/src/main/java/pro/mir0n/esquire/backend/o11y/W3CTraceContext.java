/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created: the shared W3C trace-context helpers (v1.2.11 O2/T3) used by both the bus-hop
 *                   tracer (EsqRodTracer) and the async-boundary primitive (EsqAsyncTrace). The trace id is
 *                   ALWAYS the correlationId (authoritative); a traceparent only carries the parent span id.
 * 07/17/2026 mir0n  isW3cTraceId now delegates to common.EsqUtils so the trace-id shape cannot drift (I35);
 *                   tracestate kept empty by design, note added (I37).
 */
package pro.mir0n.esquire.backend.o11y;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

/** W3C traceparent build / parse, trace-id = correlationId. Package-private -- an o11y implementation detail. */
final class W3CTraceContext {

    private W3CTraceContext() {
    }

    /** Build a traceparent from the correlationId (trace id) and a span context (parent span id + flags). */
    static String build(String correlationId, SpanContext sc) {
        return "00-" + correlationId + "-" + sc.getSpanId() + "-" + sc.getTraceFlags().asHex();
    }

    /** Rebuild the parent span carried by {@code traceparent} with the trace id forced to {@code correlationId}
     *  (authoritative). Null when there is no correlationId or no valid parent span id. */
    static SpanContext remoteParent(String traceparent, String correlationId) {
        SpanContext ret = null;
        if (isTraceId(correlationId) && traceparent != null) {
            String[] parts = traceparent.split("-");        // W3C: version-traceId-spanId-flags
            if (parts.length >= 4 && isSpanId(parts[2])) {
                TraceFlags flags = "00".equals(parts[3]) ? TraceFlags.getDefault() : TraceFlags.getSampled();
                // TraceState is EMPTY by design -- no baggage / tracestate (I37, reviewed + accepted): app-level
                // context (user identity, the rootPath "tenant") rides the JWT bearer, RELAYED to REST downstreams
                // by the Token Relay, and bus events carry their own payload -- so there is nothing to propagate in
                // tracestate. Add a baggage propagator only if a real cross-hop app-context need ever appears.
                ret = SpanContext.createFromRemoteParent(correlationId, parts[2], flags, TraceState.getDefault());
            }
        }
        return ret;
    }

    /** 32 lowercase hex, not all zero. ONE authority for the trace-id shape in Java -- delegates to
     *  {@link pro.mir0n.esquire.common.EsqUtils#isW3cTraceId} so this file cannot drift from it (I35). */
    static boolean isTraceId(String s) {
        return pro.mir0n.esquire.common.EsqUtils.isW3cTraceId(s);
    }

    /** 16 lowercase hex, not all zero. */
    static boolean isSpanId(String s) {
        return s != null && s.length() == 16 && isHex(s) && !isAllZero(s);
    }

    private static boolean isHex(String s) {
        boolean ret = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                ret = false;
                break;
            }
        }
        return ret;
    }

    private static boolean isAllZero(String s) {
        boolean ret = true;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') {
                ret = false;
                break;
            }
        }
        return ret;
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created: the async-boundary trace primitive (v1.2.11 O2/T3). When work is HANDED OFF to
 *                   another thread (a queue worker), the OTel span does not follow -- only the correlationId
 *                   travels (in the queued item / MDC). capture() grabs the current traceparent on the
 *                   submitting thread (trace id = correlationId); continueIn() re-establishes it on the worker
 *                   thread so the worker's spans nest in the SAME trace under the submitting span. NOOP when
 *                   tracing is off -- continueIn just runs the work.
 */
package pro.mir0n.esquire.backend.o11y;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Carries a trace across an in-process async boundary (e.g. the enyMan move queue). The submitting thread calls
 * {@link #capture} while its span is current and stores the result on the queued item; the worker thread passes
 * it to {@link #continueIn}, which opens a span (trace id = correlationId) parented at the submitting span, so
 * the worker's {@code @EsqTraced} / {@code EsqTraceMark} / bus spans join the originating request's trace.
 */
public final class EsqAsyncTrace {

    // Low-cardinality observation name for the async-continuation span (governed by the esq.* gate).
    private static final String OBS_NAME = "esq.async";

    private EsqAsyncTrace() {
    }

    private static volatile ObservationRegistry registry = ObservationRegistry.NOOP;

    /** Register the app's ObservationRegistry (ObservabilityConfig, at startup, only when observability is enabled). */
    public static void setRegistry(ObservationRegistry observationRegistry) {
        registry = (observationRegistry != null) ? observationRegistry : ObservationRegistry.NOOP;
    }

    /**
     * On the SUBMITTING thread (its span current), capture the traceparent to carry on the queued item -- trace id
     * = correlationId (authoritative), span id = the current span (the async work's parent). Null when there is no
     * current span; the correlationId still travels on the item either way.
     */
    public static String capture(String correlationId) {
        String ret = null;
        SpanContext sc = Span.current().getSpanContext();
        if (sc.isValid() && W3CTraceContext.isTraceId(correlationId)) {
            ret = W3CTraceContext.build(correlationId, sc);
        }
        return ret;
    }

    /**
     * On the WORKER thread, run {@code work} inside a span named {@code label} that continues the captured trace
     * (parented at the submitting span, trace id = correlationId). When there is nothing to anchor (no traceparent
     * / tracing off), just runs the work.
     */
    public static void continueIn(String traceparent, String correlationId, String label, Runnable work) {
        SpanContext parent = W3CTraceContext.remoteParent(traceparent, correlationId);
        if (parent == null) {
            work.run();
        } else {
            Context ctx = Context.root().with(Span.wrap(parent));
            try (Scope scope = ctx.makeCurrent()) {
                // The async worker's replica shows in the span's service badge (collector rewrites service.name
                // to service.instance.id on the traces pipeline), so the span name is plain.
                Observation.createNotStarted(OBS_NAME, registry).contextualName(label).observe(work);
            }
        }
    }
}

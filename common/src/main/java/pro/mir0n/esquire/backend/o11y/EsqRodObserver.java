/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created: the OTel implementation of the messaging-declared o11y.IRodTracer (v1.2.11 O2/T3),
 *                   built on the raw OTel Tracer (not the ObservationRegistry) so each bus leg carries an
 *                   explicit span kind: outbound() opens a PRODUCER span on the send and returns a traceparent
 *                   whose TRACE ID is the correlationId (authoritative) and whose span id is that span;
 *                   inbound() rebuilds the remote parent and runs the consumer worker inside a CONSUMER span, so
 *                   the worker's marks nest under the producer span in ONE trace. aliveOutbound() /
 *                   aliveInbound() are the same pair for the RR liveness round-trip (aliveOutbound opens a ROOT
 *                   trace when the send has no current span -- a CLIENT TestRequest off the heartbeat cadence);
 *                   newTraceId() mints a fresh W3C-shaped correlation id. Span names carry no instance id -- the
 *                   collector badges each span with its replica. Carries the msg-bus-alive-trace opt-in
 *                   (aliveTrace()). Registered into RodTracerHolder by TracingConfig
 *                   only when tracing is enabled; off = the bus keeps NOOP.
 * 07/11/2026 mir0n  v1.2.11 O1/T5 -- renamed from EsqRodTracer and widened to the ONE bus observer: it now
 *                   implements o11y.IRodObserver (IRodTracer + IRodMeters), so the SAME object that traces the
 *                   bus hop also meters it (a later exemplar can tag a metric sample with its trace id). The
 *                   ctor takes the Micrometer MeterRegistry alongside the OTel Tracer. The meter side emits
 *                   messaging.send.total / receive.total / error.total (counters, tagged bus-id / slot /
 *                   msg-type; error also by leg), messaging.send.duration (timer), messaging.retry.backoff /
 *                   retry.dropped, and registers the messaging.feed.depth / messaging.retry.held GAUGES.
 *                   Both gauges are built .strongReference(true): the state object is the supplier lambda
 *                   itself and nothing else holds it, so Micrometer's default WEAK reference lets it be GC'd
 *                   and the gauge then reports NaN. ObservabilityConfig.esqRodObserverRegistrar registers it.
 * 07/11/2026 mir0n  v1.2.11 O1/T7 phase A -- registerFeedDepth / registerRetryHeld no longer build their gauge:
 *                   both hand off to EsqGauge.register(), which owns Gauge.builder and always applies
 *                   strongReference(true). The hand-written strongReference at each call site is gone, and with
 *                   it the last raw Gauge.builder in the codebase; the io.micrometer Gauge import drops out
 */
package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.IdGenerator;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.messaging.o11y.IRodObserver;

import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * The host implementation of the ONE bus-hop observer -- both the OTel trace side ({@code IRodTracer}) and the
 * Micrometer meter side ({@code IRodMeters}) in one object, so a single instance carries the {@link Tracer} AND the
 * {@link MeterRegistry} and both bus seams read the same registered observer.
 * <p>
 * Trace side: the trace id is ALWAYS the correlationId (the W3C-shaped id the gateway settled in T2 --
 * {@code traceId == correlationId}); the wire {@code traceparent} only carries the producer's span id, so the
 * consumer span nests under it. The two bus legs are built on the raw OTel {@link Tracer} (not a Micrometer
 * Observation) so their span KIND is set explicitly: a bus hop is an asynchronous message, whose consumer starts
 * AFTER the producer span ends -- modelling it as an OTel PRODUCER/CONSUMER pair (vs. the default INTERNAL) tells a
 * viewer to render it as an async messaging link. The consumer span is made current while the worker runs, so the
 * worker's {@code @EsqTraced} / {@code EsqTraceMark} marks nest under it in the SAME trace.
 * <p>
 * Meter side: explicit {@code messaging.*} meters emitted at the x-rod send / receive / error seams + the
 * send-retry sublayer -- counters (send / receive / error / retry.dropped), a send-duration timer, a retry-backoff
 * summary, and two gauges (feed depth, retry hold). These are SEPARATE from the raw trace spans (the raw spans
 * still produce no Observation metrics); tags are bounded (bus-id / slot / msgType / leg) -- no correlationId /
 * entity id.
 */
public final class EsqRodObserver implements IRodObserver {

    private final Tracer tracer;
    private final MeterRegistry registry;

    // The host's opt-in for the RR liveness round-trip trace (esquire.tracing.msg-bus-alive-trace). Held here,
    // not in the bus's holder: it is this observer's own setting, and the bus reads it through the hook.
    private final boolean aliveTrace;

    public EsqRodObserver(Tracer otelTracer, MeterRegistry meterRegistry, boolean msgBusAliveTrace) {
        this.tracer = otelTracer;
        this.registry = meterRegistry;
        this.aliveTrace = msgBusAliveTrace;
    }

    @Override
    public boolean aliveTrace() {
        return aliveTrace;
    }

    // Span attributes: the acting leg, and (on receive) the SENDER's rod-id (<app>.<instance>). Bounded per
    // deployment -> low cardinality.
    private static final String SLOT_KEY = "esq.bus.slot";
    private static final String FROM_KEY = "esq.bus.from";       // the SENDER's rod-id (on receive)
    private static final String INST_KEY = "esq.bus.instance";   // the acting instance's OWN rod-id
    private static final String BUS_KEY  = "esq.bus.id";         // the bus the alive round-trip rides (its name is the msg type)

    // Span name = direction + bus id (e.g. "send to audit-c"). WHICH replica acted is shown by the span's
    // service badge -- the collector rewrites service.name to service.instance.id (the rod-id) on the traces
    // pipeline -- and is also carried on the esq.bus.instance attribute, so the name need not repeat it.
    private static String spanName(String direction, String busId) {
        return direction + " " + busId;
    }

    @Override
    public String outbound(String correlationId, String busId, String slotId, String ownRodId) {
        String ret = null;
        // Only when the producer is inside a span (its service command) and the correlationId is a real trace id.
        if (W3CTraceContext.isTraceId(correlationId) && Span.current().getSpanContext().isValid()) {
            // The SENDING service's "send to <bus-id>" span, a PRODUCER child of the current command span. Capture
            // its span id for the wire traceparent (trace id = correlationId), then close it -- a publish is a point
            // event; the consumer's receive span starts later, on its own thread, parented at this span id.
            Span send = tracer.spanBuilder(spanName("send to", busId))
                    .setSpanKind(SpanKind.PRODUCER)
                    .setParent(Context.current())
                    .setAttribute(SLOT_KEY, slotId != null ? slotId : "")
                    .setAttribute(INST_KEY, ownRodId != null ? ownRodId : "")
                    .startSpan();
            try {
                SpanContext sc = send.getSpanContext();
                if (sc.isValid()) {
                    ret = W3CTraceContext.build(correlationId, sc);
                }
            } finally {
                send.end();
            }
        }
        return ret;
    }

    @Override
    public void inbound(String traceparent, String correlationId, String busId, String slotId, String fromRodId, String ownRodId, Runnable worker) {
        SpanContext parent = W3CTraceContext.remoteParent(traceparent, correlationId);
        if (parent == null) {
            // No producer send to anchor (untraced producer / no correlationId) -- run plain, no bus span.
            worker.run();
        } else {
            // The RECEIVING service's "receive from <bus-id>" span, a CONSUMER nested under the producer's send span
            // (trace id = correlationId). Attributes: the sender (from = the message's rod-id) and THIS receiving
            // instance (own rod-id), so which replica received is visible. Made current so the worker's marks nest.
            Context parentCtx = Context.root().with(Span.wrap(parent));
            Span recv = tracer.spanBuilder(spanName("receive from", busId))
                    .setSpanKind(SpanKind.CONSUMER)
                    .setParent(parentCtx)
                    .setAttribute(SLOT_KEY, slotId != null ? slotId : "")
                    .setAttribute(FROM_KEY, fromRodId != null ? fromRodId : "")
                    .setAttribute(INST_KEY, ownRodId != null ? ownRodId : "")
                    .startSpan();
            try (Scope scope = recv.makeCurrent()) {
                worker.run();
            } finally {
                recv.end();
            }
        }
    }

    /** The trace id shape is OURS, not the bus's: a fresh W3C-shaped correlation id (traceId == correlationId). */
    @Override
    public String newTraceId() {
        return EsqUtils.settleCorrelationId(null);
    }

    @Override
    public String aliveOutbound(String correlationId, String busId, String label, String ownRodId, boolean asRoot) {
        String ret = null;
        if (W3CTraceContext.isTraceId(correlationId)) {
            Context parent;
            if (asRoot) {
                // No current span (a client TestRequest minted off the cadence) -- force a ROOT trace whose id is
                // the correlationId via a phantom parent (random span id, no relation to the trace id); the span
                // renders as the trace root and the round-trip becomes its own self-initiated trace.
                SpanContext phantom = SpanContext.create(correlationId, IdGenerator.random().generateSpanId(),
                        TraceFlags.getSampled(), TraceState.getDefault());
                parent = Context.root().with(Span.wrap(phantom));
            } else {
                // A server HeartBeat sent INSIDE its receive -- nest under the current (consumer) span.
                parent = Context.current();
            }
            Span send = tracer.spanBuilder(label)   // the span name IS the msg type (TestRequest / HeartBeat)
                    .setSpanKind(SpanKind.PRODUCER)
                    .setParent(parent)
                    .setAttribute(BUS_KEY, busId != null ? busId : "")
                    .setAttribute(INST_KEY, ownRodId != null ? ownRodId : "")
                    .startSpan();
            try {
                SpanContext sc = send.getSpanContext();
                if (sc.isValid()) {
                    ret = W3CTraceContext.build(correlationId, sc);
                }
            } finally {
                send.end();
            }
        }
        return ret;
    }

    @Override
    public void aliveInbound(String traceparent, String correlationId, String busId, String label, String fromRodId, String ownRodId, Runnable worker) {
        SpanContext parent = W3CTraceContext.remoteParent(traceparent, correlationId);
        if (parent == null) {
            worker.run();
        } else {
            // The receiving leg of the liveness round-trip: a CONSUMER span named by the msg type, nested under the
            // producer span carried on the wire. Made current so a server's HeartBeat send (aliveOutbound, asRoot
            // false) nests under it.
            Context parentCtx = Context.root().with(Span.wrap(parent));
            Span recv = tracer.spanBuilder(label)
                    .setSpanKind(SpanKind.CONSUMER)
                    .setParent(parentCtx)
                    .setAttribute(BUS_KEY, busId != null ? busId : "")
                    .setAttribute(FROM_KEY, fromRodId != null ? fromRodId : "")
                    .setAttribute(INST_KEY, ownRodId != null ? ownRodId : "")
                    .startSpan();
            try (Scope scope = recv.makeCurrent()) {
                worker.run();
            } finally {
                recv.end();
            }
        }
    }

    // ------------------------------------------------------------------ meter side (IRodMeters)

    // Micrometer tag values cannot be null; the engine may pass null slot/msgType on an unidentified leg.
    private static String nz(String s) {
        return s != null ? s : "";
    }

    @Override
    public void sent(String busId, String slotId, String msgType) {
        registry.counter("messaging.send.total", "bus-id", nz(busId), "slot", nz(slotId), "msgType", nz(msgType))
                .increment();
    }

    @Override
    public void sendDuration(String busId, String slotId, String msgType, long nanos) {
        registry.timer("messaging.send.duration", "bus-id", nz(busId), "slot", nz(slotId), "msgType", nz(msgType))
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void received(String busId, String slotId, String msgType) {
        registry.counter("messaging.receive.total", "bus-id", nz(busId), "slot", nz(slotId), "msgType", nz(msgType))
                .increment();
    }

    @Override
    public void error(String busId, String slotId, String msgType, String leg) {
        registry.counter("messaging.error.total", "bus-id", nz(busId), "slot", nz(slotId), "msgType", nz(msgType),
                "leg", nz(leg)).increment();
    }

    @Override
    public void retryBackoff(String busId, long backoffMs) {
        registry.summary("messaging.retry.backoff", "bus-id", nz(busId)).record(backoffMs);
    }

    @Override
    public void retryDropped(String busId, String msgType) {
        registry.counter("messaging.retry.dropped", "bus-id", nz(busId), "msgType", nz(msgType)).increment();
    }

    @Override
    public void registerFeedDepth(String busId, String slotId, IntSupplier depth) {
        EsqGauge.register(registry, "messaging.feed.depth", depth, "bus-id", nz(busId), "slot", nz(slotId));
    }

    @Override
    public void registerRetryHeld(String busId, String slotId, IntSupplier held) {
        EsqGauge.register(registry, "messaging.retry.held", held, "bus-id", nz(busId), "slot", nz(slotId));
    }
}

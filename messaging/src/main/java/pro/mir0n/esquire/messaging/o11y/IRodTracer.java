/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created: the bus-hop trace hook, DECLARED by the messaging bus (v1.2.11 O2/T3). The bus
 *                   deals only in String + Runnable through it and imports nothing above itself; the OTel-backed
 *                   implementation is the host application's and is handed in via RodTracerHolder. Legs: outbound()
 *                   on the PRODUCER thread stamps the traceparent (parent span) onto the event; inbound() on the
 *                   CONSUMER (pool) thread runs the worker inside a span continuing the producer's trace;
 *                   aliveOutbound() / aliveInbound() are the same pair for the RR liveness round-trip;
 *                   newTraceId() mints a trace id in the tracer's own shape, so the bus never has to know what
 *                   one looks like; aliveTrace() carries the host's opt-in for the round-trip trace. traceId is
 *                   ALWAYS the correlationId (authoritative); the traceparent only carries the parent span id.
 *                   NOOP when tracing is off = zero cost.
 */
package pro.mir0n.esquire.messaging.o11y;

/**
 * The bus-hop trace hook -- the generic seam the x-rod engine calls so a trace crosses the messaging bus without
 * the {@code messaging} module ever depending on OpenTelemetry -- or on anything above it. The hook is DECLARED
 * beside its only caller, the x-rod engine; a concrete implementation is supplied by the host application's
 * observability layer and registered through {@link RodTracerHolder}. Off by default ({@link #NOOP}) -- the engine
 * pays nothing when tracing is disabled.
 */
public interface IRodTracer {

    /**
     * On the PRODUCER thread (the {@code transmit} caller, where the producer's span is current), open a short
     * "send to {@code busId}" span on the SENDING service and return the W3C {@code traceparent} pointing at it.
     * Its trace id is {@code correlationId} (authoritative). The consumer nests under THIS send span, so the trace
     * reads "<service> send to <bus-id>" then "<service> receive from <bus-id>" -- the bus is the medium, never the
     * actor. {@code busId} names the bus (ether); {@code slotId} the sending leg; {@code ownRodId} the SENDING
     * INSTANCE ({@code <app>.<instance>}), so x2 replicas are distinguishable. Null when there is no current span.
     */
    String outbound(String correlationId, String busId, String slotId, String ownRodId);

    /**
     * On the CONSUMER (pool) thread, open a "receive from {@code busId}" span on the RECEIVING service and run
     * {@code worker} inside it. Trace id = {@code correlationId} (authoritative), parent = the producer's send span
     * carried by {@code traceparent}. {@code busId} names the bus, {@code slotId} the receiving leg, {@code fromRodId}
     * the SENDER's rod-id, {@code ownRodId} the RECEIVING INSTANCE's rod-id -- so which replica received is visible.
     * Any {@code @EsqTraced} / {@code EsqTraceMark} marks the worker fires nest under it. A NOOP tracer runs the worker.
     */
    void inbound(String traceparent, String correlationId, String busId, String slotId, String fromRodId, String ownRodId, Runnable worker);

    /**
     * Whether the RR liveness round-trip (a CLIENT TestRequest and the SERVER HeartBeat it draws) should be
     * traced -- the host's opt-in {@code esquire.tracing.msg-bus-alive-trace}. It rides on the tracer because it
     * is the tracer's own setting: heartbeats fire on a steady cadence, so tracing them is a deliberate choice.
     * False on a NOOP tracer.
     */
    boolean aliveTrace();

    /**
     * Mint a fresh trace id in the tracer's own id shape (W3C: 32 lowercase hex, non-zero) -- used by the RR
     * liveness probe, whose TestRequest starts a trace of its own off the heartbeat cadence. The bus does not
     * know what a trace id looks like, so it asks the tracer. Null on a NOOP tracer (tracing off): the caller
     * then falls back to its ordinary correlation id.
     */
    String newTraceId();

    /**
     * Producer leg of an RR liveness round-trip (the opt-in msg-bus-alive-trace path). Opens a PRODUCER span named
     * {@code label} (e.g. "TestRequest" / "HeartBeat") and returns the wire {@code traceparent} (trace id =
     * {@code correlationId}). {@code asRoot} = true opens a fresh ROOT trace -- for a send with NO current span (a
     * client TestRequest minted off the heartbeat cadence); {@code asRoot} = false nests under the current span (a
     * server HeartBeat sent inside its receive). Unlike {@link #outbound}, a root send does not need a current span.
     * Null when tracing / alive-trace is off or the id is not a real trace id.
     */
    String aliveOutbound(String correlationId, String busId, String label, String ownRodId, boolean asRoot);

    /**
     * Consumer leg of an RR liveness round-trip: run {@code worker} (the session-message handling) inside a CONSUMER
     * span named {@code label}, nested under the producer span carried by {@code traceparent} (trace id =
     * {@code correlationId}). A NOOP tracer just runs the worker.
     */
    void aliveInbound(String traceparent, String correlationId, String busId, String label, String fromRodId, String ownRodId, Runnable worker);

    /** No-op tracer -- the engine's default when the host application has not registered an implementation (tracing off). */
    IRodTracer NOOP = new IRodTracer() {
        @Override public String outbound(String correlationId, String busId, String slotId, String ownRodId) {
            return null;
        }
        @Override public boolean aliveTrace() {
            return false;
        }
        @Override public String newTraceId() {
            return null;
        }
        @Override public void inbound(String traceparent, String correlationId, String busId, String slotId, String fromRodId, String ownRodId, Runnable worker) {
            worker.run();
        }
        @Override public String aliveOutbound(String correlationId, String busId, String label, String ownRodId, boolean asRoot) {
            return null;
        }
        @Override public void aliveInbound(String traceparent, String correlationId, String busId, String label, String fromRodId, String ownRodId, Runnable worker) {
            worker.run();
        }
    };
}

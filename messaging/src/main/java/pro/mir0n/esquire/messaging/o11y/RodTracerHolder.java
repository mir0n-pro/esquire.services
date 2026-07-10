/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created: the single-slot hand-off for the bus-hop trace hook (v1.2.11 O2/T3). The host
 *                   application's tracing config sets its IRodTracer once at startup (only when tracing is
 *                   enabled); the x-rod engine reads tracer() at each bus hop. Defaults to IRodTracer.NOOP so
 *                   the bus pays nothing when tracing is off / never registered. Holds the tracer and NOTHING
 *                   else -- the RR liveness round-trip switch rides on the tracer itself (IRodTracer.aliveTrace).
 */
package pro.mir0n.esquire.messaging.o11y;

/** The single hand-off point for the bus-hop tracer: the host application registers its implementation here,
 *  the messaging engine reads it. Volatile single-writer (startup) / many-reader (every bus hop); NOOP until
 *  registered. */
public final class RodTracerHolder {

    private RodTracerHolder() {
    }

    private static volatile IRodTracer tracer = IRodTracer.NOOP;

    /** Register the tracer (the host tracing config, at startup, only when tracing is enabled). Null restores NOOP. */
    public static void setTracer(IRodTracer rodTracer) {
        tracer = (rodTracer != null) ? rodTracer : IRodTracer.NOOP;
    }

    /** The current tracer -- NOOP unless the host application has registered an implementation. */
    public static IRodTracer tracer() {
        return tracer;
    }
}

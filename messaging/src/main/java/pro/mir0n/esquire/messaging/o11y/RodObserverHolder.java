/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/09/2026 mir0n  created (as RodTracerHolder): the single-slot hand-off for the bus-hop trace hook (v1.2.11
 *                   O2/T3). The host application's observability config sets its hook once at startup (only when
 *                   enabled); the x-rod engine reads it at each bus hop. Defaults to NOOP so the bus pays nothing
 *                   when observability is off / never registered.
 * 07/11/2026 mir0n  v1.2.11 O1/T5 -- generalised from RodTracerHolder to the ONE bus observer umbrella: holds a
 *                   single IRodObserver (trace + metrics); tracer() / meters() return it as each view. One object,
 *                   two views -- so a trace seam and a metric seam read the SAME registered observer.
 */
package pro.mir0n.esquire.messaging.o11y;

/** The single hand-off point for the bus-hop observer: the host application registers its implementation here (the
 *  ONE object doing both trace and metrics), the messaging engine reads it. Volatile single-writer (startup) /
 *  many-reader (every bus hop); NOOP until registered. {@link #tracer()} and {@link #meters()} are the two views of
 *  the one held observer. */
public final class RodObserverHolder {

    private RodObserverHolder() {
    }

    private static volatile IRodObserver observer = IRodObserver.NOOP;

    /** Register the observer (the host observability config, at startup, only when observability is enabled). Null
     *  restores NOOP. */
    public static void setObserver(IRodObserver rodObserver) {
        observer = (rodObserver != null) ? rodObserver : IRodObserver.NOOP;
    }

    /** The current observer -- NOOP unless the host application has registered an implementation. */
    public static IRodObserver observer() {
        return observer;
    }

    /** The trace view of the registered observer -- read at each trace seam. */
    public static IRodTracer tracer() {
        return observer;
    }

    /** The meter view of the registered observer -- read at each metric seam. */
    public static IRodMeters meters() {
        return observer;
    }
}

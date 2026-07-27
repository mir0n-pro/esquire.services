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
 * 07/15/2026 mir0n  v1.2.11 T11 -- registrar-vs-bus-start ordering tripwire (I11): a feedDepthAgainstNoop latch +
 *                   a develop-channel logger. noteFeedDepthAgainstNoop() latches when a feed-depth gauge is
 *                   registered while the observer is still NOOP; setObserver() logs an ERROR if a real observer is
 *                   installed AFTER that (the bus started before its registrar). Silent when observability is off
 *                   (setObserver never runs).
 */
package pro.mir0n.esquire.messaging.o11y;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The single hand-off point for the bus-hop observer: the host application registers its implementation here (the
 *  ONE object doing both trace and metrics), the messaging engine reads it. Volatile single-writer (startup) /
 *  many-reader (every bus hop); NOOP until registered. {@link #tracer()} and {@link #meters()} are the two views of
 *  the one held observer. */
public final class RodObserverHolder {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodObserverHolder.class.getName());

    private RodObserverHolder() {
    }

    private static volatile IRodObserver observer = IRodObserver.NOOP;

    // Tripwire for the registrar-vs-bus-start ordering. The bus is meant to start at ApplicationReadyEvent, AFTER
    // the observer registrar (an InitializingBean) has run at context refresh -- so a feed-depth gauge binds to the
    // real observer, not NOOP. If that ever regresses (the bus starts before the registrar), the gauge registers
    // against NOOP and silently never reports. runEngine LATCHES that here; setObserver (which runs ONLY when
    // observability is on) reports it -- so the o11y-OFF case, where NOOP is correct and setObserver never runs,
    // stays silent.
    private static volatile boolean feedDepthAgainstNoop = false;

    /** Register the observer (the host observability config, at startup, only when observability is enabled). Null
     *  restores NOOP. */
    public static void setObserver(IRodObserver rodObserver) {
        observer = (rodObserver != null) ? rodObserver : IRodObserver.NOOP;
        if (rodObserver != null && feedDepthAgainstNoop) {
            devLog.error("bus observer installed AFTER a feed-depth gauge was already registered against NOOP -- the "
                    + "bus started before the observer registrar (the InitializingBean ordering regressed). Those "
                    + "gauges are bound to NOOP and will never report. The bus MUST start at ApplicationReadyEvent, "
                    + "which is after every InitializingBean; do not move runEngine into a @PostConstruct / earlier "
                    + "init phase.");
        }
    }

    /** The engine's start phase calls this when it is about to register a feed-depth gauge and the observer is still
     *  NOOP. On its own this is harmless (o11y off); it becomes an error only if the observer is installed LATER --
     *  see {@link #setObserver}. */
    public static void noteFeedDepthAgainstNoop() {
        feedDepthAgainstNoop = true;
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

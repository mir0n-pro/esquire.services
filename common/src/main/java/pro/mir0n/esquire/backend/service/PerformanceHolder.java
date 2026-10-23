/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: a ThreadLocal RequestPerformance for a worker thread with no HTTP request behind it (a
 *                   bus request): set / get / clear
 */

package pro.mir0n.esquire.backend.service;

/**
 * The JPA-time tracker of a worker thread that serves a command with no HTTP request behind it (a bus
 * request). {@link PerformanceAspect} adds to it when the thread has no request scope. Pair {@link #set}
 * with {@link #clear} in a finally.
 */
public final class PerformanceHolder {

    private static final ThreadLocal<RequestPerformance> PERFORMANCE = new ThreadLocal<>();

    private PerformanceHolder() {}

    public static void set(RequestPerformance performance) {
        PERFORMANCE.set(performance);
    }

    public static RequestPerformance get() {
        return PERFORMANCE.get();
    }

    public static void clear() {
        PERFORMANCE.remove();
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: thread-bound holder for the unified EsqRequestContext. Set once per
 *                   request (JwtAuthenticationFilter on the request thread; the move-queue worker
 *                   on its thread), read via RequestContextUtils, cleared in a finally. ThreadLocal
 *                   because both the request thread and worker threads are pooled and reused.
 */

package pro.mir0n.esquire.backend.service;

/**
 * Thread-bound store for the current {@link EsqRequestContext}.
 *
 * Lifecycle contract: whoever calls {@link #set} MUST call {@link #clear} in a finally on the
 * same thread, so a pooled thread never leaks one request's identity into the next. The holder
 * is empty for unauthenticated / public paths and for any thread that has not been hydrated;
 * readers must tolerate a null context.
 */
public final class EsqContextHolder {

    private static final ThreadLocal<EsqRequestContext> CONTEXT = new ThreadLocal<>();

    private EsqContextHolder() {}

    public static void set(EsqRequestContext context) {
        CONTEXT.set(context);
    }

    /** Current context, or null if none is bound to this thread. */
    public static EsqRequestContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

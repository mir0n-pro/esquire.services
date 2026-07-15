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
 * 07/15/2026 mir0n  v1.2.11 T11 -- MDC control centralised here (I10): set() now also stamps correlationId /
 *                   requestId into MDC (the priority path), clear() removes them, and applyMessage(RodEvent) /
 *                   applyMessage(requestId, correlationId) stamp MDC ONLY -- for a bus worker that has no full
 *                   context to set. Bus listener workers no longer touch MDC directly.
 */

package pro.mir0n.esquire.backend.service;

import org.slf4j.MDC;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.RodEvent;

/**
 * Thread-bound store for the current {@link EsqRequestContext}.
 *
 * Lifecycle contract: whoever calls {@link #set} MUST call {@link #clear} in a finally on the
 * same thread, so a pooled thread never leaks one request's identity into the next. The holder
 * is empty for unauthenticated / public paths and for any thread that has not been hydrated;
 * readers must tolerate a null context.
 *
 * <p>MDC is populated FROM the context so a worker's log lines carry the correlationId / requestId. Two entry
 * points, and {@link #set} takes priority: a worker that establishes the full context calls {@link #set} (which
 * carries the ids into MDC) and needs nothing else. {@link #applyMessage} exists ONLY for the case where there
 * is no full context to set -- a bus listener's worker whose message (a RodEvent) has no rootPath, or an
 * async-hop worker whose item is not an EsqRequestContext -- and it stamps just the MDC ids. Both pair with
 * {@link #clear} in a finally, the same way MdcFilter stamps + clears a servlet request.
 */
public final class EsqContextHolder {

    private static final ThreadLocal<EsqRequestContext> CONTEXT = new ThreadLocal<>();

    private EsqContextHolder() {}

    /** Bind the unified context to this thread AND stamp its correlationId / requestId into MDC. Pair with
     *  {@link #clear} in a finally. This is the priority entry point: a worker that sets the context needs no
     *  separate {@link #applyMessage}. */
    public static void set(EsqRequestContext context) {
        CONTEXT.set(context);
        if (context != null) {
            applyMessage(context.requestId(), context.correlationId());
        }
    }

    /** Current context, or null if none is bound to this thread. */
    public static EsqRequestContext get() {
        return CONTEXT.get();
    }

    /** Clear ALL of this thread's request state -- the context thread-local AND the MDC ids -- so a pooled
     *  thread (servlet or broker) never leaks one request/message identity into the next. */
    public static void clear() {
        CONTEXT.remove();
        MDC.remove(EsqConstants.PD_REQUEST_ID);
        MDC.remove(EsqConstants.PD_CORRELATION_ID);
    }

    /** MDC only -- for a worker that does NOT call {@link #set} (no full context to establish): stamp the
     *  received message's correlationId / requestId so its log lines carry them. Pair with {@link #clear}. */
    public static void applyMessage(RodEvent event) {
        applyMessage(event.requestId(), event.correlationId());
    }

    /** Same, from already-extracted ids -- for an async-hop worker whose item is not a RodEvent (the taijitu
     *  Monad). Pair with {@link #clear}. */
    public static void applyMessage(String requestId, String correlationId) {
        if (requestId != null) {
            MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
        }
        if (correlationId != null) {
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);
        }
    }
}

/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: monad error-listener seam (v1.2.5 Taijitu refactor Step 2).
 *                   Worker-loop catch(Exception) routes recoverable faults here so a
 *                   poisoned item never silently kills the worker thread. Default impl
 *                   is LoggingErrorListener; replaceable per monad.
 */
package pro.mir0n.esquire.bizTree.taijitu;

/**
 * Notified when the monad worker recovers from a non-fatal exception while
 * processing a queue item (e.g. an NPE on a malformed event). The worker
 * logs + continues; this is the hook for additional reaction (metrics,
 * alerting). Default implementation: {@link LoggingErrorListener}.
 *
 * Fatal {@code Error}s (OOM, StackOverflow) are NOT routed here -- they are
 * left to propagate and kill the thread, which is correct for unrecoverable
 * JVM faults.
 */
@FunctionalInterface
public interface IErrorListener {
    void onError(String context, Throwable t);
}

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: the move command, queued from the /esq-move handler.
 *                   Worker processes it by calling the per-kind move service (org or usr),
 *                   then publishing move events + KC URQ, then decrementing the counter.
 *                   No CompletableFuture: handler returns 202 Accepted at submit time and
 *                   does not wait for the worker.
 * 07/09/2026 mir0n  v1.2.11 -- the record gains a traceparent component (last)
 */

package pro.mir0n.esquire.enyMan.queue;

import java.util.List;

/**
 * Move command payload captured at /esq-move submit time. All fields are the
 * arguments today's EnyManService.esquireCommandMove() takes plus the request
 * trace context so the worker can re-bind MDC when it starts processing.
 */
public record MoveCommandItem(
        int kind,
        String id,
        String distId,
        String rootPath,
        String uid,
        List<String> roles,
        String requestId,
        String correlationId,
        String traceparent
) implements MoveQueueItem {
}

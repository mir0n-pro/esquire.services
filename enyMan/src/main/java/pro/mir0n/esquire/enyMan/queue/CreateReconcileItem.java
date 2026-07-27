/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: a post-publish path-check task. Enqueued by EnyManService
 *                   immediately after a CREATE broadcast is published, but only when
 *                   inMove() is true. Worker re-reads parent path from JPA, compares
 *                   to pathAtPublish, fixes esq_entity_path + reissues EVENT_UPDATE_PATH
 *                   on drift. Carries no trace context: the worker reads the most recent
 *                   move's CID/RID from MDC (set by the preceding MoveCommandItem).
 * 07/23/2026 mir0n  v1.2.11 -- gains correlationId/requestId components: the item now carries the originating
 *                   create's ids, which the worker stamps itself (no reliance on leftover worker MDC)
 */

package pro.mir0n.esquire.enyMan.queue;

/**
 * Captures just enough of a CREATE broadcast to let the worker reconcile its path
 * against the (possibly updated) parent state once the queued moves ahead of it
 * have all run.
 *
 * @param entityId       the entity's PK (esq_entity_path.ep_pk)
 * @param kind           entity kind (drives the path-rule selection -- ACCT / admin USR
 *                       are path-parent-only, ORG / regular USR append own PK + ".")
 * @param parentId       the parent the CREATE pointed at -- used to re-read the current
 *                       parent path from esq_entity_path
 * @param pathAtPublish  the ep_path the CREATE put in its broadcast text; reconciliation
 *                       is a no-op if the expected path still equals this value
 * @param correlationId  the originating CREATE's correlationId, captured at submit off the
 *                       peer-create receive leg; the reconcile fix runs -- and reissues its
 *                       EVENT_UPDATE_PATH broadcast -- under this id, so the whole path-fix
 *                       stays correlated to the create it repairs
 * @param requestId      the originating CREATE's requestId, captured the same way
 */
public record CreateReconcileItem(
        String entityId,
        int kind,
        String parentId,
        String pathAtPublish,
        String correlationId,
        String requestId
) implements MoveQueueItem {
}

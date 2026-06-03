/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: sealed marker for items on the move queue (v1.2.6 Goal 3) --
 *                   either a MoveCommandItem (the move itself) or a CreateReconcileItem
 *                   (post-publish path check for a CREATE that happened during a move).
 */

package pro.mir0n.esquire.enyMan.queue;

/**
 * Item on the per-instance move queue. Two kinds, one queue, one worker, FIFO:
 *   - MoveCommandItem    -- run the actual /esq-move JPA work.
 *   - CreateReconcileItem -- after a CREATE published during a move, verify the
 *                            entity's ep_path against the (possibly updated) parent
 *                            path and reissue EVENT_UPDATE_PATH on drift.
 *
 * The single worker preserves FIFO order across both kinds, which is the
 * serialisation property race 8b needs.
 */
public sealed interface MoveQueueItem permits MoveCommandItem, CreateReconcileItem {
}

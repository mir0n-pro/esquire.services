/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: monad lifecycle status (v1.2.5 Taijitu refactor Step 2, Yang).
 */
package pro.mir0n.esquire.bizTree.taijitu;

/**
 * Lifecycle position of a {@link Monad}.
 *
 *   IDLE     -- no data; not loading. Incoming events are dropped.
 *   LOADING  -- an INIT command is bulk-loading esq2025 into the H2 table.
 *               Events buffer in the queue (queueEnabled ON) but are not
 *               applied (processingEnabled OFF).
 *   LOADED   -- load succeeded; processing enabled; serving + draining.
 *   FAILED   -- load failed; buffered events dropped; processing never enabled.
 */
public enum MonadStatus {
    IDLE,
    LOADING,
    LOADED,
    FAILED
}

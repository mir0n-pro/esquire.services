/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: INIT load seam (v1.2.5 Taijitu refactor Step 2, Yang).
 */
package pro.mir0n.esquire.bizTree.taijitu;

/**
 * The bulk-load seam invoked by a monad's INIT command. Production wiring
 * points this at the existing BizTreeCacheLoader::load; unit tests inject a
 * fake (e.g. a slow load) to exercise the queue + gate mechanics.
 *
 * Throwing marks the load FAILED; the monad then drops buffered events and
 * never enables processing.
 */
@FunctionalInterface
public interface ICacheLoad {
    void load() throws Exception;
}

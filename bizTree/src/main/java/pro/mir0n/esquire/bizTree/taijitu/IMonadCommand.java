/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: monad control commands (v1.2.5 Taijitu refactor Step 2, Yang).
 *                   CHECKSUM is a stub in the Yang slice -- it gains a body once Yin
 *                   exists to compare against (full Taijitu, night-watch sweep).
 */
package pro.mir0n.esquire.bizTree.taijitu;

/**
 * Control-plane commands a director issues to a {@link Monad}. Commands travel
 * the same single queue as events, but the worker always executes a command
 * (events are gated by processingEnabled; commands are not).
 *
 *   Init     -- CLEAN, then bulk-load esq2025 into the monad's H2 table.
 *   Clean    -- drop all cached data + buffered events; status -> IDLE.
 *   Checksum -- compute a content hash of the H2 table (stub until Yin lands).
 */
public sealed interface IMonadCommand
        permits IMonadCommand.Init, IMonadCommand.Clean, IMonadCommand.Checksum {

    record Init()     implements IMonadCommand {}
    record Clean()    implements IMonadCommand {}
    record Checksum() implements IMonadCommand {}
}

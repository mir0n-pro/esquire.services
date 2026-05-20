/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: unified queue item (v1.2.5 Taijitu refactor Step 2, Yang).
 *                   Commands and events ride the SAME single queue in arrival order.
 */
package pro.mir0n.esquire.bizTree.taijitu;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One item on a monad's single FIFO queue. Commands and events share the
 * queue so their relative arrival order is preserved by the single worker:
 *
 *   - {@link Cmd}   -- a control command (always executed by the worker).
 *   - {@link Event} -- an entity-broadcast event (applied only when the
 *                      monad's processingEnabled gate is open).
 */
public sealed interface IQueueItem permits IQueueItem.Cmd, IQueueItem.Event {

    record Cmd(IMonadCommand command) implements IQueueItem {}

    record Event(String eventType, String entityId, int entityKind, JsonNode textNode)
            implements IQueueItem {}
}

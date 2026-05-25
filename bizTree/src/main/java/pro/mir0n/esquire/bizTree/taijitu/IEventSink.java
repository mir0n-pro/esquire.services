/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: event-apply seam (v1.2.5 Taijitu refactor Step 2, Yang).
 */
package pro.mir0n.esquire.bizTree.taijitu;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The event-apply seam invoked by the monad worker for each buffered event
 * once processing is enabled. Production wiring points this at
 * MessageHandlerHub::dispatch; unit tests inject a recorder.
 */
@FunctionalInterface
public interface IEventSink {
    void apply(String eventType, String entityId, int entityKind, JsonNode textNode);
}

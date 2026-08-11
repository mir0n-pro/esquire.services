/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: event-apply seam (v1.2.5 Taijitu refactor Step 2, Yang).
 * 08/11/2026 mir0n  v1.2.12 -- apply() takes the event's change number; null means the producer sent none
 *                   and the event applies unguarded
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
    /**
     * @param changeNo the event's change number, or null when the producer sent none (apply unguarded).
     *                 WHICH counter it is follows the event type -- entity number on C/U/D, PATH number
     *                 on X. They are separate counters and are never compared with each other.
     */
    void apply(String eventType, String entityId, int entityKind, JsonNode textNode, Long changeNo);
}

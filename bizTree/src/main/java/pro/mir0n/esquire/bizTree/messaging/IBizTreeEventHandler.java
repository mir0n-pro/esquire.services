/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/25/2026 mir0n  created: event handler interface for entity broadcast dispatch map
 */
package pro.mir0n.esquire.bizTree.messaging;

import com.fasterxml.jackson.databind.JsonNode;

/** Handler for a single eventType + kindBits combination in the broadcast dispatch map. */
public interface IBizTreeEventHandler {
    void handle(String entityId, int entityKind, JsonNode textNode) throws Exception;
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: record {busId, slotId, xRod} -- a service-level reference to a catalog leg: the
 *                   logical bus name (esquire.<key>.messaging-bus) points at a catalog {bus-id, slot-id}, with an
 *                   OPTIONAL service-level x-rod node that fully overwrites the catalog leg's x-rod when present.
 */
package pro.mir0n.esquire.messaging;

import java.util.Map;

/** A service-level bus reference: the catalog {bus-id, slot-id} a logical name points at, plus an OPTIONAL
 *  x-rod node that fully overwrites the catalog leg's x-rod when present (null = use the catalog leg as-is). */
public record BusRef(String busId, String slotId, Map<String, Object> xRod) {

    public String busIdOr(String def) { return busId != null && !busId.isBlank() ? busId : def; }
}

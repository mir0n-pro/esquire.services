/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: record {slotId, xRod} -- one slot (leg) on a messaging bus: its slot-id plus the raw
 *                   x-Rod config node (a Map the catalog turns into XRodParams via XRodParams.from), kept distinct
 *                   from the microservice that hosts it.
 */
package pro.mir0n.esquire.messaging;

import java.util.Map;

/** A slot on a bus: its slot-id + the raw x-Rod config node (turned into XRodParams by the catalog). */
public record BusSlot(String slotId, Map<String, Object> xRod) {
}

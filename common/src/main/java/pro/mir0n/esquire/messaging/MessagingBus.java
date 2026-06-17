/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: record {busId, slot} -- one messaging bus in the catalog: a bus-id grouping its
 *                   slots (the wire lives inside each slot's x-Rod, so the bus is purely the aggregator).
 */
package pro.mir0n.esquire.messaging;

import java.util.List;

/** A bus in the catalog: a bus-id grouping its slots. */
public record MessagingBus(String busId, List<BusSlot> slot) {
}

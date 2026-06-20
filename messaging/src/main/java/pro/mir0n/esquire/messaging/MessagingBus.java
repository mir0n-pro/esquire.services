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
 * 06/17/2026 mir0n  the record component slot -> slots (a List); @Name("slot") keeps the config key `slot`
 * 06/19/2026 mir0n  config key slot -> slots (plural list key); @Name("slot") dropped -- the component is
 *                   already `slots`, so it binds the plural key directly
 */
package pro.mir0n.esquire.messaging;

import java.util.List;

/** A bus in the catalog: a bus-id grouping its {@code slots} (the plural config key in the topology). */
public record MessagingBus(String busId, List<BusSlot> slots) {
}

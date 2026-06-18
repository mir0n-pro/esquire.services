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
 */
package pro.mir0n.esquire.messaging;

import org.springframework.boot.context.properties.bind.Name;

import java.util.List;

/** A bus in the catalog: a bus-id grouping its slots. The config key stays {@code slot} (see the topology);
 *  {@link Name} maps it to the {@code slots} component so the code reads as the plural it holds. */
public record MessagingBus(String busId, @Name("slot") List<BusSlot> slots) {
}

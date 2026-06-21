/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/17/2026 mir0n  created: one network node on a request/response bus (transport.node[*]) as a typed record --
 *                   its id plus the wire fields a node MAY own (destination / topic / params). provider and
 *                   endpoint are NOT node-owned: the base transport owns the wire (see BusTransport.refinedWith).
 *                   Replaces the flattened-key string surgery XRodRR used to dig nodes out of the raw map.
 * 06/21/2026 mir0n  the topic component removed (queue-vs-topic is now the ActiveMQ pubSubDomain param)
 */
package pro.mir0n.esquire.messaging;

import java.util.Map;

/** One network node on an R&R bus: its {@code node-id} and the wire fields a node may override -- {@code
 *  destination} / {@code params}. {@code provider} / {@code endpoint} stay base-owned. */
public record BusNode(String nodeId, String destination, Map<String, String> params) {
}

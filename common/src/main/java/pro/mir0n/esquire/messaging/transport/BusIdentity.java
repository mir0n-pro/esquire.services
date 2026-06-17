/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: the transport (x-Rod) instance identity -- bus-id + service-id + rod-id, the
 *                   three the transport config already carries. NOT a message type: the message type is a
 *                   per-message thing the producer sets at publish, never a transport / bus property.
 */
package pro.mir0n.esquire.messaging.transport;

/** The transport (x-Rod) instance identity: which bus, and the producing service / rod instance on it. */
public record BusIdentity(String busId, String slotId, String rodId) {
}

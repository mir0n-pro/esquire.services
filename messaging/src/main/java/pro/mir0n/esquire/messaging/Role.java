/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: a service's role on a bus. A Request/Response bus has TWO nodes (request + response);
 *                   the role -- together with whether the service produces or consumes -- picks the node:
 *                   CLIENT produces requests / consumes responses, SERVER consumes requests / produces responses.
 *                   BROADCAST = a single-node bus (audit / entity broadcast); the role is irrelevant.
 */
package pro.mir0n.esquire.messaging;

/** A service's role on a bus: CLIENT / SERVER on a Request-Response bus; BROADCAST on a single-node bus. */
public enum Role {
    CLIENT, SERVER, BROADCAST
}

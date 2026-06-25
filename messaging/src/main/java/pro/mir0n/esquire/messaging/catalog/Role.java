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
 * 06/22/2026 mir0n  moved to messaging.catalog (was messaging). BROADCAST -> BOTH: the constants are now
 *                   CLIENT/SERVER/BOTH (BOTH = transmit + receive on a single-node bus).
 * 06/24/2026 mir0n  BOTH removed from the role list -- the constants are now CLIENT / SERVER (BOTH was unused)
 */
package pro.mir0n.esquire.messaging.catalog;

/** A service's role on a bus, picking which legs it runs. On a Request/Response bus: CLIENT = transmit on the
 *  request node + receive on the response node; SERVER = transmit on the response node + receive on the request
 *  node. On a single-node bus: CLIENT = receive only; SERVER = transmit only. */
public enum Role {
    CLIENT, SERVER
}

/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/22/2026 mir0n  created: the connection health a transport leg reports to the bus health indicator -- UP
 *                   (the broker connection is established), DOWN (it dropped / failed to connect), or UNKNOWN
 *                   (this transport cannot observe its connection -- e.g. a client that silently auto-reconnects).
 *                   worst() folds two legs (DOWN worst, then UNKNOWN, then UP).
 */
package pro.mir0n.esquire.messaging.transport;

/** The connection health a transport leg reports. A leg that can observe its broker connection answers UP or
 *  DOWN; a leg that cannot (no connection-state signal from the vendor client) answers UNKNOWN. The x-rod folds
 *  its transmit + receive legs via {@link #worst}; the bus health indicator forwards the per-bus result. */
public enum TransportHealth {
    UP, DOWN, UNKNOWN;

    /** The worse of two leg healths (DOWN worst, then UNKNOWN, then UP). A {@code null} leg (absent) is ignored;
     *  both null -> UNKNOWN. */
    public static TransportHealth worst(TransportHealth a, TransportHealth b) {
        TransportHealth ret;
        if (a == null && b == null) {
            ret = UNKNOWN;
        } else if (a == null) {
            ret = b;
        } else if (b == null) {
            ret = a;
        } else if (a == DOWN || b == DOWN) {
            ret = DOWN;
        } else if (a == UNKNOWN || b == UNKNOWN) {
            ret = UNKNOWN;
        } else {
            ret = UP;
        }
        return ret;
    }
}

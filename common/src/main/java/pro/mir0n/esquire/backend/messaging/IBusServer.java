/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: the server end of a request-response bus: serve(RodEvent) answers one request,
 *                   setResultHandler() takes where the answer goes
 */

package pro.mir0n.esquire.backend.messaging;

import pro.mir0n.messaging.RodEvent;

import java.util.function.Consumer;

/**
 * The work behind a request-response bus in the SERVER role: it serves each request and hands its answer to the
 * result handler, which {@link BusServerAdapter} points at the rod.
 */
public interface IBusServer {

    /** Serves one request on the rod's receive thread; the answer goes to the result handler. */
    void serve(RodEvent request);

    void setResultHandler(Consumer<RodEvent> handler);
}

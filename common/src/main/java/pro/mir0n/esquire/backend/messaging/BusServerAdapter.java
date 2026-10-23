/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: one SERVER rod per bus key: start() points the rod's worker at the IBusServer and the
 *                   server's result handler at the same rod's transmit
 */

package pro.mir0n.esquire.backend.messaging;

import pro.mir0n.messaging.IXRod;
import pro.mir0n.messaging.MessagingBus;

/**
 * One end of a request-response bus in the SERVER role: one rod, both legs. Each received request goes to the
 * server, and its answer is transmitted back on the same rod.
 *
 * <p>The request leg carries no selector -- shared work, any instance takes the next one. The answer carries the
 * requester's rod-id, so only the originating instance's RodID selector picks it up. The server runs on the
 * rod's receive pool, so that pool bounds how many requests run at once.
 */
public class BusServerAdapter {

    private final String busKey;
    private final IBusServer server;
    private IXRod rod;

    public BusServerAdapter(String busKey, IBusServer server) {
        this.busKey = busKey;
        this.server = server;
    }

    /** Takes the rod of {@code busKey} and points it at the server, the answers going back out the same rod. */
    public void start() {
        this.rod = MessagingBus.getInstance().getXRod(busKey);
        this.rod.setWorker(server::serve);   // role-support: throws if the rod has no receive leg
        this.rod.transmit(null);             // role-support: probe -- throws if the rod has no transmit leg
        server.setResultHandler(this.rod::transmit);
    }
}

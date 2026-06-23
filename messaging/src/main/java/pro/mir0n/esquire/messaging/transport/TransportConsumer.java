/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/21/2026 mir0n  created: the consume-side handle from ITransportProvider.openConsumer -- a listener created
 *                   PAUSED (subscribed, delivering nothing) plus a start() that begins delivery and a close()
 *                   that stops it + releases the provider's broker connection. The two-phase mirror of
 *                   TransportPublisher: an x-rod CREATES it at init() and START()s it (facade-driven) only once
 *                   the whole bus is wired.
 */
package pro.mir0n.esquire.messaging.transport;

/** A consumer onto one destination, created PAUSED: the provider has built + subscribed the listener but it
 *  delivers nothing until {@link #start()}. {@code start()} begins delivery (the vendor-level start -- e.g. a
 *  JMS listener container's {@code start()}); {@link #close()} stops the listener and releases the provider's
 *  own broker connection. The consume mirror of {@link TransportPublisher}: the x-rod opens it at {@code init},
 *  starts it at {@code start} (only once every leg is wired and the bus is ready to move traffic). */
public interface TransportConsumer extends AutoCloseable {

    /** Begin delivering received messages to the handler. Called by the x-rod's start phase (facade-driven),
     *  after the receive pool is live -- the vendor-level start where one is needed (a no-op where the client
     *  already delivers from creation). */
    void start();

    /** Wrap a start action + a connection closer as one consumer handle. */
    static TransportConsumer of(Runnable starter, AutoCloseable closer) {
        return new TransportConsumer() {
            @Override
            public void start() {
                starter.run();
            }

            @Override
            public void close() throws Exception {
                if (closer != null) {
                    closer.close();
                }
            }
        };
    }
}

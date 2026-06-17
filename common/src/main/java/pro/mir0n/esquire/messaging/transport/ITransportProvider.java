/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the transport-provider (tp) SPI -- a broker-agnostic seam implemented once per
 *                   transport module (tp-activemq / tp-kafka / tp-redis). openPublisher / openConsumer turn a
 *                   destination into a publish sink / consume registration over the neutral TransportMessage;
 *                   supportsConsume() lets a producer-only transport (e.g. a Redis stream) skip the consume leg.
 */
package pro.mir0n.esquire.messaging.transport;

import java.util.function.Consumer;

/**
 * Transport-provider SPI. Implemented once per transport module as the class
 * {@code pro.mir0n.esquire.tp.<name>.TransportProvider} (no-arg constructor) -- or any class named by its full
 * name in config. The caller resolves the provider by its config value (see {@link TransportProviders}), then
 * opens a publisher and/or a consumer on a destination. The provider builds its OWN broker client from the
 * settings' endpoint + the vendor-specific {@code params} group, so the framework holds no vendor knowledge.
 */
public interface ITransportProvider {

    /**
     * Opens a publisher (xy-rod side) onto {@code destination}: a sink the caller feeds
     * {@link TransportMessage}s into. The provider maps each message onto its wire form and sends it.
     */
    Consumer<TransportMessage> openPublisher(String destination, PublishSettings settings);

    /**
     * Starts consuming (xx-rod side) {@code destination}: the provider runs a listener that decodes each
     * received message into a {@link TransportMessage} and dispatches it to {@code handler}. The returned
     * {@link AutoCloseable} stops the listener.
     */
    AutoCloseable openConsumer(String destination, ConsumeSettings settings, Consumer<TransportMessage> handler);

    /**
     * Whether this transport has a consume leg. A producer-only transport (e.g. a Redis stream that IS the
     * append-only log) returns {@code false}; the caller then skips opening a consumer rather than relying
     * on a vendor name. Default {@code true}.
     */
    default boolean supportsConsume() {
        return true;
    }
}

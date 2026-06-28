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
 * 06/17/2026 mir0n  openPublisher returns a TransportPublisher (closeable) instead of a bare Consumer<TransportMessage>
 * 06/22/2026 mir0n  openConsumer returns a TransportConsumer (created PAUSED) instead of a bare AutoCloseable;
 *                   the listener subscribes at openConsumer but delivers nothing until TransportConsumer.start()
 * 06/24/2026 mir0n  supportsBothLegs() default-true SPI method -- whether a single rod can run both legs (transmit
 *                   + receive) on the transport's node; false for a produce-only transport, so the bus fails a
 *                   CLIENT role fast over one that cannot
 * 06/27/2026 mir0n  openConsumerOn(publisher, destination, settings, handler) default added -- open a consumer leg
 *                   that REUSES an already-open publisher's connection (the dual-leg, one-connection shape); the
 *                   default ignores the publisher and falls back to openConsumer (a separate connection)
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
     * {@link TransportMessage}s into. The provider maps each message onto its wire form and sends it. The
     * returned {@link TransportPublisher} is {@link AutoCloseable} -- {@code close()} releases the provider's
     * own broker connection (symmetric with {@link #openConsumer}'s returned handle).
     */
    TransportPublisher openPublisher(String destination, PublishSettings settings);

    /**
     * Opens a consumer (x-rod side) on {@code destination}, created PAUSED: the provider builds + subscribes a
     * listener that decodes each received message into a {@link TransportMessage} and dispatches it to
     * {@code handler}, but it delivers nothing until the returned {@link TransportConsumer}'s {@code start()}.
     * The handle's {@code start()} begins delivery (the vendor-level start), its {@code close()} stops the
     * listener. The x-rod creates it at {@code init} and starts it (facade-driven) only once the bus is wired.
     */
    TransportConsumer openConsumer(String destination, ConsumeSettings settings, Consumer<TransportMessage> handler);

    /**
     * Whether this transport has a consume leg. A producer-only transport (e.g. a Redis stream that IS the
     * append-only log) returns {@code false}; the caller then skips opening a consumer rather than relying
     * on a vendor name. Default {@code true}.
     */
    default boolean supportsConsume() {
        return true;
    }

    /**
     * Whether a single rod can run BOTH legs -- a transmit AND a receive -- on this transport's node (a consumer
     * can also publish to the node it consumes). ActiveMQ / Kafka return {@code true} (a destination / topic is
     * bidirectional); a producer-only transport (e.g. the XADD-only Redis stream) returns {@code false}. A
     * CLIENT role needs both legs -- its consume leg plus, with the alive protocol ON, a producer leg to
     * self-heartbeat onto the same node -- so the bus FAILS FAST on such a role over a transport that cannot run
     * both, rather than silently never delivering. Default {@code true}.
     */
    default boolean supportsBothLegs() {
        return true;
    }

    /**
     * Opens a consumer leg that REUSES the connection of an already-open {@code publisher} (the producer leg) on
     * the same rod -- the dual-leg, one-connection shape: at init the x-rod opens its publisher, then ADDs this
     * consumer on the publisher's connection (the x-rod decides to do this; the transport only advertises it CAN
     * via {@link #supportsBothLegs()}). Created PAUSED like {@link #openConsumer}. A provider that runs both legs
     * on one connection reuses it here (and can honor a {@code noLocal} param to drop this connection's OWN
     * publications -- the in-broker own-exclusion); the default IGNORES the publisher and falls back to a SEPARATE
     * {@link #openConsumer}, so a provider with no shared-connection notion works unchanged.
     */
    default TransportConsumer openConsumerOn(TransportPublisher publisher, String destination,
                                             ConsumeSettings settings, Consumer<TransportMessage> handler) {
        return openConsumer(destination, settings, handler);
    }
}

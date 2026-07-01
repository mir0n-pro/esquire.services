/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/17/2026 mir0n  created: the publish-side handle from ITransportProvider.openPublisher -- a
 *                   Consumer<TransportMessage> sink that is ALSO AutoCloseable, so the caller can close the
 *                   provider's own broker connection on shutdown (symmetric with openConsumer's AutoCloseable).
 * 06/22/2026 mir0n  health() default added (UNKNOWN unless the provider can observe it); of(sink,closer)
 *                   delegates to a new of(sink,closer,healthSupplier) overload that surfaces the supplier.
 * 06/30/2026 mir0n  send-retry seam: encode(TransportMessage) (build the concrete send unit ONCE, broker-free) +
 *                   dispatch(Object) throws (send the unit, throwing on a transport failure) defaults, both routed
 *                   through accept
 */
package pro.mir0n.esquire.messaging.transport;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** A publisher onto one destination: the message sink plus a {@link #close()} that releases the provider's own
 *  broker connection (the ActiveMQ connection factory / the Kafka producer factory / the Lettuce factory). The
 *  caller (an x-rod) closes it on shutdown -- the publish mirror of {@link ITransportProvider#openConsumer}'s
 *  returned {@link AutoCloseable}. */
public interface TransportPublisher extends Consumer<TransportMessage>, AutoCloseable {

    /** This publisher leg's broker-connection health -- {@code UNKNOWN} unless the provider can observe it. */
    default TransportHealth health() {
        return TransportHealth.UNKNOWN;
    }

    /**
     * Encode a neutral {@link TransportMessage} into THIS transport's own concrete send unit -- the prepared object
     * the actual vendor send works with. It is built ONCE so a send-retry relays the SAME unit on every attempt
     * instead of re-encoding per try; the unit is opaque to the framework (only {@link #dispatch} understands it).
     * It must be buildable WITHOUT the broker (the retry case is a DOWN broker), so a vendor whose message object
     * needs a live session prepares the stamped fields here and materializes the message inside {@link #dispatch}.
     * The default keeps the neutral message as the unit (a transport that does not separate encode from send).
     */
    default Object encode(TransportMessage message) {
        return message;
    }

    /**
     * Send the concrete unit from {@link #encode} and THROW on a transport failure -- the send-retry's failure
     * signal (the alive marks and the retry hold key off this throw). The default routes through the best-effort
     * {@link #accept} sink, so a transport that overrides neither gives no throw and the retry loop never engages on
     * it (kafka / the test doubles). The production transports override both.
     */
    default void dispatch(Object encoded) throws Exception {
        accept((TransportMessage) encoded);
    }

    /** Wrap a send sink + a connection closer as one publisher handle (health UNKNOWN -- the provider observes none). */
    static TransportPublisher of(Consumer<TransportMessage> sink, AutoCloseable closer) {
        return of(sink, closer, () -> TransportHealth.UNKNOWN);
    }

    /** Wrap a send sink + a connection closer + a connection-health source as one publisher handle. */
    static TransportPublisher of(Consumer<TransportMessage> sink, AutoCloseable closer, Supplier<TransportHealth> health) {
        return new TransportPublisher() {
            @Override
            public void accept(TransportMessage message) {
                sink.accept(message);
            }

            @Override
            public TransportHealth health() {
                return health.get();
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

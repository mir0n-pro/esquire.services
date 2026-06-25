/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/17/2026 mir0n  created: the x-rod transmit-leg outbound from RodTransportAdapter.publisher -- a
 *                   Consumer<RodEvent> that is ALSO AutoCloseable, so XRod.shutdown() releases the transport
 *                   publisher's broker connection (mirrors the AutoCloseable receive consumer).
 * 06/22/2026 mir0n  import RodEvent from messaging (was the same xrod package)
 * 06/22/2026 mir0n  health() default added; of(dispatcher,closer) surfaces the closer's TransportPublisher
 *                   health (the closer's health() iff it is a TransportPublisher, else UNKNOWN).
 */
package pro.mir0n.esquire.messaging.xrod;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** The transmit-leg outbound: each {@link RodEvent} is encoded + sent, and {@link #close()} releases the
 *  underlying transport publisher's connection. */
public interface RodPublisher extends Consumer<RodEvent>, AutoCloseable {

    /** The transmit leg's transport health -- delegated to the underlying {@link TransportPublisher} (UNKNOWN if none). */
    default TransportHealth health() {
        return TransportHealth.UNKNOWN;
    }

    /** Wrap a RodEvent dispatcher + a closer (the transport publisher) as one closeable outbound. The closer's
     *  {@link TransportPublisher#health()} is surfaced as this outbound's health. */
    static RodPublisher of(Consumer<RodEvent> dispatch, AutoCloseable closer) {
        Supplier<TransportHealth> health = (closer instanceof TransportPublisher tp)
                ? tp::health : () -> TransportHealth.UNKNOWN;
        return new RodPublisher() {
            @Override
            public void accept(RodEvent event) {
                dispatch.accept(event);
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

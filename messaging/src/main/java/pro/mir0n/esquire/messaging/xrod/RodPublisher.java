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
 */
package pro.mir0n.esquire.messaging.xrod;

import java.util.function.Consumer;

/** The transmit-leg outbound: each {@link RodEvent} is encoded + sent, and {@link #close()} releases the
 *  underlying transport publisher's connection. */
public interface RodPublisher extends Consumer<RodEvent>, AutoCloseable {

    /** Wrap a RodEvent dispatcher + a closer (the transport publisher) as one closeable outbound. */
    static RodPublisher of(Consumer<RodEvent> dispatch, AutoCloseable closer) {
        return new RodPublisher() {
            @Override
            public void accept(RodEvent event) {
                dispatch.accept(event);
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

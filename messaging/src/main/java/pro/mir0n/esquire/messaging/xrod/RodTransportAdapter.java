/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the codec bridge between the x-Rod RodEvent and the generic transport seam. publisher()
 *                   opens the provider's sink once and returns a Consumer<RodEvent> that encodes each event (via
 *                   RodEventCodec) onto a TransportMessage (key = entityId); handler() adapts a RodEvent sink into
 *                   the TransportMessage handler the provider's openConsumer dispatches to (decoding the envelope).
 * 06/17/2026 mir0n  publisher() returns a RodPublisher (closeable) instead of a bare Consumer<RodEvent>
 * 06/22/2026 mir0n  import RodEvent from messaging (was messaging.xrod)
 * 06/27/2026 mir0n  publisher(TransportPublisher, ObjectMapper, BusIdentity) overload added -- wrap an ALREADY-OPEN
 *                   transport publisher (so an XRod opening its consumer on the SAME connection keeps the raw
 *                   publisher handle); the original publisher(sink, settings) opens the sink and delegates here
 * 06/30/2026 mir0n  publisher(TransportPublisher, ObjectMapper, BusIdentity) returns a full RodPublisher --
 *                   accept / encode / dispatch / health / close delegating to the transport publisher (the
 *                   send-retry encode-once + throwing-dispatch path); toMessage() helper for the wire codec
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.function.Consumer;

/** Bridges the x-Rod {@link RodEvent} to/from the generic transport seam ({@link TransportMessage}). */
public final class RodTransportAdapter {

    private RodTransportAdapter() {
    }

    /**
     * Producer side: open the transport publisher once and return the closeable {@link RodEvent} dispatcher to
     * wire as an XRod transmit-leg outbound. Each event is encoded to the property-bag envelope (key = entityId
     * so a partitioning transport keeps per-key order); the msg-type rides the event ({@code e.msgType()}).
     * The returned {@link RodPublisher}'s {@code close()} releases the transport publisher's broker connection.
     */
    public static RodPublisher publisher(ITransportProvider provider, String destination, PublishSettings s) {
        TransportPublisher sink = provider.openPublisher(destination, s);
        return publisher(sink, s.objectMapper(), s.identity());
    }

    /**
     * Wrap an ALREADY-OPEN transport publisher as the closeable {@link RodEvent} dispatcher. The caller that needs
     * the raw {@link TransportPublisher} afterwards (e.g. an XRod that opens its consumer leg on the SAME
     * connection via {@link ITransportProvider#openConsumerOn}) opens the sink itself and wraps it here.
     */
    public static RodPublisher publisher(TransportPublisher sink, ObjectMapper om, BusIdentity id) {
        // The transmit-leg outbound. accept() is the best-effort path (retry off): encode + send, swallowing.
        // encode()/dispatch() are the send-retry path: the wire codec runs ONCE in encode (down to the transport's
        // own broker-free unit), and dispatch sends that unit THROWING on a transport failure -- so a held event's
        // resend relays the same unit with no re-encode. health()/close() delegate to the transport publisher.
        return new RodPublisher() {
            @Override
            public void accept(RodEvent event) {
                sink.accept(toMessage(event, om, id));
            }

            @Override
            public Object encode(RodEvent event) {
                return sink.encode(toMessage(event, om, id));
            }

            @Override
            public void dispatch(Object encoded) throws Exception {
                sink.dispatch(encoded);
            }

            @Override
            public TransportHealth health() {
                return sink.health();
            }

            @Override
            public void close() throws Exception {
                sink.close();
            }
        };
    }

    /** The wire codec applied ONCE: encode a {@link RodEvent} onto the neutral property-bag envelope (key = entityId
     *  so a partitioning transport keeps per-key order). */
    private static TransportMessage toMessage(RodEvent e, ObjectMapper om, BusIdentity id) {
        return new TransportMessage(RodEventCodec.toProps(e, om, id), e.entityId());
    }

    /**
     * Consumer side: adapt a {@link RodEvent} sink (an XRod receive leg) into the {@link TransportMessage}
     * handler the provider's {@code openConsumer} dispatches to -- decoding the envelope back to a RodEvent
     * (msg-type included, read off the wire).
     */
    public static Consumer<TransportMessage> handler(Consumer<RodEvent> rodSink, ObjectMapper om) {
        return msg -> rodSink.accept(RodEventCodec.fromProps(msg.headers(), om));
    }
}

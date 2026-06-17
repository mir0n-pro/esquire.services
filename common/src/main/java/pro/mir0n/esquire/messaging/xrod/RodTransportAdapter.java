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
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

import java.util.function.Consumer;

/** Bridges the x-Rod {@link RodEvent} to/from the generic transport seam ({@link TransportMessage}). */
public final class RodTransportAdapter {

    private RodTransportAdapter() {
    }

    /**
     * Producer side: open the transport publisher once and return the {@link RodEvent} dispatcher to wire as
     * an XRod transmit-leg outbound. Each event is encoded to the property-bag envelope (key = entityId so a
     * partitioning transport keeps per-entity order); the msg-type rides the event ({@code e.msgType()}).
     */
    public static Consumer<RodEvent> publisher(ITransportProvider provider, String destination, PublishSettings s) {
        Consumer<TransportMessage> sink = provider.openPublisher(destination, s);
        ObjectMapper om = s.objectMapper();
        BusIdentity id  = s.identity();
        return e -> sink.accept(new TransportMessage(RodEventCodec.toProps(e, om, id), e.entityId()));
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

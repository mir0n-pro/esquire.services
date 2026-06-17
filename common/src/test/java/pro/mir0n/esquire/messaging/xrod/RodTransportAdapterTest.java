package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RodTransportAdapterTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /** A fake provider: openPublisher hands back a sink that records every TransportMessage. */
    private static final class CapturingProvider implements ITransportProvider {
        final List<TransportMessage> sent = new ArrayList<>();
        @Override public Consumer<TransportMessage> openPublisher(String destination, PublishSettings s) {
            return sent::add;
        }
        @Override public AutoCloseable openConsumer(String d, ConsumeSettings s, Consumer<TransportMessage> h) {
            throw new UnsupportedOperationException();
        }
    }

    private static RodEvent event() {
        return new RodEvent(RodEvent.Op.UPDATE, 50, "100", null, 123L, "crl", "req", "uid",
                null, EsqMsgConstants.MSG_TYPE_AUDIT, Map.of("name", "ACC", "balance", 10));
    }

    @Test
    void publisher_encodesEventToEnvelopeWithEntityIdKey() {
        CapturingProvider provider = new CapturingProvider();
        PublishSettings ts = new PublishSettings(OM, "tcp://localhost:61616", null, false,
                new BusIdentity("audit-bus", "audit", null), Map.of(), 0);

        RodTransportAdapter.publisher(provider, "esquire.rod.audit", ts).accept(event());

        assertThat(provider.sent).hasSize(1);
        TransportMessage msg = provider.sent.get(0);
        assertThat(msg.key()).isEqualTo("100");
        assertThat(msg.headers()).isNotEmpty();
    }

    @Test
    void handler_decodesEnvelopeBackToEvent() {
        // round-trip: encode an event to the envelope, then decode via the handler
        Map<String, Object> headers = RodEventCodec.toProps(event(), OM,
                new BusIdentity("audit-bus", "audit", null));
        AtomicReference<RodEvent> got = new AtomicReference<>();

        RodTransportAdapter.handler(got::set, OM).accept(new TransportMessage(headers, "100"));

        RodEvent e = got.get();
        assertThat(e).isNotNull();
        assertThat(e.op()).isEqualTo(RodEvent.Op.UPDATE);
        assertThat(e.kind()).isEqualTo(50);
        assertThat(e.entityId()).isEqualTo("100");
        assertThat(e.msgType()).isEqualTo(EsqMsgConstants.MSG_TYPE_AUDIT);
        assertThat(e.body()).containsEntry("name", "ACC");
    }
}

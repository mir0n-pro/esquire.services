package pro.mir0n.esquire.messaging;

import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.function.Consumer;

/** Test-only transport provider, resolved by its full class name (the TransportProviders escape hatch). */
public class FakeTransportProvider implements ITransportProvider {

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        return TransportPublisher.of(msg -> { }, () -> { });
    }

    @Override
    public AutoCloseable openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        return () -> { };
    }
}

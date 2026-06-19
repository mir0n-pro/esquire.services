package pro.mir0n.esquire.messaging;

import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Test-only transport provider that CAPTURES the last publish/consume settings it was opened with (so a test
 *  can assert the rod-id the pod resolved -- the publish identity + the consume selector). Resolved by FQCN. */
public class CapturingTransportProvider implements ITransportProvider {

    public static volatile PublishSettings lastPublish;
    public static volatile ConsumeSettings lastConsume;
    public static volatile String lastPublishNode;
    public static volatile String lastConsumeNode;
    public static final AtomicInteger publisherCloseCount = new AtomicInteger();   // close() calls on the publisher handle

    public static void reset() {
        lastPublish = null;
        lastConsume = null;
        lastPublishNode = null;
        lastConsumeNode = null;
        publisherCloseCount.set(0);
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        lastPublishNode = destination;
        lastPublish = s;
        return TransportPublisher.of(msg -> { }, publisherCloseCount::incrementAndGet);
    }

    @Override
    public AutoCloseable openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        lastConsumeNode = destination;
        lastConsume = s;
        return () -> { };
    }
}

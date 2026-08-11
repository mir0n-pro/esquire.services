/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.messaging;

import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.function.Consumer;

/** Resolved by full class name (the TransportProviders escape hatch). Models an unreachable broker: the send
 *  throws; opening/starting the consumer does not (a real container retries in the background). */
public class BrokerDownTransportProvider implements ITransportProvider {

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        // a down broker rejects the actual send; opening the (lazy) publisher does not throw.
        return TransportPublisher.of(msg -> { throw new RuntimeException("broker down: send refused"); }, () -> { });
    }

    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        // a real listener container starts and recovers in the background -- start() does NOT throw on a down broker.
        return TransportConsumer.of(() -> { }, () -> { });
    }
}

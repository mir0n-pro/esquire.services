/*
 *  Esquire frameworks (tm)
 *  messaging framework
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/26/2026 mir0n  created: a publisher whose encode THROWS -- the send failure that used to be swallowed
 */
package pro.mir0n.esquire.messaging;

import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.function.Consumer;

/** Test-only: encode blows up, so the feed worker throws before anything is dispatched. */
public class EncodeFailingTransportProvider implements ITransportProvider {

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        return new TransportPublisher() {
            @Override
            public Object encode(TransportMessage message) {
                throw new IllegalArgumentException("cannot encode this body");
            }
            @Override
            public void accept(TransportMessage message) {
            }
            @Override
            public void close() {
            }
        };
    }

    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        return TransportConsumer.of(() -> { }, () -> { });
    }
}

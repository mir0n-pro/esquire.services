/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/24/2026 mir0n  created: a PRODUCE-ONLY test transport (like the XADD-only Redis stream) -- supportsConsume()
 *                   and supportsBothLegs() both false, so a CLIENT/BOTH role must FAIL FAST while a SERVER is fine.
 *                   Its publisher reports DOWN health, so a SERVER rod's health() reflects the transport indicator.
 */
package pro.mir0n.esquire.messaging;

import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.function.Consumer;

/** Resolved by full class name. A produce-only transport: {@code supportsConsume()} / {@code supportsBothLegs()}
 *  both false -- a CLIENT role fails fast, a SERVER (producer) is fine. The publisher reports DOWN health so
 *  a SERVER rod's {@code health()} reflects the transport indicator (proving health folds the transport). */
public class ProducerOnlyTransportProvider implements ITransportProvider {

    @Override
    public boolean supportsConsume() {
        return false;
    }

    @Override
    public boolean supportsBothLegs() {
        return false;
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        return TransportPublisher.of(msg -> { }, () -> { }, () -> TransportHealth.DOWN);
    }

    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        throw new UnsupportedOperationException("produce-only test transport");
    }
}

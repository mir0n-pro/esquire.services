/*
 *  Esquire frameworks (tm)
 *  tp-activemq -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.tp.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration test against an EMBEDDED ActiveMQ broker: the {@code noLocal} own-exclusion is enforced by the
 *  broker, so it is proven for real here (not mockable). The two tests TOGETHER are the proof -- the shared
 *  consumer's subscription is established with the SAME timing in both, so "receives with noLocal off" + "does not
 *  receive with noLocal on" isolates noLocal as the cause (not a missed subscription). */
class NoLocalIntegrationTest {

    private static final TransportProvider PROVIDER = new TransportProvider();
    private static final ObjectMapper OM = new ObjectMapper();

    private static BrokerService broker;
    private static String url;

    @BeforeAll
    static void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setBrokerName("tpAmqNoLocalTest");
        broker.addConnector("tcp://localhost:0");   // a random free port
        broker.start();
        broker.waitUntilStarted();
        url = broker.getTransportConnectors().get(0).getPublishableConnectString();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    private static BusIdentity id() {
        return new BusIdentity("test-bus", "test-slot", "test.0");
    }

    private static PublishSettings pub() {
        return new PublishSettings(OM, url, id(), Map.of("pubSubDomain", "true"), 0);
    }

    private static ConsumeSettings con(boolean noLocal) {
        return new ConsumeSettings(OM, url, id(),
                Map.of("pubSubDomain", "true", BusConstants.PARAM_NO_LOCAL, String.valueOf(noLocal)), 1, null);
    }

    private static TransportMessage msg() {
        return new TransportMessage(
                Map.of(BusConstants.FIELD_ROD_ID, "test.0", BusConstants.FIELD_EVENT_TYPE, BusConstants.EVENT_CREATE), "e1");
    }

    @Test
    void noLocalOn_sharedConsumerDoesNotReceiveOwn_butSeparateConnectionDoes() throws Exception {
        String topic = "test.entity.noLocal.on";
        AtomicInteger sharedGot = new AtomicInteger();
        CountDownLatch separateGot = new CountDownLatch(1);

        TransportPublisher pub = PROVIDER.openPublisher(topic, pub());
        TransportConsumer shared = PROVIDER.openConsumerOn(pub, topic, con(true), m -> sharedGot.incrementAndGet());
        TransportConsumer separate = PROVIDER.openConsumer(topic, con(false), m -> separateGot.countDown());
        shared.start();
        separate.start();
        Thread.sleep(600);   // let the (non-durable) topic subscriptions establish before publishing

        pub.accept(msg());

        // the message WAS published and reached a DIFFERENT connection's consumer ...
        assertThat(separateGot.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);   // ... and the shared consumer had ample time too -- it must still NOT have it (noLocal)
        assertThat(sharedGot.get()).isZero();

        shared.close();
        separate.close();
        pub.close();
    }

    @Test
    void noLocalOff_sharedConsumerReceivesOwn() throws Exception {
        String topic = "test.entity.noLocal.off";
        CountDownLatch sharedGot = new CountDownLatch(1);

        TransportPublisher pub = PROVIDER.openPublisher(topic, pub());
        TransportConsumer shared = PROVIDER.openConsumerOn(pub, topic, con(false), m -> sharedGot.countDown());
        shared.start();
        Thread.sleep(600);

        pub.accept(msg());

        // noLocal off: the shared connection's consumer DOES receive its own publication.
        assertThat(sharedGot.await(5, TimeUnit.SECONDS)).isTrue();

        shared.close();
        pub.close();
    }
}

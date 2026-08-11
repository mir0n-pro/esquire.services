/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

/** Broker-down = graceful degradation, not fail-fast: a transport-backed rod whose broker is unreachable still
 *  inits + starts (the service comes up); the transport recovers in the background. */
class XRodBrokerDownTest {

    private static final String BROKER_DOWN = "pro.mir0n.esquire.messaging.BrokerDownTransportProvider";

    /** A transport-backed leg pointing at the broker-down provider (a real provider class name + a complete wire). */
    private static XRodParams brokerDownLeg() {
        return XRodParams.from(Map.of("transport", Map.of(
                "provider", BROKER_DOWN, "endpoint", "tcp://unreachable:61616", "destination", "test.q")))
                .withBus("test-bus", "test-slot", "test.0");
    }

    @Test
    void consumer_brokerDown_initAndStartDoNotThrow() {
        // a receive rod (CLIENT single-node) against a down broker -- init creates the (paused) consumer, start
        // begins delivery; the container recovers in the background, so neither throws. The service boots.
        XRod rod = new XRod();
        XRodParams p = brokerDownLeg();
        rod.validate(p);
        rod.configure(p, Role.CLIENT, new ObjectMapper());
        assertThatCode(() -> {
            rod.init("test-rx", null);   // create the consumer (paused) -- no connect
            rod.setWorker(e -> { });     // after init -- the receive leg exists now
            rod.start();                 // begin delivery -- the down broker does not crash startup
            rod.shutdown();
        }).doesNotThrowAnyException();
    }

    @Test
    void producer_brokerDown_initAndStartDoNotThrow() {
        // a transmit rod (SERVER single-node) against a down broker -- the publisher is lazy, so building + starting
        // the rod does not connect and does not throw. A later send would fail (handled fire-and-forget by the
        // producer), but the service still boots.
        XRod rod = new XRod();
        XRodParams p = brokerDownLeg();
        rod.validate(p);
        rod.configure(p, Role.SERVER, new ObjectMapper());
        assertThatCode(() -> {
            rod.init("test-tx", null);
            rod.start();
            rod.shutdown();
        }).doesNotThrowAnyException();
    }
}

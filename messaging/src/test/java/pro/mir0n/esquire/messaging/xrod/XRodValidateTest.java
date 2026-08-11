/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.messaging.xrod;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;
import pro.mir0n.esquire.messaging.xrod.impl.XRodRR;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fail-fast leg validation -- a transport-backed x-rod must have a complete transport, or it throws at the init
 *  phase (before any leg opens) instead of silently building a no-op rod. */
class XRodValidateTest {

    /** A complete single-node transport (provider + endpoint + destination). */
    private static final Map<String, Object> COMPLETE = Map.of("transport",
            Map.of("provider", "activemq", "endpoint", "tcp://localhost:61616", "destination", "test.q"));

    private static XRodParams leg(Map<String, Object> rawNode) {
        return XRodParams.from(rawNode).withBus("test-bus", "test-slot", "test.0");
    }

    // ----------------------------------------------------------------- XRod (the default transceiver)

    @Test
    void xRod_noTransport_failsFast() {
        // THE GAP: a knobs-only leg (no transport) -- e.g. a bogus bus-id whose only x-rod is a service override.
        assertThatThrownBy(() -> new XRod().validate(leg(Map.of("pool-size", 4))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing required transport");
    }

    @Test
    void xRod_transportMissingDestination_failsFast() {
        assertThatThrownBy(() -> new XRod().validate(leg(Map.of(
                "transport", Map.of("provider", "activemq", "endpoint", "tcp://localhost:61616")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transport.destination");
    }

    @Test
    void xRod_completeTransport_passes() {
        assertThatCode(() -> new XRod().validate(leg(COMPLETE))).doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------- XRodRR (the R&R transceiver)

    @Test
    void xRodRR_noTransport_failsFast() {
        assertThatThrownBy(() -> new XRodRR().validate(leg(Map.of("pool-size", 4))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing required transport");
    }

    @Test
    void xRodRR_singleNodeComplete_passes() {
        // a single-node R&R leg (no request/response nodes) falls back to the base destination.
        assertThatCode(() -> new XRodRR().validate(leg(COMPLETE))).doesNotThrowAnyException();
    }
}

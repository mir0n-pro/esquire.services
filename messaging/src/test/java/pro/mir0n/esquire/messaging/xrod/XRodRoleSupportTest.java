/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/24/2026 mir0n  created: the supportsBothLegs() role fail-fast (an impossible CLIENT/BOTH role over a
 *                   produce-only transport throws at init) + health() folding the transport indicator with the
 *                   alive metric (alive OFF -> health is the transport indicator alone).
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A rod that RECEIVES (CLIENT) over a produce-only transport (supportsBothLegs()=false) fails fast as an
 *  unsupported config; a SERVER (producer) is fine. And health() folds the transport indicator -- with alive OFF
 *  it IS the transport indicator. */
class XRodRoleSupportTest {

    private static final String PRODUCE_ONLY = "pro.mir0n.esquire.messaging.ProducerOnlyTransportProvider";

    private static XRodParams leg(boolean alive) {
        Map<String, Object> wire = Map.of("provider", PRODUCE_ONLY, "endpoint", "x://h", "destination", "s");
        Map<String, Object> raw = alive ? Map.of("alive", true, "transport", wire) : Map.of("transport", wire);
        return XRodParams.from(raw).withBus("test-bus", "test-slot", "test.0");
    }

    @Test
    void produceOnly_clientRole_failsFast() {
        XRod rod = new XRod();
        XRodParams p = leg(false);   // alive off -> CLIENT is a pure consumer; the transport cannot consume
        rod.configure(p, Role.CLIENT, new ObjectMapper());
        assertThatThrownBy(() -> rod.init("rx", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported config");
    }

    @Test
    void produceOnly_clientRole_aliveOn_failsFast_onBothLegs() {
        XRod rod = new XRod();
        XRodParams p = leg(true);    // alive on -> CLIENT also self-heartbeats (a producer leg) -> needs both legs
        rod.configure(p, Role.CLIENT, new ObjectMapper());
        assertThatThrownBy(() -> rod.init("rx", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both legs");
    }

    @Test
    void produceOnly_serverRole_ok_andHealthIsTransportIndicator() {
        XRod rod = new XRod();
        XRodParams p = leg(false);   // alive off -> no session -> health() is the transport indicator alone
        rod.configure(p, Role.SERVER, new ObjectMapper());
        assertThatCode(() -> {
            rod.init("tx", null);
            rod.start();
        }).doesNotThrowAnyException();
        assertThat(rod.health()).isEqualTo(TransportHealth.DOWN);   // the produce-only publisher reports DOWN
        rod.shutdown();
    }
}

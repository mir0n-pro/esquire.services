package pro.mir0n.esquire.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The readiness rule the Actuator indicator applies over the bus's per-bus health map: DOWN if any bus is
 *  DOWN, every bus state reported as a detail, and an UNKNOWN bus never fails the indicator. */
class BusHealthIndicatorTest {

    /** A bus whose per-bus health is fixed -- the indicator only reads health(), so the rest stays untouched. */
    private static BusHealthIndicator over(Map<String, TransportHealth> busStates) {
        MessagingBus stub = new MessagingBus() {
            @Override
            public Map<String, TransportHealth> health() {
                return busStates;
            }
        };
        return new BusHealthIndicator(stub);
    }

    private static Map<String, TransportHealth> buses(Object... pairs) {
        Map<String, TransportHealth> ret = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            ret.put((String) pairs[i], (TransportHealth) pairs[i + 1]);
        }
        return ret;
    }

    @Test
    void downIfAnyBusDown() {
        Health h = over(buses("kc-bus", TransportHealth.DOWN, "audit-bus", TransportHealth.UP)).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("kc-bus", "DOWN").containsEntry("audit-bus", "UP");
    }

    @Test
    void upWhenEveryBusUp() {
        Health h = over(buses("entity-bus", TransportHealth.UP, "kc-bus", TransportHealth.UP)).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void unknownBusReportedButDoesNotFail() {
        Health h = over(buses("audit-bus", TransportHealth.UNKNOWN, "entity-bus", TransportHealth.UP)).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("audit-bus", "UNKNOWN");
    }
}

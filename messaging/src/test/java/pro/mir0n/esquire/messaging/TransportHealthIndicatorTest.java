package pro.mir0n.esquire.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import static org.assertj.core.api.Assertions.assertThat;

/** The single-source indicator (auKeep's keep datasource): DOWN only when the source is DOWN, the state always
 *  reported as the "state" detail, and an UNKNOWN source stays UP (the framework does not fake confidence). */
class TransportHealthIndicatorTest {

    @Test
    void downSourceIsDown() {
        Health h = new TransportHealthIndicator(() -> TransportHealth.DOWN).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("state", "DOWN");
    }

    @Test
    void upSourceIsUp() {
        Health h = new TransportHealthIndicator(() -> TransportHealth.UP).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("state", "UP");
    }

    @Test
    void unknownSourceStaysUp() {
        Health h = new TransportHealthIndicator(() -> TransportHealth.UNKNOWN).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("state", "UNKNOWN");
    }
}

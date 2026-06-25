package pro.mir0n.esquire.messaging.transport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pro.mir0n.esquire.messaging.transport.TransportHealth.DOWN;
import static pro.mir0n.esquire.messaging.transport.TransportHealth.UNKNOWN;
import static pro.mir0n.esquire.messaging.transport.TransportHealth.UP;
import static pro.mir0n.esquire.messaging.transport.TransportHealth.worst;

/** The leg-folding rule the x-rod uses to combine its transmit + receive legs: DOWN worst, then UNKNOWN, then UP. */
class TransportHealthTest {

    @Test
    void downBeatsEverything() {
        assertThat(worst(DOWN, UP)).isEqualTo(DOWN);
        assertThat(worst(UP, DOWN)).isEqualTo(DOWN);
        assertThat(worst(DOWN, UNKNOWN)).isEqualTo(DOWN);
    }

    @Test
    void unknownBeatsUp() {
        assertThat(worst(UNKNOWN, UP)).isEqualTo(UNKNOWN);
        assertThat(worst(UP, UNKNOWN)).isEqualTo(UNKNOWN);
    }

    @Test
    void upOnlyWhenBothUp() {
        assertThat(worst(UP, UP)).isEqualTo(UP);
    }

    @Test
    void aNullLegIsIgnored() {
        assertThat(worst(null, UP)).isEqualTo(UP);      // a role that runs only one leg
        assertThat(worst(DOWN, null)).isEqualTo(DOWN);
        assertThat(worst(null, null)).isEqualTo(UNKNOWN);   // no observable leg
    }
}

package pro.mir0n.esquire.messaging.xrod.impl;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The OFF pod is fully inert: both gates report off and every leg method is a no-op (never throws), with no
 *  config treated -- so a bus key that resolves to no leg, or a slot set to rod-class XRodDisabled, is a clean
 *  no-op rather than an error. */
class XRodDisabledTest {

    @Test
    void gatesAreOff() {
        XRodDisabled rod = new XRodDisabled();
        assertThat(rod.isEnabled()).isFalse();
        assertThat(rod.usesOutboundTransport()).isFalse();
    }

    @Test
    void everyMethodIsANoOpAndNeverThrows() {
        XRodDisabled rod = new XRodDisabled();
        assertThatCode(() -> {
            rod.configure(null, Role.BROADCAST, null);          // no config treated
            rod.start("off", null, null);                       // no legs run
            rod.bindInbound(() -> { });
            rod.post(RodEvent.Op.UPDATE, 36, "8", null, (IMappable) null, "UA");   // IMappable-source overload
            rod.post(RodEvent.Op.DELETE, 36, "8", null, "UA");                     // no-body overload
            rod.post(RodEvent.Op.CREATE, 36, "8", null, Map.of("k", "v"), "UA");   // map-body overload
            rod.transmit(null);                                 // transmit no-op
            rod.submit(null);                                   // receive no-op
            rod.shutdown();
        }).doesNotThrowAnyException();
    }
}

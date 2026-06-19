package pro.mir0n.esquire.messaging.xrod.impl;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The OFF x-rod is fully inert: both gates report off and every leg method is a no-op (never throws), with no
 *  config treated -- so a bus key that resolves to no leg, or a slot set to rod-class XRodDisabled, is a clean
 *  no-op rather than an error. */
class XRodDisabledTest {

    @Test
    void gateIsOff() {
        // the OFF x-rod is the ONLY one that is not enabled (every other IXRod inherits the default true).
        assertThat(new XRodDisabled().isEnabled()).isFalse();
    }

    @Test
    void everyMethodIsANoOpAndNeverThrows() {
        XRodDisabled rod = new XRodDisabled();
        assertThatCode(() -> {
            rod.configure(null, Role.BROADCAST, null);          // no config treated
            rod.start("off", null, null);                       // no legs run
            rod.transmit(null);                                 // transmit no-op
            rod.receive(null);                                  // receive no-op
            rod.shutdown();
        }).doesNotThrowAnyException();
    }
}

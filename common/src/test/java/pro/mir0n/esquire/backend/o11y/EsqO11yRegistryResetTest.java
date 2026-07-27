/*
 *  Esquire frameworks (tm)
 *  common library  --  test support
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/15/2026 mir0n  created (v1.2.11 T11/I32): proves the auto-reset guard actually runs between tests.
 */
package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the guard (I32). {@link EsqO11yRegistryReset} is registered by AUTO-DETECTION, so nothing in the test code
 * references it -- which means a broken registration (a missing {@code junit-platform.properties} line, a deleted
 * {@code META-INF/services} entry) would silently stop resetting and be invisible. This test makes that visible:
 * one method sets a registry and DELIBERATELY does not reset; the next asserts the facility came back OFF between
 * them. It passes ONLY if the auto-reset ran. Ordered, because the proof is in the hand-off from one test to the next.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EsqO11yRegistryResetTest {

    private static final String METER = "esq.biz.perm.check.total";
    private static SimpleMeterRegistry leaked;   // test1's registry, kept so test2 can prove it was NOT reused

    @Test
    @Order(1)
    void arm_setsRegistryAndDoesNotResetIt() {
        leaked = new SimpleMeterRegistry();
        EsqBizMeters.setRegistry(leaked);
        EsqBizMeters.count(METER, "cmd", "ONE");
        assertThat(leaked.find(METER).tag("cmd", "ONE").counter()).isNotNull();
        assertThat(leaked.find(METER).tag("cmd", "ONE").counter().count()).isEqualTo(1.0);
        // NO reset here -- the guard's afterEach must do it. If it does not, test2 below fails.
    }

    @Test
    @Order(2)
    void proof_facilityWasResetBetweenTests() {
        // If the guard ran, the facility is OFF now (registry null), so a count is a no-op that CANNOT reach test1's
        // registry. If the guard did NOT run, the facility still points at `leaked` and this count increments it.
        double before = leaked.find(METER).tag("cmd", "ONE").counter().count();
        EsqBizMeters.count(METER, "cmd", "ONE");
        double after = leaked.find(METER).tag("cmd", "ONE").counter().count();
        assertThat(after)
                .as("test1's registry was reused -> the auto-reset guard did NOT run (check junit-platform.properties "
                        + "+ META-INF/services registration of EsqO11yRegistryReset)")
                .isEqualTo(before);
    }
}

package pro.mir0n.esquire.enyMan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for the v1.2.6 entity-id minter.
 *
 * The shape under test:
 *   id = (ms since esquireEpoch) * 10000
 *      + EsqUtils.instanceNo() * 1000
 *      + (sequence.getAndIncrement() % 1000)
 *
 * instanceNo() resolution is owned by common.EsqUtils -- it consults env vars
 * (ESQUIRE_INSTANCE_NO, POD_INDEX, POD_NAME) before the system property and
 * default. These tests can only drive the system-property branch, so they skip
 * (assumeNoInstanceEnv) if a higher-priority env var is set in the runner.
 */
class EntityIdGeneratorTest {

    private static final String SYSPROP = "esquire.instance.no";

    @BeforeEach
    void clearSyspropAndCache() throws Exception {
        System.clearProperty(SYSPROP);
        resetInstanceNoCache();   // v1.2.6: instanceNo() is lazy-cached -- reset between cases.
    }

    @AfterEach
    void clearSyspropAfter() throws Exception {
        System.clearProperty(SYSPROP);
        resetInstanceNoCache();
    }

    // EsqUtils.resetInstanceNoCacheForTests() is package-private to pro.mir0n.esquire.common;
    // this test lives in pro.mir0n.esquire.enyMan.service, so we reach it via reflection.
    private static void resetInstanceNoCache() throws Exception {
        Method m = EsqUtils.class.getDeclaredMethod("resetInstanceNoCacheForTests");
        m.setAccessible(true);
        m.invoke(null);
    }

    // ---- happy path: ordering + shape ----

    @Test
    @DisplayName("generateEntityId: returns a positive long")
    void generateEntityId_positive() {
        assumeNoInstanceEnv();
        assertThat(EntityIdGenerator.generateEntityId()).isPositive();
    }

    @Test
    @DisplayName("generateEntityId: consecutive calls in the same JVM are strictly increasing")
    void generateEntityId_strictlyIncreasing() {
        assumeNoInstanceEnv();
        long a = EntityIdGenerator.generateEntityId();
        long b = EntityIdGenerator.generateEntityId();
        long c = EntityIdGenerator.generateEntityId();
        assertThat(b).isGreaterThan(a);
        assertThat(c).isGreaterThan(b);
    }

    @Test
    @DisplayName("generateEntityId: when instanceNo defaults to 0, the 1000s decimal digit is 0")
    void generateEntityId_instanceDigitZeroWhenUnconfigured() {
        assumeNoInstanceEnv();
        long id = EntityIdGenerator.generateEntityId();
        int thousandsDigit = (int) ((id / 1000L) % 10L);
        assertThat(thousandsDigit).isEqualTo(0);
    }

    @Test
    @DisplayName("generateEntityId: when instanceNo=5 (via sysprop), the 1000s decimal digit is 5")
    void generateEntityId_instanceDigitMatchesInstanceNo() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "5");
        long id = EntityIdGenerator.generateEntityId();
        int thousandsDigit = (int) ((id / 1000L) % 10L);
        assertThat(thousandsDigit).isEqualTo(5);
    }

    @Test
    @DisplayName("generateEntityId: the bottom 3 digits (sequence) always stay in [0, 999]")
    void generateEntityId_sequenceWithinThreeDigits() {
        assumeNoInstanceEnv();
        for (int i = 0; i < 2500; i++) {
            long id = EntityIdGenerator.generateEntityId();
            int seq = (int) (id % 1000L);
            assertThat(seq).isBetween(0, 999);
        }
    }

    // ---- guard: instance number out of allowed range [0, 9] ----

    @Test
    @DisplayName("generateEntityId: throws IllegalStateException when instanceNo == 10")
    void generateEntityId_throwsForInstanceTen() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "10");
        assertThatThrownBy(EntityIdGenerator::generateEntityId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0-9");
    }

    @Test
    @DisplayName("generateEntityId: throws IllegalStateException when instanceNo > 10")
    void generateEntityId_throwsForLargeInstance() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "42");
        assertThatThrownBy(EntityIdGenerator::generateEntityId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("generateEntityId: throws IllegalStateException when instanceNo is negative")
    void generateEntityId_throwsForNegativeInstance() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "-1");
        assertThatThrownBy(EntityIdGenerator::generateEntityId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("generateEntityId: accepts the upper bound instanceNo == 9")
    void generateEntityId_acceptsNine() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "9");
        long id = EntityIdGenerator.generateEntityId();
        int thousandsDigit = (int) ((id / 1000L) % 10L);
        assertThat(thousandsDigit).isEqualTo(9);
    }

    // ---- helpers ----

    private static void assumeNoInstanceEnv() {
        assumeTrue(System.getenv("ESQUIRE_INSTANCE_NO") == null,
                "ESQUIRE_INSTANCE_NO is set in this runner; cannot drive instanceNo via sysprop.");
        assumeTrue(System.getenv("POD_INDEX") == null,
                "POD_INDEX is set in this runner; cannot drive instanceNo via sysprop.");
        assumeTrue(System.getenv("POD_NAME") == null,
                "POD_NAME is set in this runner; cannot drive instanceNo via sysprop.");
    }
}

package pro.mir0n.esquire.enyMan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the entity-id minter.
 *
 * The shape under test:
 *   id = (ms since esquireEpoch) * 10000
 *      + EsqUtils.instanceNo() * 1000
 *      + (sequence.getAndIncrement() % 1000)
 *
 * instanceNo() resolution is owned by common.EsqUtils -- it is the trailing ordinal of the host
 * name (instanceHost), else 0. These tests pin the instance number directly via the EsqUtils test
 * seam, so they are deterministic regardless of the runner's host name.
 */
class EntityIdGeneratorTest {

    @BeforeEach
    void pinInstanceZero() throws Exception {
        setInstanceNo(0);         // deterministic default; individual cases override.
    }

    @AfterEach
    void clearInstanceCache() throws Exception {
        resetInstanceNoCache();   // drop the pin so other test classes re-resolve from the host name.
    }

    // EsqUtils.resetInstanceNoCacheForTests() / setInstanceNoForTests(int) are package-private to
    // pro.mir0n.esquire.common; this test lives in pro.mir0n.esquire.enyMan.service, so we reach
    // them via reflection.
    private static void resetInstanceNoCache() throws Exception {
        Method m = EsqUtils.class.getDeclaredMethod("resetInstanceNoCacheForTests");
        m.setAccessible(true);
        m.invoke(null);
    }

    private static void setInstanceNo(int n) throws Exception {
        Method m = EsqUtils.class.getDeclaredMethod("setInstanceNoForTests", int.class);
        m.setAccessible(true);
        m.invoke(null, n);
    }

    // ---- happy path: ordering + shape ----

    @Test
    @DisplayName("generateEntityId: returns a positive long")
    void generateEntityId_positive() {
        assertThat(EntityIdGenerator.generateEntityId()).isPositive();
    }

    @Test
    @DisplayName("generateEntityId: consecutive calls in the same JVM are strictly increasing")
    void generateEntityId_strictlyIncreasing() {
        long a = EntityIdGenerator.generateEntityId();
        long b = EntityIdGenerator.generateEntityId();
        long c = EntityIdGenerator.generateEntityId();
        assertThat(b).isGreaterThan(a);
        assertThat(c).isGreaterThan(b);
    }

    @Test
    @DisplayName("generateEntityId: when instanceNo is 0, the 1000s decimal digit is 0")
    void generateEntityId_instanceDigitZeroWhenUnconfigured() {
        long id = EntityIdGenerator.generateEntityId();
        int thousandsDigit = (int) ((id / 1000L) % 10L);
        assertThat(thousandsDigit).isEqualTo(0);
    }

    @Test
    @DisplayName("generateEntityId: when instanceNo=5, the 1000s decimal digit is 5")
    void generateEntityId_instanceDigitMatchesInstanceNo() throws Exception {
        setInstanceNo(5);
        long id = EntityIdGenerator.generateEntityId();
        int thousandsDigit = (int) ((id / 1000L) % 10L);
        assertThat(thousandsDigit).isEqualTo(5);
    }

    @Test
    @DisplayName("generateEntityId: the bottom 3 digits (sequence) always stay in [0, 999]")
    void generateEntityId_sequenceWithinThreeDigits() {
        for (int i = 0; i < 2500; i++) {
            long id = EntityIdGenerator.generateEntityId();
            int seq = (int) (id % 1000L);
            assertThat(seq).isBetween(0, 999);
        }
    }

    // ---- guard: instance number out of allowed range [0, 9] ----

    @Test
    @DisplayName("generateEntityId: throws IllegalStateException when instanceNo == 10")
    void generateEntityId_throwsForInstanceTen() throws Exception {
        setInstanceNo(10);
        assertThatThrownBy(EntityIdGenerator::generateEntityId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0-9");
    }

    @Test
    @DisplayName("generateEntityId: throws IllegalStateException when instanceNo > 10")
    void generateEntityId_throwsForLargeInstance() throws Exception {
        setInstanceNo(42);
        assertThatThrownBy(EntityIdGenerator::generateEntityId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("generateEntityId: throws IllegalStateException when instanceNo is negative")
    void generateEntityId_throwsForNegativeInstance() throws Exception {
        setInstanceNo(-1);
        assertThatThrownBy(EntityIdGenerator::generateEntityId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("generateEntityId: accepts the upper bound instanceNo == 9")
    void generateEntityId_acceptsNine() throws Exception {
        setInstanceNo(9);
        long id = EntityIdGenerator.generateEntityId();
        int thousandsDigit = (int) ((id / 1000L) % 10L);
        assertThat(thousandsDigit).isEqualTo(9);
    }
}

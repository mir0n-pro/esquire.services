package pro.mir0n.esquire.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for the v1.2.6 additions to EsqUtils:
 *   - instanceNo()           -- five-tier source priority
 *   - parsePodNameOrdinal()  -- StatefulSet ordinal extractor (private; reached via reflection)
 *
 * Env-var branches of instanceNo() (ESQUIRE_INSTANCE_NO, POD_INDEX, POD_NAME) cannot be
 * driven from JUnit on the JDK because the process env is immutable post-startup.
 * Those branches are exercised by the hauberk e2e smoke. Here we cover sysprop,
 * default, the parseInt-failure fallback, and the parser helper edge cases -- and
 * skip the sysprop tests if any of the higher-priority env vars happen to be set
 * in the runner's environment.
 */
class EsqUtilsTest {

    private static final String SYSPROP = "esquire.instance.no";

    @BeforeEach
    void clearSyspropAndCache() {
        System.clearProperty(SYSPROP);
        EsqUtils.resetInstanceNoCacheForTests();   // v1.2.6: instanceNo() is lazy-cached;
                                                    // tests need a fresh resolution per case.
    }

    @AfterEach
    void clearSyspropAfter() {
        System.clearProperty(SYSPROP);
        EsqUtils.resetInstanceNoCacheForTests();
    }

    // ---- instanceNo: defaults / sysprop branch ----

    @Test
    @DisplayName("instanceNo: returns 0 when nothing is configured (default branch)")
    void instanceNo_defaultsToZero() {
        assumeNoInstanceEnv();
        assertThat(EsqUtils.instanceNo()).isEqualTo(0);
    }

    @Test
    @DisplayName("instanceNo: reads esquire.instance.no system property when env is unset")
    void instanceNo_readsSystemProperty() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "7");
        assertThat(EsqUtils.instanceNo()).isEqualTo(7);
    }

    @Test
    @DisplayName("instanceNo: returns 0 for an unparseable system property (no throw)")
    void instanceNo_unparseableSyspropFallsBackToZero() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "not-a-number");
        assertThat(EsqUtils.instanceNo()).isEqualTo(0);
    }

    @Test
    @DisplayName("instanceNo: trims whitespace around the system property value")
    void instanceNo_trimsSyspropValue() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "  3  ");
        assertThat(EsqUtils.instanceNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("instanceNo: blank system property falls through to default 0")
    void instanceNo_blankSyspropFallsThroughToDefault() {
        assumeNoInstanceEnv();
        System.setProperty(SYSPROP, "   ");
        assertThat(EsqUtils.instanceNo()).isEqualTo(0);
    }

    // ---- parsePodNameOrdinal: private helper, reached via reflection ----

    @Test
    @DisplayName("parsePodNameOrdinal: returns trailing digits after the last dash")
    void parsePodNameOrdinal_returnsTrailingDigits() throws Exception {
        assertThat(invokeParse("enyman-3")).isEqualTo("3");
        assertThat(invokeParse("enyman-42")).isEqualTo("42");
        assertThat(invokeParse("esquire-enyman-enyman-0")).isEqualTo("0");
    }

    @Test
    @DisplayName("parsePodNameOrdinal: returns null for null input")
    void parsePodNameOrdinal_nullInput() throws Exception {
        assertThat(invokeParse(null)).isNull();
    }

    @Test
    @DisplayName("parsePodNameOrdinal: returns null when there is no dash")
    void parsePodNameOrdinal_noDash() throws Exception {
        assertThat(invokeParse("noseparator")).isNull();
        assertThat(invokeParse("")).isNull();
    }

    @Test
    @DisplayName("parsePodNameOrdinal: returns null when the suffix after the last dash is not all digits")
    void parsePodNameOrdinal_nonDigitSuffix() throws Exception {
        assertThat(invokeParse("enyman-abc")).isNull();
        assertThat(invokeParse("enyman-3a")).isNull();
        assertThat(invokeParse("enyman-")).isNull();
    }

    // ---- generateCorrelationId: unchanged, smoke test ----

    @Test
    @DisplayName("generateCorrelationId: returns a UUID-shaped string")
    void generateCorrelationId_shape() {
        String id = EsqUtils.generateCorrelationId();
        assertThat(id).isNotNull();
        assertThat(id.chars().filter(c -> c == '-').count()).isEqualTo(4L);
    }

    // ---- helpers ----

    private static String invokeParse(String podName) throws Exception {
        Method m = EsqUtils.class.getDeclaredMethod("parsePodNameOrdinal", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, podName);
    }

    // Sysprop tests are meaningful only when no higher-priority env var is set in the runner.
    // If any of ESQUIRE_INSTANCE_NO / POD_INDEX / POD_NAME is present, the resolution short-
    // circuits there and the sysprop branch never runs -- skip rather than report a false fail.
    private static void assumeNoInstanceEnv() {
        assumeTrue(System.getenv("ESQUIRE_INSTANCE_NO") == null,
                "ESQUIRE_INSTANCE_NO is set in this runner; sysprop branch is unreachable.");
        assumeTrue(System.getenv("POD_INDEX") == null,
                "POD_INDEX is set in this runner; sysprop branch is unreachable.");
        assumeTrue(System.getenv("POD_NAME") == null,
                "POD_NAME is set in this runner; sysprop branch is unreachable.");
    }
}

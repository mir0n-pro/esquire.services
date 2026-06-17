package pro.mir0n.esquire.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the instance-identity helpers in EsqUtils:
 *   - instanceNo()           -- the trailing ordinal of the host name (instanceHost()), else 0
 *   - parsePodNameOrdinal()  -- host-name ordinal extractor (private; reached via reflection)
 *
 * The host-name source of instanceNo() cannot be varied from JUnit on the JDK (the process env is
 * immutable post-startup). So instanceNo() is covered two ways: a wiring check that it equals the
 * parsed ordinal of the actual host name (deterministic on any runner), and the test seam that pins
 * a value directly. The parser edge cases are covered against parsePodNameOrdinal directly.
 */
class EsqUtilsTest {

    @BeforeEach
    @AfterEach
    void resetCache() {
        EsqUtils.resetInstanceNoCacheForTests();   // instanceNo() is lazy-cached; fresh per case.
    }

    // ---- instanceNo: host-ordinal resolution + the test seam ----

    @Test
    @DisplayName("instanceNo: equals the parsed trailing ordinal of the host name, else 0")
    void instanceNo_isHostOrdinalElseZero() throws Exception {
        String ordinal = invokeParse(EsqUtils.instanceHost());
        int expected = ordinal != null ? Integer.parseInt(ordinal) : 0;
        EsqUtils.resetInstanceNoCacheForTests();
        assertThat(EsqUtils.instanceNo()).isEqualTo(expected);
    }

    @Test
    @DisplayName("instanceNo: a pinned test value bypasses host-name resolution")
    void instanceNo_pinnedValueIsReturned() {
        EsqUtils.setInstanceNoForTests(7);
        assertThat(EsqUtils.instanceNo()).isEqualTo(7);
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
}

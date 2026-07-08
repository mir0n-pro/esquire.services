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

    // ---- correlation-id / W3C trace-id settlement ----

    @Test
    @DisplayName("generateCorrelationId: returns a W3C-shaped id (32 lowercase hex, non-zero)")
    void generateCorrelationId_shape() {
        String id = EsqUtils.generateCorrelationId();
        assertThat(id).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(EsqUtils.isW3cTraceId(id)).isTrue();
    }

    @Test
    @DisplayName("generateCorrelationId: distinct ids across calls")
    void generateCorrelationId_distinct() {
        assertThat(EsqUtils.generateCorrelationId()).isNotEqualTo(EsqUtils.generateCorrelationId());
    }

    @Test
    @DisplayName("isW3cTraceId: accepts 32 lowercase hex, rejects wrong length / case / all-zero / non-hex")
    void isW3cTraceId_validation() {
        assertThat(EsqUtils.isW3cTraceId("0123456789abcdef0123456789abcdef")).isTrue();
        assertThat(EsqUtils.isW3cTraceId("0123456789ABCDEF0123456789ABCDEF")).isFalse(); // upper-case
        assertThat(EsqUtils.isW3cTraceId("00000000000000000000000000000000")).isFalse(); // all zero
        assertThat(EsqUtils.isW3cTraceId("abc")).isFalse();                               // too short
        assertThat(EsqUtils.isW3cTraceId("0123456789abcdef0123456789abcdeg")).isFalse();  // non-hex
        assertThat(EsqUtils.isW3cTraceId(null)).isFalse();
    }

    @Test
    @DisplayName("toW3cTraceId: deterministic W3C-shaped hash of any value")
    void toW3cTraceId_deterministic() {
        String a = EsqUtils.toW3cTraceId("some-external-id");
        String b = EsqUtils.toW3cTraceId("some-external-id");
        assertThat(a).isEqualTo(b);                       // stable for the same input
        assertThat(EsqUtils.isW3cTraceId(a)).isTrue();
        assertThat(EsqUtils.toW3cTraceId("other")).isNotEqualTo(a);
    }

    @Test
    @DisplayName("settleCorrelationId: keeps a W3C-shaped incoming correlation id unchanged")
    void settle_keepsValidCorrelationId() {
        String valid = "0123456789abcdef0123456789abcdef";
        assertThat(EsqUtils.settleCorrelationId(valid)).isEqualTo(valid);
    }

    @Test
    @DisplayName("settleCorrelationId: converts a non-W3C incoming correlation id")
    void settle_convertsNonW3cCorrelationId() {
        String result = EsqUtils.settleCorrelationId("esq-111");
        assertThat(result).isEqualTo(EsqUtils.toW3cTraceId("esq-111"));
        assertThat(EsqUtils.isW3cTraceId(result)).isTrue();
    }

    @Test
    @DisplayName("settleCorrelationId: with no correlation id, GENERATES a fresh W3C id (never from a request id)")
    void settle_generatesWhenAbsent() {
        String a = EsqUtils.settleCorrelationId(null);
        String b = EsqUtils.settleCorrelationId(null);
        assertThat(EsqUtils.isW3cTraceId(a)).isTrue();
        assertThat(a).isNotEqualTo(b);   // fresh each call -- not a deterministic hash of anything
    }

    // ---- W3C traceparent build / validate / extract ----

    @Test
    @DisplayName("buildTraceparent: 00-<traceId>-<16hex spanId>-01, carrying the given trace id")
    void buildTraceparent_shape() {
        String traceId = "0123456789abcdef0123456789abcdef";
        String tp = EsqUtils.buildTraceparent(traceId);
        assertThat(tp).matches("00-" + traceId + "-[0-9a-f]{16}-01");
        assertThat(EsqUtils.isValidTraceparent(tp)).isTrue();
        assertThat(EsqUtils.traceIdFromTraceparent(tp)).isEqualTo(traceId);
    }

    @Test
    @DisplayName("buildTraceparent: fresh span id each call")
    void buildTraceparent_distinctSpanIds() {
        String traceId = "0123456789abcdef0123456789abcdef";
        assertThat(EsqUtils.buildTraceparent(traceId)).isNotEqualTo(EsqUtils.buildTraceparent(traceId));
    }

    @Test
    @DisplayName("isValidTraceparent: rejects malformed / zero / wrong-length forms")
    void isValidTraceparent_validation() {
        assertThat(EsqUtils.isValidTraceparent("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")).isTrue();
        assertThat(EsqUtils.isValidTraceparent("00-00000000000000000000000000000000-0123456789abcdef-01")).isFalse(); // zero trace id
        assertThat(EsqUtils.isValidTraceparent("00-0123456789abcdef0123456789abcdef-0000000000000000-01")).isFalse(); // zero span id
        assertThat(EsqUtils.isValidTraceparent("00-0123456789abcdef0123456789abcdef-0123456789abcdef")).isFalse();    // too few fields
        assertThat(EsqUtils.isValidTraceparent("00-XYZ-0123456789abcdef-01")).isFalse();                              // non-hex
        assertThat(EsqUtils.isValidTraceparent(null)).isFalse();
    }

    @Test
    @DisplayName("traceIdFromTraceparent: returns null for a malformed traceparent")
    void traceIdFromTraceparent_invalid() {
        assertThat(EsqUtils.traceIdFromTraceparent("not-a-traceparent")).isNull();
    }

    // ---- helpers ----

    private static String invokeParse(String podName) throws Exception {
        Method m = EsqUtils.class.getDeclaredMethod("parsePodNameOrdinal", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, podName);
    }
}

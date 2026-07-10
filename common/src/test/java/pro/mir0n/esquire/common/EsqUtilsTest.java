package pro.mir0n.esquire.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.utils.HostId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EsqUtils: the instance-identity delegation, and the correlation-id / W3C trace-context
 * helpers.
 *
 * The instance-identity RULE lives one layer down, in pro.mir0n.utils.HostId, and is covered by HostIdTest.
 * Here we only assert that EsqUtils reports what HostId resolves, and that the test seam pins it.
 */
class EsqUtilsTest {

    @BeforeEach
    @AfterEach
    void resetCache() {
        EsqUtils.resetInstanceNoCacheForTests();   // instanceNo() is lazy-cached; fresh per case.
    }

    // ---- instance identity: delegation to HostId ----

    @Test
    @DisplayName("instanceNo / instanceHost: report what HostId resolves")
    void instanceIdentity_delegatesToHostId() {
        assertThat(EsqUtils.instanceHost()).isEqualTo(HostId.instanceHost());
        EsqUtils.resetInstanceNoCacheForTests();
        assertThat(EsqUtils.instanceNo()).isEqualTo(HostId.instanceNo());
    }

    @Test
    @DisplayName("instanceNo: a pinned test value bypasses host-name resolution")
    void instanceNo_pinnedValueIsReturned() {
        EsqUtils.setInstanceNoForTests(7);
        assertThat(EsqUtils.instanceNo()).isEqualTo(7);
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

}

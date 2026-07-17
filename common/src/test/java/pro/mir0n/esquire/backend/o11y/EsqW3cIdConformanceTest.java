/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/15/2026 mir0n  created (v1.2.11 T11/I35): CROSS-LANGUAGE conformance vectors for the W3C trace-id shape.
 */
package pro.mir0n.esquire.backend.o11y;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I35 -- the trace-id shape ({@code traceId == correlationId}) is computed by TWO implementations that must never
 * drift: Java {@link EsqUtils} and the BFF's {@code explorer/backend/src/util/trace.ts}. A drift (a different hash,
 * upper-case hex, a changed slice) breaks the log&lt;-&gt;trace join SILENTLY -- and no test used to catch it.
 *
 * <p>These VECTORS are the cross-language contract. The BFF test
 * ({@code explorer/backend/test/util/w3c-id.conformance.test.ts}) asserts the SAME input-&gt;output pairs against
 * {@code trace.ts}. If the two languages ever disagree, one side's build fails. KEEP THE TWO FILES IN LOCKSTEP:
 * change a vector here, change it there. (Java's own second copy, {@code W3CTraceContext.isTraceId}, was collapsed
 * to delegate to {@code EsqUtils.isW3cTraceId}, so Java has a single authority -- asserted below.)
 *
 * <p>{@code generateCorrelationId()} / a blank settle are RANDOM, so only their SHAPE is checkable, not an exact
 * value; the deterministic functions ({@code toW3cTraceId}, {@code isW3cTraceId}, {@code settleCorrelationId} of a
 * present input) carry the vectors.
 */
class EsqW3cIdConformanceTest {

    // -- toW3cTraceId: SHA-256(input, UTF-8), first 16 bytes -> 32 lowercase hex. (input, expected) --
    static final String[][] TO_W3C = {
            {"esquire",           "355593035828b00bd3db642efe0b29a3"},
            {"X-Request-ID:7f3a", "b8b06019e070ae5645aaa89b4e95eabf"},
            {"user-42",           "6d894aa3ee802549d7f340e7c1cf0d1c"},
            {"hello world",       "b94d27b9934d3e08a52e52d7da7dabfa"},
    };

    // -- isW3cTraceId: 32 LOWERCASE hex, not all zero. (input, expected) --
    static final Object[][] IS_W3C = {
            {"3f8a1c2e4b6d8f0a1c2e4b6d8f0a1c2e", true},   // valid
            {"3F8A1C2E4B6D8F0A1C2E4B6D8F0A1C2E", false},  // upper-case -> not W3C (the drift that would break the join)
            {"00000000000000000000000000000000", false},  // all zero
            {"abc",                              false},  // too short
            {"3f8a1c2e4b6d8f0a1c2e4b6d8f0a1c2",  false},  // 31 chars
    };

    @Test
    void toW3cTraceId_matchesTheContract() {
        for (String[] v : TO_W3C) {
            assertThat(EsqUtils.toW3cTraceId(v[0])).as("toW3cTraceId(%s)", v[0]).isEqualTo(v[1]);
        }
    }

    @Test
    void isW3cTraceId_matchesTheContract() {
        for (Object[] v : IS_W3C) {
            assertThat(EsqUtils.isW3cTraceId((String) v[0])).as("isW3cTraceId(%s)", v[0]).isEqualTo(v[1]);
        }
    }

    @Test
    void settleCorrelationId_keepsAW3cId_convertsANonW3cOne() {
        // an already-W3C-shaped id is kept verbatim
        String w3c = "3f8a1c2e4b6d8f0a1c2e4b6d8f0a1c2e";
        assertThat(EsqUtils.settleCorrelationId(w3c)).isEqualTo(w3c);
        // a non-W3C id is converted via toW3cTraceId (same value as the TO_W3C vector for "user-42")
        assertThat(EsqUtils.settleCorrelationId("user-42")).isEqualTo("6d894aa3ee802549d7f340e7c1cf0d1c");
        // blank -> a freshly GENERATED id: not an exact value, but it MUST be W3C-shaped
        assertThat(EsqUtils.isW3cTraceId(EsqUtils.settleCorrelationId(""))).isTrue();
    }

    @Test
    void javaHasOneAuthority_w3cTraceContextDelegatesToEsqUtils() {
        // I35 reduction: W3CTraceContext.isTraceId must agree with EsqUtils.isW3cTraceId on every vector, so the
        // two Java copies cannot drift (it now delegates).
        for (Object[] v : IS_W3C) {
            assertThat(W3CTraceContext.isTraceId((String) v[0]))
                    .as("W3CTraceContext.isTraceId(%s) agrees with EsqUtils", v[0])
                    .isEqualTo(EsqUtils.isW3cTraceId((String) v[0]));
        }
    }
}

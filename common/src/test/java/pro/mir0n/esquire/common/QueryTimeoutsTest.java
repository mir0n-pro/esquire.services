package pro.mir0n.esquire.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The R6 opt-out resolution + the overflow-safety of the "no practical limit" sentinel. The sentinel guards a
 * real defect found on docker: an Integer.MAX_VALUE timeout overflows the JDBC driver's seconds*1000 conversion
 * (wraps negative) and the driver rejects it -- so the value must stay within int range after *1000.
 */
class QueryTimeoutsTest {

    @Test
    @DisplayName("resolveOptOut: 0 / negative -> the no-practical-limit sentinel (uncapped)")
    void resolveOptOut_disabled_returnsSentinel() {
        assertThat(QueryTimeouts.resolveOptOut(0)).isEqualTo(QueryTimeouts.NO_PRACTICAL_LIMIT_SECONDS);
        assertThat(QueryTimeouts.resolveOptOut(-1)).isEqualTo(QueryTimeouts.NO_PRACTICAL_LIMIT_SECONDS);
        assertThat(QueryTimeouts.resolveOptOut(Integer.MIN_VALUE)).isEqualTo(QueryTimeouts.NO_PRACTICAL_LIMIT_SECONDS);
    }

    @Test
    @DisplayName("resolveOptOut: a positive value caps the long op verbatim")
    void resolveOptOut_positive_returnsConfigured() {
        assertThat(QueryTimeouts.resolveOptOut(5)).isEqualTo(5);
        assertThat(QueryTimeouts.resolveOptOut(3600)).isEqualTo(3600);
    }

    @Test
    @DisplayName("sentinel is overflow-safe: seconds*1000 stays a positive int (the docker bug guard)")
    void sentinel_overflowSafe() {
        long asMillis = (long) QueryTimeouts.NO_PRACTICAL_LIMIT_SECONDS * 1000;
        assertThat(asMillis).isLessThanOrEqualTo(Integer.MAX_VALUE);
        // and the int-arithmetic the driver actually performs does not wrap negative
        assertThat(QueryTimeouts.NO_PRACTICAL_LIMIT_SECONDS * 1000).isPositive();
    }

    @Test
    @DisplayName("sentinel is a genuine no-practical-limit (at least a day)")
    void sentinel_isLarge() {
        assertThat(QueryTimeouts.NO_PRACTICAL_LIMIT_SECONDS).isGreaterThanOrEqualTo(86_400);
    }
}

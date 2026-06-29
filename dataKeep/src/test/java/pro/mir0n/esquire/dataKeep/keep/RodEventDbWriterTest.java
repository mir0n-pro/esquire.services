package pro.mir0n.esquire.dataKeep.keep;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The keep-surface (R5/R6) query-timeout wiring: the per-service value is applied to the apply-statement
 * template only when it is a positive number; null or {@code <= 0} leaves the keep uncapped (the pre-HA
 * default). JdbcTemplate reports -1 when no timeout is set.
 */
class RodEventDbWriterTest {

    private static final int JDBC_NO_TIMEOUT = -1;

    @Test
    @DisplayName("null timeout -> uncapped (no setQueryTimeout)")
    void nullTimeout_leavesUncapped() {
        RodEventDbWriter w = new RodEventDbWriter(mock(DataSource.class), "postgres", null, null);
        assertThat(w.queryTimeoutSeconds()).isEqualTo(JDBC_NO_TIMEOUT);
    }

    @Test
    @DisplayName("zero / negative timeout -> uncapped (no setQueryTimeout)")
    void nonPositiveTimeout_leavesUncapped() {
        assertThat(new RodEventDbWriter(mock(DataSource.class), "postgres", null, 0).queryTimeoutSeconds())
                .isEqualTo(JDBC_NO_TIMEOUT);
        assertThat(new RodEventDbWriter(mock(DataSource.class), "postgres", null, -5).queryTimeoutSeconds())
                .isEqualTo(JDBC_NO_TIMEOUT);
    }

    @Test
    @DisplayName("positive timeout -> applied verbatim to the apply-statement template")
    void positiveTimeout_isApplied() {
        RodEventDbWriter w = new RodEventDbWriter(mock(DataSource.class), "postgres", null, 8);
        assertThat(w.queryTimeoutSeconds()).isEqualTo(8);
    }
}

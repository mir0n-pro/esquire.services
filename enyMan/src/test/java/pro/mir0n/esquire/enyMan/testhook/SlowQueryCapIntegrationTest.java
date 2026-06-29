package pro.mir0n.esquire.enyMan.testhook;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R6 cap, proven on a real Postgres: the request-path cap (spring.transaction.default-timeout, here a
 * DataSourceTransactionManager default-timeout) cancels a pg_sleep that runs past it, while the opt-out
 * template (QueryTimeouts -- the move / cache-load mechanism) lets the same query complete. Drives the
 * actual {@link SlowQueryTestService}, so it also pins the QueryTimeoutException classification the hook
 * relies on. Skipped when Docker is absent.
 */
@Testcontainers(disabledWithoutDocker = true)
class SlowQueryCapIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("esq2025").withUsername("esq2025").withPassword("q");

    private HikariDataSource ds;

    private SlowQueryTestService serviceWithCap(int capSeconds) {
        ds = new HikariDataSource();
        ds.setJdbcUrl(PG.getJdbcUrl());
        ds.setUsername(PG.getUsername());
        ds.setPassword(PG.getPassword());
        DataSourceTransactionManager mgr = new DataSourceTransactionManager(ds);
        mgr.setDefaultTimeout(capSeconds);   // the request-path cap the capped template inherits
        return new SlowQueryTestService(new JdbcTemplate(ds), mgr, PG.getJdbcUrl());
    }

    @AfterEach
    void closePool() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }

    @Test
    @DisplayName("capped path: a query past the cap is cancelled at ~the cap")
    void cappedQueryCancelled() {
        SlowQueryResult r = serviceWithCap(2).runCapped(10);
        assertThat(r.timedOut()).as("cap should cancel the 10s query").isTrue();
        assertThat(r.elapsedMs()).as("cancelled at ~2s, not the full 10s").isLessThan(6000);
    }

    @Test
    @DisplayName("opt-out path: a long query runs to completion despite the cap")
    void optOutQueryCompletes() {
        SlowQueryResult r = serviceWithCap(2).runOptOut(3);
        assertThat(r.timedOut()).as("opt-out must not be capped").isFalse();
        assertThat(r.elapsedMs()).as("ran the full ~3s").isGreaterThanOrEqualTo(2500);
    }
}

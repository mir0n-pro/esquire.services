/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: the R6 query-timeout test hook. Runs a deliberately long DB statement so the
 *                   request-path cap can be observed firing -- a DB-side sleep is required because the cap is a
 *                   JDBC query timeout (it cancels a statement blocked IN the database, not a Java sleep). Gated
 *                   by esq.test.slow-query-enabled (default false) so it is NEVER wired in production.
 */
package pro.mir0n.esquire.enyMan.testhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.common.QueryTimeouts;

/**
 * Runs a long DB statement on two paths so the R6 cap can be observed: the {@code capped} path inherits the
 * manager default-timeout (the request-path cap) and is cancelled at the cap; the {@code opt-out} path uses the
 * same {@link QueryTimeouts} opt-out the move / cache load use and runs to completion. A QueryTimeoutException
 * (pgjdbc SQLState 57014 "query_canceled", translated by Spring) is the cap firing.
 */
@Component
@ConditionalOnProperty(name = "esq.test.slow-query-enabled", havingValue = "true")
public class SlowQueryTestService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate cappedTx;   // inherits the manager default-timeout = the request-path cap
    private final TransactionTemplate optOutTx;   // the R6 opt-out -- never inherits the cap
    private final String  sleepSql;
    private final boolean oracle;

    public SlowQueryTestService(JdbcTemplate jdbc,
                                PlatformTransactionManager txManager,
                                @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jdbc     = jdbc;
        this.cappedTx = new TransactionTemplate(txManager);
        this.optOutTx = new TransactionTemplate(txManager);
        this.optOutTx.setTimeout(QueryTimeouts.resolveOptOut(0));
        this.oracle   = datasourceUrl != null && datasourceUrl.contains("oracle");
        this.sleepSql = oracle ? "BEGIN DBMS_SESSION.SLEEP(?); END;" : "SELECT pg_sleep(?)";
    }

    /** Run the sleep on the capped path (subject to the request-path cap). */
    public SlowQueryResult runCapped(int seconds) {
        return run("capped", cappedTx, seconds);
    }

    /** Run the sleep on the opt-out path (the move / cache-load mechanism -- never capped). */
    public SlowQueryResult runOptOut(int seconds) {
        return run("opt-out", optOutTx, seconds);
    }

    private SlowQueryResult run(String mode, TransactionTemplate tx, int seconds) {
        long    start    = System.nanoTime();
        boolean timedOut = false;
        String  error    = null;
        try {
            tx.executeWithoutResult(status -> executeSleep(seconds));
        } catch (QueryTimeoutException cancelled) {
            timedOut = true;
            error    = cancelled.getClass().getSimpleName();
        } catch (DataAccessException unexpected) {
            error    = "unexpected: " + unexpected.getClass().getSimpleName();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        SlowQueryResult ret = new SlowQueryResult(mode, seconds, elapsedMs, timedOut, error);
        return ret;
    }

    private void executeSleep(int seconds) {
        if (oracle) {
            jdbc.update(sleepSql, seconds);
        } else {
            jdbc.query(sleepSql, rs -> null, seconds);
        }
    }
}

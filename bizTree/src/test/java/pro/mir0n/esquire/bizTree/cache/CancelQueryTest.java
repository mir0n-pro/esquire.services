package pro.mir0n.esquire.bizTree.cache;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the night-watch cancel for REAL: {@code PreparedStatement.cancel()} -- the exact call
 * {@code Monad.PrepareStatementCancelable.cancel()} makes -- aborts an in-flight H2 query mid-execution,
 * not merely after a wall-clock timeout. A row-dependent sleeping ALIAS over SYSTEM_RANGE makes a
 * query that would run ~20s; cancelled from another thread after 400ms, it must abort in well under a
 * second and surface as a SQL error (which the monad reports as CHECKSUM FAILED -> inconclusive).
 */
class CancelQueryTest {

    /** H2 ALIAS target. Sleeps a fixed 10ms per call; the arg is the row value so H2 evaluates it per row. */
    public static long sleepPerRow(long rowVal) throws InterruptedException {
        Thread.sleep(10);
        return rowVal;
    }

    @Test
    void cancelAbortsRunningQueryMidFlight() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:canceltest;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");

        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.execute("CREATE ALIAS SLEEP_PER_ROW FOR \"" + CancelQueryTest.class.getName() + ".sleepPerRow\"");
        }

        // ~2000 rows * 10ms = ~20s if left to run; arg X (per row) blocks constant-folding to one call
        Connection con = ds.getConnection();
        PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(SLEEP_PER_ROW(X)) FROM SYSTEM_RANGE(1, 2000)");

        AtomicReference<Throwable> err     = new AtomicReference<>();
        AtomicLong                 elapsed = new AtomicLong();
        Thread runner = new Thread(() -> {
            long t0 = System.nanoTime();
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            } catch (Throwable e) {
                err.set(e);
            } finally {
                elapsed.set((System.nanoTime() - t0) / 1_000_000L);
            }
        }, "cancel-test-query");

        runner.start();
        Thread.sleep(400);     // let the query get well underway
        ps.cancel();           // <-- the real mechanism: abort the in-flight statement from another thread
        runner.join(5000);

        assertThat(runner.isAlive()).as("query thread finished after cancel").isFalse();
        assertThat(err.get()).as("cancel surfaced as a SQL error (query aborted, not completed)").isNotNull();
        assertThat(elapsed.get()).as("aborted fast (ms), not the full ~20s run").isLessThan(3000L);

        con.close();
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: the shared AUDIT wiring over the generic x-Rod. build() resolves the log
 *                   datasource (shared = service DB, or dedicated = a separate Hikari pool / vendor), makes
 *                   the AuditLogWriter, registers each kind -> AuditLogSql key on the xx-Rod pool, starts
 *                   the xy/xx-Rod, and returns a Handle (the XYRod + a shutdown hook). Each service's
 *                   AuditConfig just supplies its AuditSettings + its kind->sql-key map.
 * 06/06/2026 mir0n  option (c) + modes: MODE_IN_PROCESS / MODE_BUS constants; buildBus() wires the xy-Rod
 *                   feed to a bus dispatcher (no local writer / registry / datasource -- the standalone xxRod
 *                   owns those); buildBusPool() wires the feed to an XXRod publisher pool (N async senders)
 *                   for the high-bandwidth bus path.
 */
package pro.mir0n.esquire.common.audit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import pro.mir0n.esquire.common.xrod.RodEvent;
import pro.mir0n.esquire.common.xrod.RodRepositoryRegistry;
import pro.mir0n.esquire.common.xrod.XXRod;
import pro.mir0n.esquire.common.xrod.XYRod;

import javax.sql.DataSource;
import java.util.Map;
import java.util.function.Consumer;

public final class AuditRod {

    /** Audit dispatch mode (the {@code ...audit-logging.x-rod.mode} value). */
    public static final String MODE_IN_PROCESS = "in-process";
    public static final String MODE_BUS        = "bus";

    private AuditRod() {
    }

    public static Handle build(String name, AuditSettings s, Map<Integer, String> kindToSqlKey,
                               DataSource serviceDataSource, Logger log) {
        boolean dedicated = "dedicated".equalsIgnoreCase(s.logDatastore());
        DataSource logDs;
        boolean oracle;
        HikariDataSource dedicatedDs = null;
        if (dedicated) {
            dedicatedDs = buildDedicated(name, s);
            logDs = dedicatedDs;
            oracle = s.logDbVendor() != null && s.logDbVendor().contains("oracle");
        } else {
            logDs = serviceDataSource;
            oracle = s.businessProfile() != null && s.businessProfile().contains("oracle");
        }

        AuditLogWriter writer = new AuditLogWriter(logDs, oracle);
        RodRepositoryRegistry registry = new RodRepositoryRegistry();
        kindToSqlKey.forEach((kind, sqlKey) -> registry.register(kind, e -> writer.applyEvent(sqlKey, e)));

        XXRod xx = new XXRod(registry, s.poolSize(), s.virtualThreads());
        xx.start(name + "-xxrod", log);
        XYRod xy = new XYRod(xx::submit, s.enabled(), s.feedCapacity());
        xy.start(name + "-xyrod", log);

        log.info("audit x-Rod wired: name={}, enabled={}, poolSize={}, virtual={}, feedCapacity={}, log-datastore={}, oracle={}",
                name, s.enabled(), s.poolSize(), s.virtualThreads(), s.feedCapacity(), dedicated ? "dedicated" : "shared", oracle);
        return new Handle(xy, xx, dedicatedDs);
    }

    /**
     * Option (c) bus mode: the xy-Rod feed dispatches to {@code busDispatcher} (publishes to the audit
     * queue) instead of an in-process xx-Rod. No writer / registry / datasource is built here -- the
     * standalone xxRod consumer owns those.
     */
    public static Handle buildBus(String name, AuditSettings s, Consumer<RodEvent> busDispatcher, Logger log) {
        XYRod xy = new XYRod(busDispatcher, s.enabled(), s.feedCapacity());
        xy.start(name + "-xyrod", log);
        log.info("audit x-Rod wired: name={}, mode=bus, enabled={}, feedCapacity={}",
                name, s.enabled(), s.feedCapacity());
        return new Handle(xy, null, null);
    }

    /**
     * Option (c) bus mode WITH a publisher pool: the xy-Rod feed dispatches to a bounded XXRod pool that
     * issues a thread per event to run {@code busPublisher} (the same thread-per-event mechanism as the
     * in-process apply pool). No extra queue -- the xy-Rod feed is the only queue; the pool sits after it.
     * Pair {@code busPublisher} with an async (useAsyncSend) connection factory for real throughput gain.
     */
    public static Handle buildBusPool(String name, AuditSettings s, Consumer<RodEvent> busPublisher,
                                      int publisherPoolSize, Logger log) {
        XXRod pubPool = new XXRod(busPublisher, publisherPoolSize, s.virtualThreads());
        pubPool.start(name + "-pubpool", log);
        XYRod xy = new XYRod(pubPool::submit, s.enabled(), s.feedCapacity());
        xy.start(name + "-xyrod", log);
        log.info("audit x-Rod wired: name={}, mode=bus(pool), enabled={}, publisherPoolSize={}, feedCapacity={}",
                name, s.enabled(), publisherPoolSize, s.feedCapacity());
        return new Handle(xy, pubPool, null);
    }

    private static HikariDataSource buildDedicated(String name, AuditSettings s) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(s.logDbUrl());
        hc.setUsername(s.logDbUsername());
        hc.setPassword(s.logDbPassword());
        hc.setMaximumPoolSize(s.logDbPoolSize());
        hc.setPoolName(name + "-audit-log-pool");
        // The xx-rod applies have no Spring transaction, so each INSERT/MERGE must auto-commit.
        hc.setAutoCommit(true);
        return new HikariDataSource(hc);
    }

    /** What a service's AuditConfig keeps: the XYRod (to inject) + a shutdown hook for @PreDestroy. */
    public static final class Handle {
        private final XYRod xyRod;
        private final XXRod xxRod;
        private final HikariDataSource dedicatedDataSource;

        Handle(XYRod xyRod, XXRod xxRod, HikariDataSource dedicatedDataSource) {
            this.xyRod = xyRod;
            this.xxRod = xxRod;
            this.dedicatedDataSource = dedicatedDataSource;
        }

        public XYRod xyRod() {
            return xyRod;
        }

        public void shutdown() {
            xyRod.shutdown();
            if (xxRod != null) {
                xxRod.shutdown();
            }
            if (dedicatedDataSource != null) {
                dedicatedDataSource.close();
            }
        }
    }
}

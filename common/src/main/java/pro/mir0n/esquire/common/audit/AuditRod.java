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
 */
package pro.mir0n.esquire.common.audit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import pro.mir0n.esquire.common.xrod.RodRepositoryRegistry;
import pro.mir0n.esquire.common.xrod.XXRod;
import pro.mir0n.esquire.common.xrod.XYRod;

import javax.sql.DataSource;
import java.util.Map;

public final class AuditRod {

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
            xxRod.shutdown();
            if (dedicatedDataSource != null) {
                dedicatedDataSource.close();
            }
        }
    }
}

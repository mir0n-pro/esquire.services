/*
 *  Esquire frameworks (tm)
 *  esquire-dataKeep
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/18/2026 mir0n  created: the generic keep DB-apply assembly. A RodEventDbWriter over the dialect-keyed SQL
 *                   store + a kind->statement registry from the director's kinds; exposes the applier (the worker
 *                   an x-rod runs). Two pool modes: DEDICATED -- builds and OWNS its own auto-commit Hikari pool
 *                   from the datasource group; SHARED -- reuses a provided DataSource (the service's own pool) and
 *                   does NOT own it. AutoCloseable -- close() closes only a pool it owns; the kinds + SQL are data.
 * 06/21/2026 mir0n  dedicated-mode dialect now KeepSqlStore.dialectOf(ds.url()) -- derived from the datasource
 *                   URL subprotocol instead of the vendor/profile label.
 */
package pro.mir0n.esquire.dataKeep.keep;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodEventRepoRegistry;

import javax.sql.DataSource;
import java.util.Map;
import java.util.function.Consumer;

/** Builds a keep's DB applier: own auto-commit pool (from the datasource group) + a {@link RodEventDbWriter}
 *  over the {@link KeepSqlStore}, registered per the {@code kind -> sql-key} map the director supplies. The
 *  {@link #applier()} is the worker an in-process or bus x-rod runs. */
public final class KeepApplier implements AutoCloseable {

    private static final int DEFAULT_POOL_SIZE = 8;

    private final DataSource dataSource;
    private final boolean ownsPool;
    private final Consumer<RodEvent> applier;

    /** DEDICATED pool: the keep builds and OWNS its own auto-commit Hikari pool from the datasource group;
     *  the dialect is derived from the group's JDBC URL (its subprotocol). */
    public KeepApplier(KeepDataSourceParams ds, KeepSqlStore sql, Map<Integer, String> kindToSqlKey, Logger devLog) {
        this(buildPool(ds), KeepSqlStore.dialectOf(ds.url()), true, sql, kindToSqlKey, devLog);
    }

    /** SHARED pool: the keep REUSES a provided DataSource (e.g. the service's own business pool) and does NOT
     *  own it -- {@link #close()} leaves it open. The dialect is supplied by the caller (from the service's URL). */
    public KeepApplier(DataSource shared, String dialect, KeepSqlStore sql, Map<Integer, String> kindToSqlKey, Logger devLog) {
        this(shared, dialect, false, sql, kindToSqlKey, devLog);
    }

    private KeepApplier(DataSource dataSource, String dialect, boolean ownsPool, KeepSqlStore sql,
                        Map<Integer, String> kindToSqlKey, Logger devLog) {
        this.dataSource = dataSource;
        this.ownsPool   = ownsPool;
        RodEventDbWriter writer = new RodEventDbWriter(dataSource, dialect, sql);
        RodEventRepoRegistry registry = new RodEventRepoRegistry();
        kindToSqlKey.forEach((kind, sqlKey) -> registry.register(kind, e -> writer.applyEvent(sqlKey, e)));
        this.applier = registry.applier(devLog);
    }

    /** The worker that applies each relayed event to the DB sink (run on an x-rod's worker pool). */
    public Consumer<RodEvent> applier() {
        return applier;
    }

    /** Closes the pool ONLY if this keep owns it (dedicated). A shared (service) DataSource is left open. */
    @Override
    public void close() {
        if (ownsPool && dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }

    private static HikariDataSource buildPool(KeepDataSourceParams p) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(p.url());
        hc.setUsername(p.username());
        hc.setPassword(p.password());
        KeepDataSourceParams.Hikari h = p.hikariOrEmpty();
        hc.setMaximumPoolSize(h.maximumPoolSize() != null ? h.maximumPoolSize() : DEFAULT_POOL_SIZE);
        if (h.minimumIdle() != null)       hc.setMinimumIdle(h.minimumIdle());
        if (h.connectionTimeout() != null) hc.setConnectionTimeout(h.connectionTimeout());
        if (h.maxLifetime() != null)       hc.setMaxLifetime(h.maxLifetime());
        if (h.idleTimeout() != null)       hc.setIdleTimeout(h.idleTimeout());
        hc.setPoolName("keep-db");
        // the applies run OUTSIDE any caller transaction -> each INSERT/MERGE must auto-commit.
        hc.setAutoCommit(true);
        return new HikariDataSource(hc);
    }
}

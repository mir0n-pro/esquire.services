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
 * 06/22/2026 mir0n  dropped SHARED pool mode: removed the shared-DataSource constructor and the ownsPool field;
 *                   the keep is always a DEDICATED pool now -- close() closes its own pool unconditionally. RodEvent
 *                   / RodEventRepoRegistry imports moved to messaging.xrod.
 * 06/22/2026 mir0n  added health(): pings the keep pool -- a pooled connection that validates within 2s -> UP,
 *                   any failure (cannot reach / validate the DB) -> DOWN; the keep-datasource health source.
 * 06/23/2026 mir0n  buildPool forwards hikari.data-source-properties to the JDBC driver (addDataSourceProperty) --
 *                   so pgjdbc socketTimeout / tcpKeepAlive let health() fail fast on a vanished DB instead of
 *                   hanging on a half-open socket.
 * 06/29/2026 mir0n  passes ds.queryTimeoutSeconds() to the RodEventDbWriter -- the per-apply statement cap (R6)
 */
package pro.mir0n.esquire.dataKeep.keep;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.RodEventRepoRegistry;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.function.Consumer;

/** Builds a keep's DB applier: own auto-commit pool (from the datasource group) + a {@link RodEventDbWriter}
 *  over the {@link KeepSqlStore}, registered per the {@code kind -> sql-key} map the director supplies. The
 *  {@link #applier()} is the worker an in-process or bus x-rod runs. */
public final class KeepApplier implements AutoCloseable {

    private static final int DEFAULT_POOL_SIZE = 8;

    private final DataSource dataSource;
    private final Consumer<RodEvent> applier;

    /** The keep builds and OWNS its own auto-commit Hikari pool from the datasource group; the dialect is
     *  derived from the group's JDBC URL (its subprotocol). */
    public KeepApplier(KeepDataSourceParams ds, KeepSqlStore sql, Map<Integer, String> kindToSqlKey, Logger devLog) {
        this.dataSource = buildPool(ds);
        RodEventDbWriter writer = new RodEventDbWriter(dataSource, KeepSqlStore.dialectOf(ds.url()), sql, ds.queryTimeoutSeconds());
        RodEventRepoRegistry registry = new RodEventRepoRegistry();
        kindToSqlKey.forEach((kind, sqlKey) -> registry.register(kind, e -> writer.applyEvent(sqlKey, e)));
        this.applier = registry.applier(devLog);
    }

    /** The worker that applies each relayed event to the DB sink (run on an x-rod's worker pool). */
    public Consumer<RodEvent> applier() {
        return applier;
    }

    /** The keep DB connection health: UP if a pooled connection validates within 2s, DOWN otherwise (the pool
     *  cannot reach / validate the database). The in-process keep x-rod reports this as its receiver-side health
     *  (the DB it applies to), and auKeep forwards it as a separate keep-datasource health contributor. */
    public TransportHealth health() {
        TransportHealth ret;
        try (Connection c = dataSource.getConnection()) {
            ret = c.isValid(2) ? TransportHealth.UP : TransportHealth.DOWN;
        } catch (Exception probeFailed) {
            ret = TransportHealth.DOWN;
        }
        return ret;
    }

    /** Closes the keep's own pool. */
    @Override
    public void close() {
        if (dataSource instanceof HikariDataSource hikari) {
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
        // driver connection properties forwarded VERBATIM to the JDBC driver (pgjdbc socketTimeout / tcpKeepAlive,
        // ...) -- the same passthrough as spring.datasource.hikari.data-source-properties.
        if (h.dataSourceProperties() != null) {
            h.dataSourceProperties().forEach(hc::addDataSourceProperty);
        }
        hc.setPoolName("keep-db");
        // the applies run OUTSIDE any caller transaction -> each INSERT/MERGE must auto-commit.
        hc.setAutoCommit(true);
        return new HikariDataSource(hc);
    }
}

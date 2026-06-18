/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: the in-process log-DB audit pod -- a pluggable IXRod resolved by x-rod.rod-class.
 *                   post() buffers the change in the current tx and (after commit) feeds the rod's OWN receive
 *                   pool, which applies each event to the service's *_log via the kind->RodEventRepo registry
 *                   (AuditKinds + AuditLogSql; the service ships only the *_log SQL it needs). Self-configures
 *                   EVERYTHING from the leg's x-rod.custom layer, bound into this pod's OWN XRodLogDbParams (vendor +
 *                   jdbc url/user/password + pool-size) -- it builds + owns its Hikari pool. Composes the default
 *                   XRod transceiver (feed + pool + msg-audit) so it carries no duplicate engine code.
 * 06/17/2026 mir0n  extends AXRod (was composing an inner XRod); validate() requires x-rod.log-db.url (moved out
 *                   of configure); the Hikari pool default renamed DEFAULT_DB_POOL (distinct from the worker pool)
 */
package pro.mir0n.esquire.common.audit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodEventRepoRegistry;
import pro.mir0n.esquire.messaging.xrod.impl.AXRod;

import java.util.function.Consumer;

/** In-process audit pod: an {@link AXRod} whose feed loops into its own worker pool, which applies each event to
 *  the {@code *_log}; configured from x-rod.log-db (owns its Hikari pool). No transport leg. */
public final class XRodLogDb extends AXRod {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + XRodLogDb.class.getName());
    /** The Hikari connection-pool default -- the *_log DATASOURCE pool (distinct from the engine worker pool,
     *  whose default is {@link AXRod#DEFAULT_POOL_SIZE}). */
    private static final int DEFAULT_DB_POOL = 8;
    /** This pod's OWN named param sub-block under the leg's x-rod (x-rod.log-db). */
    public static final String PARAM = "log-db";

    private HikariDataSource dataSource;     // the log-DB pool this rod owns; closed on shutdown
    private Consumer<RodEvent> applier;      // built in configure (prepare), run by the engine pool at start

    @Override
    public void validate(XRodParams params) {
        XRodLogDbParams logDb = params != null ? params.sub(PARAM, XRodLogDbParams.class) : null;
        require(logDb != null && logDb.url() != null && !logDb.url().isBlank(),
                "x-rod." + PARAM + ".url (the *_log datasource)", params);
    }

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        super.configure(params, role, objectMapper);   // identity + engine knobs (feed / worker pool)
        // read this pod's OWN log-db sub-block, build the datasource + the *_log applier (url guaranteed by validate).
        XRodLogDbParams logDb = params.sub(PARAM, XRodLogDbParams.class);
        boolean oracle = logDb.vendorOr("dev-postgres").contains("oracle");
        this.dataSource = buildPool(logDb);
        AuditLogWriter writer = new AuditLogWriter(dataSource, oracle);
        RodEventRepoRegistry registry = new RodEventRepoRegistry();
        AuditKinds.all(EsqObjectKindStorage.getInstance())
                .forEach((kind, sqlKey) -> registry.register(kind, e -> writer.applyEvent(sqlKey, e)));
        this.applier = registry.applier(devLog);
    }

    @Override
    public void start(String name, Logger devLogger, Consumer<RodEvent> worker) {
        // in-process: outbound loops the feed into the receive pool, which applies each event via the registry
        // to the *_log. There is no transport leg and no codec -- the rod never opens a bus consumer/publisher.
        startEngine(name, devLogger, this::receive, applier);
    }

    @Override
    public void shutdown() {
        super.shutdown();   // wind the feed + worker pool down; in-flight applies finish
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static HikariDataSource buildPool(XRodLogDbParams p) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(p.url());
        hc.setUsername(p.username());
        hc.setPassword(p.password());
        hc.setMaximumPoolSize(p.poolSizeOr(DEFAULT_DB_POOL));
        hc.setPoolName("xrod-logdb");
        // the applies run OUTSIDE a Spring transaction -> each INSERT/MERGE must auto-commit.
        hc.setAutoCommit(true);
        return new HikariDataSource(hc);
    }
}

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
 */
package pro.mir0n.esquire.common.audit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodEventRepoRegistry;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;

import java.util.Map;
import java.util.function.Consumer;

/** In-process audit pod: post() -> own pool -> apply to the *_log; configured from x-rod.custom (owns its pool). */
public final class XRodLogDb implements IXRod {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + XRodLogDb.class.getName());
    private static final int DEFAULT_POOL = 8;
    /** This pod's OWN named param sub-block under the leg's x-rod (x-rod.log-db). */
    public static final String PARAM = "log-db";

    private final XRod inner = new XRod();   // the transceiver core (feed + receive pool + msg-audit)
    private HikariDataSource dataSource;     // the log-DB pool this rod owns; closed on shutdown
    private Consumer<RodEvent> applier;      // built in configure (prepare), run by the inner pool at start

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        // PREPARE: read this pod's OWN log-db sub-block, build the datasource + the *_log applier.
        XRodLogDbParams logDb = params != null ? params.sub(PARAM, XRodLogDbParams.class) : null;
        if (logDb == null || logDb.url() == null || logDb.url().isBlank()) {
            throw new IllegalStateException(
                    "XRodLogDb: audit log-db mode requires x-rod." + PARAM + ".url (the *_log datasource)");
        }
        boolean oracle = logDb.vendorOr("dev-postgres").contains("oracle");
        this.dataSource = buildPool(logDb);
        AuditLogWriter writer = new AuditLogWriter(dataSource, oracle);
        RodEventRepoRegistry registry = new RodEventRepoRegistry();
        AuditKinds.all(EsqObjectKindStorage.getInstance())
                .forEach((kind, sqlKey) -> registry.register(kind, e -> writer.applyEvent(sqlKey, e)));
        this.applier = registry.applier(devLog);
        // in-process transceiver: hand the inner the SAME params but NO codec (objectMapper=null) -> it is not a
        // bus pod (a bus needs both a transport AND a codec), so it runs in-process: post -> feed -> own pool ->
        // applier -> *_log. The msg-audit (TX feed + RX apply) rides the inner's identity (kept from the params).
        inner.configure(params, role, null);
    }

    @Override
    public void start(String name, Logger devLog, Consumer<RodEvent> worker) {
        inner.start(name, devLog, applier);   // in-process: the inner's receive pool applies via the registry
    }

    @Override
    public void bindInbound(AutoCloseable inbound) {
        inner.bindInbound(inbound);
    }

    @Override
    public void shutdown() {
        inner.shutdown();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public boolean isEnabled() {
        return inner.isEnabled();
    }

    @Override
    public boolean usesOutboundTransport() {
        return false;   // in-process: writes the *_log from its own pool, no transport leg
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source, String msgType) {
        inner.post(op, kind, entityId, subId, source, msgType);
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, String msgType) {
        inner.post(op, kind, entityId, subId, msgType);
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body, String msgType) {
        inner.post(op, kind, entityId, subId, body, msgType);
    }

    @Override
    public void transmit(RodEvent event) {
        inner.transmit(event);
    }

    @Override
    public void submit(RodEvent event) {
        inner.submit(event);
    }

    private static HikariDataSource buildPool(XRodLogDbParams p) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(p.url());
        hc.setUsername(p.username());
        hc.setPassword(p.password());
        hc.setMaximumPoolSize(p.poolSizeOr(DEFAULT_POOL));
        hc.setPoolName("xrod-logdb");
        // the applies run OUTSIDE a Spring transaction -> each INSERT/MERGE must auto-commit.
        hc.setAutoCommit(true);
        return new HikariDataSource(hc);
    }
}

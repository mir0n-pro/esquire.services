/*
 *  Esquire frameworks (tm)
 *  esquire-dataKeep
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/18/2026 mir0n  created (generic, was common.audit.AuditLogWriter): applies a relayed RodEvent to a DB sink.
 *                   applyEvent() binds the uniform identity/header params + the event body (the source's field
 *                   map) onto the dialect-keyed SQL (KeepSqlStore) via NamedParameterJdbcTemplate. One INSERT/MERGE
 *                   per call, on the keep's worker thread. A param the body lacks binds NULL (e.g. a DELETE). The
 *                   writer knows nothing of any specific use -- the SQL keys + statements are the deployment's data.
 * 06/22/2026 mir0n  RodEvent import moved to messaging.xrod.
 * 06/29/2026 mir0n  constructor takes queryTimeoutSeconds (Integer): when set (> 0) applies it via JdbcTemplate
 *                   setQueryTimeout so a stuck *_log apply is cancelled instead of pinning a keep connection;
 *                   null / <= 0 leaves it uncapped (pre-HA). Added queryTimeoutSeconds() accessor (R6)
 * 07/08/2026 mir0n  applyEvent() body wrapped in EsqTraceMark.around("esq.keep.apply", "keep audit log", ...) --
 *                   the writer is not a Spring bean, so the programmatic mark stands in for @EsqTraced
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- applyEvent() counts esq.biz.keep.write.total and times
 *                   esq.biz.keep.write.duration (tags op = the RodEvent op, outcome = ok|error) around the DB
 *                   write. This is the one thing the bus meters cannot see: messaging.receive.total says the
 *                   audit event ARRIVED, only this says whether the row was WRITTEN. The op tag is null-safe --
 *                   these read from a finally, and a meter that throws there would REPLACE the real exception
 * 08/11/2026 mir0n  v1.2.12 -- PARAM_CHANGE_NO added and bound from the event in the header parameter map
 * 08/26/2026 mir0n  an op the action mapping does not cover is ignored with a named warning and counted
 *                   outcome=ignored, instead of falling through to a delete; action() answers null for it
 */
package pro.mir0n.esquire.dataKeep.keep;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.AbstractSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pro.mir0n.esquire.backend.o11y.EsqTraceMark;
import pro.mir0n.esquire.messaging.RodEvent;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Applies a {@link RodEvent} to a DB sink: the uniform identity/header + the event body bound to the
 *  dialect-keyed {@link KeepSqlStore} statement, via {@link NamedParameterJdbcTemplate}. */
public class RodEventDbWriter {

    private static final Logger log    = LoggerFactory.getLogger(RodEventDbWriter.class);
    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodEventDbWriter.class.getName());

    private final NamedParameterJdbcTemplate jdbc;
    private final String dialect;
    private final KeepSqlStore sql;

    // Uniform header bind-param names -- the :params every statement uses for the identity/audit columns.
    // (Data params are the source's field names from RodEvent.body().)
    public static final String PARAM_ACTION    = "action";
    public static final String PARAM_ENTITY_ID = "entityId";
    public static final String PARAM_KIND      = "kind";
    public static final String PARAM_SUB_ID    = "subId";
    public static final String PARAM_CRL       = "crl";
    public static final String PARAM_REQ       = "req";
    public static final String PARAM_UID       = "uid";
    public static final String PARAM_ACTION_TS = "actionTs";
    public static final String PARAM_CHANGE_NO = "changeNo";

    public RodEventDbWriter(DataSource dataSource, String dialect, KeepSqlStore sql, Integer queryTimeoutSeconds) {
        this.jdbc    = new NamedParameterJdbcTemplate(dataSource);
        this.dialect = dialect;
        this.sql     = sql;
        // Per-service apply-statement cap (JDBC setQueryTimeout): null or <= 0 leaves it uncapped (pre-HA
        // default). When set, a stuck *_log apply is cancelled instead of pinning a keep connection.
        if (queryTimeoutSeconds != null && queryTimeoutSeconds > 0) {
            this.jdbc.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);
        }
    }

    /** The effective per-statement query timeout (seconds) on this writer's template; -1 = none (the default
     *  when no keep cap is configured). Exposed for the keep-surface timeout test. */
    int queryTimeoutSeconds() {
        return jdbc.getJdbcTemplate().getQueryTimeout();
    }

    /** Write one row for the event: uniform header (identity) + the event body, straight to the keyed SQL. */
    public void applyEvent(String sqlKey, RodEvent e) {

        final String op = (e == null) ? null : e.dbAction();

        if (op == null) {
            String reason = (e == null || e.op() == null) ? "NONE" : e.op().name();
            log.warn("keep-db: IGNORED an event the keep cannot record (op={}) -- key={}, entityId={}, kind={}",
                    reason, sqlKey, e == null ? null : e.entityId(), e == null ? null : e.kind());
            EsqBizMeters.count("esq.biz.keep.write.total", "op", reason, "outcome", "ignored");
            return;
        }

        String outcome = "error";
        long startedAt = System.nanoTime();
        try {
            EsqTraceMark.around("esq.keep.apply", "keep audit log", () -> {
                Map<String, Object> params = header(e, op);
                if (e.body() != null) {
                    params.putAll(e.body());   // field names -> the SQL's data params (a DELETE body is empty)
                }
                int rows = jdbc.update(sql.forVendor(dialect, sqlKey), new TolerantSource(params));
                devLog.debug("keep-db apply key={} rows={}", sqlKey, rows);
            });
            outcome = "ok";
        } finally {
            //xxx: op is safe here
            EsqBizMeters.count("esq.biz.keep.write.total", "op", e.op().name() , "outcome", outcome);
            EsqBizMeters.time("esq.biz.keep.write.duration", System.nanoTime() - startedAt, "op", e.op().name());
        }
    }

    /** Uniform identity + header params, by the standardized names the SQL uses for every table. */
    private static Map<String, Object> header(RodEvent e, String op) {
        Map<String, Object> p = new HashMap<>();
        p.put(PARAM_ACTION,    op);
        p.put(PARAM_ENTITY_ID, e.entityId());
        p.put(PARAM_KIND,      e.kind());
        p.put(PARAM_SUB_ID,    e.subId());
        p.put(PARAM_CRL,       e.correlationId());
        p.put(PARAM_REQ,       e.requestId());
        p.put(PARAM_UID,       e.uid());
        p.put(PARAM_ACTION_TS, Timestamp.from(Instant.ofEpochMilli(e.actionTime())));
        p.put(PARAM_CHANGE_NO, e.changeNo());
        return p;
    }

    /**
     * Binds any SQL :param from the map; a param the map lacks resolves to NULL instead of failing. This lets a
     * DELETE (empty body -> only the header present) bind the data columns as NULL without the writer needing to
     * know each table's column list.
     */
    private static final class TolerantSource extends AbstractSqlParameterSource {
        private final Map<String, Object> values;

        TolerantSource(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public boolean hasValue(String paramName) {
            return true;
        }

        @Override
        public Object getValue(String paramName) {
            return values.get(paramName);
        }
    }
}

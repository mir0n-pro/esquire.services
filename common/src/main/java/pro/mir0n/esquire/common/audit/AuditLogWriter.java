/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created (was enyMan.rod.RodLogWriter): the AUDIT sink. applyEvent() = the uniform
 *                   identity/audit header + the RodEvent body (the entity's fillMap output) bound to the
 *                   vendor-keyed AuditLogSql, via NamedParameterJdbcTemplate against the configured log
 *                   datasource (shared = service DB, or dedicated = a separate pool / vendor). One INSERT/
 *                   MERGE per call, on the xx-Rod worker thread. A param the body lacks binds NULL (DELETE).
 */
package pro.mir0n.esquire.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.AbstractSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pro.mir0n.esquire.common.xrod.RodEvent;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class AuditLogWriter {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditLogWriter.class.getName());

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean oracle;

    public AuditLogWriter(DataSource logDataSource, boolean oracle) {
        this.jdbc = new NamedParameterJdbcTemplate(logDataSource);
        this.oracle = oracle;
    }

    /** Write one *_log row for the event: uniform header (identity/audit) + the event body, straight to SQL. */
    public void applyEvent(String sqlKey, RodEvent e) {
        Map<String, Object> params = header(e);
        if (e.body() != null) {
            params.putAll(e.body());   // entity property names -> the SQL's data params (DELETE body is empty)
        }
        int rows = jdbc.update(AuditLogSql.forVendor(oracle, sqlKey), new TolerantSource(params));
        devLog.debug("audit-log apply key={} rows={}", sqlKey, rows);
    }

    /** Uniform identity + audit params, by the standardized names AuditLogSql uses for every table. */
    private static Map<String, Object> header(RodEvent e) {
        Map<String, Object> p = new HashMap<>();
        p.put("action", action(e.op()));
        p.put("entityId", e.entityId());
        p.put("kind", e.kind());
        p.put("subId", e.subId());
        p.put("crl", e.correlationId());
        p.put("req", e.requestId());
        p.put("uid", e.uid());
        p.put("actionTs", Timestamp.from(Instant.ofEpochMilli(e.actionTime())));
        return p;
    }

    /** RodEvent op -> *_log action code. */
    private static String action(RodEvent.Op op) {
        String ret;
        switch (op) {
            case CREATE -> ret = "I";
            case UPDATE -> ret = "U";
            default     -> ret = "D";
        }
        return ret;
    }

    /**
     * Binds any SQL :param from the map; a param the map lacks resolves to NULL instead of failing. This
     * lets a DELETE (empty body -> only the header identity present) bind the data columns as NULL without
     * the writer needing to know each table's column list.
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

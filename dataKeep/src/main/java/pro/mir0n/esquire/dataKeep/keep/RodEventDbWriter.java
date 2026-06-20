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
 */
package pro.mir0n.esquire.dataKeep.keep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.AbstractSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Applies a {@link RodEvent} to a DB sink: the uniform identity/header + the event body bound to the
 *  dialect-keyed {@link KeepSqlStore} statement, via {@link NamedParameterJdbcTemplate}. */
public class RodEventDbWriter {

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

    // action column codes (Insert / Update / Delete).
    public static final String ACTION_INSERT = "I";
    public static final String ACTION_UPDATE = "U";
    public static final String ACTION_DELETE = "D";

    public RodEventDbWriter(DataSource dataSource, String dialect, KeepSqlStore sql) {
        this.jdbc    = new NamedParameterJdbcTemplate(dataSource);
        this.dialect = dialect;
        this.sql     = sql;
    }

    /** Write one row for the event: uniform header (identity) + the event body, straight to the keyed SQL. */
    public void applyEvent(String sqlKey, RodEvent e) {
        Map<String, Object> params = header(e);
        if (e.body() != null) {
            params.putAll(e.body());   // field names -> the SQL's data params (a DELETE body is empty)
        }
        int rows = jdbc.update(sql.forVendor(dialect, sqlKey), new TolerantSource(params));
        devLog.debug("keep-db apply key={} rows={}", sqlKey, rows);
    }

    /** Uniform identity + header params, by the standardized names the SQL uses for every table. */
    private static Map<String, Object> header(RodEvent e) {
        Map<String, Object> p = new HashMap<>();
        p.put(PARAM_ACTION,    action(e.op()));
        p.put(PARAM_ENTITY_ID, e.entityId());
        p.put(PARAM_KIND,      e.kind());
        p.put(PARAM_SUB_ID,    e.subId());
        p.put(PARAM_CRL,       e.correlationId());
        p.put(PARAM_REQ,       e.requestId());
        p.put(PARAM_UID,       e.uid());
        p.put(PARAM_ACTION_TS, Timestamp.from(Instant.ofEpochMilli(e.actionTime())));
        return p;
    }

    private static String action(RodEvent.Op op) {
        String ret;
        switch (op) {
            case CREATE -> ret = ACTION_INSERT;
            case UPDATE -> ret = ACTION_UPDATE;
            default     -> ret = ACTION_DELETE;
        }
        return ret;
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

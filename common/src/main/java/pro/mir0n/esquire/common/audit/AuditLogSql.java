/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created (was enyMan.rod.RodLogSql): vendor-keyed *_log INSERT/MERGE for the AUDIT use
 *                   of x-Rod. Lives under common.audit (NOT common.xrod) -- x-Rod is a generic fan-out
 *                   substrate; this is one sink (audit). Identity params are uniform (:entityId/:kind/:subId
 *                   from the RodEvent header); data params are the entity property names (from IMappable.
 *                   fillMap), so the body binds straight through. Postgres: INSERT .. ON CONFLICT DO NOTHING;
 *                   Oracle: MERGE .. WHEN NOT MATCHED. Shared by enyMan / pacMan / keySmith.
 * 06/06/2026 mir0n  SQL EXTERNALIZED: the statements moved out of code into per-module resources
 *                   META-INF/audit-log-{postgres,oracle}.xml (each service ships the subset it writes;
 *                   xxRod ships the full set). This class is now just the vendor-keyed loader/lookup.
 */
package pro.mir0n.esquire.common.audit;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vendor-keyed lookup for the audit-log SQL. The statements are NOT in code -- they live in the
 * per-module resource {@code META-INF/audit-log-postgres.xml} / {@code audit-log-oracle.xml}: each asset
 * service ships only the tables it writes in option (b), the xxRod consumer ships the full set. The
 * {@code key} attribute matches the constants below; the statement text uses {@code :named} params bound
 * by {@link AuditLogWriter}.
 */
public final class AuditLogSql {

    // Logical statement keys (the XML <statement key="..."> values; one per *_log table).
    public static final String ORG     = "org";
    public static final String ORG_PAR = "orgPar";
    public static final String USER    = "user";
    public static final String PERSON  = "person";
    public static final String ADDRESS = "address";
    public static final String USR_PAR = "usrPar";
    public static final String ACCOUNT = "account";
    public static final String AUTH    = "auth";

    private static final String POSTGRES_RESOURCE = "/META-INF/audit/postgres.xml";
    private static final String ORACLE_RESOURCE   = "/META-INF/audit/oracle.xml";

    // The audit-log SQL resource schema: <statement key="...">SQL</statement>.
    private static final String ELEM_STATEMENT = "statement";
    private static final String ATTR_KEY       = "key";

    private static final Map<String, String> POSTGRES = load(POSTGRES_RESOURCE);
    private static final Map<String, String> ORACLE   = load(ORACLE_RESOURCE);

    private AuditLogSql() {
    }

    /** SQL for the given statement key in the given vendor's dialect (true = Oracle, false = Postgres). */
    public static String forVendor(boolean oracle, String key) {
        String ret = (oracle ? ORACLE : POSTGRES).get(key);
        if (ret == null) {
            throw new IllegalArgumentException("no audit-log SQL for key '" + key + "' ("
                    + (oracle ? "oracle" : "postgres") + ") -- is "
                    + (oracle ? ORACLE_RESOURCE : POSTGRES_RESOURCE) + " on the classpath with this statement?");
        }
        return ret;
    }

    /** True iff the vendor resource on this classpath ships SQL for the key. */
    public static boolean has(boolean oracle, String key) {
        return (oracle ? ORACLE : POSTGRES).containsKey(key);
    }

    /** Load a per-module audit-log SQL resource into a key -> statement map. A module that ships no such
     *  resource yields an empty map (it simply has no audit SQL on its classpath). */
    private static Map<String, String> load(String resource) {
        Map<String, String> ret = new LinkedHashMap<>();
        try (InputStream in = AuditLogSql.class.getResourceAsStream(resource)) {
            if (in != null) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                NodeList stmts = doc.getElementsByTagName(ELEM_STATEMENT);
                for (int i = 0; i < stmts.getLength(); i++) {
                    Element e = (Element) stmts.item(i);
                    ret.put(e.getAttribute(ATTR_KEY).trim(), e.getTextContent().trim());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot load audit-log SQL resource: " + resource, e);
        }
        return ret;
    }
}

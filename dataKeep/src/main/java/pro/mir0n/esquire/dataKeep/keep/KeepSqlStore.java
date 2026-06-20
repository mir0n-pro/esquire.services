/*
 *  Esquire frameworks (tm)
 *  esquire-dataKeep
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/18/2026 mir0n  created (generic, was common.audit.AuditLogSql): a dialect-keyed SQL store for a DB keep.
 *                   The statements are NOT in code -- they live in a classpath resource group
 *                   META-INF/<group>/<dialect>.xml as <statement key="...">SQL</statement>, loaded on first use;
 *                   dialectOf() normalizes a vendor/profile label to the dialect token (which is the resource name),
 *                   so a new vendor is a dropped-in file, not an engine change. A keep loads the group it was
 *                   configured with (audit -> META-INF/audit). The store knows nothing of any specific use.
 */
package pro.mir0n.esquire.dataKeep.keep;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialect-keyed lookup for a keep's DB-sink SQL. Statements live in the per-module resource group
 * {@code META-INF/<group>/<dialect>.xml} ({@code <statement key="...">SQL</statement>}); the {@code key}
 * matches the kind->key map a keep's director supplies, the statement text uses {@code :named} params bound
 * by {@link RodEventDbWriter}. The dialect is a key, not a fixed pair: a vendor/profile label is normalized
 * to a dialect token by {@link #dialectOf} and the matching {@code <dialect>.xml} is loaded on first use, so
 * a new vendor is a dropped-in resource file (and JDBC driver), no code change. Generic: any keep that writes
 * a DB sink loads its own group.
 */
public final class KeepSqlStore {

    private static final String ELEM_STATEMENT = "statement";
    private static final String ATTR_KEY       = "key";

    /** The dialect a vendor/profile label resolves to when it matches no known token. */
    public static final String DEFAULT_DIALECT = "postgres";

    // Known SQL dialects, matched as a token inside a vendor/profile label (e.g. "dev-postgres" -> "postgres",
    // "prod-mysql" -> "mysql"). The matched token IS the resource name: META-INF/<group>/<dialect>.xml. Ship a
    // <dialect>.xml (and the JDBC driver) to add one; mariadb before mysql so the more specific token wins.
    private static final List<String> KNOWN_DIALECTS =
            List.of("postgres", "oracle", "mariadb", "mysql", "mssql", "h2");

    private final String group;
    // dialect -> (statement key -> SQL); loaded once per requested dialect (a keep uses exactly one).
    private final Map<String, Map<String, String>> byDialect = new ConcurrentHashMap<>();

    /** A SQL store for a resource {@code group} (e.g. {@code "audit"} -> META-INF/audit/<dialect>.xml). */
    public KeepSqlStore(String group) {
        this.group = group;
    }

    /** The SQL dialect a vendor/profile label resolves to: the first known dialect token the label contains,
     *  else {@link #DEFAULT_DIALECT} (e.g. {@code "dev-postgres" -> "postgres"}, {@code "prod-oracle" -> "oracle"};
     *  null/blank/unknown -> default). The result is the {@code <dialect>.xml} resource name. */
    public static String dialectOf(String vendor) {
        String ret = DEFAULT_DIALECT;
        if (vendor != null) {
            String v = vendor.toLowerCase(Locale.ROOT);
            for (String dialect : KNOWN_DIALECTS) {
                if (v.contains(dialect)) {
                    ret = dialect;
                    break;
                }
            }
        }
        return ret;
    }

    /** SQL for the given statement key in the given {@code dialect} (e.g. {@code "postgres"} / {@code "oracle"}),
     *  loaded from {@code META-INF/<group>/<dialect>.xml}. */
    public String forVendor(String dialect, String key) {
        String ret = statements(dialect).get(key);
        if (ret == null) {
            throw new IllegalArgumentException("no keep SQL for key '" + key + "' (" + dialect + ") -- is /META-INF/"
                    + group + "/" + dialect + ".xml on the classpath with this statement?");
        }
        return ret;
    }

    /** True iff this classpath ships SQL for the key in the given {@code dialect}. */
    public boolean has(String dialect, String key) {
        return statements(dialect).containsKey(key);
    }

    /** The (cached) statement map for a dialect; loaded on first use. A missing resource yields an empty map. */
    private Map<String, String> statements(String dialect) {
        return byDialect.computeIfAbsent(dialect, d -> load("/META-INF/" + group + "/" + d + ".xml"));
    }

    /** Load a resource into a key -> statement map. A missing resource yields an empty map. */
    private static Map<String, String> load(String resource) {
        Map<String, String> ret = new LinkedHashMap<>();
        try (InputStream in = KeepSqlStore.class.getResourceAsStream(resource)) {
            if (in != null) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                NodeList stmts = doc.getElementsByTagName(ELEM_STATEMENT);
                for (int i = 0; i < stmts.getLength(); i++) {
                    Element e = (Element) stmts.item(i);
                    ret.put(e.getAttribute(ATTR_KEY).trim(), e.getTextContent().trim());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot load keep SQL resource: " + resource, e);
        }
        return ret;
    }
}

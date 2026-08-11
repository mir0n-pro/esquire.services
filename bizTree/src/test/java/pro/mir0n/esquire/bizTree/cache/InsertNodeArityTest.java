/*
 *  Esquire frameworks (tm)
 *  BizTree service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.bizTree.cache;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the shipped {@code insert-node} statement and its callers in step. */
class InsertNodeArityTest {

    private static final String TABLE = "ARITY_T";

    private static Properties props() throws Exception {
        Properties p = new Properties();
        try (var in = InsertNodeArityTest.class.getResourceAsStream("/META-INF/h2-cache-sql.properties")) {
            p.load(in);
        }
        return p;
    }

    private static String sql(Properties p, String key) {
        return p.getProperty(key).replace("{table}", TABLE);
    }

    private static JdbcTemplate h2() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:aritytest;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        return new JdbcTemplate(ds);
    }

    /**
     * The column list and the VALUES list of the shipped statement must have the same length. A mismatch
     * here is a typo in the properties file that no other test would notice.
     */
    @Test
    void insertNodeColumnCountMatchesPlaceholderCount() throws Exception {
        String insert = sql(props(), "biztree.cache.sql.loader.insert-node");

        int open  = insert.indexOf('(');
        int close = insert.indexOf(')', open);
        int columns = insert.substring(open + 1, close).split(",").length;

        int values = insert.substring(insert.lastIndexOf("VALUES")).chars().filter(c -> c == '?').count() > 0
                ? (int) insert.substring(insert.lastIndexOf("VALUES")).chars().filter(c -> c == '?').count()
                : 0;

        assertThat(values).as("VALUES placeholders vs named columns in insert-node").isEqualTo(columns);
    }

    /**
     * The statement actually runs against the shipped DDL with exactly that many arguments. This is the
     * half that catches a CALLER drifting from the statement: build the table the real way, then insert
     * the real way.
     */
    @Test
    void insertNodeExecutesAgainstTheShippedDdl() throws Exception {
        Properties p = props();
        JdbcTemplate jt = h2();
        jt.execute("DROP TABLE IF EXISTS " + TABLE);
        jt.execute(sql(p, "biztree.cache.sql.ddl.create-table"));

        String insert = sql(p, "biztree.cache.sql.loader.insert-node");
        int argc = (int) insert.substring(insert.lastIndexOf("VALUES")).chars().filter(c -> c == '?').count();

        // The row shape both callers build: identity columns, then the two change numbers last.
        Object[] args = new Object[argc];
        args[0]  = "1";                 // tree_pk
        args[1]  = 20;                  // tree_et_pk
        args[2]  = "node";              // tree_name
        args[3]  = "desc";              // tree_desc
        for (int i = 4; i < argc; i++) {
            args[i] = null;
        }
        args[argc - 3] = 0;             // tree_status -- NOT NULL-ish with a default, keep it real
        args[argc - 2] = 7L;            // tree_entity_change_no
        args[argc - 1] = 3L;            // tree_path_change_no

        jt.update(insert, args);

        Long entityChangeNo = jt.queryForObject(
                "SELECT tree_entity_change_no FROM " + TABLE + " WHERE tree_pk = '1'", Long.class);
        Long pathChangeNo = jt.queryForObject(
                "SELECT tree_path_change_no FROM " + TABLE + " WHERE tree_pk = '1'", Long.class);
        assertThat(entityChangeNo).isEqualTo(7L);
        assertThat(pathChangeNo).isEqualTo(3L);
    }

    /** The guard's own statements must parse and run against the shipped DDL too. */
    @Test
    void guardStatementsExecuteAgainstTheShippedDdl() throws Exception {
        Properties p = props();
        JdbcTemplate jt = h2();
        jt.execute("DROP TABLE IF EXISTS " + TABLE);
        jt.execute(sql(p, "biztree.cache.sql.ddl.create-table"));

        String insert = sql(p, "biztree.cache.sql.loader.insert-node");
        int argc = (int) insert.substring(insert.lastIndexOf("VALUES")).chars().filter(c -> c == '?').count();
        Object[] args = new Object[argc];
        args[0] = "9"; args[1] = 20; args[2] = "n"; args[3] = "d";
        for (int i = 4; i < argc; i++) args[i] = null;
        args[6] = 999L;                 // tree_entity_pk -- the guard reads by this
        args[argc - 3] = 0;
        args[argc - 2] = null;
        args[argc - 1] = null;
        jt.update(insert, args);

        jt.update(sql(p, "biztree.cache.sql.repo.stamp-entity-change-no"), 5L, 999L);
        jt.update(sql(p, "biztree.cache.sql.repo.stamp-path-change-no"),   2L, 999L);

        Long[] found = jt.queryForObject(sql(p, "biztree.cache.sql.repo.find-change-numbers"),
                (rs, i) -> new Long[]{ (Long) rs.getObject(1), (Long) rs.getObject(2) }, 999L);

        assertThat(found).containsExactly(5L, 2L);
    }
}

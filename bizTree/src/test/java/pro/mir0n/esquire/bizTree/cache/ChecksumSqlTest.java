package pro.mir0n.esquire.bizTree.cache;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the REAL night-watch CHECKSUM SQL as it ships in h2-cache-sql.properties (loaded from the
 * classpath, {table}-substituted exactly as CacheSqlSet does). Catches malformed SQL -- including
 * the .properties line-continuation whitespace traps that a hardcoded-string test would miss --
 * and proves the digest is a non-null MD5 hex and INDEPENDENT of physical row insert order.
 */
class ChecksumSqlTest {

    private static String checksumSqlFor(String table) throws Exception {
        Properties p = new Properties();
        try (var in = ChecksumSqlTest.class.getResourceAsStream("/META-INF/h2-cache-sql.properties")) {
            p.load(in);
        }
        return p.getProperty("biztree.cache.sql.repo.checksum").replace("{table}", table);
    }

    private static JdbcTemplate h2() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:checksumtest;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        return new JdbcTemplate(ds);
    }

    private static void createTable(JdbcTemplate jt, String table) {
        jt.execute("CREATE TABLE " + table + " (TREE_PK VARCHAR(33) PRIMARY KEY, TREE_ET_PK INT DEFAULT 0, " +
                "TREE_NAME VARCHAR(50), TREE_DESC VARCHAR(1024), TREE_TREE_PK_PARENT VARCHAR(33), " +
                "TREE_TREE_PK_LINK VARCHAR(33), TREE_ENTITY_PK BIGINT, TREE_LEVEL INT, " +
                "TREE_PATH VARCHAR(2000), TREE_ENTITY_PATH VARCHAR(2000), TREE_STATUS INT DEFAULT 0, " +
                "TREE_ENTITY_CHANGE_NO BIGINT, TREE_PATH_CHANGE_NO BIGINT)");
    }

    private static void insert(JdbcTemplate jt, String table, String pk, int et, String name) {
        jt.update("INSERT INTO " + table + " (TREE_PK, TREE_ET_PK, TREE_NAME, TREE_STATUS) VALUES (?, ?, ?, 0)",
                pk, et, name);
    }

    @Test
    void checksumSqlExecutes_andYieldsMd5Hex() throws Exception {
        JdbcTemplate jt = h2();
        createTable(jt, "CK_A");
        insert(jt, "CK_A", "1", 20, "root");
        insert(jt, "CK_A", "2", 30, "kid");

        String digest = jt.queryForObject(checksumSqlFor("CK_A"), String.class);

        assertThat(digest).as("MD5 hex").isNotNull().matches("(?i)[0-9a-f]{32}");
    }

    @Test
    void checksumIsIndependentOfRowInsertOrder() throws Exception {
        JdbcTemplate jt = h2();
        createTable(jt, "CK_B");
        createTable(jt, "CK_C");
        insert(jt, "CK_B", "1", 20, "root");
        insert(jt, "CK_B", "2", 30, "kid");
        insert(jt, "CK_C", "2", 30, "kid");      // same rows, reversed insert order
        insert(jt, "CK_C", "1", 20, "root");

        String a = jt.queryForObject(checksumSqlFor("CK_B"), String.class);
        String b = jt.queryForObject(checksumSqlFor("CK_C"), String.class);

        assertThat(a).isEqualTo(b);
    }
}

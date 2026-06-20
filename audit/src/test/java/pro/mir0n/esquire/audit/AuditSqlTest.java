package pro.mir0n.esquire.audit;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.dataKeep.keep.KeepSqlStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The audit *_log SQL data (META-INF/audit) loaded through the generic KeepSqlStore. */
class AuditSqlTest {

    private static final KeepSqlStore SQL = new KeepSqlStore(AuditKeepDirector.SQL_GROUP);

    private static final List<String> KEYS = List.of(
            AuditKinds.ORG, AuditKinds.ORG_PAR, AuditKinds.USER, AuditKinds.PERSON,
            AuditKinds.ADDRESS, AuditKinds.USR_PAR, AuditKinds.ACCOUNT, AuditKinds.AUTH);

    @Test
    void everyKey_resolvesInBothVendors() {
        for (String key : KEYS) {
            assertThat(SQL.forVendor("postgres", key)).as("postgres " + key).isNotBlank();
            assertThat(SQL.forVendor("oracle", key)).as("oracle " + key).isNotBlank();
        }
    }

    @Test
    void postgresIsInsertOnConflict_oracleIsMerge() {
        for (String key : KEYS) {
            assertThat(SQL.forVendor("postgres", key)).as("postgres " + key)
                    .contains("INSERT INTO").contains("ON CONFLICT");
            assertThat(SQL.forVendor("oracle", key)).as("oracle " + key)
                    .contains("MERGE INTO");
        }
    }

    @Test
    void targetsTheMatchingLogTable() {
        assertThat(SQL.forVendor("postgres", AuditKinds.ACCOUNT)).contains("esq_account_log");
        assertThat(SQL.forVendor("oracle", AuditKinds.ACCOUNT)).contains("esq_account_log");
        assertThat(SQL.forVendor("postgres", AuditKinds.AUTH)).contains("esq_auth_log");
        assertThat(SQL.forVendor("oracle", AuditKinds.AUTH)).contains("esq_auth_log");
    }

    @Test
    void unknownKey_throws() {
        assertThatThrownBy(() -> SQL.forVendor("postgres", "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void dialectOf_normalizesTheProfileLabel() {
        assertThat(KeepSqlStore.dialectOf("dev-postgres")).isEqualTo("postgres");
        assertThat(KeepSqlStore.dialectOf("dev-oracle")).isEqualTo("oracle");
        assertThat(KeepSqlStore.dialectOf("prod-mysql")).isEqualTo("mysql");
        assertThat(KeepSqlStore.dialectOf("ORACLE")).isEqualTo("oracle");
        assertThat(KeepSqlStore.dialectOf("h2-mem")).isEqualTo("h2");
        assertThat(KeepSqlStore.dialectOf("something-else")).isEqualTo("postgres");
        assertThat(KeepSqlStore.dialectOf(null)).isEqualTo("postgres");
    }
}

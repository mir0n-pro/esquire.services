package pro.mir0n.esquire.common.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogSqlTest {

    private static final List<String> KEYS = List.of(
            AuditLogSql.ORG, AuditLogSql.ORG_PAR, AuditLogSql.USER, AuditLogSql.PERSON,
            AuditLogSql.ADDRESS, AuditLogSql.USR_PAR, AuditLogSql.ACCOUNT, AuditLogSql.AUTH);

    @Test
    void everyKey_resolvesInBothVendors() {
        for (String key : KEYS) {
            assertThat(AuditLogSql.forVendor(false, key)).as("postgres " + key).isNotBlank();
            assertThat(AuditLogSql.forVendor(true, key)).as("oracle " + key).isNotBlank();
        }
    }

    @Test
    void postgresIsInsertOnConflict_oracleIsMerge() {
        for (String key : KEYS) {
            assertThat(AuditLogSql.forVendor(false, key)).as("postgres " + key)
                    .contains("INSERT INTO").contains("ON CONFLICT");
            assertThat(AuditLogSql.forVendor(true, key)).as("oracle " + key)
                    .contains("MERGE INTO");
        }
    }

    @Test
    void targetsTheMatchingLogTable() {
        assertThat(AuditLogSql.forVendor(false, AuditLogSql.ACCOUNT)).contains("esq_account_log");
        assertThat(AuditLogSql.forVendor(true, AuditLogSql.ACCOUNT)).contains("esq_account_log");
        assertThat(AuditLogSql.forVendor(false, AuditLogSql.AUTH)).contains("esq_auth_log");
        assertThat(AuditLogSql.forVendor(true, AuditLogSql.AUTH)).contains("esq_auth_log");
    }

    @Test
    void unknownKey_throws() {
        assertThatThrownBy(() -> AuditLogSql.forVendor(false, "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }
}

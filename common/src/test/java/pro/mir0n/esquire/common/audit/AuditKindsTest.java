/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the audit kind -> sql-key routing map (entity kinds from the dictionary by
 *                   flag; sub-entity / param / auth kinds from EsqConstants).
 */
package pro.mir0n.esquire.common.audit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditKindsTest {

    @BeforeAll
    static void loadKinds() {
        EsqObjectKindStorage.getInstance().init((String) null);
    }

    @Test
    void mapsEntitySubEntityAndParamKinds() {
        Map<Integer, String> m = AuditKinds.all(EsqObjectKindStorage.getInstance());

        assertThat(m)
                .containsEntry(20, AuditLogSql.ORG)                                   // org (isOrg)
                .containsEntry(30, AuditLogSql.USER)                                  // sysadmin (isUsr)
                .containsEntry(34, AuditLogSql.USER)                                  // client   (isUsr)
                .containsEntry(50, AuditLogSql.ACCOUNT)                               // cacct    (isAcct)
                .containsEntry(EsqConstants.KIND_ORG_PAR, AuditLogSql.ORG_PAR)
                .containsEntry(EsqConstants.KIND_USR_PAR, AuditLogSql.USR_PAR)
                .containsEntry(EsqConstants.KIND_PERSON_PRIMARY, AuditLogSql.PERSON)
                .containsEntry(EsqConstants.KIND_ADDRESS_POSTAL, AuditLogSql.ADDRESS)
                .containsEntry(EsqConstants.KIND_ACCESS_PROFILE, AuditLogSql.AUTH);
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.audit;

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
                .containsEntry(20, AuditKinds.ORG)                                   // org (isOrg)
                .containsEntry(30, AuditKinds.USER)                                  // sysadmin (isUsr)
                .containsEntry(34, AuditKinds.USER)                                  // client   (isUsr)
                .containsEntry(50, AuditKinds.ACCOUNT)                               // cacct    (isAcct)
                .containsEntry(EsqConstants.KIND_ORG_PAR, AuditKinds.ORG_PAR)
                .containsEntry(EsqConstants.KIND_USR_PAR, AuditKinds.USR_PAR)
                .containsEntry(EsqConstants.KIND_PERSON_PRIMARY, AuditKinds.PERSON)
                .containsEntry(EsqConstants.KIND_ADDRESS_POSTAL, AuditKinds.ADDRESS)
                .containsEntry(EsqConstants.KIND_ACCESS_PROFILE, AuditKinds.AUTH);
    }
}

package pro.mir0n.esquire.backend.jpa;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.jpa.access.EsqAuthJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the fillMap body contracts that feed the x-Rod audit SQL params. */
class EntityFillMapTest {

    @Test
    void esqAuth_carriesManagedFields_andNeverSecurityQuestionOrAnswer() {
        EsqAuthJpa auth = new EsqAuthJpa();
        auth.setLoginId("jdoe");
        auth.setEmail("jdoe@example.com");
        auth.setConnectFlg("Y");
        auth.setTfaMethod("G");
        auth.setForceChangeFlg("N");

        Map<String, Object> body = new HashMap<>();
        auth.fillMap(body);

        assertThat(body)
                .containsEntry("loginId", "jdoe")
                .containsEntry("email", "jdoe@example.com")
                .containsEntry("connectFlg", "Y")
                .containsEntry("tfaMethod", "G")
                .containsEntry("forceChangeFlg", "N");
        // secrets must never reach esq_auth_log
        assertThat(body).doesNotContainKeys("securityQuestion", "securityAnswer");
        // au_usr_pk is the x-Rod header (entityId), not the body
        assertThat(body).doesNotContainKeys("name", "desc", "parentId");
    }

    @Test
    void esqAcct_carriesSuperFieldsPlusAccountData() {
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setName("C100");
        acct.setParentId("200");
        acct.setCcy("USD");
        acct.setBalance(123.45);
        acct.setStatus("O");
        acct.setFundedDate("2026-06-05");
        acct.setNegativeAllowed("N");

        Map<String, Object> body = new HashMap<>();
        acct.fillMap(body);

        assertThat(body)
                .containsEntry("name", "C100")          // -> accl_id
                .containsEntry("parentId", "200")       // -> accl_usr_pk
                .containsEntry("ccy", "USD")
                .containsEntry("balance", 123.45)
                .containsEntry("status", "O")
                .containsEntry("fundedDate", "2026-06-05")
                .containsEntry("negativeAllowed", "N");
    }
}

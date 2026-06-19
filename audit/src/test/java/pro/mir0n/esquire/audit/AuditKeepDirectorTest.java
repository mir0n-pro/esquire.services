package pro.mir0n.esquire.audit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

import static org.assertj.core.api.Assertions.assertThat;

class AuditKeepDirectorTest {

    @BeforeAll
    static void loadKinds() {
        EsqObjectKindStorage.getInstance().init((String) null);
    }

    @Test
    void declaresAuditSqlGroupAndKinds() {
        AuditKeepDirector director = new AuditKeepDirector();
        assertThat(director.sqlGroup()).isEqualTo("audit");
        assertThat(director.kinds())
                .isNotEmpty()
                .containsEntry(20, AuditKinds.ORG)
                .containsEntry(50, AuditKinds.ACCOUNT);
    }
}

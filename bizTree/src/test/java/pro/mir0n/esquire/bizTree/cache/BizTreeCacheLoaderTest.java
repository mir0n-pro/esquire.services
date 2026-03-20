package pro.mir0n.esquire.bizTree.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqAcctRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqOrgRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqUsrRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BizTreeCacheLoaderTest {

    @Mock private EsqOrgRepository  orgRepo;
    @Mock private EsqUsrRepository  usrRepo;
    @Mock private EsqAcctRepository acctRepo;
    @Mock private JdbcTemplate      cacheDb;
    @Mock private ApplicationReadyEvent event;

    private BizTreeCacheLoader loader;

    @BeforeEach
    void setUp() {
        BizTreeCacheSql sql = new BizTreeCacheSql(
                new BizTreeCacheSql.Ddl("", "", ""),
                new BizTreeCacheSql.Repo("", "", "", "", "", "", "", ""),
                new BizTreeCacheSql.Loader("INSERT", "UPDATE", "SELECT")
        );
        loader = new BizTreeCacheLoader(orgRepo, usrRepo, acctRepo, cacheDb, sql);
        when(cacheDb.batchUpdate(anyString(), anyList()))
                .thenAnswer(inv -> new int[((List<?>) inv.getArgument(1)).size()]);
    }

    // ---- helpers ----

    private EsqOrgJpa org(String id, int kind, String parentId, String path) {
        EsqOrgJpa o = new EsqOrgJpa();
        o.setId(id); o.setKind(kind); o.setName("Org-" + id);
        o.setDesc("desc"); o.setParentId(parentId); o.setPath(path);
        return o;
    }

    private EsqUsrJpa usr(String id, int kind, String parentId, String path, String deleted) {
        EsqUsrJpa u = new EsqUsrJpa();
        u.setId(id); u.setKind(kind); u.setName("Usr-" + id);
        u.setDesc("desc"); u.setParentId(parentId); u.setPath(path); u.setDeleted(deleted);
        return u;
    }

    private EsqAcctJpa acct(String id, int kind, String usrId, String path, String status) {
        EsqAcctJpa a = new EsqAcctJpa();
        a.setId(id); a.setKind(kind); a.setName("ACC-" + id);
        a.setDesc("desc"); a.setParentId(usrId); a.setPath(path); a.setStatus(status);
        return a;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> captureNodes() {
        ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(cacheDb).batchUpdate(eq("INSERT"), captor.capture());
        return captor.getValue();
    }

    // ---- org: root org (etPk == 1) generates org node + sys-admin folder ----

    @Test
    @DisplayName("root org (etPk=1) → 2 nodes: org + sys-admin folder")
    void rootOrg_generates2Nodes() {
        when(orgRepo.findAllForTree()).thenReturn(List.of(org("1", 1, null, "1.")));
        when(usrRepo.findAllForTree()).thenReturn(List.of());
        when(acctRepo.findAllForTree()).thenReturn(List.of());

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[0]).isEqualTo("1");      // org pk
        assertThat(rows.get(1)[0]).isEqualTo("1~2");    // sys-admin folder pk
        assertThat(rows.get(1)[1]).isEqualTo(2);        // folder etPk
    }

    // ---- org: non-root org (etPk > 1) generates org node + 4 folder nodes ----

    @Test
    @DisplayName("non-root org (etPk=3) → 5 nodes: org + 4 folders")
    void nonRootOrg_generates5Nodes() {
        when(orgRepo.findAllForTree()).thenReturn(List.of(org("2", 3, "1", "1.2.")));
        when(usrRepo.findAllForTree()).thenReturn(List.of());
        when(acctRepo.findAllForTree()).thenReturn(List.of());

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0)[0]).isEqualTo("2");
        assertThat(rows.get(1)[0]).isEqualTo("2~4");
        assertThat(rows.get(2)[0]).isEqualTo("2~6");
        assertThat(rows.get(3)[0]).isEqualTo("2~8");
        assertThat(rows.get(4)[0]).isEqualTo("2~10");
    }

    // ---- user: orgPk=1 → folder type 2 (sys-admin) ----

    @Test
    @DisplayName("user in org 1 → parent = 1~2")
    void user_inRootOrg_parentIsSysAdminFolder() {
        when(orgRepo.findAllForTree()).thenReturn(List.of());
        when(usrRepo.findAllForTree()).thenReturn(List.of(usr("10", 4, "1", "1.10.", null)));
        when(acctRepo.findAllForTree()).thenReturn(List.of());

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[4]).isEqualTo("1~2");    // parentPk
    }

    // ---- user: etPk=34 → folder type 8 (clients) ----

    @Test
    @DisplayName("user etPk=34 (client) → parent = orgPk~8")
    void user_client_parentIsClientsFolder() {
        when(orgRepo.findAllForTree()).thenReturn(List.of());
        when(usrRepo.findAllForTree()).thenReturn(List.of(usr("20", 34, "2", "1.2.20.", null)));
        when(acctRepo.findAllForTree()).thenReturn(List.of());

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows.get(0)[4]).isEqualTo("2~8");
    }

    // ---- user: etPk=36 → folder type 10 (merchants) ----

    @Test
    @DisplayName("user etPk=36 (merchant) → parent = orgPk~10")
    void user_merchant_parentIsMerchantsFolder() {
        when(orgRepo.findAllForTree()).thenReturn(List.of());
        when(usrRepo.findAllForTree()).thenReturn(List.of(usr("30", 36, "2", "1.2.30.", null)));
        when(acctRepo.findAllForTree()).thenReturn(List.of());

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows.get(0)[4]).isEqualTo("2~10");
    }

    // ---- user: deleted flag Y → STATUS_DELETED ----

    @Test
    @DisplayName("deleted user → status = 1")
    void user_deleted_statusIsDeleted() {
        when(orgRepo.findAllForTree()).thenReturn(List.of());
        when(usrRepo.findAllForTree()).thenReturn(List.of(usr("10", 4, "2", "1.2.10.", "Y")));
        when(acctRepo.findAllForTree()).thenReturn(List.of());

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows.get(0)[10]).isEqualTo(1);   // STATUS_DELETED
    }

    // ---- account: generates direct node + shortcut node ----

    @Test
    @DisplayName("account → 2 nodes: direct (under user) + shortcut (under org~6)")
    void account_generates2Nodes_directAndShortcut() {
        when(orgRepo.findAllForTree()).thenReturn(List.of());
        when(usrRepo.findAllForTree()).thenReturn(List.of(usr("10", 4, "2", "1.2.10.", null)));
        when(acctRepo.findAllForTree()).thenReturn(List.of(acct("100", 6, "10", "1.2.10.100.", "A")));

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        // user node + 2 account nodes
        assertThat(rows).hasSize(3);
        Object[] direct   = rows.get(1);
        Object[] shortcut = rows.get(2);

        assertThat(direct[0]).isEqualTo("100");         // direct pk
        assertThat(direct[4]).isEqualTo("10");          // parent = user pk
        assertThat(direct[5]).isNull();                 // no link

        assertThat(shortcut[0]).isEqualTo("2~100");     // shortcut pk = orgPk~accPk
        assertThat(shortcut[1]).isEqualTo(7);           // etPk + 1
        assertThat(shortcut[4]).isEqualTo("2~6");       // parent = org accounts folder
        assertThat(shortcut[5]).isEqualTo("100");       // link = direct pk
    }

    // ---- account: status L → STATUS_LOCKED ----

    @Test
    @DisplayName("locked account → status = 2 on both nodes")
    void account_locked_statusIsLocked() {
        when(orgRepo.findAllForTree()).thenReturn(List.of());
        when(usrRepo.findAllForTree()).thenReturn(List.of(usr("10", 4, "2", "1.2.10.", null)));
        when(acctRepo.findAllForTree()).thenReturn(List.of(acct("100", 6, "10", "1.2.10.100.", "L")));

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows.get(1)[10]).isEqualTo(2);   // direct  STATUS_LOCKED
        assertThat(rows.get(2)[10]).isEqualTo(2);   // shortcut STATUS_LOCKED
    }

    // ---- account: status Y → STATUS_DELETED ----

    @Test
    @DisplayName("closed account (Y) → status = 1 on both nodes")
    void account_closed_statusIsDeleted() {
        when(orgRepo.findAllForTree()).thenReturn(List.of());
        when(usrRepo.findAllForTree()).thenReturn(List.of(usr("10", 4, "2", "1.2.10.", null)));
        when(acctRepo.findAllForTree()).thenReturn(List.of(acct("100", 6, "10", "1.2.10.100.", "Y")));

        loader.onApplicationEvent(event);

        List<Object[]> rows = captureNodes();
        assertThat(rows.get(1)[10]).isEqualTo(1);
        assertThat(rows.get(2)[10]).isEqualTo(1);
    }
}

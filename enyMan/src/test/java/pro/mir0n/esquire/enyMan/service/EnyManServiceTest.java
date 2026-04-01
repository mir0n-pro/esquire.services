package pro.mir0n.esquire.enyMan.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.Mockito;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionary;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.DeleteRestrictedException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.messaging.EsqEntityBroadcastPublisher;
import pro.mir0n.esquire.enyMan.service.impl.EnyManService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
class EnyManServiceTest {

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Mock
    private EsqEntityDictionaryRepository dictRepo;

    @Mock
    private EsqOrgRepository orgRepo;

    @Mock
    private EsqUsrRepository usrRepo;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager em;

    @Mock
    private EsqEntityBroadcastPublisher broadcastPublisher;

    private EnyManService service;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage oks = EsqObjectKindStorage.getInstance();
        oks.init(new EsqObjectKind(10, "org", "Org", "orgs", "Test org",
            true, false, false, "", false, false, "", null, null, null, false));
        oks.init(new EsqObjectKind(20, "usr", "Usr", "usrs", "Test usr",
            false, true, false, "", false, false, "", null, null, null, false));

        EsqRoleJpa roleJpa = new EsqRoleJpa();
        roleJpa.setId("1");
        roleJpa.setName(ROLE_ADMIN);
        roleJpa.setKind(EsqConstants.KIND_ADMIN_ROLE);

        EsqPermissionJpa orgPerm = new EsqPermissionJpa();
        orgPerm.setId("10");
        orgPerm.setKind(10);
        orgPerm.setFlags("Y,Y,Y,Y,Y");

        EsqPermissionJpa usrPerm = new EsqPermissionJpa();
        usrPerm.setId("20");
        usrPerm.setKind(20);
        usrPerm.setFlags("Y,Y,Y,Y,Y");

        JpaRolesRepository rolesRepo = Mockito.mock(JpaRolesRepository.class);
        when(rolesRepo.roles()).thenReturn(List.of(roleJpa));
        when(rolesRepo.permissions("1")).thenReturn(List.of(orgPerm, usrPerm));
        EsqRolesStorage.getInstance().init(rolesRepo);

        // Dictionary entry for kind 50 — used by esquireDictionary() tests
        EsqEntityLayer layer = new EsqEntityLayer();
        layer.setLayer(1);
        layer.setTitle("Test Layer");
        layer.setFields(List.of());
        EsqEntityDictionary dict = new EsqEntityDictionary();
        dict.setKind(50);
        dict.getLayers().add(layer);
        dict.setCompleted(true);
        EsqEntityDictionaryStorage.getInstance().init(dict);
    }

    @BeforeEach
    void setUp() {
        service = new EnyManService(dictRepo, orgRepo, usrRepo, transactionTemplate, em, broadcastPublisher);
    }

    // ---- esquireCommandSave: org kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandSave: org kind, null roles → PermissionDeniedException")
    void esquireCommandSave_orgKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(10, "100", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandSave: usr kind, null roles, not self → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandSave: usr kind, null roles, id != uid → PermissionDeniedException")
    void esquireCommandSave_usrKind_nullRoles_notSelf_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(20, "50", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandSave: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandSave: unknown kind → ResourceNotFoundException")
    void esquireCommandSave_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(99, "1", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommand: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommand: unknown kind → ResourceNotFoundException")
    void esquireCommand_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommand(99, "1", "details", "1.2.3", "99")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandNew: org kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandNew: org kind, null roles → PermissionDeniedException")
    void esquireCommandNew_orgKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandNew(10, "1", "new", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandNew: usr kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandNew: usr kind, null roles → PermissionDeniedException")
    void esquireCommandNew_usrKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandNew(20, "1", "new", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandNew: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandNew: unknown kind → ResourceNotFoundException")
    void esquireCommandNew_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandNew(99, "1", "new", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandDelete: org kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandDelete: org kind, null roles → PermissionDeniedException")
    void esquireCommandDelete_orgKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandDelete(10, "100", "delete", "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandDelete: usr kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandDelete: usr kind, null roles → PermissionDeniedException")
    void esquireCommandDelete_usrKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandDelete(20, "100", "delete", "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandDelete: unknown kind, null roles → PermissionDeniedException (permission checked before kind dispatch) ----

    @Test
    @DisplayName("esquireCommandDelete: unknown kind, null roles → PermissionDeniedException (permission gate fires first)")
    void esquireCommandDelete_unknownKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandDelete(99, "100", "delete", "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireDictionary: even kind → returns layers ----

    @Test
    @DisplayName("esquireDictionary: even kind 50 → returns layers")
    void esquireDictionary_evenKind_returnsLayers() {
        List<?> ret = service.esquireDictionary(50);
        assertThat(ret).isNotNull().isNotEmpty();
    }

    // ---- esquireDictionary: odd kind normalized to even → same result ----

    @Test
    @DisplayName("esquireDictionary: odd kind 51 normalized to 50 → returns same layers")
    void esquireDictionary_oddKind_normalizedToEven_returnsLayers() {
        List<?> ret = service.esquireDictionary(51);
        assertThat(ret).isNotNull().isNotEmpty();
    }

    // ---- esquireDictionary: null kind → ResourceNotFoundException (no NPE) ----

    @Test
    @DisplayName("esquireDictionary: null kind → ResourceNotFoundException, not NPE")
    void esquireDictionary_nullKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireDictionary(null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireDictionary: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireDictionary: unknown kind 9999 → ResourceNotFoundException")
    void esquireDictionary_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireDictionary(9999)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandDelete: usr not found → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandDelete: usr not found → ResourceNotFoundException")
    void esquireCommandDelete_usrNotFound_throwsResourceNotFoundException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("100", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandDelete(20, "100", "delete", "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandDelete: usr active (connectFlg=Y) → DeleteRestrictedException ----

    @Test
    @DisplayName("esquireCommandDelete: usr connected (connectFlg=Y) → DeleteRestrictedException")
    void esquireCommandDelete_usrConnected_throwsDeleteRestrictedException() {
        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId("100");
        usr.setConnectFlg("Y");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("100", "1.2.3")).thenReturn(usr);

        assertThatThrownBy(() ->
            service.esquireCommandDelete(20, "100", "delete", "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(DeleteRestrictedException.class);
    }

    // ---- esquireCommandNew: org — insertOrgPath called before insertOrg ----

    @Test
    @DisplayName("esquireCommandNew: org — insertOrgPath called before insertOrg")
    void esquireCommandNew_org_insertsOrgPath_beforeInsertOrg() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(orgRepo.orgPath("1", "1.")).thenReturn("1.");
        when(dictRepo.findCustom(10)).thenReturn(List.of());

        service.esquireCommandNew(10, "1", "new", new HashMap<>(), "1.", "99", List.of(ROLE_ADMIN));

        InOrder order = inOrder(orgRepo);
        order.verify(orgRepo).insertOrgPath(anyLong(), anyInt(), anyString());
        order.verify(orgRepo).insertOrg(anyLong(), anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    // ---- esquireCommandDelete: org — deleteEntityPath called after deleteOrg ----

    @Test
    @DisplayName("esquireCommandDelete: org — deleteEntityPath called after deleteOrg")
    void esquireCommandDelete_org_deletesEntityPath_afterDeleteOrg() {
        pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa org = new pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa();
        org.setId("100");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(orgRepo.detailOrgForUpdate("100", "1.")).thenReturn(org);

        service.esquireCommandDelete(10, "100", "delete", "1.", "99", List.of(ROLE_ADMIN));

        InOrder order = inOrder(orgRepo);
        order.verify(orgRepo).deleteOrg("100");
        order.verify(orgRepo).deleteEntityPath("100");
    }

    // ---- esquireCommandMove: permission + dispatch ----

    @Test
    @DisplayName("esquireCommandMove: org kind, null roles → PermissionDeniedException")
    void esquireCommandMove_orgKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandMove(10, "100", "200", "1.", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: usr kind, null roles → PermissionDeniedException")
    void esquireCommandMove_usrKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandMove(20, "100", "200", "1.", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: unknown kind → ResourceNotFoundException")
    void esquireCommandMove_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandMove(99, "100", "200", "1.", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: dest org not found → ResourceNotFoundException")
    void esquireCommandMove_destNotFound_throwsResourceNotFoundException() {
        when(orgRepo.detailOrg("200", "1.")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandMove(10, "100", "200", "1.", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: dest org kind has no UPDATE permission → PermissionDeniedException")
    void esquireCommandMove_destNoUpdatePermission_throwsPermissionDeniedException() {
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(30); // kind 30 has no entry in permissions map
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        assertThatThrownBy(() ->
            service.esquireCommandMove(10, "100", "200", "1.", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: usr kind, id equals uid → PermissionDeniedException (cannot move yourself)")
    void esquireCommandMove_usrKind_selfMove_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandMove(20, "99", "200", "1.", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandMove: org behavioural ----

    @Test
    @DisplayName("esquireCommandMove: org — skip when distId equals current parentId")
    void esquireCommandMove_org_sameParent_skipsMove() {
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(10);
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        EsqOrgJpa org = new EsqOrgJpa();
        org.setId("100");
        org.setParentId("200"); // already at destination
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(orgRepo.detailOrgForUpdate("100", "1.")).thenReturn(org);

        service.esquireCommandMove(10, "100", "200", "1.", "99", List.of(ROLE_ADMIN));

        verify(orgRepo, never()).moveOrgPaths(anyString(), anyString());
    }

    @Test
    @DisplayName("esquireCommandMove: org — descendant guard → PermissionDeniedException")
    void esquireCommandMove_org_descendantGuard_throwsPermissionDeniedException() {
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(10);
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        EsqOrgJpa org = new EsqOrgJpa();
        org.setId("100");
        org.setParentId("5");
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(orgRepo.detailOrgForUpdate("100", "1.")).thenReturn(org);
        when(orgRepo.orgPath("100", "1.")).thenReturn("1.100.");
        when(orgRepo.orgPath("200", "1.")).thenReturn("1.100.200."); // dest is under moving org

        assertThatThrownBy(() ->
            service.esquireCommandMove(10, "100", "200", "1.", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: org — moveOrgPaths called before moveOrgParent")
    void esquireCommandMove_org_moveOrgPaths_beforeMoveOrgParent() {
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(10);
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        EsqOrgJpa org = new EsqOrgJpa();
        org.setId("100");
        org.setParentId("5");
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(orgRepo.detailOrgForUpdate("100", "1.")).thenReturn(org);
        when(orgRepo.orgPath("100", "1.")).thenReturn("1.5.100.");
        when(orgRepo.orgPath("200", "1.")).thenReturn("1.9.200.");

        service.esquireCommandMove(10, "100", "200", "1.", "99", List.of(ROLE_ADMIN));

        InOrder order = inOrder(orgRepo);
        order.verify(orgRepo).moveOrgPaths(anyString(), anyString());
        order.verify(orgRepo).moveOrgParent(anyString(), anyString(), any(), any(), any());
    }

    // ---- esquireCommandMove: usr behavioural ----

    @Test
    @DisplayName("esquireCommandMove: usr — skip when distId equals current parentId")
    void esquireCommandMove_usr_sameParent_skipsMove() {
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(10);
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId("100");
        usr.setParentId("200"); // already at destination
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("100", "1.")).thenReturn(usr);

        service.esquireCommandMove(20, "100", "200", "1.", "99", List.of(ROLE_ADMIN));

        verify(usrRepo, never()).moveUsrPaths(anyString(), anyString());
    }

    @Test
    @DisplayName("esquireCommandMove: usr — moveUsrPaths called before moveUsrParent")
    void esquireCommandMove_usr_moveUsrPaths_beforeMoveUsrParent() {
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(10);
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId("100");
        usr.setParentId("5");
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("100", "1.")).thenReturn(usr);
        when(usrRepo.usrPath("100", "1.")).thenReturn("1.5.100.");
        when(usrRepo.usrPath("200", "1.")).thenReturn("1.9.200.");

        service.esquireCommandMove(20, "100", "200", "1.", "99", List.of(ROLE_ADMIN));

        InOrder order = inOrder(usrRepo);
        order.verify(usrRepo).moveUsrPaths(anyString(), anyString());
        order.verify(usrRepo).moveUsrParent(anyString(), anyString(), any(), any(), any());
    }

    // ---- esquireCommandDelete: usr — deleteEntityPath called after deleteUsr ----

    @Test
    @DisplayName("esquireCommandDelete: usr — deleteEntityPath called after deleteUsr")
    void esquireCommandDelete_usr_deletesEntityPath_afterDeleteUsr() {
        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId("200");
        usr.setConnectFlg("N");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("200", "1.")).thenReturn(usr);

        service.esquireCommandDelete(20, "200", "delete", "1.", "99", List.of(ROLE_ADMIN));

        InOrder order = inOrder(usrRepo);
        order.verify(usrRepo).deletePersonAddresses("200");
        order.verify(usrRepo).deletePersonBankInfo("200");
        order.verify(usrRepo).deleteUsr("200");
        order.verify(usrRepo).deleteEntityPath("200");
    }
}

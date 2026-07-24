package pro.mir0n.esquire.enyMan.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
import pro.mir0n.esquire.backend.error.MissingRequestIdException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.xrod.impl.XRodDisabled;
import pro.mir0n.esquire.enyMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqSubtreeRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqSubtreeRow;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.enyMan.messaging.EntityBusAdapter;
import pro.mir0n.esquire.enyMan.queue.MoveCommandItem;
import pro.mir0n.esquire.enyMan.queue.MoveQueueManager;
import pro.mir0n.esquire.enyMan.service.impl.EnyManService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
class EnyManServiceTest {

    /** A disabled x-Rod stand-in: the OFF rod -- AuditBusBridge.post() skips it (isEnabled() is false). */
    private static IXRod noopRod() {
        return new XRodDisabled();
    }

    static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final String UID = "99";

    @Mock
    private EsqEntityDictionaryRepository dictRepo;

    @Mock
    private EsqOrgRepository orgRepo;

    @Mock
    private EsqUsrRepository usrRepo;

    @Mock
    private EsqAcctRepository acctRepo;

    @Mock
    private EsqSubtreeRepository subtreeRepo;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager em;

    @Mock
    private EntityBusAdapter broadcastPublisher;

    @Mock
    private MoveQueueManager moveQueue;

    private EnyManService service;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage oks = EsqObjectKindStorage.getInstance();
        oks.init(new EsqObjectKind(20, "org", "Org", "orgs", "Test org",
            true, false, false, "", false, false, "", null, null, null, false));
        oks.init(new EsqObjectKind(32, "usr", "Usr", "usrs", "Test usr",
            false, true, false, "", false, false, "", null, null, null, false));
        oks.init(new EsqObjectKind(34, "usr", "Usr", "usrs", "Test usr",
            false, true, false, "", false, false, "", null, null, null, false));
        oks.init(new EsqObjectKind(50, "clAcct", "Client Account", "clAccts", "Test acct",
            false, false, true, "", false, false, "", null, null, null, false));

        EsqRoleJpa roleJpa = new EsqRoleJpa();
        roleJpa.setId("1");
        roleJpa.setName(ROLE_ADMIN);
        roleJpa.setKind(EsqConstants.KIND_ADMIN_ROLE);

        EsqPermissionJpa orgPerm = new EsqPermissionJpa();
        orgPerm.setId("20");
        orgPerm.setKind(20);
        orgPerm.setFlags("Y,Y,Y,Y,Y");

        EsqPermissionJpa usrPerm = new EsqPermissionJpa();
        usrPerm.setId("32");
        usrPerm.setKind(32);
        usrPerm.setFlags("Y,Y,Y,Y,Y");

        EsqPermissionJpa clientPerm = new EsqPermissionJpa();
        clientPerm.setId("34");
        clientPerm.setKind(34);
        clientPerm.setFlags("Y,Y,Y,Y,Y");

        EsqPermissionJpa acctPerm = new EsqPermissionJpa();
        acctPerm.setId("50");
        acctPerm.setKind(50);
        acctPerm.setFlags("Y,Y,Y,Y,Y");

        JpaRolesRepository rolesRepo = Mockito.mock(JpaRolesRepository.class);
        when(rolesRepo.roles()).thenReturn(List.of(roleJpa));
        when(rolesRepo.permissions("1")).thenReturn(List.of(orgPerm, usrPerm, clientPerm, acctPerm));
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
        service = new EnyManService(dictRepo, orgRepo, usrRepo, acctRepo, subtreeRepo, transactionTemplate, em, broadcastPublisher, moveQueue, new AuditBusBridge(noopRod()));
    }

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
    }

    // uid / rootPath now come from the unified per-request context (read via RequestContextUtils),
    // not from method params. Each test establishes the context the same way the request thread
    // does -- with the rootPath its mocks are stubbed against and the fixed uid "99".
    private void ctx(String rootPath) {
        EsqContextHolder.set(new EsqRequestContext(null, "req-test", UID, rootPath));
    }

    // ---- esquireCommandSave: missing X-Request-ID → MissingRequestIdException ----

    @Test
    @DisplayName("esquireCommandSave: missing X-Request-ID → MissingRequestIdException")
    void esquireCommandSave_missingRequestId_throwsMissingRequestIdException() {
        EsqContextHolder.set(new EsqRequestContext(null, null, UID, "1.2.3")); // no reqId
        assertThatThrownBy(() ->
            service.esquireCommandSave(20, "100", "save", Map.of(), List.of(ROLE_ADMIN))
        ).isInstanceOf(MissingRequestIdException.class);
    }

    // ---- esquireCommandSave: org kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandSave: org kind, null roles → PermissionDeniedException")
    void esquireCommandSave_orgKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandSave(20, "100", "save", Map.of(), null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandSave: usr kind, null roles, not self → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandSave: usr kind, null roles, id != uid → PermissionDeniedException")
    void esquireCommandSave_usrKind_nullRoles_notSelf_throwsPermissionDeniedException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandSave(32, "50", "save", Map.of(), null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandSave: unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandSave: unknown kind → ResourceNotFoundException")
    void esquireCommandSave_unknownKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandSave(99, "1", "save", Map.of(), null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandSave: odd kind 33 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommandSave_oddKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandSave(33, "1", "save", Map.of(), null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommand: unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommand: unknown kind → ResourceNotFoundException")
    void esquireCommand_unknownKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommand(99, "1", "details")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommand: odd kind 33 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommand_oddKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommand(33, "1", "details")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandNew: org kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandNew: org kind, null roles → PermissionDeniedException")
    void esquireCommandNew_orgKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandNew(20, "1", "new", Map.of(), null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandNew: usr kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandNew: usr kind, null roles → PermissionDeniedException")
    void esquireCommandNew_usrKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandNew(32, "1", "new", Map.of(), null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandNew: acct kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandNew: acct kind, null roles → PermissionDeniedException")
    void esquireCommandNew_acctKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandNew(50, "1", "new", Map.of(), null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandNew: acct kind, parent usr not found → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandNew: acct — parent path not found → ResourceNotFoundException")
    void esquireCommandNew_acct_parentNotFound_throwsResourceNotFoundException() {
        ctx("1.2.3");
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(acctRepo.acctPath("1", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandNew(50, "1", "new", new HashMap<>(), List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandNew: unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandNew: unknown kind → ResourceNotFoundException")
    void esquireCommandNew_unknownKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandNew(99, "1", "new", Map.of(), null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandNew: odd kind 33 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommandNew_oddKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandNew(33, "1", "new", Map.of(), null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandDelete: org kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandDelete: org kind, null roles → PermissionDeniedException")
    void esquireCommandDelete_orgKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandDelete(20, "100", "delete", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandDelete: usr kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandDelete: usr kind, null roles → PermissionDeniedException")
    void esquireCommandDelete_usrKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandDelete(32, "100", "delete", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandDelete: unknown kind → ResourceNotFoundException (kind check fires before permission gate) ----

    @Test
    @DisplayName("esquireCommandDelete: unknown kind → ResourceNotFoundException")
    void esquireCommandDelete_unknownKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandDelete(99, "100", "delete", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandDelete: odd kind 33 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommandDelete_oddKind_throwsResourceNotFoundException() {
        ctx("1.2.3");
        assertThatThrownBy(() ->
            service.esquireCommandDelete(33, "100", "delete", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireDictionary: even kind → returns layers ----

    @Test
    @DisplayName("esquireDictionary: even kind 50 → returns layers")
    void esquireDictionary_evenKind_returnsLayers() {
        List<?> ret = service.esquireDictionary(50);
        assertThat(ret).isNotNull().isNotEmpty();
    }

    // ---- esquireDictionary: odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireDictionary: odd kind 51 (not registered) → ResourceNotFoundException")
    void esquireDictionary_oddKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireDictionary(51)
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
        ctx("1.2.3");
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("100", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandDelete(32, "100", "delete", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandDelete: usr active (connectFlg=Y) → DeleteRestrictedException ----

    @Test
    @DisplayName("esquireCommandDelete: usr connected (connectFlg=Y) → DeleteRestrictedException")
    void esquireCommandDelete_usrConnected_throwsDeleteRestrictedException() {
        ctx("1.2.3");
        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId("100");
        usr.setConnectFlg("Y");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("100", "1.2.3")).thenReturn(usr);

        assertThatThrownBy(() ->
            service.esquireCommandDelete(32, "100", "delete", List.of(ROLE_ADMIN))
        ).isInstanceOf(DeleteRestrictedException.class);
    }

    // ---- esquireCommandNew: org — insertOrgPath called before insertOrg ----

    @Test
    @DisplayName("esquireCommandNew: org — insertOrgPath called before insertOrg")
    void esquireCommandNew_org_insertsOrgPath_beforeInsertOrg() {
        ctx("1.");
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(orgRepo.orgPath("1", "1.")).thenReturn("1.");
        when(dictRepo.findCustom(20)).thenReturn(List.of());

        service.esquireCommandNew(20, "1", "new", new HashMap<>(), List.of(ROLE_ADMIN));

        InOrder order = inOrder(orgRepo);
        order.verify(orgRepo).insertOrgPath(anyLong(), anyInt(), anyString());
        order.verify(orgRepo).insertOrg(anyLong(), anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    // ---- esquireCommandNew: acct — insertAcctPath called before insertAcct ----

    @Test
    @DisplayName("esquireCommandNew: acct — insertAcctPath called before insertAcct")
    void esquireCommandNew_acct_insertsAcctPath_beforeInsertAcct() {
        ctx("1.5.");
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(acctRepo.acctPath("10", "1.5.")).thenReturn("1.5.");

        service.esquireCommandNew(50, "10", "new", new HashMap<>(), List.of(ROLE_ADMIN));

        InOrder order = inOrder(acctRepo);
        order.verify(acctRepo).insertAcctPath(anyLong(), anyInt(), anyString());
        order.verify(acctRepo).insertAcct(anyLong(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ---- esquireCommandDelete: org — deleteEntityPath called after deleteOrg ----

    @Test
    @DisplayName("esquireCommandDelete: org — deleteEntityPath called after deleteOrg")
    void esquireCommandDelete_org_deletesEntityPath_afterDeleteOrg() {
        ctx("1.");
        pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa org = new pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa();
        org.setId("100");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(orgRepo.detailOrgForUpdate("100", "1.")).thenReturn(org);

        service.esquireCommandDelete(20, "100", "delete", List.of(ROLE_ADMIN));

        InOrder order = inOrder(orgRepo);
        order.verify(orgRepo).deleteOrg("100");
        order.verify(orgRepo).deleteEntityPath("100");
    }

    // ---- esquireCommandMove: permission + dispatch ----

    @Test
    @DisplayName("esquireCommandMove: org kind, null roles → PermissionDeniedException")
    void esquireCommandMove_orgKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.");
        assertThatThrownBy(() ->
            service.esquireCommandMove(20, "100", "200", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: usr kind, null roles → PermissionDeniedException")
    void esquireCommandMove_usrKind_nullRoles_throwsPermissionDeniedException() {
        ctx("1.");
        assertThatThrownBy(() ->
            service.esquireCommandMove(32, "100", "200", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: unknown kind → ResourceNotFoundException")
    void esquireCommandMove_unknownKind_throwsResourceNotFoundException() {
        ctx("1.");
        assertThatThrownBy(() ->
            service.esquireCommandMove(99, "100", "200", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: odd kind 33 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommandMove_oddKind_throwsResourceNotFoundException() {
        ctx("1.");
        assertThatThrownBy(() ->
            service.esquireCommandMove(33, "100", "200", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: dest org not found → ResourceNotFoundException")
    void esquireCommandMove_destNotFound_throwsResourceNotFoundException() {
        ctx("1.");
        when(orgRepo.detailOrg("200", "1.")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandMove(20, "100", "200", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: dest org kind has no UPDATE permission → PermissionDeniedException")
    void esquireCommandMove_destNoUpdatePermission_throwsPermissionDeniedException() {
        ctx("1.");
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(30); // kind 30 has no entry in permissions map
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        assertThatThrownBy(() ->
            service.esquireCommandMove(20, "100", "200", List.of(ROLE_ADMIN))
        ).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("esquireCommandMove: usr kind, id equals uid → PermissionDeniedException (cannot move yourself)")
    void esquireCommandMove_usrKind_selfMove_throwsPermissionDeniedException() {
        ctx("1.");
        assertThatThrownBy(() ->
            service.esquireCommandMove(32, "99", "200", List.of(ROLE_ADMIN))
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandMove: submit-to-queue contract (v1.2.6 Goal 3) ----
    // Pre-checks still run on the request thread. The actual move execution + broadcasts
    // happen on the move-queue worker (covered separately by MoveQueueManagerTest); here we
    // verify only that pre-checks pass through to a moveQueue.submitMove call with the right
    // item shape. Worker-side behavioural tests (moveOrgPaths/moveUsrPaths ordering, KC URQ
    // emission, same-parent skip, descendant guard) were exercised here pre-Goal-3 -- they
    // now live as integration coverage in the hauberk move-smoke + race-move-create sims.

    @Test
    @DisplayName("esquireCommandMove: pre-checks pass -> moveQueue.submitMove called with item carrying request params")
    void esquireCommandMove_submitsToMoveQueue() {
        ctx("1.");
        EsqOrgJpa destOrg = new EsqOrgJpa();
        destOrg.setId("200");
        destOrg.setKind(20);
        when(orgRepo.detailOrg("200", "1.")).thenReturn(destOrg);

        service.esquireCommandMove(20, "100", "200", List.of(ROLE_ADMIN));

        org.mockito.ArgumentCaptor<MoveCommandItem> capt = org.mockito.ArgumentCaptor.forClass(MoveCommandItem.class);
        verify(moveQueue).submitMove(capt.capture());
        MoveCommandItem item = capt.getValue();
        assertThat(item.kind()).isEqualTo(20);
        assertThat(item.id()).isEqualTo("100");
        assertThat(item.distId()).isEqualTo("200");
        assertThat(item.rootPath()).isEqualTo("1.");
        assertThat(item.uid()).isEqualTo("99");
        assertThat(item.roles()).containsExactly(ROLE_ADMIN);
    }

    @Test
    @DisplayName("esquireCommandMove: pre-checks fail -> moveQueue.submitMove NOT called")
    void esquireCommandMove_preCheckFails_doesNotSubmit() {
        ctx("1.");
        assertThatThrownBy(() ->
            service.esquireCommandMove(20, "100", "200", null)
        ).isInstanceOf(PermissionDeniedException.class);

        verify(moveQueue, never()).submitMove(any(MoveCommandItem.class));
    }

    // ---- esquireCommandDelete: usr — deleteEntityPath called after deleteUsr ----

    @Test
    @DisplayName("esquireCommandDelete: usr — deleteEntityPath called after deleteUsr")
    void esquireCommandDelete_usr_deletesEntityPath_afterDeleteUsr() {
        ctx("1.");
        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId("200");
        usr.setConnectFlg("N");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(usrRepo.detailUsrForUpdate("200", "1.")).thenReturn(usr);

        service.esquireCommandDelete(32, "200", "delete", List.of(ROLE_ADMIN));

        InOrder order = inOrder(usrRepo);
        order.verify(usrRepo).deletePersonAddresses("200");
        order.verify(usrRepo).deletePersonBankInfo("200");
        order.verify(usrRepo).deleteUsr("200");
        order.verify(usrRepo).deleteEntityPath("200");
    }

    // ---- esquireCommandTree: org kind -> subtreeFromOrg + EsqSubtreeRow projection ----

    @Test
    @DisplayName("esquireCommandTree: org kind -> subtreeFromOrg, projects rows into EsqTreeNode with entityPath")
    void esquireCommandTree_orgKind_callsSubtreeFromOrg_projectsRows() {
        ctx("1.");
        EsqSubtreeRow row1 = new EsqSubtreeRow("10", 100L, 20, "Office",    null, "1",  1, "1.10.");
        EsqSubtreeRow row2 = new EsqSubtreeRow("11", 101L, 34, "Test User", null, "10", 2, "1.10.11.");
        when(subtreeRepo.subtreeFromOrg("10", "1.")).thenReturn(List.of(row1, row2));

        List<EsqTreeNode> result = service.esquireCommandTree(20, "10");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("10");
        assertThat(result.get(0).getEntityPath()).isEqualTo("1.10.");
        assertThat(result.get(1).getId()).isEqualTo("11");
        assertThat(result.get(1).getEntityPath()).isEqualTo("1.10.11.");
        verify(subtreeRepo).subtreeFromOrg("10", "1.");
        verify(subtreeRepo, never()).subtreeFromUsr(any(), any());
        verify(subtreeRepo, never()).subtreeFromAcct(any(), any());
    }

    // ---- esquireCommandTree: usr kind -> subtreeFromUsr ----

    @Test
    @DisplayName("esquireCommandTree: usr kind -> subtreeFromUsr branch")
    void esquireCommandTree_usrKind_callsSubtreeFromUsr() {
        ctx("1.");
        when(subtreeRepo.subtreeFromUsr("11", "1.")).thenReturn(List.of());

        service.esquireCommandTree(34, "11");

        verify(subtreeRepo).subtreeFromUsr("11", "1.");
        verify(subtreeRepo, never()).subtreeFromOrg(any(), any());
        verify(subtreeRepo, never()).subtreeFromAcct(any(), any());
    }

    // ---- esquireCommandTree: unknown kind -> ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandTree: unknown kind -> ResourceNotFoundException, no repo call")
    void esquireCommandTree_unknownKind_throwsResourceNotFoundException() {
        ctx("1.");
        // kind 1000 is not registered in initStorage() -> EsqObjectKind defaults to a kind that is
        // neither org/usr/acct -> upfront applicability check fails
        assertThatThrownBy(() -> service.esquireCommandTree(1000, "10"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(subtreeRepo);
    }
}

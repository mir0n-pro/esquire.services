package pro.mir0n.esquire.keySmith.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.error.MissingRequestIdException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.keySmith.jpa.EsqAccessProfileRepository;
import pro.mir0n.esquire.backend.identity.IIdentityGateway;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.keySmith.service.impl.KeySmithService;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeySmithServiceTest {

    @Mock
    private EsqAccessProfileRepository accessProfileRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager em;

    @Mock
    private IIdentityGateway identityGateway;

    @Mock
    private pro.mir0n.esquire.audit.AuditBusBridge audit;

    private KeySmithService service;

    @BeforeAll
    static void initStorages() {
        EsqEntityDictionaryStorage.getInstance().init((String) null);
        ValidatorFactory.getInstance().init(BizValidatorFactory.getBizValidators());
    }

    @BeforeEach
    void setUp() {
        service = new KeySmithService(accessProfileRepository, transactionTemplate, em, identityGateway, audit);
        // Default context for the majority of tests (rootPath "/root", uid "uid-1");
        // the three "1.2.3" / "uid-99" tests override it.
        ctx("/root", "uid-1");
    }

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
    }

    // uid / rootPath now come from the unified per-request context. Each test sets the exact pair
    // its mocks / self-checks expect, the same way the request thread would.
    private void ctx(String rootPath, String uid) {
        EsqContextHolder.set(new EsqRequestContext(null, "req-test", uid, rootPath));
    }

    // ---- helper: runs transactionTemplate.execute() lambda inline ----

    private void executeTransactionInline() {
        doAnswer(inv -> {
            inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());
    }

    // ---- helper: real JPA for esquireKeySave tests ----

    private EsqAccessProfileJpa jpaWith(String connectFlg, String tfaMethod, String loginId) {
        EsqAccessProfileJpa jpa = new EsqAccessProfileJpa();
        jpa.setKind(0);
        jpa.setConnectFlg(connectFlg);
        jpa.setTfaMethod(tfaMethod);
        jpa.setLoginId(loginId);
        jpa.setPwdChangeForced("N");
        jpa.setEmail("user@test.com");
        jpa.setPath("/root/1");
        return jpa;
    }

    // ---- helper: admin storage mock (permits AUTH on any kind) ----
    // connectFlg has personal=N so only admin (id != uid) can change it.
    // EsqRolesStorage.getInstance() is a static singleton — mock it for admin tests.

    private EsqRolesStorage adminStorageMock(MockedStatic<EsqRolesStorage> ms) {
        EsqRolesStorage mockStorage = mock(EsqRolesStorage.class);
        ms.when(EsqRolesStorage::getInstance).thenReturn(mockStorage);
        when(mockStorage.findAdminPermissions(any())).thenReturn(new java.util.HashMap<>());
        when(mockStorage.isAdminCmdPermitted(any(), any())).thenReturn(true);
        when(mockStorage.roles()).thenReturn(List.of());
        return mockStorage;
    }

    // =========================================================
    // esquireKey: not found
    // =========================================================

    @Test
    @DisplayName("esquireKey: not found → ResourceNotFoundException")
    void esquireKey_notFound_throwsResourceNotFoundException() {
        when(accessProfileRepository.access("uid-99", "1.2.3")).thenReturn(null);

        ctx("1.2.3", "uid-99");
        assertThatThrownBy(() -> service.esquireKey(null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================
    // esquireKeySave: missing X-Request-ID
    // =========================================================

    @Test
    @DisplayName("esquireKeySave: missing X-Request-ID → MissingRequestIdException")
    void esquireKeySave_missingRequestId_throwsMissingRequestIdException() {
        EsqContextHolder.set(new EsqRequestContext(null, null, "uid-1", "/root")); // no reqId
        assertThatThrownBy(() -> service.esquireKeySave("10", Map.of(), List.of()))
                .isInstanceOf(MissingRequestIdException.class);
    }

    // =========================================================
    // esquireKeySave: not found / permission denied
    // =========================================================

    @Test
    @DisplayName("esquireKeySave: not found → ResourceNotFoundException")
    void esquireKeySave_notFound_throwsResourceNotFoundException() {
        executeTransactionInline();
        when(accessProfileRepository.accessForUpdate("other", "1.2.3")).thenReturn(null);

        ctx("1.2.3", "uid-99");
        assertThatThrownBy(() -> service.esquireKeySave("other", Map.of(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireKeySave: id != uid, roles null → PermissionDeniedException")
    void esquireKeySave_notSelf_nullRoles_throwsPermissionDeniedException() {
        executeTransactionInline();
        when(accessProfileRepository.accessForUpdate("other", "1.2.3"))
                .thenReturn(mock(EsqAccessProfileJpa.class));

        ctx("1.2.3", "uid-99");
        assertThatThrownBy(() -> service.esquireKeySave("other", Map.of(), null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // =========================================================
    // esquireKey: login handshake — confirmPendingFlags
    // =========================================================

    @Test
    @DisplayName("esquireKey handshake: pwdChangeForced=Y → confirmPendingFlags('N', null)")
    void esquireKey_handshake_confirmsPwdForced() {
        EsqAccessProfileJpa jpa = mock(EsqAccessProfileJpa.class);
        when(jpa.getPwdChangeForced()).thenReturn("Y");
        when(jpa.getTfaMethod()).thenReturn("G");
        when(accessProfileRepository.access("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKey(null);

        verify(accessProfileRepository).confirmPendingFlags("uid-1", "N", null);
        verify(jpa).setPwdChangeForced("N");
    }

    @Test
    @DisplayName("esquireKey handshake: tfaMethod=g (pending enable) → confirmPendingFlags(null, 'G')")
    void esquireKey_handshake_confirmsTfaPendingEnable() {
        EsqAccessProfileJpa jpa = mock(EsqAccessProfileJpa.class);
        when(jpa.getPwdChangeForced()).thenReturn("N");
        when(jpa.getTfaMethod()).thenReturn("g");
        when(accessProfileRepository.access("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKey(null);

        verify(accessProfileRepository).confirmPendingFlags("uid-1", null, "G");
        verify(jpa).setTfaMethod("G");
    }

    @Test
    @DisplayName("esquireKey handshake: tfaMethod=n (pending disable) → confirmPendingFlags(null, 'N')")
    void esquireKey_handshake_confirmsTfaPendingDisable() {
        EsqAccessProfileJpa jpa = mock(EsqAccessProfileJpa.class);
        when(jpa.getPwdChangeForced()).thenReturn("N");
        when(jpa.getTfaMethod()).thenReturn("n");
        when(accessProfileRepository.access("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKey(null);

        verify(accessProfileRepository).confirmPendingFlags("uid-1", null, "N");
        verify(jpa).setTfaMethod("N");
    }

    @Test
    @DisplayName("esquireKey handshake: nothing pending → confirmPendingFlags NOT called")
    void esquireKey_handshake_nothingPending_noConfirm() {
        EsqAccessProfileJpa jpa = mock(EsqAccessProfileJpa.class);
        when(jpa.getPwdChangeForced()).thenReturn("N");
        when(jpa.getTfaMethod()).thenReturn("G");
        when(accessProfileRepository.access("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKey(null);

        verify(accessProfileRepository, never()).confirmPendingFlags(any(), any(), any());
    }

    @Test
    @DisplayName("esquireKey handshake: pwdForced=Y + tfaMethod=g → confirmPendingFlags('N', 'G')")
    void esquireKey_handshake_confirmsBothFlags() {
        EsqAccessProfileJpa jpa = mock(EsqAccessProfileJpa.class);
        when(jpa.getPwdChangeForced()).thenReturn("Y");
        when(jpa.getTfaMethod()).thenReturn("g");
        when(accessProfileRepository.access("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKey(null);

        verify(accessProfileRepository).confirmPendingFlags("uid-1", "N", "G");
    }

    @Test
    @DisplayName("esquireKey with explicit id: handshake skipped → confirmPendingFlags NOT called")
    void esquireKey_explicitId_handshakeSkipped() {
        EsqAccessProfileJpa jpa = mock(EsqAccessProfileJpa.class);
        when(jpa.getPwdChangeForced()).thenReturn("Y");
        when(accessProfileRepository.access("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKey("uid-1");

        verify(accessProfileRepository, never()).confirmPendingFlags(any(), any(), any());
    }

    // =========================================================
    // syncToKeycloak: connect flag transitions
    // connectFlg has personal=N — only admin (id != uid) can change it.
    // EsqRolesStorage is mocked to permit AUTH.
    // =========================================================

    @Test
    @DisplayName("kcSync: Y→N → post called with the DELETE command and connectFlg=N")
    void esquireKeySave_connectFlg_YtoN_publishesDelete() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user1");
        when(accessProfileRepository.accessForUpdate("target-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("target-1")).thenReturn(List.of());

        try (MockedStatic<EsqRolesStorage> ms = mockStatic(EsqRolesStorage.class)) {
            adminStorageMock(ms);
            service.esquireKeySave("target-1", Map.of("connectFlg", "N"), List.of("ADMIN"));
        }

        verify(identityGateway).postRequest(argThat(e -> BusConstants.EVENT_DELETE.equals(e.opCode())
                && "user1".equals(e.body().get("loginId"))));
    }

    @Test
    @DisplayName("kcSync: N→Y → post called with the CREATE command and connectFlg=Y")
    void esquireKeySave_connectFlg_NtoY_publishesCreate() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("N", "N", "user2");
        when(accessProfileRepository.accessForUpdate("target-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("target-1")).thenReturn(List.of());

        try (MockedStatic<EsqRolesStorage> ms = mockStatic(EsqRolesStorage.class)) {
            adminStorageMock(ms);
            service.esquireKeySave("target-1", Map.of("connectFlg", "Y"), List.of("ADMIN"));
        }

        verify(identityGateway).postRequest(argThat(e -> BusConstants.EVENT_CREATE.equals(e.opCode())
                && "user2".equals(e.body().get("loginId")) && "Y".equals(e.body().get("connectFlg"))));
    }

    @Test
    @DisplayName("kcSync: Y→Y (no change) → post called with the UPDATE command and connectFlg=Y")
    void esquireKeySave_connectFlg_noChange_publishesUpdate() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user3");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKeySave("uid-1", Map.of(), null);

        verify(identityGateway).postRequest(argThat(e -> BusConstants.EVENT_UPDATE.equals(e.opCode())
                && "Y".equals(e.body().get("connectFlg"))));
    }

    @Test
    @DisplayName("audit: the auth event is a COPY of the row, so it must carry the raised change number")
    void esquireKeySave_auditEvent_carriesRaisedChangeNumber() {
        // The audit source here is a fresh EsqAuthJpa built for the event, NOT the row that was read, so the
        // change number does not come along on its own. It has to be copied over, and the *_log column is
        // NOT NULL -- miss it and the keep drops the record with a constraint violation, asynchronously,
        // long after the save has already answered 200.
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user9");
        jpa.setChangeNo(4L);
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKeySave("uid-1", Map.of("tfaMethod", "G"), null);

        ArgumentCaptor<pro.mir0n.esquire.backend.jpa.IMappable> src =
                ArgumentCaptor.forClass(pro.mir0n.esquire.backend.jpa.IMappable.class);
        verify(audit).post(eq(RodEvent.Op.UPDATE), anyInt(), eq("uid-1"), isNull(), src.capture());
        assertThat(src.getValue().getChangeNo()).isEqualTo(5L);   // read at 4, raised by the UPDATE
    }

    // =========================================================
    // applyFields: tfaMethod state machine
    // tfaMethod has personal=Y — users can update their own.
    // =========================================================

    @Test
    @DisplayName("applyFields: N→G submitted → stored as 'g' (pending enable)")
    void applyFields_tfaMethod_NtoG_pendingEnable() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user4");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        EsqAccessProfile result = service.esquireKeySave("uid-1", Map.of("tfaMethod", "G"), null);

        assertThat(result.getTfaMethod()).isEqualTo("g");
    }

    @Test
    @DisplayName("applyFields: G→N submitted → stored as 'n' (pending disable)")
    void applyFields_tfaMethod_GtoN_pendingDisable() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "G", "user5");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        EsqAccessProfile result = service.esquireKeySave("uid-1", Map.of("tfaMethod", "N"), null);

        assertThat(result.getTfaMethod()).isEqualTo("n");
    }

    @Test
    @DisplayName("applyFields: G→G (same value) → no-op, tfaMethod unchanged")
    void applyFields_tfaMethod_sameValue_noOp() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "G", "user6");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        EsqAccessProfile result = service.esquireKeySave("uid-1", Map.of("tfaMethod", "G"), null);

        assertThat(result.getTfaMethod()).isEqualTo("G");
    }

    @Test
    @DisplayName("applyFields: invalid tfaMethod value → InvalidValueException (validator rejects before state machine)")
    void applyFields_tfaMethod_invalidValue_rejectsWithException() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user7");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);

        assertThatThrownBy(() -> service.esquireKeySave("uid-1", Map.of("tfaMethod", "X"), null))
                .isInstanceOf(InvalidValueException.class);
    }

    // =========================================================
    // saveAccess: TOTP reset on connect N→Y (admin)
    // =========================================================

    @Test
    @DisplayName("saveAccess: connect N→Y with active TOTP → tfaMethod forced to 'N'")
    void saveAccess_connectNtoY_resetsTotpToN() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("N", "G", "user8");
        when(accessProfileRepository.accessForUpdate("target-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("target-1")).thenReturn(List.of());

        try (MockedStatic<EsqRolesStorage> ms = mockStatic(EsqRolesStorage.class)) {
            adminStorageMock(ms);
            EsqAccessProfile result = service.esquireKeySave("target-1", Map.of("connectFlg", "Y"), List.of("ADMIN"));
            assertThat(result.getTfaMethod()).isEqualTo("N");
        }
    }

    @Test
    @DisplayName("saveAccess: connect N→Y with tfaMethod already N → tfaMethod stays 'N'")
    void saveAccess_connectNtoY_totpAlreadyN_noReset() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("N", "N", "user9");
        when(accessProfileRepository.accessForUpdate("target-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("target-1")).thenReturn(List.of());

        try (MockedStatic<EsqRolesStorage> ms = mockStatic(EsqRolesStorage.class)) {
            adminStorageMock(ms);
            EsqAccessProfile result = service.esquireKeySave("target-1", Map.of("connectFlg", "Y"), List.of("ADMIN"));
            assertThat(result.getTfaMethod()).isEqualTo("N");
        }
    }

}

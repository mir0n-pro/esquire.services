package pro.mir0n.esquire.keySmith.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.keySmith.jpa.EsqAccessProfileRepository;
import pro.mir0n.esquire.keySmith.messaging.KcSyncPublisher;
import pro.mir0n.esquire.keySmith.service.impl.KeySmithService;

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
    private KcSyncPublisher kcSyncPublisher;

    private KeySmithService service;

    @BeforeAll
    static void initStorages() {
        EsqEntityDictionaryStorage.getInstance().init((String) null);
        ValidatorFactory.getInstance().init(BizValidatorFactory.getBizValidators());
    }

    @BeforeEach
    void setUp() {
        service = new KeySmithService(accessProfileRepository, transactionTemplate, em, kcSyncPublisher);
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

        assertThatThrownBy(() -> service.esquireKey(null, "1.2.3", "uid-99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================
    // esquireKeySave: not found / permission denied
    // =========================================================

    @Test
    @DisplayName("esquireKeySave: not found → ResourceNotFoundException")
    void esquireKeySave_notFound_throwsResourceNotFoundException() {
        executeTransactionInline();
        when(accessProfileRepository.accessForUpdate("other", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() -> service.esquireKeySave("other", Map.of(), "1.2.3", "uid-99", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireKeySave: id != uid, roles null → PermissionDeniedException")
    void esquireKeySave_notSelf_nullRoles_throwsPermissionDeniedException() {
        executeTransactionInline();
        when(accessProfileRepository.accessForUpdate("other", "1.2.3"))
                .thenReturn(mock(EsqAccessProfileJpa.class));

        assertThatThrownBy(() -> service.esquireKeySave("other", Map.of(), "1.2.3", "uid-99", null))
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

        service.esquireKey(null, "/root", "uid-1");

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

        service.esquireKey(null, "/root", "uid-1");

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

        service.esquireKey(null, "/root", "uid-1");

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

        service.esquireKey(null, "/root", "uid-1");

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

        service.esquireKey(null, "/root", "uid-1");

        verify(accessProfileRepository).confirmPendingFlags("uid-1", "N", "G");
    }

    @Test
    @DisplayName("esquireKey with explicit id: handshake skipped → confirmPendingFlags NOT called")
    void esquireKey_explicitId_handshakeSkipped() {
        EsqAccessProfileJpa jpa = mock(EsqAccessProfileJpa.class);
        when(jpa.getPwdChangeForced()).thenReturn("Y");
        when(accessProfileRepository.access("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKey("uid-1", "/root", "uid-99");

        verify(accessProfileRepository, never()).confirmPendingFlags(any(), any(), any());
    }

    // =========================================================
    // syncToKeycloak: connect flag transitions
    // connectFlg has personal=N — only admin (id != uid) can change it.
    // EsqRolesStorage is mocked to permit AUTH.
    // =========================================================

    @Test
    @DisplayName("kcSync: Y→N → publish called with oldConnectFlg=Y and updated jpa connectFlg=N")
    void esquireKeySave_connectFlg_YtoN_publishesDelete() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user1");
        when(accessProfileRepository.accessForUpdate("target-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("target-1")).thenReturn(List.of());

        try (MockedStatic<EsqRolesStorage> ms = mockStatic(EsqRolesStorage.class)) {
            adminStorageMock(ms);
            service.esquireKeySave("target-1", Map.of("connectFlg", "N"), "/root", "admin-99", List.of("ADMIN"));
        }

        verify(kcSyncPublisher).publish(eq("user1"), eq("Y"), argThat(j -> "N".equals(j.getConnectFlg())), any(), any(), any());
    }

    @Test
    @DisplayName("kcSync: N→Y → publish called with oldConnectFlg=N and updated jpa connectFlg=Y")
    void esquireKeySave_connectFlg_NtoY_publishesCreate() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("N", "N", "user2");
        when(accessProfileRepository.accessForUpdate("target-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("target-1")).thenReturn(List.of());

        try (MockedStatic<EsqRolesStorage> ms = mockStatic(EsqRolesStorage.class)) {
            adminStorageMock(ms);
            service.esquireKeySave("target-1", Map.of("connectFlg", "Y"), "/root", "admin-99", List.of("ADMIN"));
        }

        verify(kcSyncPublisher).publish(eq("user2"), eq("N"), argThat(j -> "Y".equals(j.getConnectFlg())), any(), any(), any());
    }

    @Test
    @DisplayName("kcSync: Y→Y (no change) → publish called with oldConnectFlg=Y and jpa connectFlg=Y")
    void esquireKeySave_connectFlg_noChange_publishesUpdate() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user3");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        service.esquireKeySave("uid-1", Map.of(), "/root", "uid-1", null);

        verify(kcSyncPublisher).publish(any(), eq("Y"), argThat(j -> "Y".equals(j.getConnectFlg())), any(), any(), any());
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

        EsqAccessProfile result = service.esquireKeySave("uid-1", Map.of("tfaMethod", "G"), "/root", "uid-1", null);

        assertThat(result.getTfaMethod()).isEqualTo("g");
    }

    @Test
    @DisplayName("applyFields: G→N submitted → stored as 'n' (pending disable)")
    void applyFields_tfaMethod_GtoN_pendingDisable() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "G", "user5");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        EsqAccessProfile result = service.esquireKeySave("uid-1", Map.of("tfaMethod", "N"), "/root", "uid-1", null);

        assertThat(result.getTfaMethod()).isEqualTo("n");
    }

    @Test
    @DisplayName("applyFields: G→G (same value) → no-op, tfaMethod unchanged")
    void applyFields_tfaMethod_sameValue_noOp() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "G", "user6");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);
        when(accessProfileRepository.roles("uid-1")).thenReturn(List.of());

        EsqAccessProfile result = service.esquireKeySave("uid-1", Map.of("tfaMethod", "G"), "/root", "uid-1", null);

        assertThat(result.getTfaMethod()).isEqualTo("G");
    }

    @Test
    @DisplayName("applyFields: invalid tfaMethod value → InvalidValueException (validator rejects before state machine)")
    void applyFields_tfaMethod_invalidValue_rejectsWithException() {
        executeTransactionInline();
        EsqAccessProfileJpa jpa = jpaWith("Y", "N", "user7");
        when(accessProfileRepository.accessForUpdate("uid-1", "/root")).thenReturn(jpa);

        assertThatThrownBy(() -> service.esquireKeySave("uid-1", Map.of("tfaMethod", "X"), "/root", "uid-1", null))
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
            EsqAccessProfile result = service.esquireKeySave("target-1", Map.of("connectFlg", "Y"), "/root", "admin-99", List.of("ADMIN"));
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
            EsqAccessProfile result = service.esquireKeySave("target-1", Map.of("connectFlg", "Y"), "/root", "admin-99", List.of("ADMIN"));
            assertThat(result.getTfaMethod()).isEqualTo("N");
        }
    }

}

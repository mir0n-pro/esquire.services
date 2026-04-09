package pro.mir0n.esquire.pacMan.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionary;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.DeleteRestrictedException;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.messaging.EsqEntityBroadcastPublisher;
import pro.mir0n.esquire.pacMan.service.impl.PacManService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class PacManServiceTest {

    @Mock
    private EsqAcctRepository entityRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager em;

    @Mock
    private EsqEntityBroadcastPublisher broadcastPublisher;

    private PacManService service;

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(50, "clAcct", "Client Account", "clAccts", "Client account",
                false, false, true, "", false, false, "", null, null, null, false)
        );

        EsqRoleJpa roleJpa = new EsqRoleJpa();
        roleJpa.setId("1");
        roleJpa.setName(ROLE_ADMIN);
        roleJpa.setKind(EsqConstants.KIND_ADMIN_ROLE);

        EsqPermissionJpa permJpa = new EsqPermissionJpa();
        permJpa.setId("50");
        permJpa.setKind(50);
        permJpa.setFlags("Y,Y,Y,Y,Y");   // CREATE,UPDATE,DELETE,AUTH,ACCT all permitted

        JpaRolesRepository rolesRepo = Mockito.mock(JpaRolesRepository.class);
        when(rolesRepo.roles()).thenReturn(List.of(roleJpa));
        when(rolesRepo.permissions("1")).thenReturn(List.of(permJpa));
        EsqRolesStorage.getInstance().init(rolesRepo);

        EsqEntityField ccyField = new EsqEntityField();
        ccyField.setName(EsqMsgConstants.TEXT_CCY);
        ccyField.setNullable("N");
        ccyField.setDefaultValue(EsqMsgConstants.CCY_DEFAULT);
        ccyField.setReadwrite(3);

        EsqEntityField statusField = new EsqEntityField();
        statusField.setName(EsqMsgConstants.TEXT_STATUS);
        statusField.setNullable("N");
        statusField.setDefaultValue(EsqMsgConstants.FLAG_OPEN);
        statusField.setReadwrite(3);

        EsqEntityField negativeAllowedField = new EsqEntityField();
        negativeAllowedField.setName("negativeAllowed");
        negativeAllowedField.setType("flag");
        negativeAllowedField.setNullable("N");
        negativeAllowedField.setDefaultValue("N");
        negativeAllowedField.setReadwrite(3);

        EsqEntityLayer acctLayer = new EsqEntityLayer();
        acctLayer.setLayer(1);
        acctLayer.setTitle("Generic");
        acctLayer.setFields(List.of(ccyField, statusField, negativeAllowedField));

        EsqEntityDictionary dict50 = new EsqEntityDictionary();
        dict50.setKind(50);
        dict50.getLayers().add(acctLayer);
        EsqEntityDictionaryStorage.getInstance().init(dict50);
        ValidatorFactory.getInstance().init(BizValidatorFactory.getBizValidators());
    }

    @BeforeEach
    void setUp() {
        service = new PacManService(entityRepository, transactionTemplate, em, broadcastPublisher);
    }

    // ---- esquireCommand: unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommand: unknown kind → ResourceNotFoundException")
    void esquireCommand_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommand(99, "1", "details", "1.2.3", "99")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommand: odd kind 51 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommand_oddKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommand(51, "1", "details", "1.2.3", "99")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandSave: unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandSave: unknown kind → ResourceNotFoundException")
    void esquireCommandSave_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(99, "1", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandSave: odd kind 51 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommandSave_oddKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(51, "1", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandSave: acct kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandSave: acct kind, null roles → PermissionDeniedException")
    void esquireCommandSave_acctKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(50, "10", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandNew: unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandNew: unknown kind → ResourceNotFoundException")
    void esquireCommandNew_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandNew(99, "1", "new", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandNew: odd kind 51 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommandNew_oddKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandNew(51, "1", "new", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandNew: acct kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandNew: acct kind, null roles → PermissionDeniedException")
    void esquireCommandNew_acctKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandNew(50, "1", "new", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandDelete: unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandDelete: unknown kind → ResourceNotFoundException")
    void esquireCommandDelete_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandDelete(99, "10", "delete", "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandDelete: odd kind 51 (sub-variant, not registered) → ResourceNotFoundException")
    void esquireCommandDelete_oddKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandDelete(51, "10", "delete", "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandDelete: acct kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandDelete: acct kind, null roles → PermissionDeniedException")
    void esquireCommandDelete_acctKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandDelete(50, "10", "delete", "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandNew: parent user not found → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandNew: parent user not found → ResourceNotFoundException")
    void esquireCommandNew_parentNotFound_throwsResourceNotFoundException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.acctPath("1")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandNew(50, "1", "new", new HashMap<>(), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandNew: success with defaults — ccy=USD, status=O ----

    @Test
    @DisplayName("esquireCommandNew: success with auto name, default ccy=USD and status=O")
    void esquireCommandNew_defaultCcyAndStatus_callsInsertAcctWithDefaults() throws Exception {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.acctPath("10")).thenReturn("1.5.");

        service.esquireCommandNew(50, "10", "new", new HashMap<>(), "1.5.", "99", List.of(ROLE_ADMIN));

        verify(entityRepository).insertAcct(anyLong(), eq(50), anyString(), any(),
                eq(EsqMsgConstants.CCY_DEFAULT), eq(EsqMsgConstants.FLAG_OPEN),
                any(), eq("10"), any(), any(), any());
    }

    // ---- esquireCommandNew: ccy present in request → request value wins over dictionary default ----

    @Test
    @DisplayName("esquireCommandNew: ccy present in request → overrides dictionary default USD")
    void esquireCommandNew_ccyInRequest_requestValueWins() throws Exception {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.acctPath("10")).thenReturn("1.5.");

        Map<String, Object> fields = new HashMap<>();
        fields.put(EsqMsgConstants.TEXT_CCY, "EUR");

        service.esquireCommandNew(50, "10", "new", fields, "1.5.", "99", List.of(ROLE_ADMIN));

        verify(entityRepository).insertAcct(anyLong(), eq(50), anyString(), any(),
                eq("EUR"), eq(EsqMsgConstants.FLAG_OPEN),
                any(), eq("10"), any(), any(), any());
    }

    // ---- esquireCommandNew: insertAcctPath called before insertAcct ----

    @Test
    @DisplayName("esquireCommandNew: insertAcctPath called before insertAcct")
    void esquireCommandNew_insertsEntityPath_beforeInsertAcct() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.acctPath("10")).thenReturn("1.5.");

        service.esquireCommandNew(50, "10", "new", new HashMap<>(), "1.5.", "99", List.of(ROLE_ADMIN));

        InOrder order = inOrder(entityRepository);
        order.verify(entityRepository).insertAcctPath(anyLong(), anyInt(), anyString());
        order.verify(entityRepository).insertAcct(anyLong(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ---- esquireCommandDelete: deleteEntityPath called after deleteAcct ----

    @Test
    @DisplayName("esquireCommandDelete: deleteEntityPath called after deleteAcct")
    void esquireCommandDelete_deletesEntityPath_afterDeleteAcct() {
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10");
        acct.setKind(50);
        acct.setStatus("C");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        service.esquireCommandDelete(50, "10", "delete", "1.2.3", "99", List.of(ROLE_ADMIN));

        InOrder order = inOrder(entityRepository);
        order.verify(entityRepository).deleteAcct("10");
        order.verify(entityRepository).deleteEntityPath("10");
    }

    // ---- esquireCommandDelete: account not found → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandDelete: account not found → ResourceNotFoundException")
    void esquireCommandDelete_acctNotFound_throwsResourceNotFoundException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandDelete(50, "10", "delete", "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandDelete: account not closed → DeleteRestrictedException ----

    @Test
    @DisplayName("esquireCommandDelete: account not closed (status=O) → DeleteRestrictedException")
    void esquireCommandDelete_acctNotClosed_throwsDeleteRestrictedException() {
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10");
        acct.setKind(50);
        acct.setStatus("O");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        assertThatThrownBy(() ->
            service.esquireCommandDelete(50, "10", "delete", "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(DeleteRestrictedException.class);
    }
}

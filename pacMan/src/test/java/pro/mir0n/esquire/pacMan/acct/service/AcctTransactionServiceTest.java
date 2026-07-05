package pro.mir0n.esquire.pacMan.acct.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.error.MissingRequestIdException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.pacMan.acct.AcctOperation;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.service.BizValidatorFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcctTransactionServiceTest {

    @Mock private EsqAcctRepository entityRepository;
    @Mock private EsqAcctTransactionRepository transactionRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private EntityManager em;

    private AcctTransactionProcessorSingle service;

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(50, "clAcct", "Client Account", "clAccts", "Client account",
                false, false, true, "", false, false, "", null, null, null, false));

        EsqRoleJpa roleJpa = new EsqRoleJpa();
        roleJpa.setId("1");
        roleJpa.setName(ROLE_ADMIN);
        roleJpa.setKind(EsqConstants.KIND_ADMIN_ROLE);

        EsqPermissionJpa permJpa = new EsqPermissionJpa();
        permJpa.setId("50");
        permJpa.setKind(50);
        permJpa.setFlags("Y,Y,Y,Y,Y");

        JpaRolesRepository rolesRepo = Mockito.mock(JpaRolesRepository.class);
        when(rolesRepo.roles()).thenReturn(List.of(roleJpa));
        when(rolesRepo.permissions("1")).thenReturn(List.of(permJpa));
        EsqRolesStorage.getInstance().init(rolesRepo);

        EsqEntityDictionaryStorage.getInstance().init((String) null);
        ValidatorFactory.getInstance().init(BizValidatorFactory.getBizValidators());
    }

    @BeforeEach
    void setUp() {
        service = new AcctTransactionProcessorSingle(entityRepository, transactionRepository, transactionTemplate, em, Mockito.mock(pro.mir0n.esquire.audit.AuditBusBridge.class));
    }

    // ---- missing X-Request-ID → MissingRequestIdException ----

    @Test
    @DisplayName("esquireCommandAcct: missing X-Request-ID → MissingRequestIdException")
    void esquireCommandAcct_missingRequestId_throwsMissingRequestIdException() {
        AcctTransactionService svc = new AcctTransactionService(
                entityRepository, transactionRepository, transactionTemplate, em,
                Mockito.mock(pro.mir0n.esquire.audit.AuditBusBridge.class));
        EsqContextHolder.set(new EsqRequestContext(null, null, "99", "1.2.3")); // no reqId
        try {
            assertThatThrownBy(() ->
                svc.esquireCommandAcct(50, "10", "acct", Map.of(), List.of(ROLE_ADMIN))
            ).isInstanceOf(MissingRequestIdException.class);
        } finally {
            EsqContextHolder.clear();
        }
    }

    // ---- unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandAcct: unknown kind → ResourceNotFoundException")
    void esquireCommandAcct_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandAcct(99, "10", null, Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandAcct: odd kind 51 → ResourceNotFoundException")
    void esquireCommandAcct_oddKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandAcct(51, "10", null, Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandAcct: null roles → PermissionDeniedException")
    void esquireCommandAcct_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", null, Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- account not found → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandAcct: account not found → ResourceNotFoundException")
    void esquireCommandAcct_acctNotFound_throwsResourceNotFoundException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,Map.of("amount", 100.0), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- zero amount → InvalidValueException ----

    @Test
    @DisplayName("esquireCommandAcct: amount=0 → InvalidValueException")
    void esquireCommandAcct_zeroAmount_throwsInvalidValueException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,
                    Map.of("amount", 0.0), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- negative amount → InvalidValueException ----

    @Test
    @DisplayName("esquireCommandAcct: amount < 0 → InvalidValueException")
    void esquireCommandAcct_negativeAmount_throwsInvalidValueException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,
                    Map.of("amount", -50.0), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- account not open → InvalidValueException ----

    @Test
    @DisplayName("esquireCommandAcct: account status != O → InvalidValueException")
    void esquireCommandAcct_accountNotOpen_throwsInvalidValueException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(500.0); acct.setNegativeAllowed("N"); acct.setStatus("C");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(acct);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,
                    Map.of("amount", 100.0), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- insufficient balance, negativeAllowed=N → InvalidValueException ----

    @Test
    @DisplayName("esquireCommandAcct: balance+amount < 0 and negativeAllowed=N → InvalidValueException")
    void esquireCommandAcct_insufficientBalance_throwsInvalidValueException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(-900.0); acct.setNegativeAllowed("N"); acct.setStatus("O");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(acct);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,
                    Map.of("amount", 50.0), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- invalid refCode value → InvalidValueException ----

    @Test
    @DisplayName("esquireCommandAcct: refCode not in list → InvalidValueException")
    void esquireCommandAcct_invalidRefCode_throwsInvalidValueException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(500.0); acct.setNegativeAllowed("N"); acct.setStatus("O");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 100.0);
        fields.put("refCode", "wire");

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- null refCode2 → InvalidValueException ----

    @Test
    @DisplayName("esquireCommandAcct: refCode2=null → InvalidValueException")
    void esquireCommandAcct_nullRefCode2_throwsInvalidValueException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(500.0); acct.setNegativeAllowed("N"); acct.setStatus("O");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 100.0);
        fields.put("refCode", "cash");

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- negativeAllowed=Y → posts even when balance goes negative ----

    @Test
    @DisplayName("esquireCommandAcct: negativeAllowed=Y → posts without balance check")
    void esquireCommandAcct_negativeAllowedY_postsSuccessfully() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(-900.0); acct.setNegativeAllowed("Y"); acct.setStatus("O");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 50.0);
        fields.put("refCode", "cc");
        fields.put("refCode2", "REF-001");

        AcctTransactionSingle ret = service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,fields, "1.2.3", "99", List.of(ROLE_ADMIN));

        org.assertj.core.api.Assertions.assertThat(ret).isNotNull();
        verify(entityRepository).updateAcctBalance(eq("10"), eq(-850.0), any(), any(), any());
    }

    // ---- skipValidation=true → posts despite insufficient balance and closed status ----

    @Test
    @DisplayName("esquireCommandAcct: skipValidation=true → posts despite insufficient balance")
    void esquireCommandAcct_skipValidation_postsSuccessfully() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(-900.0); acct.setNegativeAllowed("N"); acct.setStatus("C");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 50.0);

        AcctTransactionSingle ret = service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN));

        org.assertj.core.api.Assertions.assertThat(ret).isNotNull();
        verify(transactionRepository).insertAcctTransaction(anyString(), any(), anyLong(), anyInt(),
                eq(50.0), eq(-900.0), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("10"), eq(-850.0), any(), any(), any());
    }

    // ---- success — insertAcctTransaction then updateAcctBalance ----

    @Test
    @DisplayName("esquireCommandAcct: success → insertAcctTransaction called before updateAcctBalance; result fields correct")
    void esquireCommandAcct_success_insertsTransactionAndUpdatesBalance() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(500.0); acct.setNegativeAllowed("N"); acct.setStatus("O");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 100.0);
        fields.put("typeId", 1);
        fields.put("refCode", "cash");
        fields.put("refCode2", "REF-001");
        fields.put("memo", "test deposit");

        AcctTransactionSingle ret = service.esquireCommandAcct(50, "10", AcctOperation.Code.DEPOSIT,fields, "1.2.3", "99", List.of(ROLE_ADMIN));

        org.assertj.core.api.Assertions.assertThat(ret).isNotNull();
        org.assertj.core.api.Assertions.assertThat(ret.getAmount()).isEqualTo(100.0);
        org.assertj.core.api.Assertions.assertThat(ret.getKind()).isEqualTo(50);
        org.assertj.core.api.Assertions.assertThat(ret.getTypeId()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(ret.getRefCode()).isEqualTo("cash");
        org.assertj.core.api.Assertions.assertThat(ret.getRefCode2()).isEqualTo("REF-001");
        org.assertj.core.api.Assertions.assertThat(ret.getMemo()).isEqualTo("test deposit");

        InOrder order = inOrder(transactionRepository, entityRepository);
        order.verify(transactionRepository).insertAcctTransaction(anyString(), any(), eq(10L), eq(1),
                eq(100.0), eq(500.0), any(), eq("cash"), eq("REF-001"), any(), any(), eq("test deposit"), any(), any(), any(), any(), any(), any());
        order.verify(entityRepository).updateAcctBalance(eq("10"), eq(600.0), any(), any(), any());
    }
}

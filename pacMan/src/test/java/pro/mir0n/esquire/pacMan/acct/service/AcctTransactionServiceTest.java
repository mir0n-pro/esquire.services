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
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSimple;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;

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

    private AcctTransactionService service;

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
        permJpa.setFlags("Y,Y,Y,Y,Y");

        JpaRolesRepository rolesRepo = Mockito.mock(JpaRolesRepository.class);
        when(rolesRepo.roles()).thenReturn(List.of(roleJpa));
        when(rolesRepo.permissions("1")).thenReturn(List.of(permJpa));
        EsqRolesStorage.getInstance().init(rolesRepo);
    }

    @BeforeEach
    void setUp() {
        service = new AcctTransactionService(entityRepository, transactionRepository, transactionTemplate, em);
    }

    // ---- unknown or odd kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandAcct: unknown kind → ResourceNotFoundException")
    void esquireCommandAcct_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandAcct(99, "10", "acct", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("esquireCommandAcct: odd kind 51 → ResourceNotFoundException")
    void esquireCommandAcct_oddKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandAcct(51, "10", "acct", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandAcct: null roles → PermissionDeniedException")
    void esquireCommandAcct_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", "acct", Map.of(), "1.2.3", "99", null)
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
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", "acct", Map.of("amount", 100.0), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- zero amount → returns null, no DB calls ----

    @Test
    @DisplayName("esquireCommandAcct: amount=0 → returns null, no insert/update called")
    void esquireCommandAcct_zeroAmount_returnsNull() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(500.0); acct.setNegativeAllowed("N");
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        AcctTransactionSimple ret = service.esquireCommandAcct(50, "10", "acct",
                Map.of("amount", 0.0), "1.2.3", "99", List.of(ROLE_ADMIN));

        org.assertj.core.api.Assertions.assertThat(ret).isNull();
        Mockito.verifyNoInteractions(transactionRepository);
        verify(entityRepository, Mockito.never()).updateAcctBalance(anyString(), anyDouble(), anyString(), anyString(), anyString());
    }

    // ---- negative amount → InvalidValueException ----

    @Test
    @DisplayName("esquireCommandAcct: amount < 0 → InvalidValueException")
    void esquireCommandAcct_negativeAmount_throwsInvalidValueException() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(500.0); acct.setNegativeAllowed("N");
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", "acct",
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
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", "acct",
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
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", "acct",
                    Map.of("amount", 50.0), "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- skipValidation=true → posts even when balance goes negative ----

    @Test
    @DisplayName("esquireCommandAcct: skipValidation=true → posts despite insufficient balance")
    void esquireCommandAcct_skipValidation_postsSuccessfully() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setId("10"); acct.setKind(50); acct.setBalance(-900.0); acct.setNegativeAllowed("N");
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 50.0);
        fields.put("skipValidation", true);

        AcctTransactionSimple ret = service.esquireCommandAcct(50, "10", "acct", fields, "1.2.3", "99", List.of(ROLE_ADMIN));

        org.assertj.core.api.Assertions.assertThat(ret).isNotNull();
        verify(transactionRepository).insertAcctTransaction(anyLong(), anyLong(), anyInt(),
                eq(50.0), eq(-900.0), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("10"), eq(-850.0), any(), any(), any());
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
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 50.0);

        AcctTransactionSimple ret = service.esquireCommandAcct(50, "10", "acct", fields, "1.2.3", "99", List.of(ROLE_ADMIN));

        org.assertj.core.api.Assertions.assertThat(ret).isNotNull();
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
        when(entityRepository.detailAcctForUpdate("10", "1.2.3")).thenReturn(acct);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 100.0);
        fields.put("typeId", 980);
        fields.put("refCode", "cash");
        fields.put("memo", "test deposit");

        AcctTransactionSimple ret = service.esquireCommandAcct(50, "10", "acct", fields, "1.2.3", "99", List.of(ROLE_ADMIN));

        org.assertj.core.api.Assertions.assertThat(ret).isNotNull();
        org.assertj.core.api.Assertions.assertThat(ret.getAmount()).isEqualTo(100.0);
        org.assertj.core.api.Assertions.assertThat(ret.getKind()).isEqualTo(980);
        org.assertj.core.api.Assertions.assertThat(ret.getTypeId()).isEqualTo(980);
        org.assertj.core.api.Assertions.assertThat(ret.getRefCode()).isEqualTo("cash");
        org.assertj.core.api.Assertions.assertThat(ret.getMemo()).isEqualTo("test deposit");

        InOrder order = inOrder(transactionRepository, entityRepository);
        order.verify(transactionRepository).insertAcctTransaction(anyLong(), eq(10L), eq(980),
                eq(100.0), eq(500.0), any(), eq("cash"), any(), any(), any(), eq("test deposit"), any(), any(), any());
        order.verify(entityRepository).updateAcctBalance(eq("10"), eq(600.0), any(), any(), any());
    }
}

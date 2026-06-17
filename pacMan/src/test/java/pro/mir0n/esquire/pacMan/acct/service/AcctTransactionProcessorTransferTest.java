package pro.mir0n.esquire.pacMan.acct.service;

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
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcctTransactionProcessorTransferTest {

    @Mock private EsqAcctRepository entityRepository;
    @Mock private EsqAcctTransactionRepository transactionRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private EntityManager em;

    private AcctTransactionProcessorTransfer service;

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(50, "clAcct", "Client Account", "clAccts", "Client account",
                false, false, true, "", false, false, "", null, null, null, false));
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(54, "paper", "Paper Account", "papers", "Paper account",
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
        service = new AcctTransactionProcessorTransfer(entityRepository, transactionRepository, transactionTemplate, em, Mockito.mock(pro.mir0n.esquire.messaging.xrod.IXRod.class));
    }

    // ---- missing id2 / kind2 → IllegalArgumentException ----

    @Test
    @DisplayName("transfer: missing id2 → IllegalArgumentException")
    void transfer_missingId2_throwsIllegalArgumentException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("kind2", 50);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transfer: missing kind2 → IllegalArgumentException")
    void transfer_missingKind2_throwsIllegalArgumentException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- same id for both legs → InvalidValueException ----

    @Test
    @DisplayName("transfer: id == id2 → InvalidValueException")
    void transfer_sameId_throwsInvalidValueException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "10");
        fields.put("kind2", 50);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- paper kind on either leg → InvalidValueException ----

    @Test
    @DisplayName("transfer: source kind=54 (paper) → InvalidValueException")
    void transfer_sourcePaper_throwsInvalidValueException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(54, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("transfer: target kind2=54 (paper) → InvalidValueException")
    void transfer_targetPaper_throwsInvalidValueException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 54);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- unknown kind2 → ResourceNotFoundException ----

    @Test
    @DisplayName("transfer: unknown kind2 → ResourceNotFoundException")
    void transfer_unknownKind2_throwsResourceNotFoundException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 99);
        fields.put("rate", 1.0);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("transfer: missing rate → InvalidValueException")
    void transfer_missingRate_throwsInvalidValueException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("transfer: rate <= 0 → InvalidValueException")
    void transfer_zeroRate_throwsInvalidValueException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);
        fields.put("rate", 0.0);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);
    }

    // ---- success: source debited, target credited, first-leg result returned ----

    @Test
    @DisplayName("transfer: success → source debited, target credited, first-leg result returned")
    void transfer_success_debitsSourceCreditsTarget() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("N"); source.setStatus("O"); source.setCcy("USD");
        EsqAcctJpa target = new EsqAcctJpa();
        target.setId("20"); target.setKind(50); target.setBalance(100.0); target.setNegativeAllowed("N"); target.setStatus("C");
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcctForUpdate("20", 50, "1.2.3")).thenReturn(target);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);
        fields.put("rate", 1.25);

        AcctTransactionSingle ret = service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN));

        assertThat(ret).isNotNull();
        assertThat(ret.getAmount()).isEqualTo(-100.0);
        assertThat(ret.getRefCode4()).isEqualTo("Transfer 100.00 USD to Account 20");
        verify(entityRepository).updateAcctBalance(eq("10"), eq(400.0), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("20"), eq(225.0), any(), any(), any());
    }
}

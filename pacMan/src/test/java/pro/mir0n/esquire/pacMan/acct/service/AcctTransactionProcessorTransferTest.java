package pro.mir0n.esquire.pacMan.acct.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
        service = new AcctTransactionProcessorTransfer(entityRepository, transactionRepository, transactionTemplate, em, Mockito.mock(pro.mir0n.esquire.audit.AuditBusBridge.class));
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
        target.setId("20"); target.setKind(50); target.setBalance(100.0); target.setNegativeAllowed("N"); target.setStatus("O");
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcct("20", "1.2.3")).thenReturn(target);
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
        verify(entityRepository).updateAcctBalance(eq("10"), eq(400.0), any(), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("20"), eq(225.0), any(), any(), any(), any());
    }

    // ---- RD1: an amount exactly on a 3rd-decimal tie must round symmetrically, so the two legs balance ----

    @Test
    @DisplayName("transfer: 3rd-decimal tie amount rounds symmetrically → debit and credit magnitudes balance")
    void transfer_tieAmount_legsBalance() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("N"); source.setStatus("O"); source.setCcy("USD");
        EsqAcctJpa target = new EsqAcctJpa();
        target.setId("20"); target.setKind(50); target.setBalance(100.0); target.setNegativeAllowed("N"); target.setStatus("O");
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcct("20", "1.2.3")).thenReturn(target);
        when(entityRepository.detailAcctForUpdate("20", 50, "1.2.3")).thenReturn(target);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.005);   // exactly on a 2nd-decimal tie -- the scale the ledger keeps
        fields.put("id2", "20");
        fields.put("kind2", 50);
        fields.put("rate", 1.0);           // same currency: the legs must be equal and opposite

        service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN));

        ArgumentCaptor<Double> src = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> tgt = ArgumentCaptor.forClass(Double.class);
        verify(entityRepository).updateAcctBalance(eq("10"), src.capture(), any(), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("20"), tgt.capture(), any(), any(), any(), any());
        double debited  = 500.0 - src.getValue();   // magnitude removed from the source
        double credited = tgt.getValue() - 100.0;    // magnitude added to the target
        // symmetric round3(-x) == -round3(x): both legs are 100.001. Under the old half-up round3 the debit was
        // 100.000 and the credit 100.001 -> off by 0.001 (RD1).
        assertThat(debited).isCloseTo(credited, within(1e-9));
        assertThat(debited).isCloseTo(100.01, within(1e-9));   // half away from zero, at 2dp
    }

    // ---- T9: each leg carries ITS OWN account's change number ----

    @Test
    @DisplayName("transfer: each leg's ledger line carries its OWN account's raised number, not the other's")
    void transfer_eachLegCarriesItsOwnAccountChangeNo() {
        // A transfer writes TWO ledger rows against TWO different accounts. The reference column needs no
        // "which leg" discriminator precisely because each row is scoped to one account -- but only while
        // the two legs keep their numbers apart. Crossing them would still balance the money and still
        // insert two rows, so nothing else in the suite would notice.
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("N");
        source.setStatus("O"); source.setCcy("USD");
        source.setChangeNo(7L);           // -> the source leg must carry 8
        EsqAcctJpa target = new EsqAcctJpa();
        target.setId("20"); target.setKind(50); target.setBalance(100.0); target.setNegativeAllowed("N"); target.setStatus("O");
        target.setChangeNo(3L);           // -> the target leg must carry 4, from a DIFFERENT counter
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcct("20", "1.2.3")).thenReturn(target);
        when(entityRepository.detailAcctForUpdate("20", 50, "1.2.3")).thenReturn(target);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);
        fields.put("rate", 1.0);

        service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN));

        // the ledger row of each leg
        verify(transactionRepository).insertAcctTransaction(any(), any(), eq(10L), anyInt(),
                anyDouble(), anyDouble(), eq(8L),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(transactionRepository).insertAcctTransaction(any(), any(), eq(20L), anyInt(),
                anyDouble(), anyDouble(), eq(4L),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // and the balance update of each leg carries the SAME number as that leg's ledger row
        verify(entityRepository).updateAcctBalance(eq("10"), anyDouble(), eq(8L), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("20"), anyDouble(), eq(4L), any(), any(), any());
    }

    @Test
    @DisplayName("transfer: an account with no number yet starts its ledger reference at 1")
    void transfer_nullChangeNo_startsAtOne() {
        // Defensive: bumpChangeNo() treats an absent number as 0, so the first write is 1 rather than a
        // NullPointerException. A ledger row is not the place to discover that.
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("N");
        source.setStatus("O"); source.setCcy("USD");           // changeNo left null on purpose
        EsqAcctJpa target = new EsqAcctJpa();
        target.setId("20"); target.setKind(50); target.setBalance(100.0); target.setNegativeAllowed("N"); target.setStatus("O");
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcct("20", "1.2.3")).thenReturn(target);
        when(entityRepository.detailAcctForUpdate("20", 50, "1.2.3")).thenReturn(target);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);
        fields.put("rate", 1.0);

        service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN));

        verify(entityRepository).updateAcctBalance(eq("10"), anyDouble(), eq(1L), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("20"), anyDouble(), eq(1L), any(), any(), any());
    }

    // ---- P1 (round 3): the kind the caller names must be the kind of the row it points at ----

    @Test
    @DisplayName("transfer: kind2 is not the target's kind -> refused BEFORE the debit, nothing posted")
    void transfer_kind2MismatchesTheRow_refusedBeforeTheDebit() {
        // validatePermissions is asked about the kind the CALLER named and answers completely for it -- so a
        // caller holding ACCT on 50 passes the gate while the money would reach a kind-52 account the gate was
        // never asked about. The credit leg's own read carries the kind, so this used to be discovered there:
        // after the debit had committed.
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("N");
        source.setStatus("O"); source.setCcy("USD");
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);

        EsqAcctJpa target = new EsqAcctJpa();
        target.setId("20"); target.setKind(52); target.setBalance(0.0); target.setNegativeAllowed("N");
        target.setStatus("O"); target.setCcy("USD");
        when(entityRepository.detailAcct("20", "1.2.3")).thenReturn(target);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);          // the row is 52
        fields.put("rate", 1.0);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);

        // neither leg posted: no balance written, and the debit leg was never even entered
        verify(entityRepository, never()).updateAcctBalance(any(), anyDouble(), any(), any(), any(), any());
        verify(entityRepository, never()).detailAcctForUpdate(any(), anyInt(), any());
    }

    // ---- P1b: what happens to the debit when the CREDIT leg's account cannot be read ----

    @Test
    @DisplayName("transfer: target not readable -> refused BEFORE the debit, source untouched")
    void transfer_targetMissing_refusedBeforeTheDebit() {
        // no transaction stub: the pre-check refuses before one is opened
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("N");
        source.setStatus("O"); source.setCcy("USD");
        source.setStatus("O");
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        // the target: an id that names no account the caller can read -- absent, deleted, or outside rootPath
        when(entityRepository.detailAcct("999999999", "1.2.3")).thenReturn(null);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "999999999");
        fields.put("kind2", 50);
        fields.put("rate", 1.0);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);

        // The point of the pre-check: the refusal comes before any money moves.
        verify(entityRepository, never()).updateAcctBalance(eq("10"), anyDouble(), any(), any(), any(), any());
        verify(entityRepository, never()).detailAcctForUpdate(eq("10"), anyInt(), any());
    }

    @Test
    @DisplayName("transfer: target not open -> refused before the debit, source untouched")
    void transfer_targetClosed_refusedBeforeTheDebit() {
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("N");
        source.setStatus("O"); source.setCcy("USD");
        EsqAcctJpa target = new EsqAcctJpa();
        target.setId("20"); target.setKind(50); target.setBalance(100.0); target.setStatus("C");
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcct("20", "1.2.3")).thenReturn(target);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.0);
        fields.put("id2", "20");
        fields.put("kind2", 50);
        fields.put("rate", 1.0);

        assertThatThrownBy(() ->
            service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN))
        ).isInstanceOf(InvalidValueException.class);

        verify(entityRepository, never()).updateAcctBalance(eq("10"), anyDouble(), any(), any(), any(), any());
    }

    // ---- P3: the credit must follow the amount that was DEBITED, not the amount the request asked for ----

    @Test
    @DisplayName("transfer: a request with more decimals than the ledger keeps creates no money across the legs")
    void transfer_extraDecimals_legsStayBalancedAtTheRate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            inv.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        });
        EsqAcctJpa source = new EsqAcctJpa();
        source.setId("10"); source.setKind(50); source.setBalance(500.0); source.setNegativeAllowed("Y");
        source.setStatus("O"); source.setCcy("USD");
        EsqAcctJpa target = new EsqAcctJpa();
        target.setId("20"); target.setKind(50); target.setBalance(0.0); target.setNegativeAllowed("N");
        target.setStatus("O"); target.setCcy("EUR");
        when(entityRepository.detailAcct("10", "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcct("20", "1.2.3")).thenReturn(target);
        when(entityRepository.detailAcctForUpdate("10", 50, "1.2.3")).thenReturn(source);
        when(entityRepository.detailAcctForUpdate("20", 50, "1.2.3")).thenReturn(target);

        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", -100.004);    // a decimal the ledger does not keep
        fields.put("id2", "20");
        fields.put("kind2", 50);
        fields.put("rate", 1000.0);        // a large rate makes the discarded decimal visible

        service.esquireCommandAcct(50, "10", AcctOperation.Code.TRANSFER, fields, true, "1.2.3", "99", List.of(ROLE_ADMIN));

        ArgumentCaptor<Double> src = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> tgt = ArgumentCaptor.forClass(Double.class);
        verify(entityRepository).updateAcctBalance(eq("10"), src.capture(), any(), any(), any(), any());
        verify(entityRepository).updateAcctBalance(eq("20"), tgt.capture(), any(), any(), any(), any());
        double debited  = 500.0 - src.getValue();
        double credited = tgt.getValue();

        // the debit rounds to 100.00, so the credit is 100.00 * 1000 -- not 100.004 * 1000
        assertThat(debited).isCloseTo(100.00, within(1e-9));
        assertThat(credited).isCloseTo(debited * 1000.0, within(1e-9));
    }
}

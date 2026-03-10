package pro.mir0n.esquire.keySmith.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.keySmith.jpa.EsqAccessProfileRepository;
import pro.mir0n.esquire.keySmith.service.impl.KeySmithService;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeySmithServiceTest {

    @Mock
    private EsqAccessProfileRepository accessProfileRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager em;

    private KeySmithService service;

    @BeforeEach
    void setUp() {
        service = new KeySmithService(accessProfileRepository, transactionTemplate, em);
    }

    // ---- helper: makes transactionTemplate.execute() run the lambda inline ----

    private void executeTransactionInline() {
        doAnswer(inv -> {
            inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());
    }

    // ---- esquireKey: not found → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireKey: accessProfileRepository returns null → ResourceNotFoundException")
    void esquireKey_notFound_throwsResourceNotFoundException() {
        when(accessProfileRepository.access("uid-99", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() -> service.esquireKey(null, "1.2.3", "uid-99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireKeySave: not found → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireKeySave: accessForUpdate returns null → ResourceNotFoundException")
    void esquireKeySave_notFound_throwsResourceNotFoundException() {
        executeTransactionInline();
        when(accessProfileRepository.accessForUpdate("other", "1.2.3")).thenReturn(null);

        assertThatThrownBy(() -> service.esquireKeySave("other", Map.of(), "1.2.3", "uid-99", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireKeySave: not self, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireKeySave: id != uid, roles null → PermissionDeniedException")
    void esquireKeySave_notSelf_nullRoles_throwsPermissionDeniedException() {
        executeTransactionInline();
        when(accessProfileRepository.accessForUpdate("other", "1.2.3"))
                .thenReturn(mock(EsqAccessProfileJpa.class));

        assertThatThrownBy(() -> service.esquireKeySave("other", Map.of(), "1.2.3", "uid-99", null))
                .isInstanceOf(PermissionDeniedException.class);
    }

}

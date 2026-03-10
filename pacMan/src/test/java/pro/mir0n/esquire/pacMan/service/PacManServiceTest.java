package pro.mir0n.esquire.pacMan.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.service.impl.PacManService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class PacManServiceTest {

    @Mock
    private EsqAcctRepository entityRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager em;

    private PacManService service;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(50, "clAcct", "Client Account", "clAccts", "Client account",
                false, false, true, "", false, false, "", null, null, null, false)
        );
    }

    @BeforeEach
    void setUp() {
        service = new PacManService(entityRepository, transactionTemplate, em);
    }

    // ---- esquireCommand: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommand: unknown kind → ResourceNotFoundException")
    void esquireCommand_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommand(99, "1", "details", "1.2.3", "99")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommandSave: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandSave: unknown kind → ResourceNotFoundException")
    void esquireCommandSave_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(99, "1", "save", Map.of(), "1.2.3", "99", null)
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
}

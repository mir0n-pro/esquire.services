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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.service.impl.OrgService;
import pro.mir0n.esquire.enyMan.service.impl.UsrService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The move's destination is checked on the request thread and used later on the queue worker. If it is deleted
 * or moved out of the caller's subtree in between, the path lookup returns null -- and the regular-user branch
 * used to concatenate that into "null&lt;id&gt;.", a path no rootPath matches, leaving the user and every
 * account under it unreachable through the API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoveDestinationGuardTest {

    private static final String ROOT = "1.2.";
    private static final String UID  = "99";

    @Mock private EsqEntityDictionaryRepository dictRepo;
    @Mock private EsqUsrRepository usrRepository;
    @Mock private EsqOrgRepository orgRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private EntityManager em;
    @Mock private AuditBusBridge audit;

    private UsrService usrService;
    private OrgService orgService;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage oks = EsqObjectKindStorage.getInstance();
        oks.init(new EsqObjectKind(20, "org", "Org", "orgs", "Test org",
                true, false, false, "", false, false, "", null, null, null, false));
        oks.init(new EsqObjectKind(34, "usr", "Usr", "usrs", "Test usr",
                false, true, false, "", false, false, "", null, null, null, false));
    }

    @BeforeEach
    void setUp() {
        // the template just runs the callback -- the guard is what is under test, not the transaction
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        usrService = new UsrService(dictRepo, usrRepository, transactionTemplate, em, audit);
        orgService = new OrgService(dictRepo, orgRepository, transactionTemplate, em, audit);
        EsqContextHolder.set(new EsqRequestContext(null, "req-test", UID, ROOT));
    }

    @AfterEach
    void clear() {
        EsqContextHolder.clear();
    }

    @Test
    @DisplayName("moveUsr: destination gone -> NOT FOUND, and no path is written")
    void moveUsr_destinationOutOfScope_refuses() {
        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId("42");
        usr.setKind(34);
        usr.setParentId("7");
        when(usrRepository.detailUsrForUpdate("42", ROOT)).thenReturn(usr);
        when(usrRepository.usrPath("9", ROOT)).thenReturn(null);   // the destination is no longer in scope

        assertThatThrownBy(() -> usrService.esquireCommandMove(34, "42", "9", List.of("ADMIN")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(usrRepository, never()).moveUsrPaths(anyString(), anyString());
        verify(usrRepository, never()).moveAdminPath(anyString(), any());
    }

    @Test
    @DisplayName("moveOrg: destination gone -> NOT FOUND instead of an NPE")
    void moveOrg_destinationOutOfScope_refuses() {
        EsqOrgJpa org = new EsqOrgJpa();
        org.setId("42");
        org.setKind(20);
        org.setParentId("7");
        when(orgRepository.detailOrgForUpdate("42", ROOT)).thenReturn(org);
        when(orgRepository.orgPath("42", ROOT)).thenReturn("1.2.42.");
        when(orgRepository.orgPath("9", ROOT)).thenReturn(null);

        assertThatThrownBy(() -> orgService.esquireCommandMove(20, "42", "9", List.of("ADMIN")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orgRepository, never()).moveOrgPaths(anyString(), anyString());
    }
}

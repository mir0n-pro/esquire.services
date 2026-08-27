package pro.mir0n.esquire.enyMan.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.enyMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqSubtreeRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.messaging.EntityBusAdapter;
import pro.mir0n.esquire.enyMan.queue.MoveQueueManager;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.enyMan.service.impl.EnyManService;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.xrod.impl.XRodDisabled;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The denial rule: <b>404 when visibility is checked, 403 when permission is checked</b>, and a 403
 * must never be the answer that tells a caller something exists.
 *
 * <p>Two shapes satisfy it, and both are pinned here. A permission answer that reads only the roles
 * and the kind may be given BEFORE anything is fetched -- it is identical for an id that exists, one
 * that does not, and one the caller cannot see, so it carries no information. A permission answer
 * that reads the entity must come AFTER a path-scoped fetch, so an invisible entity is a 404 and the
 * 403 is only ever spoken about something the caller can already see.
 */
@ExtendWith(MockitoExtension.class)
class DenialStatusRuleTest {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String UID        = "99";
    private static final String ROOT_PATH  = "1.14.";

    @Mock private EsqEntityDictionaryRepository dictRepo;
    @Mock private EsqOrgRepository              orgRepo;
    @Mock private EsqUsrRepository              usrRepo;
    @Mock private EsqAcctRepository             acctRepo;
    @Mock private EsqSubtreeRepository          subtreeRepo;
    @Mock private TransactionTemplate           transactionTemplate;
    @Mock private EntityManager                 em;
    @Mock private EntityBusAdapter              broadcastPublisher;
    @Mock private MoveQueueManager              moveQueue;

    private EnyManService service;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage oks = EsqObjectKindStorage.getInstance();
        oks.init(new EsqObjectKind(20, "org", "Org", "orgs", "Test org",
            true, false, false, "", false, false, "", null, null, null, false));
        oks.init(new EsqObjectKind(34, "usr", "Usr", "usrs", "Test usr",
            false, true, false, "", false, false, "", null, null, null, false));

        EsqRoleJpa roleJpa = new EsqRoleJpa();
        roleJpa.setId("1");
        roleJpa.setName(ROLE_ADMIN);
        roleJpa.setKind(EsqConstants.KIND_ADMIN_ROLE);

        EsqPermissionJpa orgPerm = new EsqPermissionJpa();
        orgPerm.setId("20");
        orgPerm.setKind(20);
        orgPerm.setFlags("Y,Y,Y,Y,Y");

        // kind 34 is granted; kind 32 is NOT in the map at all -- the shape the kind guard is about
        EsqPermissionJpa usrPerm = new EsqPermissionJpa();
        usrPerm.setId("34");
        usrPerm.setKind(34);
        usrPerm.setFlags("Y,Y,Y,Y,Y");

        JpaRolesRepository rolesRepo = Mockito.mock(JpaRolesRepository.class);
        when(rolesRepo.roles()).thenReturn(List.of(roleJpa));
        when(rolesRepo.permissions("1")).thenReturn(List.of(orgPerm, usrPerm));
        EsqRolesStorage.getInstance().init(rolesRepo);
    }

    @BeforeEach
    void setUp() {
        service = new EnyManService(dictRepo, orgRepo, usrRepo, acctRepo, subtreeRepo,
                transactionTemplate, em, broadcastPublisher, moveQueue, new AuditBusBridge(noopRod()));
        EsqContextHolder.set(new EsqRequestContext(null, "req-test", UID, ROOT_PATH));
    }

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
    }

    private static IXRod noopRod() {
        return new XRodDisabled();
    }

    @Test
    @DisplayName("a role-only refusal answers 403 without reading the entity -- so it cannot confirm one exists")
    void permissionRefusalDoesNotTouchTheEntity() {
        assertThatThrownBy(() ->
            service.esquireCommandDelete(20, "100", "delete", null)
        ).isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(orgRepo, usrRepo, acctRepo, subtreeRepo);
    }

    @Test
    @DisplayName("an invisible move target answers 404, before any permission answer names it")
    void invisibleMoveTargetIsNotFoundRatherThanDenied() {
        when(orgRepo.detailOrg("200", ROOT_PATH)).thenReturn(null);

        assertThatThrownBy(() ->
            service.esquireCommandMove(20, "100", "200", List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a save naming a permitted kind over a row of another kind is refused, not written")
    void saveOverAnotherKindsRowIsRefused() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((org.springframework.transaction.support.TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        EsqUsrJpa row = new EsqUsrJpa();
        row.setId("8");
        row.setKind(32);                       // the row is an ADMIN user
        when(usrRepo.detailUsrForUpdate("8", ROOT_PATH)).thenReturn(row);

        assertThatThrownBy(() ->
            service.esquireCommandSave(34, "8", "save", new java.util.HashMap<>(), List.of(ROLE_ADMIN))
        ).isInstanceOf(ResourceNotFoundException.class);

        verify(usrRepo, never()).updateUsr(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}

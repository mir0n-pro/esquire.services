package pro.mir0n.esquire.backend.storage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.dto.access.EsqRole;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EsqRolesStorageTest {

    private JpaRolesRepository mockRepo;
    private EsqRolesStorage storage;

    // ---- helpers ----

    private EsqRoleJpa makeAdminRoleJpa(String id, String name) {
        EsqRoleJpa r = new EsqRoleJpa();
        r.setId(id);
        r.setName(name);
        r.setKind(EsqConstants.KIND_ADMIN_ROLE);
        return r;
    }

    private EsqPermissionJpa makePermissionJpa(String id, String type, String flags) {
        EsqPermissionJpa p = new EsqPermissionJpa();
        p.setId(id);
        p.setType(type);
        p.setKind(1);
        p.setName("Permission " + type);
        p.setFlags(flags);
        return p;
    }

    @BeforeEach
    void setUp() {
        mockRepo = Mockito.mock(JpaRolesRepository.class);
        storage = EsqRolesStorage.getInstance();

        EsqRoleJpa adminRole = makeAdminRoleJpa("1", "ADMIN");
        EsqPermissionJpa perm = makePermissionJpa("1", "admin", "Y,N,Y,N,N");

        when(mockRepo.roles()).thenReturn(List.of(adminRole));
        when(mockRepo.permissions("1")).thenReturn(List.of(perm));

        storage.init(mockRepo);
    }

    // ---- init ----

    @Test
    @DisplayName("init with roles and permissions → returns true")
    void init_populatesRolesAndPermissions() {
        boolean ret = storage.init(mockRepo);
        assertThat(ret).isTrue();
    }

    @Test
    @DisplayName("init with empty roles list → returns false")
    void init_emptyRoles_returnsFalse() {
        JpaRolesRepository emptyRepo = Mockito.mock(JpaRolesRepository.class);
        when(emptyRepo.roles()).thenReturn(new ArrayList<>());

        boolean ret = storage.init(emptyRepo);
        assertThat(ret).isFalse();
    }

    @Test
    @DisplayName("init with repository throwing → returns false (ERROR log is expected)")
    void init_repositoryThrows_returnsFalse() {
        // EsqRolesStorage.init() logs ERROR when the repository throws — that is the
        // behaviour under test here. Silence the logger for this test so the expected
        // stack trace does not pollute the test output and mislead the reader.
        Logger logger = (Logger) LoggerFactory.getLogger(EsqRolesStorage.class);
        Level saved = logger.getLevel();
        logger.setLevel(Level.OFF);
        try {
            JpaRolesRepository badRepo = Mockito.mock(JpaRolesRepository.class);
            when(badRepo.roles()).thenThrow(new RuntimeException("DB error"));

            boolean ret = storage.init(badRepo);
            assertThat(ret).isFalse();
        } finally {
            logger.setLevel(saved);
        }
    }

    // ---- findAdminPermissions ----

    @Test
    @DisplayName("findAdminPermissions with admin role name → returns permissions map")
    void findAdminPermissions_withAdminRole_returnsMap() {
        Map<Integer, EsqPermission> ret = storage.findAdminPermissions(List.of("ADMIN"));
        assertThat(ret).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("findAdminPermissions with non-admin role name → returns null")
    void findAdminPermissions_noAdminRole_returnsNull() {
        // Add a regular (non-admin) role
        EsqRoleJpa regular = new EsqRoleJpa();
        regular.setId("2");
        regular.setName("MANAGER");
        regular.setKind(1);

        JpaRolesRepository repo2 = Mockito.mock(JpaRolesRepository.class);
        when(repo2.roles()).thenReturn(List.of(regular));
        when(repo2.permissions("2")).thenReturn(new ArrayList<>());
        storage.init(repo2);

        Map<Integer, EsqPermission> ret = storage.findAdminPermissions(List.of("MANAGER"));
        assertThat(ret).isNull();
    }

    @Test
    @DisplayName("findAdminPermissions with null list → returns null")
    void findAdminPermissions_nullList_returnsNull() {
        Map<Integer, EsqPermission> ret = storage.findAdminPermissions(null);
        assertThat(ret).isNull();
    }

    // ---- fillPermissionsForRole ----

    @Test
    @DisplayName("fillPermissionsForRole with null list → creates new list and fills it")
    void fillPermissionsForRole_nullList_createsAndFills() {
        List<EsqPermission> ret = storage.fillPermissionsForRole("ADMIN", null);
        assertThat(ret).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("fillPermissionsForRole with existing list → appends to it")
    void fillPermissionsForRole_existingList_appends() {
        List<EsqPermission> existing = new ArrayList<>();
        EsqPermission existing1 = new EsqPermission();
        existing.add(existing1);

        List<EsqPermission> ret = storage.fillPermissionsForRole("ADMIN", existing);
        assertThat(ret).hasSizeGreaterThan(1);
        assertThat(ret.get(0)).isSameAs(existing1);
    }

    @Test
    @DisplayName("fillPermissionsForRole for unknown role → returns empty list")
    void fillPermissionsForRole_unknownRole_returnsEmptyList() {
        List<EsqPermission> ret = storage.fillPermissionsForRole("UNKNOWN_ROLE", null);
        assertThat(ret).isNotNull().isEmpty();
    }

    // ---- roles ----

    @Test
    @DisplayName("roles() returns all cached roles")
    void roles_returnsAllCachedRoles() {
        List<EsqRole> ret = storage.roles();
        assertThat(ret).isNotNull().isNotEmpty();
    }

    // ---- isAdminCmdPermitted (static) ----

    @Test
    @DisplayName("isAdminCmdPermitted with flag Y → returns true")
    void isAdminCmdPermitted_flagY_returnsTrue() {
        EsqPermission perm = new EsqPermission();
        perm.setFlags(List.of("Y", "Y", "Y", "Y", "Y"));
        boolean ret = EsqRolesStorage.isAdminCmdPermitted(perm, EsqRolesStorage.AdminCmd.UPDATE);
        assertThat(ret).isTrue();
    }

    @Test
    @DisplayName("isAdminCmdPermitted with flag N → returns false")
    void isAdminCmdPermitted_flagN_returnsFalse() {
        EsqPermission perm = new EsqPermission();
        perm.setFlags(List.of("N", "N", "N", "N", "N"));
        boolean ret = EsqRolesStorage.isAdminCmdPermitted(perm, EsqRolesStorage.AdminCmd.UPDATE);
        assertThat(ret).isFalse();
    }

    @Test
    @DisplayName("isAdminCmdPermitted with null permission → returns false")
    void isAdminCmdPermitted_nullPermission_returnsFalse() {
        boolean ret = EsqRolesStorage.isAdminCmdPermitted(null, EsqRolesStorage.AdminCmd.UPDATE);
        assertThat(ret).isFalse();
    }

    @Test
    @DisplayName("isAdminCmdPermitted with flags list shorter than cmd ordinal → returns false")
    void isAdminCmdPermitted_flagsListTooShort_returnsFalse() {
        EsqPermission perm = new EsqPermission();
        perm.setFlags(List.of("Y")); // only CREATE (0), UPDATE (1) is out of bounds
        boolean ret = EsqRolesStorage.isAdminCmdPermitted(perm, EsqRolesStorage.AdminCmd.UPDATE);
        assertThat(ret).isFalse();
    }
}

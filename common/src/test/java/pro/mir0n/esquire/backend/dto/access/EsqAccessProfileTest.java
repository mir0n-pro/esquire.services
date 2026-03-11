package pro.mir0n.esquire.backend.dto.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EsqAccessProfileTest {

    private EsqAccessProfileJpa jpa;

    // ---- helpers ----

    private EsqAccessProfileJpa makeJpa() {
        EsqAccessProfileJpa ret = new EsqAccessProfileJpa();
        ret.setId("42");
        ret.setKind(1);
        ret.setName("testUser");
        ret.setLoginId("tuser");
        ret.setEmail("tuser@example.com");
        ret.setPwdChangeForced("N");
        ret.setTfaMethod("G");
        return ret;
    }

    private EsqRoleJpa makeRoleJpa(String id, String name, int kind) {
        EsqRoleJpa ret = new EsqRoleJpa();
        ret.setId(id);
        ret.setName(name);
        ret.setKind(kind);
        return ret;
    }

    private EsqPermissionJpa makePermJpa(String id, String type, int kind, String flags) {
        EsqPermissionJpa ret = new EsqPermissionJpa();
        ret.setId(id);
        ret.setType(type);
        ret.setKind(kind);
        ret.setName("Permission " + type);
        ret.setFlags(flags);
        return ret;
    }

    private EsqPermission makePermDto(String id, String type, List<String> flags) {
        EsqPermission ret = new EsqPermission();
        ret.setId(id);
        ret.setType(type);
        ret.setFlags(flags);
        return ret;
    }

    @BeforeEach
    void setUp() {
        jpa = makeJpa();
    }

    // ---- fill() DTO overload ----

    @Test
    @DisplayName("fill() with all inputs present → sets all fields")
    void fill_allInputsPresent_setsAllFields() {
        EsqRoleJpa roleJpa = makeRoleJpa("10", "ADMIN", 980);
        EsqRole rolesAllEntry = new EsqRole();
        rolesAllEntry.setId("11");
        rolesAllEntry.setName("MANAGER");
        EsqPermission perm = makePermDto("1", "admin", List.of("Y", "N"));

        EsqAccessProfile ret = new EsqAccessProfile().fill(
                jpa,
                List.of(roleJpa),
                List.of(rolesAllEntry),
                List.of(perm)
        );

        assertThat(ret.getId()).isEqualTo("42");
        assertThat(ret.getKind()).isEqualTo(1);
        assertThat(ret.getName()).isEqualTo("testUser");
        assertThat(ret.getLoginId()).isEqualTo("tuser");
        assertThat(ret.getEmail()).isEqualTo("tuser@example.com");
        assertThat(ret.getPwdChangeForced()).isEqualTo("N");
        assertThat(ret.getTfaMethod()).isEqualTo("G");
        assertThat(ret.getRoles()).hasSize(1);
        assertThat(ret.getRolesAll()).hasSize(1);
        assertThat(ret.getPermissions()).isNotEmpty();
    }

    @Test
    @DisplayName("fill() with null roles → sets empty roles list")
    void fill_nullRoles_setsEmptyRolesList() {
        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, null, List.of(), List.of());
        assertThat(ret.getRoles()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("fill() with null rolesAll → sets empty rolesAll")
    void fill_nullRolesAll_setsEmptyRolesAll() {
        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, List.of(), null, List.of());
        assertThat(ret.getRolesAll()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("fill() with null permissions → sets empty permissions map")
    void fill_nullPermissions_setsEmptyPermissionsMap() {
        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, List.of(), List.of(), null);
        assertThat(ret.getPermissions()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("fill() two permissions with same type → grouped in same list")
    void fill_permissionsGroupedByType() {
        EsqPermission p1 = makePermDto("1", "admin", List.of("Y"));
        EsqPermission p2 = makePermDto("2", "admin", List.of("N"));

        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, List.of(), List.of(), List.of(p1, p2));

        assertThat(ret.getPermissions().get("admin")).hasSize(2);
    }

    @Test
    @DisplayName("fill() permission type is lowercased in the map key")
    void fill_permissionsTypeIsLowercased() {
        EsqPermission perm = makePermDto("1", "ADMIN", List.of("Y"));

        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, List.of(), List.of(), List.of(perm));

        assertThat(ret.getPermissions()).containsKey("admin");
        assertThat(ret.getPermissions()).doesNotContainKey("ADMIN");
    }

    // ---- fillJpa() ----

    @Test
    @DisplayName("fillJpa() with all inputs present → sets all fields")
    void fillJpa_allInputsPresent_setsAllFields() {
        EsqRoleJpa roleJpa = makeRoleJpa("10", "ADMIN", 980);
        EsqPermissionJpa permJpa = makePermJpa("1", "admin", 1, "Y,N,Y,N,N");

        EsqAccessProfile ret = new EsqAccessProfile().fillJpa(
                jpa,
                List.of(roleJpa),
                List.of(roleJpa),
                List.of(permJpa)
        );

        assertThat(ret.getId()).isEqualTo("42");
        assertThat(ret.getLoginId()).isEqualTo("tuser");
        assertThat(ret.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("fillJpa() converts rolesAll JPA to DTO")
    void fillJpa_convertedRolesAllToDto() {
        EsqRoleJpa roleJpa = makeRoleJpa("10", "MANAGER", 1);

        EsqAccessProfile ret = new EsqAccessProfile().fillJpa(
                jpa, List.of(), List.of(roleJpa), List.of());

        assertThat(ret.getRolesAll()).hasSize(1);
        assertThat(ret.getRolesAll().get(0).getName()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("fillJpa() converts permissions JPA to DTO")
    void fillJpa_convertedPermissionsToDto() {
        EsqPermissionJpa permJpa = makePermJpa("1", "admin", 1, "Y,N,Y,N,N");

        EsqAccessProfile ret = new EsqAccessProfile().fillJpa(
                jpa, List.of(), List.of(), List.of(permJpa));

        assertThat(ret.getPermissions()).containsKey("admin");
        assertThat(ret.getPermissions().get("admin")).hasSize(1);
        assertThat(ret.getPermissions().get("admin").get(0).getFlags()).contains("Y");
    }
}

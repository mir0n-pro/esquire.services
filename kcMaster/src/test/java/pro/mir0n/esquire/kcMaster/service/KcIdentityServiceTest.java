package pro.mir0n.esquire.kcMaster.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import jakarta.ws.rs.core.Response;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.kcMaster.messaging.ParkedPath;
import pro.mir0n.utils.concurrent.ExpiringCache;
import pro.mir0n.esquire.backend.identity.KcConnectionSettings;
import pro.mir0n.esquire.kcMaster.service.impl.KcIdentityService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KcIdentityServiceTest {

    @Mock private Keycloak keycloak;
    /** A value, not a collaborator -- built rather than mocked. */
    private final KcConnectionSettings kcConnection =
            new KcConnectionSettings("http://keycloak:8080/kc-auth", "esquire",
                    "esq-kcMaster", "secret", 5000, 10000);
    @SuppressWarnings("unchecked")
    @Mock private ExpiringCache<String, ParkedPath> pathBuffer;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;
    @Mock private RolesResource rolesResource;
    @Mock private RoleMappingResource roleMappingResource;
    @Mock private RoleScopeResource realmLevel;

    private KcIdentityService service;

    @BeforeEach
    void setUp() {
        service = new KcIdentityService(keycloak, kcConnection, pathBuffer);
        when(keycloak.realm("esquire")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    // -------------------------------------------------------------------------
    // updateEntityPath
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateEntityPath: user not found — no KC update")
    void updateEntityPath_userNotFound_skips() {
        when(usersResource.searchByAttributes(anyString(), any(Boolean.class)))
                .thenReturn(Collections.emptyList());

        service.updateEntityPath("uid-001", "1.10.uid-001", "cid", "rid");

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateEntityPath: path unchanged — no KC update")
    void updateEntityPath_pathUnchanged_skips() {
        UserRepresentation rep = userRepWithPath("1.10.uid-001");
        stubByAttributeSearch("uid-001", rep);

        service.updateEntityPath("uid-001", "1.10.uid-001", "cid", "rid");

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateEntityPath: path changed — update called with new path")
    void updateEntityPath_pathChanged_updatesAttribute() {
        UserRepresentation rep = userRepWithPath("1.10.uid-001");
        stubByAttributeSearch("uid-001", rep);

        service.updateEntityPath("uid-001", "1.20.uid-001", "cid", "rid");

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(captor.capture());
        List<String> newPath = captor.getValue().getAttributes().get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH);
        assertThat(newPath).containsExactly("1.20.uid-001");
    }

    @Test
    @DisplayName("updateEntityPath: user has no attributes yet — update called with new path")
    void updateEntityPath_noExistingAttributes_updatesAttribute() {
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-001");
        rep.setAttributes(null);
        stubByAttributeSearch("uid-001", rep);

        service.updateEntityPath("uid-001", "1.20.uid-001", "cid", "rid");

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(captor.capture());
        assertThat(captor.getValue().getAttributes())
                .containsEntry(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, List.of("1.20.uid-001"));
    }

    @Test
    @DisplayName("updateEntityPath: existing attributes preserved alongside updated path")
    void updateEntityPath_pathChanged_preservesOtherAttributes() {
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-001");
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, List.of("1.10.uid-001"));
        attrs.put(EsqConstants.JWT_CLAIM_ENTITY_ID, List.of("uid-001"));
        rep.setAttributes(attrs);
        stubByAttributeSearch("uid-001", rep);

        service.updateEntityPath("uid-001", "1.20.uid-001", "cid", "rid");

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(captor.capture());
        Map<String, List<String>> updated = captor.getValue().getAttributes();
        assertThat(updated).containsEntry(EsqConstants.JWT_CLAIM_ENTITY_ID, List.of("uid-001"));
        assertThat(updated).containsEntry(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, List.of("1.20.uid-001"));
    }

    // -------------------------------------------------------------------------
    // updateUserAuthState — change detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateUserAuthState: nothing changed — no KC update")
    void updateUserAuthState_nothingChanged_skipsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "alice@example.com", false, null,
                null, null, null, "cid", "rid");

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: email changed — update called")
    void updateUserAuthState_emailChanged_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "newalice@example.com", false, null,
                null, null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: email same as current — no KC update")
    void updateUserAuthState_emailUnchanged_skipsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "alice@example.com", false, null,
                null, null, null, "cid", "rid");

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: forcePasswordChange — update called")
    void updateUserAuthState_forcePasswordChange_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, null, true, null,
                null, null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: requireTotp — update called")
    void updateUserAuthState_requireTotp_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, null, false, Boolean.TRUE,
                null, null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: username changed — update called")
    void updateUserAuthState_usernameChanged_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", "alice2", null, false, null,
                null, null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: attributes provided — update called")
    void updateUserAuthState_attributesProvided_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        Map<String, List<String>> attrs = Map.of("custom-attr", List.of("val"));
        service.updateUserAuthState(
                "alice", null, null, false, null,
                null, null, attrs, "cid", "rid");

        verify(userResource).update(any());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private UserRepresentation userRepWithPath(String path) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-001");
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, List.of(path));
        rep.setAttributes(attrs);
        return rep;
    }

    private UserRepresentation userRepForUpdate(String username, String email) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-001");
        rep.setUsername(username);
        rep.setEmail(email);
        return rep;
    }

    // -------------------------------------------------------------------------
    // the realm role mapping -- SET, not merged
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("realm roles: a role Esquire did not name is removed -- KeyCloak's own default included")
    void realmRoles_roleNotNamedByEsquire_isRemoved() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);
        stubRoleMapping(role("default-roles-esquire"), role("SUPPORT"));

        service.updateUserAuthState(
                "alice", null, null, null, null,
                null, List.of("SUPPORT"), null, "cid", "rid");

        ArgumentCaptor<List<RoleRepresentation>> removed = ArgumentCaptor.forClass(List.class);
        verify(realmLevel).remove(removed.capture());
        assertThat(removed.getValue()).extracting(RoleRepresentation::getName)
                .containsExactly("default-roles-esquire");
        verify(realmLevel, never()).add(any());
    }

    @Test
    @DisplayName("realm roles: a role Esquire names and the user lacks is added")
    void realmRoles_roleEsquireNames_isAdded() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);
        stubRoleMapping(role("SUPPORT"));
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.list()).thenReturn(List.of(role("SUPPORT"), role("MANAGER")));

        service.updateUserAuthState(
                "alice", null, null, null, null,
                null, List.of("SUPPORT", "MANAGER"), null, "cid", "rid");

        ArgumentCaptor<List<RoleRepresentation>> added = ArgumentCaptor.forClass(List.class);
        verify(realmLevel).add(added.capture());
        assertThat(added.getValue()).extracting(RoleRepresentation::getName).containsExactly("MANAGER");
        verify(realmLevel, never()).remove(any());
    }

    @Test
    @DisplayName("realm roles: the mapping already matches -- KeyCloak is not touched")
    void realmRoles_unchanged_touchesNothing() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);
        stubRoleMapping(role("SUPPORT"));

        service.updateUserAuthState(
                "alice", null, null, null, null,
                null, List.of("SUPPORT"), null, "cid", "rid");

        verify(realmLevel, never()).add(any());
        verify(realmLevel, never()).remove(any());
    }

    @Test
    @DisplayName("realm roles: an empty Esquire set empties the mapping")
    void realmRoles_emptySet_emptiesTheMapping() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);
        stubRoleMapping(role("default-roles-esquire"), role("SUPPORT"));

        service.updateUserAuthState(
                "alice", null, null, null, null,
                null, Collections.emptyList(), null, "cid", "rid");

        ArgumentCaptor<List<RoleRepresentation>> removed = ArgumentCaptor.forClass(List.class);
        verify(realmLevel).remove(removed.capture());
        assertThat(removed.getValue()).extracting(RoleRepresentation::getName)
                .containsExactlyInAnyOrder("default-roles-esquire", "SUPPORT");
    }

    // -------------------------------------------------------------------------
    // createUser -- the create response is a resource, not a value
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUser: the create response is closed even when a later step throws")
    void createUser_laterStepThrows_stillClosesTheResponse() {
        Response created = mock(Response.class);
        when(created.getStatus()).thenReturn(201);
        when(created.getHeaderString("Location")).thenReturn("http://kc/admin/realms/esquire/users/u-1");
        when(usersResource.create(any())).thenReturn(created);
        // The role sync is the first thing to touch KeyCloak after the create -- let it fail there.
        when(usersResource.get("u-1")).thenThrow(new RuntimeException("KeyCloak is not answering"));

        assertThatThrownBy(() ->
                service.createUser("alice", "alice@example.com", null, true, false, false,
                        List.of("SUPPORT"), null, "cid", "rid"))
                .isInstanceOf(RuntimeException.class);

        verify(created).close();
    }

    @Test
    @DisplayName("createUser: a create that does not answer 201 closes the response too")
    void createUser_createRefused_closesTheResponse() {
        Response refused = mock(Response.class);
        when(refused.getStatus()).thenReturn(409);
        when(usersResource.create(any())).thenReturn(refused);

        assertThatThrownBy(() ->
                service.createUser("alice", "alice@example.com", null, true, false, false,
                        null, null, "cid", "rid"))
                .isInstanceOf(RuntimeException.class);

        verify(refused).close();
    }

    private static RoleRepresentation role(String name) {
        RoleRepresentation ret = new RoleRepresentation();
        ret.setName(name);
        return ret;
    }

    private void stubRoleMapping(RoleRepresentation... current) {
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(realmLevel);
        when(realmLevel.listAll()).thenReturn(List.of(current));
    }

    private void stubByAttributeSearch(String entityId, UserRepresentation rep) {
        when(usersResource.searchByAttributes(
                EsqConstants.JWT_CLAIM_ENTITY_ID + ":" + entityId, true))
                .thenReturn(List.of(rep));
        when(usersResource.get(rep.getId())).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(rep);
    }

    private void stubLoginSearch(String loginId, UserRepresentation rep) {
        when(usersResource.search(loginId, true)).thenReturn(List.of(rep));
        when(usersResource.get(rep.getId())).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(rep);
    }
}

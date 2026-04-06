package pro.mir0n.esquire.kcMaster.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;
import pro.mir0n.esquire.kcMaster.service.impl.KcIdentityService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KcIdentityServiceTest {

    @Mock private Keycloak keycloak;
    @Mock private KeycloakConfig kcConfig;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;

    private KcIdentityService service;

    @BeforeEach
    void setUp() {
        service = new KcIdentityService(keycloak, kcConfig);
        when(kcConfig.getRealm()).thenReturn("esquire");
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
                "alice", null, "alice@example.com",  // same email
                null, null, false, null, null,
                null, null, "cid", "rid"
        );

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: email changed — update called")
    void updateUserAuthState_emailChanged_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "newalice@example.com",
                null, null, false, null, null,
                null, null, "cid", "rid"
        );

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: email same as current — no KC update")
    void updateUserAuthState_emailUnchanged_skipsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "alice@example.com",
                null, null, false, null, null,
                null, null, "cid", "rid"
        );

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: forcePasswordChange — update called")
    void updateUserAuthState_forcePasswordChange_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, null,
                null, null, true, null, null,
                null, null, "cid", "rid"
        );

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: requireTotp — update called")
    void updateUserAuthState_requireTotp_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, null,
                null, null, false, Boolean.TRUE, null,
                null, null, "cid", "rid"
        );

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: username changed — update called")
    void updateUserAuthState_usernameChanged_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", "alice2", null,
                null, null, false, null, null,
                null, null, "cid", "rid"
        );

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: attributes provided — update called")
    void updateUserAuthState_attributesProvided_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        Map<String, List<String>> attrs = Map.of("custom-attr", List.of("val"));
        service.updateUserAuthState(
                "alice", null, null,
                null, null, false, null, null,
                null, attrs, "cid", "rid"
        );

        verify(userResource).update(any());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private UserRepresentation userRepWithPath(String path) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-001");
        rep.setAttributes(new HashMap<>(Map.of(
                EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, List.of(path)
        )));
        return rep;
    }

    private UserRepresentation userRepForUpdate(String username, String email) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-001");
        rep.setUsername(username);
        rep.setEmail(email);
        return rep;
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

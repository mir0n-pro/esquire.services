package pro.mir0n.esquire.kcMaster.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import jakarta.ws.rs.core.Response;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.mir0n.esquire.backend.identity.KcConnectionSettings;
import pro.mir0n.esquire.kcMaster.service.impl.KcIdentityService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;

    private KcIdentityService service;

    @BeforeEach
    void setUp() {
        service = new KcIdentityService(keycloak, kcConnection);
        when(keycloak.realm("esquire")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    // -------------------------------------------------------------------------
    // updateUserAuthState -- change detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateUserAuthState: nothing changed -- no KC update")
    void updateUserAuthState_nothingChanged_skipsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "alice@example.com", false, null,
                null, null, "cid", "rid");

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: email changed -- update called")
    void updateUserAuthState_emailChanged_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "newalice@example.com", false, null,
                null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: email same as current -- no KC update")
    void updateUserAuthState_emailUnchanged_skipsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, "alice@example.com", false, null,
                null, null, "cid", "rid");

        verify(userResource, never()).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: forcePasswordChange -- update called")
    void updateUserAuthState_forcePasswordChange_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, null, true, null,
                null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: requireTotp -- update called")
    void updateUserAuthState_requireTotp_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", null, null, false, Boolean.TRUE,
                null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: username changed -- update called")
    void updateUserAuthState_usernameChanged_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        service.updateUserAuthState(
                "alice", "alice2", null, false, null,
                null, null, "cid", "rid");

        verify(userResource).update(any());
    }

    @Test
    @DisplayName("updateUserAuthState: attributes provided -- update called")
    void updateUserAuthState_attributesProvided_callsUpdate() {
        UserRepresentation rep = userRepForUpdate("alice", "alice@example.com");
        stubLoginSearch("alice", rep);

        Map<String, List<String>> attrs = Map.of("custom-attr", List.of("val"));
        service.updateUserAuthState(
                "alice", null, null, false, null,
                null, attrs, "cid", "rid");

        verify(userResource).update(any());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private UserRepresentation userRepForUpdate(String username, String email) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId("kc-001");
        rep.setUsername(username);
        rep.setEmail(email);
        return rep;
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
        // The password is the first thing to touch KeyCloak after the create -- let it fail there.
        when(usersResource.get("u-1")).thenThrow(new RuntimeException("KeyCloak is not answering"));

        assertThatThrownBy(() ->
                service.createUser("alice", "alice@example.com", "changeit", true, false, false,
                        null, "cid", "rid"))
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
                        null, "cid", "rid"))
                .isInstanceOf(RuntimeException.class);

        verify(refused).close();
    }

    private void stubLoginSearch(String loginId, UserRepresentation rep) {
        when(usersResource.search(loginId, true)).thenReturn(List.of(rep));
        when(usersResource.get(rep.getId())).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(rep);
    }
}

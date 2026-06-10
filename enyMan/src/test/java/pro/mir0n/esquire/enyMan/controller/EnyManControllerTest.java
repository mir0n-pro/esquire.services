package pro.mir0n.esquire.enyMan.controller;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.enyMan.service.IEnyManService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnyManControllerTest {

    @Mock
    private IEnyManService service;

    @Mock
    private Claims claims;

    private EnyManController controller;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(10, "org", "Org", "orgs", "Test org",
                true, false, false, "", false, false, "", null, null, null, false)
        );
    }

    @BeforeEach
    void setUp() {
        controller = new EnyManController(service);
    }

    // uid / rootPath are no longer extracted in the controller -- they ride the unified per-request
    // context captured upstream (JwtAuthenticationFilter). The controller now extracts only roles
    // from realm_access and delegates without uid/rootPath.

    // ---- esquireDictionary ----

    @Test
    @DisplayName("esquireDictionary: delegates to service, returns 200 with body")
    void esquireDictionary_delegatesToService_returnsOk() {
        EsqEntityLayer layer = new EsqEntityLayer(1, "General", null);
        when(service.esquireDictionary(5)).thenReturn(List.of(layer));

        ResponseEntity<List<EsqEntityLayer>> response = controller.esquireDictionary(5, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        verify(service).esquireDictionary(5);
    }

    // ---- esquireCommand ----

    @Test
    @DisplayName("esquireCommand: delegates to service, returns 200")
    void esquireCommand_delegates_returnsOk() {
        EsqEntity mockEntity = mock(EsqEntity.class);
        when(service.esquireCommand(1, "10", "details")).thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommand(1, "10", "details", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommand(1, "10", "details");
    }

    // ---- esquireCommandSave: roles extracted from realm_access ----

    @Test
    @DisplayName("esquireCommandSave: extracts roles from realm_access, delegates with roles")
    void esquireCommandSave_extractsRolesFromRealmAccess_delegatesWithRoles() {
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        EsqEntity mockEntity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "ACME");
        when(service.esquireCommandSave(1, "10", "save", fields, List.of("admin")))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandSave(1, "10", "save", fields, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandSave(1, "10", "save", fields, List.of("admin"));
    }

    // ---- esquireCommandSave: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandSave: null realm_access passes null roles to service")
    void esquireCommandSave_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        Map<String, Object> fields = Map.of();
        when(service.esquireCommandSave(1, "10", "save", fields, null))
            .thenReturn(null);

        controller.esquireCommandSave(1, "10", "save", fields, claims);

        verify(service).esquireCommandSave(1, "10", "save", fields, null);
    }

    // ---- esquireCommandNew ----

    @Test
    @DisplayName("esquireCommandNew: extracts roles and parentId, delegates, returns 200")
    void esquireCommandNew_extractsRolesAndDelegates_returnsOk() {
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        EsqEntity mockEntity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "New Org");
        when(service.esquireCommandNew(10, "100", "new", fields, List.of("admin")))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandNew(10, "100", "new", fields, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommandNew(10, "100", "new", fields, List.of("admin"));
    }

    // ---- esquireCommandNew: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandNew: null realm_access passes null roles to service")
    void esquireCommandNew_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        Map<String, Object> fields = Map.of();
        when(service.esquireCommandNew(10, "100", "new", fields, null)).thenReturn(null);

        controller.esquireCommandNew(10, "100", "new", fields, claims);

        verify(service).esquireCommandNew(10, "100", "new", fields, null);
    }

    // ---- esquireCommandDelete ----

    @Test
    @DisplayName("esquireCommandDelete: extracts roles, delegates, returns 200")
    void esquireCommandDelete_extractsRolesAndDelegates_returnsOk() {
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        doNothing().when(service).esquireCommandDelete(10, "100", "delete", List.of("admin"));

        ResponseEntity<Void> response = controller.esquireCommandDelete(10, "100", "delete", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandDelete(10, "100", "delete", List.of("admin"));
    }

    // ---- esquireCommandDelete: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandDelete: null realm_access passes null roles to service")
    void esquireCommandDelete_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        doNothing().when(service).esquireCommandDelete(10, "100", "delete", null);

        controller.esquireCommandDelete(10, "100", "delete", claims);

        verify(service).esquireCommandDelete(10, "100", "delete", null);
    }

    // ---- esquireCommandMove ----
    // v1.2.6 Goal 3: /esq-move is async-ack -- handler submits to the move queue and returns
    // 202 Accepted (was 200 OK). Body stays Void.

    @Test
    @DisplayName("esquireCommandMove: extracts roles, delegates, returns 202 Accepted")
    void esquireCommandMove_extractsRolesAndDelegates_returnsAccepted() {
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        when(service.esquireCommandMove(10, "100", "200", List.of("admin"))).thenReturn(null);

        ResponseEntity<Void> response = controller.esquireCommandMove(10, "100", "200", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).esquireCommandMove(10, "100", "200", List.of("admin"));
    }

    @Test
    @DisplayName("esquireCommandMove: null realm_access passes null roles to service")
    void esquireCommandMove_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        when(service.esquireCommandMove(10, "100", "200", null)).thenReturn(List.of());

        controller.esquireCommandMove(10, "100", "200", claims);

        verify(service).esquireCommandMove(10, "100", "200", null);
    }

    // ---- esquireKinds ----

    @Test
    @DisplayName("esquireKinds: returns 200 with non-empty kinds list from storage")
    void esquireKinds_returnsOkWithKindsList() {
        ResponseEntity<List<EsqObjectKind>> response = controller.esquireKinds(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }
}

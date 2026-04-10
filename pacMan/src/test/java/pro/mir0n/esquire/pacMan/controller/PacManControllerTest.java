package pro.mir0n.esquire.pacMan.controller;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.pacMan.acct.service.AcctTransactionService;
import pro.mir0n.esquire.pacMan.service.IPacManService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacManControllerTest {

    @Mock
    private IPacManService service;

    @Mock
    private AcctTransactionService acctTransactionService;

    @Mock
    private Claims claims;

    private PacManController controller;

    @BeforeEach
    void setUp() {
        controller = new PacManController(service, acctTransactionService);
    }

    // ---- esquireCommand ----

    @Test
    @DisplayName("esquireCommand: extracts claims and delegates, returns 200")
    void esquireCommand_extractsClaimsAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqEntity mockEntity = mock(EsqEntity.class);
        when(service.esquireCommand(50, "10", "details", "1.2.3", "5")).thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommand(50, "10", "details", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommand(50, "10", "details", "1.2.3", "5");
    }

    // ---- esquireCommandSave: roles extracted from realm_access ----

    @Test
    @DisplayName("esquireCommandSave: extracts roles from realm_access, delegates with roles")
    void esquireCommandSave_extractsRolesFromRealmAccess_delegatesWithRoles() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        EsqEntity mockEntity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "ACME");
        when(service.esquireCommandSave(50, "10", "save", fields, "1.2.3", "5", List.of("admin")))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandSave(50, "10", "save", fields, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandSave(50, "10", "save", fields, "1.2.3", "5", List.of("admin"));
    }

    // ---- esquireCommandSave: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandSave: null realm_access passes null roles to service")
    void esquireCommandSave_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        Map<String, Object> fields = Map.of();
        when(service.esquireCommandSave(50, "10", "save", fields, "1.2.3", "5", null))
            .thenReturn(null);

        controller.esquireCommandSave(50, "10", "save", fields, claims);

        verify(service).esquireCommandSave(50, "10", "save", fields, "1.2.3", "5", null);
    }

    // ---- esquireCommandNew ----

    @Test
    @DisplayName("esquireCommandNew: extracts roles and parentId, delegates, returns 200")
    void esquireCommandNew_extractsRolesAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        EsqEntity mockEntity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "ACC-1");
        when(service.esquireCommandNew(50, "10", "new", fields, "1.2.3", "5", List.of("admin")))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandNew(50, "10", "new", fields, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommandNew(50, "10", "new", fields, "1.2.3", "5", List.of("admin"));
    }

    // ---- esquireCommandNew: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandNew: null realm_access passes null roles to service")
    void esquireCommandNew_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        Map<String, Object> fields = Map.of();
        when(service.esquireCommandNew(50, "10", "new", fields, "1.2.3", "5", null)).thenReturn(null);

        controller.esquireCommandNew(50, "10", "new", fields, claims);

        verify(service).esquireCommandNew(50, "10", "new", fields, "1.2.3", "5", null);
    }

    // ---- esquireCommandDelete ----

    @Test
    @DisplayName("esquireCommandDelete: extracts roles, delegates, returns 200")
    void esquireCommandDelete_extractsRolesAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        doNothing().when(service).esquireCommandDelete(50, "10", "delete", "1.2.3", "5", List.of("admin"));

        ResponseEntity<Void> response = controller.esquireCommandDelete(50, "10", "delete", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandDelete(50, "10", "delete", "1.2.3", "5", List.of("admin"));
    }

    // ---- esquireCommandDelete: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandDelete: null realm_access passes null roles to service")
    void esquireCommandDelete_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        doNothing().when(service).esquireCommandDelete(50, "10", "delete", "1.2.3", "5", null);

        controller.esquireCommandDelete(50, "10", "delete", claims);

        verify(service).esquireCommandDelete(50, "10", "delete", "1.2.3", "5", null);
    }
}

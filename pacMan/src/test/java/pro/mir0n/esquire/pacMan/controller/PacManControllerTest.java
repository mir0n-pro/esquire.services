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

    // uid / rootPath are no longer extracted in the controller -- they ride the unified per-request
    // context (JwtAuthenticationFilter). The controller now reads only roles from realm_access.

    // ---- esquireCommand ----

    @Test
    @DisplayName("esquireCommand: delegates to service, returns 200")
    void esquireCommand_delegates_returnsOk() {
        EsqEntity mockEntity = mock(EsqEntity.class);
        when(service.esquireCommand(50, "10", "details")).thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommand(50, "10", "details", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommand(50, "10", "details");
    }

    // ---- esquireCommandSave: roles extracted from realm_access ----

    @Test
    @DisplayName("esquireCommandSave: extracts roles from realm_access, delegates with roles")
    void esquireCommandSave_extractsRolesFromRealmAccess_delegatesWithRoles() {
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        EsqEntity mockEntity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "ACME");
        when(service.esquireCommandSave(50, "10", "save", fields, List.of("admin")))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandSave(50, "10", "save", fields, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandSave(50, "10", "save", fields, List.of("admin"));
    }

    // ---- esquireCommandSave: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandSave: null realm_access passes null roles to service")
    void esquireCommandSave_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        Map<String, Object> fields = Map.of();
        when(service.esquireCommandSave(50, "10", "save", fields, null))
            .thenReturn(null);

        controller.esquireCommandSave(50, "10", "save", fields, claims);

        verify(service).esquireCommandSave(50, "10", "save", fields, null);
    }

    // ---- esquireCommandDelete ----

    @Test
    @DisplayName("esquireCommandDelete: extracts roles, delegates, returns 200")
    void esquireCommandDelete_extractsRolesAndDelegates_returnsOk() {
        Map<String, Object> realmAccess = Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin"));
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(realmAccess);
        when(service.esquireCommandDelete(50, "10", "delete", List.of("admin"))).thenReturn(4L);

        ResponseEntity<Void> response = controller.esquireCommandDelete(50, "10", "delete", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandDelete(50, "10", "delete", List.of("admin"));
    }

    // ---- esquireCommandDelete: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandDelete: null realm_access passes null roles to service")
    void esquireCommandDelete_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        when(service.esquireCommandDelete(50, "10", "delete", null)).thenReturn(4L);

        controller.esquireCommandDelete(50, "10", "delete", claims);

        verify(service).esquireCommandDelete(50, "10", "delete", null);
    }
}

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
    @DisplayName("esquireCommand: extracts claims and delegates, returns 200")
    void esquireCommand_extractsClaimsAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqEntity mockEntity = mock(EsqEntity.class);
        when(service.esquireCommand(1, "10", "details", "1.2.3", "5")).thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommand(1, "10", "details", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommand(1, "10", "details", "1.2.3", "5");
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
        when(service.esquireCommandSave(1, "10", "save", fields, "1.2.3", "5", List.of("admin")))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandSave(1, "10", "save", fields, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandSave(1, "10", "save", fields, "1.2.3", "5", List.of("admin"));
    }

    // ---- esquireCommandSave: null realm_access → null roles ----

    @Test
    @DisplayName("esquireCommandSave: null realm_access passes null roles to service")
    void esquireCommandSave_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        Map<String, Object> fields = Map.of();
        when(service.esquireCommandSave(1, "10", "save", fields, "1.2.3", "5", null))
            .thenReturn(null);

        controller.esquireCommandSave(1, "10", "save", fields, claims);

        verify(service).esquireCommandSave(1, "10", "save", fields, "1.2.3", "5", null);
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

package pro.mir0n.esquire.keySmith.controller;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.keySmith.service.IKeySmithService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeySmithControllerTest {

    @Mock
    private IKeySmithService service;

    @Mock
    private Claims claims;

    private KeySmithController controller;

    @BeforeEach
    void setUp() {
        controller = new KeySmithController(service);
    }

    // ---- esquireCommand: delegation ----

    @Test
    @DisplayName("esquireCommand: extracts claims, delegates to service, returns 200")
    void esquireCommand_extractsClaimsAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqAccessProfile profile = mock(EsqAccessProfile.class);
        when(service.esquireKey("10", "1.2.3", "5")).thenReturn(profile);

        ResponseEntity<EsqAccessProfile> response = controller.esquireCommand("10", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireKey("10", "1.2.3", "5");
    }

    // ---- esquireKeySave: roles extracted from realm_access ----

    @Test
    @DisplayName("esquireKeySave: extracts roles from realm_access, delegates with roles")
    void esquireKeySave_extractsRolesFromRealmAccess_delegatesWithRoles() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class))
                .thenReturn(Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin")));
        EsqAccessProfile profile = mock(EsqAccessProfile.class);
        Map<String, Object> fields = Map.of();
        when(service.esquireKeySave("10", fields, "1.2.3", "5", List.of("admin"))).thenReturn(profile);

        ResponseEntity<EsqAccessProfile> response = controller.esquireKeySave("10", fields, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireKeySave("10", fields, "1.2.3", "5", List.of("admin"));
    }

    // ---- esquireKeySave: null realm_access passes null roles ----

    @Test
    @DisplayName("esquireKeySave: null realm_access → passes null roles to service")
    void esquireKeySave_nullRealmAccess_passesNullRolesToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class)).thenReturn(null);
        Map<String, Object> fields = Map.of();
        when(service.esquireKeySave("10", fields, "1.2.3", "5", null)).thenReturn(null);

        controller.esquireKeySave("10", fields, claims);

        verify(service).esquireKeySave("10", fields, "1.2.3", "5", null);
    }

}

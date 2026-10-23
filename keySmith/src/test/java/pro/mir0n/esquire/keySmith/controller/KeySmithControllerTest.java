package pro.mir0n.esquire.keySmith.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import pro.mir0n.esquire.keySmith.service.IKeySmithService;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeySmithControllerTest {

    @Mock
    private IKeySmithService service;


    private KeySmithController controller;

    @BeforeEach
    void setUp() {
        controller = new KeySmithController(service);
    }

    // uid / rootPath / roles are not read in the controller -- they ride the unified per-request context
    // (JwtClaimsExtractionFilter).

    // ---- esquireCommand: delegation ----

    @Test
    @DisplayName("esquireCommand: delegates to service, returns 200")
    void esquireCommand_delegates_returnsOk() {
        EsqAccessProfile profile = mock(EsqAccessProfile.class);
        when(service.esquireKey("10")).thenReturn(profile);

        ResponseEntity<EsqAccessProfile> response = controller.esquireCommand("10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireKey("10");
    }

    // ---- esquireKeySave: delegation ----

    @Test
    @DisplayName("esquireKeySave: delegates to service, returns 200")
    void esquireKeySave_delegates_returnsOk() {
        EsqAccessProfile profile = mock(EsqAccessProfile.class);
        Map<String, Object> fields = Map.of();
        when(service.esquireKeySave("10", fields)).thenReturn(profile);

        ResponseEntity<EsqAccessProfile> response = controller.esquireKeySave("10", fields);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireKeySave("10", fields);
    }



}

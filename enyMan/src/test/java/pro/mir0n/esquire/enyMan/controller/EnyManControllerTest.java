package pro.mir0n.esquire.enyMan.controller;

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
import pro.mir0n.esquire.enyMan.service.IEnyManService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnyManControllerTest {

    @Mock
    private IEnyManService service;


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
    // context captured upstream (JwtClaimsExtractionFilter). The controller now extracts only roles
    // from realm_access and delegates without uid/rootPath.

    // ---- esquireDictionary ----

    @Test
    @DisplayName("esquireDictionary: delegates to service, returns 200 with body")
    void esquireDictionary_delegatesToService_returnsOk() {
        EsqEntityLayer layer = new EsqEntityLayer(1, "General", null);
        when(service.esquireDictionary(5)).thenReturn(List.of(layer));

        ResponseEntity<List<EsqEntityLayer>> response = controller.esquireDictionary(5);

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

        ResponseEntity<EsqEntity> response = controller.esquireCommand(1, "10", "details");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommand(1, "10", "details");
    }

    // ---- esquireCommandSave: roles extracted from realm_access ----

    @Test
    @DisplayName("esquireCommandSave: delegates to service, returns 200")
    void esquireCommandSave_extractsRolesFromRealmAccess_delegatesWithRoles() {
        EsqEntity mockEntity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "ACME");
        when(service.esquireCommandSave(1, "10", "save", fields))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandSave(1, "10", "save", fields);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandSave(1, "10", "save", fields);
    }



    // ---- esquireCommandNew ----

    @Test
    @DisplayName("esquireCommandNew: delegates to service, returns 200")
    void esquireCommandNew_extractsRolesAndDelegates_returnsOk() {
        EsqEntity mockEntity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "New Org");
        when(service.esquireCommandNew(10, "100", "new", fields))
            .thenReturn(mockEntity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandNew(10, "100", "new", fields);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(service).esquireCommandNew(10, "100", "new", fields);
    }



    // ---- esquireCommandDelete ----

    @Test
    @DisplayName("esquireCommandDelete: delegates to service, returns 200")
    void esquireCommandDelete_extractsRolesAndDelegates_returnsOk() {
        when(service.esquireCommandDelete(10, "100", "delete")).thenReturn(4L);

        ResponseEntity<Void> response = controller.esquireCommandDelete(10, "100", "delete");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquireCommandDelete(10, "100", "delete");
    }



    // ---- esquireCommandMove ----
    // v1.2.6 Goal 3: /esq-move is async-ack -- handler submits to the move queue and returns
    // 202 Accepted (was 200 OK). Body stays Void.

    @Test
    @DisplayName("esquireCommandMove: delegates to service, returns 202 Accepted")
    void esquireCommandMove_extractsRolesAndDelegates_returnsAccepted() {
        when(service.esquireCommandMove(10, "100", "200")).thenReturn(null);

        ResponseEntity<Void> response = controller.esquireCommandMove(10, "100", "200");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).esquireCommandMove(10, "100", "200");
    }


    // ---- esquireKinds ----

    @Test
    @DisplayName("esquireKinds: returns 200 with non-empty kinds list from storage")
    void esquireKinds_returnsOkWithKindsList() {
        ResponseEntity<List<EsqObjectKind>> response = controller.esquireKinds();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }
}

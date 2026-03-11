package pro.mir0n.esquire.bizTree.controller;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BizTreeControllerTest {

    @Mock
    private IBizTreeService service;

    @Mock
    private Claims claims;

    private BizTreeController controller;

    @BeforeEach
    void setUp() {
        controller = new BizTreeController(service);
    }

    // ---- helper ----

    private EsqTreeNode makeNode(String id, String name, Integer kind) {
        EsqTreeNode node = new EsqTreeNode();
        node.setId(id);
        node.setName(name);
        node.setKind(kind);
        return node;
    }

    // ---- esquire: id provided ----

    @Test
    @DisplayName("esquire: extracts claims, delegates to service, returns 200")
    void esquire_extractsClaimsAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqTreeNode node = makeNode("10", "ACME", 1);
        when(service.esquire("10", 0, 0, "1.2.3", "5")).thenReturn(List.of(node));

        ResponseEntity<List<EsqTreeNode>> response = controller.esquire("10", null, null, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getId()).isEqualTo("10");
    }

    // ---- esquire: id null ----

    @Test
    @DisplayName("esquire: id null → passes null to service")
    void esquire_noId_passesNullToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(service.esquire(null, 0, 0, "1.2.3", "5")).thenReturn(List.of());

        ResponseEntity<List<EsqTreeNode>> response = controller.esquire(null, null, null, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).esquire(null, 0, 0, "1.2.3", "5");
    }

    // ---- esquireEntityNode: id provided ----

    @Test
    @DisplayName("esquireEntityNode: id provided → service called with id, returns 200")
    void esquireEntityNode_withId_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqTreeNode node = makeNode("10", "ACME", 1);
        when(service.esquireEntityNode(1, "42", null, "1.2.3", "5")).thenReturn(node);

        ResponseEntity<EsqTreeNode> response = controller.esquireEntityNode(1, "42", null, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("10");
        verify(service).esquireEntityNode(1, "42", null, "1.2.3", "5");
    }

    // ---- esquireEntityNode: name provided ----

    @Test
    @DisplayName("esquireEntityNode: name provided → service called with name, returns 200")
    void esquireEntityNode_withName_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqTreeNode node = makeNode("10", "ACME", 1);
        when(service.esquireEntityNode(1, null, "ACME", "1.2.3", "5")).thenReturn(node);

        ResponseEntity<EsqTreeNode> response = controller.esquireEntityNode(1, null, "ACME", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("ACME");
        verify(service).esquireEntityNode(1, null, "ACME", "1.2.3", "5");
    }

    // ---- esquirePath ----

    @Test
    @DisplayName("esquirePath: service returns path list → 200 with path")
    void esquirePath_returnsOkWithPath() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2");
        when(service.esquirePath("5", "1.2")).thenReturn(List.of("1", "2", "3"));

        ResponseEntity<List<String>> response = controller.esquirePath("5", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly("1", "2", "3");
        verify(service).esquirePath("5", "1.2");
    }
}

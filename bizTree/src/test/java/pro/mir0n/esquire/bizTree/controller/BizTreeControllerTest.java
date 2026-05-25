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
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BizTreeControllerTest {

    @Mock
    private IBizTreeDirector director;

    @Mock
    private Claims claims;

    private BizTreeController controller;

    @BeforeEach
    void setUp() {
        controller = new BizTreeController(director);
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
    @DisplayName("esquire: extracts claims, delegates to director, returns 200")
    void esquire_extractsClaimsAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqTreeNode node = makeNode("10", "ACME", 1);
        when(director.esquire("10", 0, 0, "1.2.3", "5")).thenReturn(List.of(node));

        ResponseEntity<List<EsqTreeNode>> response = controller.esquire("10", null, null, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getId()).isEqualTo("10");
    }

    // ---- esquire: id null ----

    @Test
    @DisplayName("esquire: id null → passes null to director")
    void esquire_noId_passesNullToService() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        when(director.esquire(null, 0, 0, "1.2.3", "5")).thenReturn(List.of());

        ResponseEntity<List<EsqTreeNode>> response = controller.esquire(null, null, null, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(director).esquire(null, 0, 0, "1.2.3", "5");
    }

    // ---- esquireEntityNode: id provided ----

    @Test
    @DisplayName("esquireEntityNode: id provided → director called with id, returns 200")
    void esquireEntityNode_withId_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqTreeNode node = makeNode("10", "ACME", 1);
        when(director.esquireEntityNode(1, "42", null, "1.2.3", "5")).thenReturn(node);

        ResponseEntity<EsqTreeNode> response = controller.esquireEntityNode(1, "42", null, claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("10");
        verify(director).esquireEntityNode(1, "42", null, "1.2.3", "5");
    }

    // ---- esquireEntityNode: name provided ----

    @Test
    @DisplayName("esquireEntityNode: name provided → director called with name, returns 200")
    void esquireEntityNode_withName_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2.3");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("5");
        EsqTreeNode node = makeNode("10", "ACME", 1);
        when(director.esquireEntityNode(1, null, "ACME", "1.2.3", "5")).thenReturn(node);

        ResponseEntity<EsqTreeNode> response = controller.esquireEntityNode(1, null, "ACME", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("ACME");
        verify(director).esquireEntityNode(1, null, "ACME", "1.2.3", "5");
    }

    // ---- esquirePath ----

    @Test
    @DisplayName("esquirePath: director returns path list → 200 with path")
    void esquirePath_returnsOkWithPath() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.2");
        when(director.esquirePath("5", "1.2")).thenReturn(List.of("1", "2", "3"));

        ResponseEntity<List<String>> response = controller.esquirePath("5", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly("1", "2", "3");
        verify(director).esquirePath("5", "1.2");
    }

    // ---- esquireSubtree / GET /esq-tree ----

    @Test
    @DisplayName("esquireSubtree: extracts claims, delegates to director.esquireSubtree, returns 200")
    void esquireSubtree_extractsClaimsAndDelegates_returnsOk() {
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class)).thenReturn("1.14.");
        when(claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class)).thenReturn("15");
        EsqTreeNode root  = makeNode("10", "Office",    20);
        EsqTreeNode child = makeNode("11", "Test User", 34);
        when(director.esquireSubtree("10", "1.14.", "15")).thenReturn(List.of(root, child));

        ResponseEntity<List<EsqTreeNode>> response = controller.esquireSubtree("10", claims);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getId()).isEqualTo("10");
        assertThat(response.getBody().get(1).getId()).isEqualTo("11");
        verify(director).esquireSubtree("10", "1.14.", "15");
    }
}

package pro.mir0n.esquire.bizTree.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.service.impl.BizTreeService;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BizTreeServiceTest {

    @Mock
    private IBizTreeCacheRepository repo;

    @InjectMocks
    private BizTreeService service;

    // ---- helper ----

    private EsqTreeNodeJpa makeNode(String id, String parentId, String name, Integer kind, String path) {
        return new EsqTreeNodeJpa(id, parentId, null, name, kind, null, 0, 1, null, path);
    }

    // ---- esquire: id branch ----

    @Test
    @DisplayName("esquire with id → calls findNodes, returns mapped list")
    void esquire_withId_callsFindNodes_returnsMappedList() {
        EsqTreeNodeJpa node = makeNode("10", "2", "ACME", 1, "1.2.3");
        when(repo.findNodes("10", 2, "1.2.3")).thenReturn(List.of(node));

        List<EsqTreeNode> result = service.esquire("10", 0, 0, "1.2.3", "99");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("10");
        assertThat(result.get(0).getName()).isEqualTo("ACME");
        assertThat(result.get(0).getKind()).isEqualTo(1);
        verify(repo).findNodes("10", 2, "1.2.3");
        verify(repo, never()).findRoot(any(), anyInt(), any());
    }

    // ---- esquire: no-id branch ----

    @Test
    @DisplayName("esquire with no id → calls findRoot with last path element")
    void esquire_noId_callsFindRoot_withLastPathElement() {
        EsqTreeNodeJpa node = makeNode("3", "2", "Root", 1, "1.2.3");
        when(repo.findRoot("3", 2, "1.2.3")).thenReturn(List.of(node));

        List<EsqTreeNode> result = service.esquire(null, 0, 0, "1.2.3", "99");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("3");
        verify(repo).findRoot("3", 2, "1.2.3");
        verify(repo, never()).findNodes(any(), anyInt(), any());
    }

    // ---- esquire: null result → exception ----

    @Test
    @DisplayName("esquire: repo returns null → ResourceNotFoundException")
    void esquire_repoReturnsNull_throwsResourceNotFoundException() {
        when(repo.findNodes("10", 2, "1.2.3")).thenReturn(null);

        assertThatThrownBy(() -> service.esquire("10", 0, 0, "1.2.3", "99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquire: rootLevel admin (uid != last path element) ----

    @Test
    @DisplayName("esquire: admin uid (not last path element) → rootLevel = path.size - 1")
    void esquire_adminUser_rootLevelIsPathSizeMinusOne() {
        // rootPath "1.2.3" → size=3, uid="99" ≠ "3" → rootLevel=2
        EsqTreeNodeJpa node = makeNode("10", "2", "Node", 1, "1.2.3");
        when(repo.findNodes("10", 2, "1.2.3")).thenReturn(List.of(node));

        service.esquire("10", 0, 0, "1.2.3", "99");

        verify(repo).findNodes("10", 2, "1.2.3");
    }

    // ---- esquire: rootLevel non-admin (uid == last path element) ----

    @Test
    @DisplayName("esquire: non-admin uid (equals last path element) → rootLevel = path.size")
    void esquire_nonAdminUser_rootLevelIsPathSize() {
        // rootPath "1.2.3" → size=3, uid="3" == "3" → rootLevel=3
        EsqTreeNodeJpa node = makeNode("10", "2", "Node", 1, "1.2.3");
        when(repo.findNodes("10", 3, "1.2.3")).thenReturn(List.of(node));

        service.esquire("10", 0, 0, "1.2.3", "3");

        verify(repo).findNodes("10", 3, "1.2.3");
    }

    // ---- esquire: rootLevel single-element path → always 0 ----

    @Test
    @DisplayName("esquire: single-element rootPath → rootLevel = 0")
    void esquire_singleElementPath_rootLevelZero() {
        // rootPath "1" → size=1 → rootLevel stays 0 → id=null → findRoot("1", 0, "1")
        EsqTreeNodeJpa node = makeNode("1", null, "SystemRoot", 0, "1");
        when(repo.findRoot("1", 0, "1")).thenReturn(List.of(node));

        service.esquire(null, 0, 0, "1", "1");

        verify(repo).findRoot("1", 0, "1");
    }

    // ---- esquireEntityNode: id branch ----

    @Test
    @DisplayName("esquireEntityNode with id → calls findByEntityId")
    void esquireEntityNode_withId_callsFindByEntityId() {
        EsqTreeNodeJpa node = makeNode("10", "2", "ACME", 1, "1.2.3");
        when(repo.findByEntityId("42", 2, "1.2.3")).thenReturn(node);

        EsqTreeNode result = service.esquireEntityNode(1, "42", null, "1.2.3", "99");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("10");
        verify(repo).findByEntityId("42", 2, "1.2.3");
        verify(repo, never()).findByNameKind(any(), any(), anyInt(), any());
    }

    // ---- esquireEntityNode: name+kind branch ----

    @Test
    @DisplayName("esquireEntityNode with name and kind → calls findByNameKind")
    void esquireEntityNode_withNameAndKind_callsFindByNameKind() {
        EsqTreeNodeJpa node = makeNode("10", "2", "ACME", 1, "1.2.3");
        when(repo.findByNameKind("ACME", 1, 2, "1.2.3")).thenReturn(node);

        EsqTreeNode result = service.esquireEntityNode(1, null, "ACME", "1.2.3", "99");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("ACME");
        verify(repo).findByNameKind("ACME", 1, 2, "1.2.3");
        verify(repo, never()).findByEntityId(any(), anyInt(), any());
    }

    // ---- esquireEntityNode: null result → exception ----

    @Test
    @DisplayName("esquireEntityNode: repo returns null → ResourceNotFoundException")
    void esquireEntityNode_nodeNull_throwsResourceNotFoundException() {
        when(repo.findByEntityId("42", 2, "1.2.3")).thenReturn(null);

        assertThatThrownBy(() -> service.esquireEntityNode(1, "42", null, "1.2.3", "99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireEntityNode: neither id nor name → exception ----

    @Test
    @DisplayName("esquireEntityNode: neither id nor name → ResourceNotFoundException, no repo calls")
    void esquireEntityNode_neitherIdNorName_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> service.esquireEntityNode(1, null, null, "1.2.3", "99"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repo, never()).findByEntityId(any(), anyInt(), any());
        verify(repo, never()).findByNameKind(any(), any(), anyInt(), any());
    }

    // ---- esquirePath: single-element rootPath → full path returned ----

    @Test
    @DisplayName("esquirePath: rootPath has 1 element → full node path returned")
    void esquirePath_singleElementRootPath_returnsFullPath() {
        when(repo.findPathScoped("5", "1")).thenReturn("1.2.3.4");

        List<String> result = service.esquirePath("5", "1");

        assertThat(result).containsExactly("1", "2", "3", "4");
    }

    // ---- esquirePath: rootPath larger than node path → empty ----

    @Test
    @DisplayName("esquirePath: rootPath larger than node path → empty list")
    void esquirePath_rootPathLargerThanNodePath_returnsEmpty() {
        when(repo.findPathScoped("5", "1.2.3")).thenReturn("1.2");

        List<String> result = service.esquirePath("5", "1.2.3");

        assertThat(result).isEmpty();
    }

    // ---- esquirePath: rootPath shorter than node path → trimmed subList ----

    @Test
    @DisplayName("esquirePath: rootPath shorter than node path → trimmed user-visible slice")
    void esquirePath_subPathExtracted() {
        when(repo.findPathScoped("5", "1.2")).thenReturn("1.2.3.4");

        List<String> result = service.esquirePath("5", "1.2");

        assertThat(result).containsExactly("2", "3", "4");
    }

    // ---- esquirePath: findPath returns null → empty ----

    @Test
    @DisplayName("esquirePath: findPath returns null → empty list")
    void esquirePath_nullNodePath_returnsEmpty() {
        when(repo.findPathScoped("5", "1.2")).thenReturn(null);

        List<String> result = service.esquirePath("5", "1.2");

        assertThat(result).isEmpty();
    }

    // ---- esquirePath: a node outside the caller's subtree ----

    @Test
    @DisplayName("esquirePath: outside the caller's subtree -> empty, because the READ is scoped")
    void esquirePath_outsideSubtree_returnsEmpty() {
        // The trim is a length trim, so a foreign path of the same depth would come back well-formed.
        // What keeps it out is the predicate on the read: no row, no path.
        when(repo.findPathScoped("10", "1.14.")).thenReturn(null);

        List<String> result = service.esquirePath("10", "1.14.");

        assertThat(result).isEmpty();
        verify(repo, never()).findPath(anyString());
    }

    // ---- esquireSubtree: happy path ----

    @Test
    @DisplayName("esquireSubtree: delegates to findSubtree with rootLevel + rootPath; returns mapped list")
    void esquireSubtree_callsFindSubtree_returnsMappedList() {
        EsqTreeNodeJpa root  = makeNode("10", "2", "Office",  20, "1.2.10.");
        EsqTreeNodeJpa child = makeNode("11", "10", "User",   34, "1.2.10.11.");
        // rootPath "1.2.3" → size=3, uid="99" ≠ "3" → rootLevel=2 (admin branch)
        when(repo.findSubtree("10", 2, "1.2.3")).thenReturn(List.of(root, child));

        List<EsqTreeNode> result = service.esquireSubtree("10", "1.2.3", "99");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("10");
        assertThat(result.get(1).getId()).isEqualTo("11");
        verify(repo).findSubtree("10", 2, "1.2.3");
    }

    // ---- esquireSubtree: null result → exception ----

    @Test
    @DisplayName("esquireSubtree: repo returns null → ResourceNotFoundException")
    void esquireSubtree_repoReturnsNull_throwsResourceNotFoundException() {
        when(repo.findSubtree("10", 2, "1.2.3")).thenReturn(null);

        assertThatThrownBy(() -> service.esquireSubtree("10", "1.2.3", "99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireSubtree: rootPath scoping reaches findSubtree ----

    @Test
    @DisplayName("esquireSubtree: passes rootPath through to findSubtree (security scope)")
    void esquireSubtree_rootPathPassedThrough() {
        // Test Driver scope: rootPath="1.14." (pathArray drops trailing empty -> size=2),
        // uid="15" not matching last path element "14" -> admin branch -> rootLevel = 2-1 = 1.
        when(repo.findSubtree("10", 1, "1.14.")).thenReturn(List.of());

        service.esquireSubtree("10", "1.14.", "15");

        verify(repo).findSubtree("10", 1, "1.14.");
    }
}

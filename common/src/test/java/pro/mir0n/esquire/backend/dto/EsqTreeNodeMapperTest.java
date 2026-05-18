package pro.mir0n.esquire.backend.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EsqTreeNodeMapper#stripVirtualSegments(String)} --
 * the pure-function transformation that derives biztree-side entityPath
 * from tree_path by stripping virtual-folder segments (those containing
 * '~'). Result is the diff axis the hauberk CompareTrees scenario uses
 * to compare biztree cache against the natural-FK subtree.
 */
class EsqTreeNodeMapperTest {

    @Test
    @DisplayName("stripVirtualSegments: null input -> null")
    void stripVirtualSegments_nullInput_returnsNull() {
        assertThat(EsqTreeNodeMapper.stripVirtualSegments(null)).isNull();
    }

    @Test
    @DisplayName("stripVirtualSegments: empty input -> empty")
    void stripVirtualSegments_emptyInput_returnsEmpty() {
        assertThat(EsqTreeNodeMapper.stripVirtualSegments("")).isEmpty();
    }

    @Test
    @DisplayName("stripVirtualSegments: no virtual segments -> path returned unchanged (trailing dot)")
    void stripVirtualSegments_noVirtualSegments_returnsUnchanged() {
        assertThat(EsqTreeNodeMapper.stripVirtualSegments("1.5.12."))
                .isEqualTo("1.5.12.");
    }

    @Test
    @DisplayName("stripVirtualSegments: one virtual segment in middle is removed")
    void stripVirtualSegments_oneVirtualSegmentMiddle_isRemoved() {
        assertThat(EsqTreeNodeMapper.stripVirtualSegments("1.5.5~8.12."))
                .isEqualTo("1.5.12.");
    }

    @Test
    @DisplayName("stripVirtualSegments: multiple virtual segments are all removed")
    void stripVirtualSegments_multipleVirtualSegments_allRemoved() {
        assertThat(EsqTreeNodeMapper.stripVirtualSegments("1.5~2.5.5~8.12."))
                .isEqualTo("1.5.12.");
    }

    @Test
    @DisplayName("stripVirtualSegments: leading and trailing virtual segments are removed")
    void stripVirtualSegments_leadingAndTrailing_removed() {
        assertThat(EsqTreeNodeMapper.stripVirtualSegments("1~a.1.5.12.12~b."))
                .isEqualTo("1.5.12.");
    }

    @Test
    @DisplayName("stripVirtualSegments: every segment virtual -> empty result")
    void stripVirtualSegments_allVirtual_returnsEmpty() {
        assertThat(EsqTreeNodeMapper.stripVirtualSegments("1~a.5~b.12~c."))
                .isEmpty();
    }
}

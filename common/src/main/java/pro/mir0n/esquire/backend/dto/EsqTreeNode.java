/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/26/2025 mir0n  refine API doc
 * 02/03/2026 mir0n  extends EsqThing
 * 02/13/2026 mir0n removed treeFlags
 * 05/14/2026 mir0n  entityPath String field added (raw ep_path / tree_entity_path; the diff axis for
 *                   the hauberk CompareTrees scenario between biztree cache and natural-FK subtree)
 */

package pro.mir0n.esquire.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@Schema(
        name = "EsqTreeNode",
        description = "Holds Tree Node information"
)

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class EsqTreeNode extends EsqThing {

    @Schema(
            description = "Parent Node ID", example = ""
    )
    private String parentId;

    @Schema(
            description = "Shortcut Node ID", example = "3"
    )
    private String linkId;

    @Schema(
            description = "Entity ID", example = "1"
    )
    private Long entityId;

    @Schema(
            description = "Status Code: 0: normal, 1: deleted or closed 2: locked", example = "1"
    )
    private Integer statusCode;

    @Schema(
            description = "More remaining ", example = "false for now always"
    )
    @Builder.Default
    private Boolean moreRemaining = false;

    @Schema(
            description = "Tree level ", example = "1"
    )
    @Builder.Default
    private Integer level = 0;

    @Schema(
            description = "Node path", example = "[1,2,3]"
    )
    private java.util.List<String> path;

    @Schema(
            description = "Object description", example = "Entity description"
    )
    private String desc;

    @Schema(
            description = "Raw entity-path string (esq_entity_path.ep_path on the "
                        + "DB side; ESQ_TREE.tree_entity_path on the biztree side). "
                        + "Both should be identical when the cache is in sync. "
                        + "Distinct from path (which on biztree is the navigation "
                        + "tree path traversing folder nodes).",
            example = "1.5.12."
    )
    private String entityPath;

}

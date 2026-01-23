/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/26/2025 mir0n  refine API doc
 */

package pro.mir0n.esquire.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(
        name = "EsqTreeNode",
        description = "Holds Tree Node information"
)

public class EsqTreeNode {
    @Schema(
            description = "Node ID", example = ""
    )
    private String id;

    @Schema(
            description = "Parent Node ID", example = ""
    )
    private String parentId;

    @Schema(
            description = "Shortcut Node ID", example = "3"
    )
    private String linkId;

    @Schema(
            description = "Node name", example = "System"
    )
    private String name;

    @Schema(
            description = "Type of node", example = "1 for system"
    )
    private Integer kind;

    @Schema(
            description = "Entity ID", example = "1"
    )
    private Long entityId;

    @Schema(
            description = "Tree flags", example = "BTb"
    )
    private String treeFlags;

    @Schema(
            description = "Status Code: 0: normal, 1: deleted or closed 2: locked", example = "1"
    )
    private Integer statusCode;

    @Schema(
            description = "More remaining ", example = "false for now always"
    )
    private Boolean moreRemaining = false;

    @Schema(
            description = "Tree level ", example = "1"
    )
    private Integer level = 0;

    @Schema(
            description = "Node description", example = "1"
    )

    private String desc;
    @Schema(
            description = "Node path", example = "[1,2,3]"
    )
    private java.util.List<String> path;

}

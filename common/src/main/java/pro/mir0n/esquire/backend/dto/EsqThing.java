/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@Schema(
        name = "EsqEntity",
        description = "Holds generic object information"
)
@SuperBuilder
@NoArgsConstructor
public class EsqThing {

    @Schema(
            description = "Object ID", example = ""
    )
    private String id;

    @Schema(
            description = "Type of entity", example = "1 for system"
    )
    private Integer kind;

    @Schema(
            description = "Object name", example = "System"
    )
    private String name;

}


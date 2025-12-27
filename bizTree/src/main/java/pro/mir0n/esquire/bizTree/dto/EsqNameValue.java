/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/27/2024 mir0n correct name
 */

package pro.mir0n.esquire.bizTree.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(
        name = "EsqNameValue",
        description = "Name - Value pair"
)

public class EsqNameValue {
    @Schema(
            description = "Parameter name", example = "DB_VERSION"
    )
    private String name;

    @Schema(
            description = "Parameter value", example = "2.0.0"
    )
    private String value;

    public  EsqNameValue (String name, String value ) {
        this.name = name;
        this.value = value;
    }


}


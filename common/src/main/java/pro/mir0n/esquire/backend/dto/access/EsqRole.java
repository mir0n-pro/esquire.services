/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/04/2026 mir0n adminFlg added
 */

package pro.mir0n.esquire.backend.dto.access;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

@Data
@Schema(
        name = "EsqRole",
        description = "Role identification"
)
@SuperBuilder
public class EsqRole {
    public EsqRole() {}

    @Schema(
            description = "Role ID", example = "100"
    )
    private int id;

    @Schema(
            description = "Role Name", example = "TREE"
    )
    private String name;

    @Schema(
            description = "Role Admin Flag", example = "Y"
    )
    private String adminFlg;

    public EsqRole fill (EsqRoleJpa jpa) {
        setId(jpa.getId());
        setName(jpa.getName());
        setAdminFlg(jpa.getAdminFlg());
        return this;
    }

}


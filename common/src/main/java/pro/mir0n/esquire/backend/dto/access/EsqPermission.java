/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/03/2026 mir0n  extends EsqThing
 */

package pro.mir0n.esquire.backend.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqThing;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;

import java.util.List;

@Data
@Schema(
        name = "EsqAccessProfile",
        description = "Holds user entitlements"
)
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EsqPermission extends EsqThing {
    @Schema(
            description = "Type of permission", example = "Admin rights"
    )
    private String type;

    @Schema(
            description = "Permission flags", example = "set of Y/N flag, create, update, delete, security, accounting"
    )
    private List<String> flags;


    public EsqPermission fill (EsqPermissionJpa jpa) {
        setId(String.valueOf(jpa.getId()));
        setKind(jpa.getKind());
        setName(jpa.getName());
        setType(jpa.getType());
        setFlags(List.of(jpa.getFlags().split(",")));
        return this;
    }

}


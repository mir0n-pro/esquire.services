/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;

import java.util.List;

@Data
@Schema(
        name = "EsqAccessProfile",
        description = "Holds user entitlements"
)
@SuperBuilder
@JsonIgnoreProperties({"type"})
public class EsqPermission {
    public EsqPermission() {}

    @Schema(
            description = "Permission ID", example = "112"
    )
    private int id;

    @Schema(
            description = "Kind  of user were access defined", example = "12 for client"
    )
    private Integer entityKind;

    @Schema(
            description = "Type of permission", example = "Admin rights"
    )
    private String type;

    @Schema(
            description = "Name of permission", example = "Client"
    )
    private String name;

    @Schema(
            description = "Permission flags", example = "set of Y/N flag, create, update, delete, security, accounting"
    )
    private List<String> flags;


    public EsqPermission fill (EsqPermissionJpa jpa) {
        setId(jpa.getId());
        setType(jpa.getType());
        setEntityKind(jpa.getEntityKind());
        setName(jpa.getName());
        setFlags(List.of(jpa.getFlags().split(",")));
        return this;
    }

}


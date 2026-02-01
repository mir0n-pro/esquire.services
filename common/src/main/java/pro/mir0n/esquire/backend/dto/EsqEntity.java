/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/26/2025 mir0n  refine API doc
 * 12/27/2025 mir0n  use @SuperBuilder
 *                   add actual fields isntead of absract set/get methods
 * 02/01/2026 mir0n  made class concrete
 */

package pro.mir0n.esquire.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;

import java.util.List;

@Data
@Schema(
        name = "EsqEntity",
        description = "Holds generic entity information; abstract object, actual entity implementations: " +
                "EsqAcct, EsqOrg, EsqUsr have their specific fields including custom ones"
)
@SuperBuilder
public class EsqEntity {
    public EsqEntity() {}

    @Schema(
            description = "Entity ID", example = ""
    )
    private String id;

    @Schema(
            description = "Type of entity", example = "1 for system"
    )
    private Integer kind;

    @Schema(
            description = "Entity name", example = "System"
    )
    private String name;

    @Schema(
            description = "Entity description", example = "Entity description"
    )
    private String desc;

    protected void fillDetails(EsqEntityJpa jpa) {};
    protected void fillCustom(List<EsqNameValueJpa> custom) {};
    protected void fillChildren(List<EsqTreeNodeJpa> children) {};

    public void fill (EsqEntityJpa jpa, List<EsqNameValueJpa> custom, List<EsqTreeNodeJpa> children) {
        setId(jpa.getId());
        setKind(jpa.getKind());
        setName (jpa.getName());
        setDesc(jpa.getDesc());
        fillDetails(jpa);
        if (custom != null) {
            fillCustom(custom);
        }
        if (custom != null) {
            fillChildren(children);
        }
    }

}


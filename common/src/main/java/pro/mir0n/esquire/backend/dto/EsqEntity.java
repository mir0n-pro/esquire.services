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
 * 02/03/2026 mir0n  extends EsqThing
 *                   back to abstract
 * 02/13/2026 mir0n  use EsqEntityJpa for children
 * 02/28/2026 mir0n  fillPerson/fillAddress/fillBizAddress abstract methods added
 *                   fill() extended with person, address, address2 subentity params
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
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class EsqEntity extends EsqThing{

    protected abstract void fillDetails(EsqEntityJpa jpa);
    protected abstract void fillCustom(List<EsqNameValueJpa> custom);
    protected abstract void fillChildren(List<EsqEntityJpa> children);
    protected abstract void fillPerson(EsqEntityJpa person);
    protected abstract void fillAddress(EsqEntityJpa address);
    protected abstract void fillBizAddress(EsqEntityJpa address);

    @Schema(
            description = "Object description", example = "Entity description"
    )
    private String desc;

    public void fill (EsqEntityJpa jpa,
            List<EsqNameValueJpa> custom,
            List<EsqEntityJpa> children,
            EsqEntityJpa person,
            EsqEntityJpa address,
            EsqEntityJpa address2
    ) {

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
        if (person != null) {
            fillPerson(person);
        }
        if (address != null) {
            fillAddress(address);
        }
        if (address2 != null) {
            fillBizAddress(address2);
        }
    }

}


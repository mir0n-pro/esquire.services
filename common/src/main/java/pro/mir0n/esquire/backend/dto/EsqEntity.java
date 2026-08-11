/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
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
 * 03/26/2026 mir0n  parentId field added
 * 08/11/2026 mir0n  v1.2.12 -- changeNo field added, carried from the JPA row; @JsonIgnore +
 *                   @Schema(hidden) keep it out of the REST contract
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;

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

    @Schema(
            description = "Parent entity ID"
    )
    private String parentId;

    /**
     * The row's CHANGE NUMBER, carried from the JPA row so the service layer can put it on the entity
     * broadcast. INTERNAL ONLY -- {@code @JsonIgnore} plus {@code hidden} keeps it out of every request,
     * every response and the OpenAPI model, so the REST contract is unchanged (decision 4: the number lives
     * in the database, the message header and the audit log, never on the API).
     */
    @JsonIgnore
    @Schema(hidden = true)
    private Long changeNo;

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
        setParentId(jpa.getParentId());
        setChangeNo(jpa.getChangeNo());
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


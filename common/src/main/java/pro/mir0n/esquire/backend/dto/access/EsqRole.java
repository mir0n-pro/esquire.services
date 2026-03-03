/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/04/2026 mir0n adminFlg added
 * 03/03/2026 mir0n  extends EsqThing; adminFlg removed; id/name/kind inherited from EsqThing
 */

package pro.mir0n.esquire.backend.dto.access;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqThing;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

@Data
@Schema(
        name = "EsqRole",
        description = "Role identification"
)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class EsqRole extends EsqThing {
    public EsqRole() {super();}

    public EsqRole fill (EsqRoleJpa jpa) {
        setId(jpa.getId());
        setName(jpa.getName());
        setKind(jpa.getKind());
        return this;
    }

}


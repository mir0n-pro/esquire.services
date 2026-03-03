/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/04/2026 mir0n  entityKind renamed with [just] kind
 * 03/03/2026 mir0n  extends EsqEntityJpa; individual id field removed — inherited from base
 */

package pro.mir0n.esquire.backend.jpa.access;

import jakarta.persistence.*;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqPermissionJpa extends EsqEntityJpa {
    @Id
    private String type;
    private Integer kind;
    private String name;
    private String flags;
}

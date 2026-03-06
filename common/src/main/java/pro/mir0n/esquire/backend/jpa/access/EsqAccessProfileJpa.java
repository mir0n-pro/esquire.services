/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/04/2026 mir0n name added
 * 03/06/2026 mir0n extends EsqEntityJpa (id/kind/name inherited from base)
 */

package pro.mir0n.esquire.backend.jpa.access;

import jakarta.persistence.*;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqAccessProfileJpa extends EsqEntityJpa {
    private String loginId;
    private String email;
    private String pwdChangeForced;
    private String tfaMethod;
}

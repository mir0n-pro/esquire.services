/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.jpa.access;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqAccessProfileJpa {
    @Id
    private String id;
    private Integer kind;
    private String loginId;
    private String email;
    private String pwdChangeForced;
    private String tfaMethod;
}

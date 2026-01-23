/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.enyMan.jpa;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqEntityJpa {
    @Id
    private String id;
    private Integer kind;
    private String name;
    private String desc;
}

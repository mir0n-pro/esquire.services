/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/13/2026 mir0n @MappedSuperclass
 * 03/26/2026 mir0n  parentId field added — consolidated from EsqAcctJpa/EsqOrgJpa/EsqUsrJpa
 */

package pro.mir0n.esquire.backend.jpa;

import jakarta.persistence.*;
import lombok.*;

@MappedSuperclass
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqEntityJpa {
    @Id
    private String id;
    private Integer kind;
    private String name;
    private String desc;
    private String parentId;
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  parentId, path fields added
 * 03/26/2026 mir0n  parentId removed — consolidated to EsqEntityJpa
 */

package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqAcctJpa extends EsqEntityJpa {
    private String ccy;
    private Double balance;
    private String status;
    private String path;
}


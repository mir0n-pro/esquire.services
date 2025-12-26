/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqNameValueJpa {
    @Id
    private String name;
    private String value;
};




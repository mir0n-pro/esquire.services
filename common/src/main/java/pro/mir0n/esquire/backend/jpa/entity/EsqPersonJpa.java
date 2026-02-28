/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: person JPA entity with all person fields
 *                   getName(): null-safe middleName (was NPE root cause of HTTP 500 on GET /esq-cmd)
 */

package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

@Entity
@Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
public class EsqPersonJpa extends EsqEntityJpa {
    private String firstName;
    private String middleName;
    private String lastName;
    private String title;
    private String dob;
    private String birthPlace;
    private String sex;
    private String taxId;
    private String citizenship;
    private String marStatus;
    private String personIdType;
    private String personIdNumber;
    private String email;
    private String phone;
    private String phone2;

    public String getName() {
        return firstName +
                ((middleName != null && !middleName.isBlank()) ? " " + middleName.charAt(0) + "." : "") +
                " " + lastName;
    }

}


/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: person JPA entity with all person fields
 *                   getName(): null-safe middleName (was NPE root cause of HTTP 500 on GET /esq-cmd)
 * 06/05/2026 mir0n  fillMap() override (IMappable): emits the person data fields (firstName..phone2);
 *                   no super call (person_log has no name/desc/parentId) for the x-Rod person audit body
 */

package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

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

    // person_log keys on usr_pk + kind (header); no name/desc/parentId columns -> no super.fillMap.
    @Override
    public void fillMap(Map<String, Object> body) {
        body.put("firstName", firstName);
        body.put("middleName", middleName);
        body.put("lastName", lastName);
        body.put("title", title);
        body.put("dob", dob);
        body.put("birthPlace", birthPlace);
        body.put("sex", sex);
        body.put("taxId", taxId);
        body.put("citizenship", citizenship);
        body.put("marStatus", marStatus);
        body.put("personIdType", personIdType);
        body.put("personIdNumber", personIdNumber);
        body.put("email", email);
        body.put("phone", phone);
        body.put("phone2", phone2);
    }

}


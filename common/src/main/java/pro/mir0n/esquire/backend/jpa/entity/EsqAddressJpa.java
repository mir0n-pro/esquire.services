/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: address JPA entity
 * 06/05/2026 mir0n  fillMap() override (IMappable): emits addr/addr2/city/company/country/department/
 *                   fax/postalCode/province/title/url (+ super desc) for the x-Rod address audit body
 */

package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

@Entity
@Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
public class EsqAddressJpa extends EsqEntityJpa {
    private String addr;
    private String addr2;
    private String city;
    private String company;
    private String country;
    private String department;
    private String fax;
    private String postalCode;
    private String province;
    private String title;
    private String url;

    @Override
    public void fillMap(Map<String, Object> body) {
        super.fillMap(body);          // desc (-> adl_desc); name/parentId unused by esq_address_log
        body.put("addr", addr);
        body.put("addr2", addr2);
        body.put("city", city);
        body.put("company", company);
        body.put("country", country);
        body.put("department", department);
        body.put("fax", fax);
        body.put("postalCode", postalCode);
        body.put("province", province);
        body.put("title", title);
        body.put("url", url);
    }
}


/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  parentId, path fields added
 * 03/26/2026 mir0n  parentId removed — consolidated to EsqEntityJpa
 * 06/05/2026 mir0n  fillMap() override (IMappable): emits fullName on top of super (name/desc/parentId)
 *                   for the x-Rod org audit body
 */

package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqOrgJpa extends EsqEntityJpa {
    private String fullName;
    private String path;

    @Override
    public void fillMap(Map<String, Object> body) {
        super.fillMap(body);          // name, desc, parentId (-> org_org_pk)
        body.put("fullName", fullName);
    }
}


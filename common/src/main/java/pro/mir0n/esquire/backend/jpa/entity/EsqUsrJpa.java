/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/14/2026 mir0n  email field added
 * 03/20/2026 mir0n  parentId, path fields added
 * 03/26/2026 mir0n  parentId removed — consolidated to EsqEntityJpa
 * 03/28/2026 mir0n  connectFlg field added — pre-delete check for active auth connection
 * 06/05/2026 mir0n  fillMap() override (IMappable): emits registration/deleted on top of super
 *                   (name/desc/parentId); auth fields excluded (keySmith domain) for the x-Rod user audit body
 */

package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

@Entity
@Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
public class EsqUsrJpa extends EsqEntityJpa {
    private String loginId;
    private String connectFlg;
    private String registration;
    private String deleted;
    private String email;
    private String path;

    @Override
    public void fillMap(Map<String, Object> body) {
        super.fillMap(body);          // name, desc, parentId (-> usr_org_pk)
        body.put("registration", registration);
        body.put("deleted", deleted);
        // loginId / email / connectFlg are auth fields -- not audited here (keySmith's domain)
    }
}


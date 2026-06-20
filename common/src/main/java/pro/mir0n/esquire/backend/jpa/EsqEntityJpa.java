/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/13/2026 mir0n @MappedSuperclass
 * 03/26/2026 mir0n  parentId field added — consolidated from EsqAcctJpa/EsqOrgJpa/EsqUsrJpa
 * 06/05/2026 mir0n  implements IMappable: fillMap() emits the common data fields (name/desc/parentId) by
 *                   property name; concrete entities override to add their own. id/kind are identity ->
 *                   carried in the x-Rod header (entityId/kind), not the body.
 * 06/12/2026 mir0n  systemFlg field added (system-entity anti-delete flag); not emitted by fillMap()
 */

package pro.mir0n.esquire.backend.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.util.Map;

@MappedSuperclass
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqEntityJpa implements IMappable {
    @Id
    private String id;
    private Integer kind;
    private String name;
    private String desc;
    private String parentId;
    private String systemFlg;

    @Override
    public void fillMap(Map<String, Object> body) {
        body.put("name", name);
        body.put("desc", desc);
        body.put("parentId", parentId);
    }
}

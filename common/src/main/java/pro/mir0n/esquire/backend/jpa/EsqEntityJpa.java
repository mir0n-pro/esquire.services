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
 * 08/11/2026 mir0n  v1.2.12 -- changeNo and pathChangeNo fields added (two separate counters) plus
 *                   bumpChangeNo(), the one place a change number moves; neither is emitted by fillMap()
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
    // The (sub)entity change number. Read under the row's FOR UPDATE lock, raised in the service, and
    // written back by the same statement -- see the v1.2.12 sprint notes. Deliberately NOT in fillMap():
    // it rides the x-Rod HEADER (ChangeNo, tag 50015), not the body, because the body is emptied on DELETE.
    private Long changeNo;
    // The PATH row's change number (ESQ_ENTITY_PATH.EP_CHANGE_NO), a SEPARATE counter from the one above.
    // Selected only by the cache-load reads (findAllForTree), which join the path table anyway -- the
    // bizTree cache has to start from the true path number, not from null, or the night-watch would see a
    // freshly loaded shadow differ from a serving monad on every sweep. Not in fillMap() either.
    private Long pathChangeNo;

    /**
     * Bump the change number and return the new value -- THE one place a change number moves.
     * <p>
     * Two callers, one rule:
     * <ul>
     *   <li><b>a write</b> -- called only once the caller has decided the row actually changes, so the number
     *       never moves on a no-op save. The row was read under its FOR UPDATE lock, so this
     *       read-bump-write needs no further guard.</li>
     *   <li><b>a delete</b> -- the row is going away, and its delete record takes the number AFTER its last
     *       live state. Reusing the old number would put the delete record and the last update record on the
     *       same dedup key. The database trigger writes {@code OLD.<x>_change_no + 1} for the same reason,
     *       which is why one bump here makes both audit paths agree.</li>
     * </ul>
     * Bump ONCE per delete, on the row object, and let every reporter read it off that object -- the
     * broadcast and the audit event must not each compute it.
     * <p>
     * Null-safe: the column is NOT NULL DEFAULT 1, so null can only mean the reading query did not select it;
     * starting from 0 keeps the first written value at 1 rather than going backwards.
     */
    public Long bumpChangeNo() {
        changeNo = (changeNo == null ? 0L : changeNo) + 1L;
        return changeNo;
    }

    @Override
    public void fillMap(Map<String, Object> body) {
        body.put("name", name);
        body.put("desc", desc);
        body.put("parentId", parentId);
    }
}

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: SQL result-set row for EsqSubtreeRepository -- pk, kind, name, parentPk,
 *                   ep_path, level columns mapped from the recursive CTE result for /esq-cmd-tree
 */
package pro.mir0n.esquire.enyMan.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/*
 * Synthetic JPA entity used only as a result-set-mapping target for the
 * subtree native queries. Not mapped to any table. Service layer projects
 * these rows into EsqTreeNode for the public API.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class EsqSubtreeRow {

    @Id
    private String id;

    private Long entityId;

    private Integer kind;

    private String name;

    private String desc;

    private String parentId;

    private Integer level;

    // Raw esq_entity_path.ep_path string. Surfaced for biztree-vs-DB
    // path-drift verification (Phase 8 race repros).
    private String entityPath;
}

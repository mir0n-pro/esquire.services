/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: native-query repository for the recursive CTE behind /esq-cmd-tree;
 *                   FK-walk of esq_org / esq_user / esq_account; leaves-first ordering; rootPath-scoped
 */
package pro.mir0n.esquire.enyMan.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/*
 * Subtree native-query repository: FK-based traversal of the natural entity
 * tree, independent from the biztree cache. Three method variants by seed
 * kind; rootPath check applied inside each query for JWT-scoped authorization.
 *
 * Rows come back leaves-first (ORDER BY level DESC) so iterating consumers
 * can delete bottom-up without reordering.
 */
@Repository
public interface EsqSubtreeRepository extends JpaRepository<EsqSubtreeRow, String> {

    @NativeQuery
    List<EsqSubtreeRow> subtreeFromOrg(@Param("seedId") String seedId,
                                      @Param("rootPath") String rootPath);

    @NativeQuery
    List<EsqSubtreeRow> subtreeFromUsr(@Param("seedId") String seedId,
                                      @Param("rootPath") String rootPath);

    @NativeQuery
    List<EsqSubtreeRow> subtreeFromAcct(@Param("seedId") String seedId,
                                       @Param("rootPath") String rootPath);
}

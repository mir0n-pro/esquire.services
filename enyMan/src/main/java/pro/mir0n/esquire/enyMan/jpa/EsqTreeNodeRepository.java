/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n root* params added
 */

package pro.mir0n.esquire.enyMan.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.NativeQuery;

@Repository
public interface EsqTreeNodeRepository extends JpaRepository<EsqTreeNodeJpa, String> {

    //@NativeQuery
    //Optional<EsqTreeNodeJpa> findById(@Param("id") String id);

    //@NativeQuery
    //List<TreeNode> findAll();

    @NativeQuery
    List<EsqTreeNodeJpa> findRoot(@Param("rootId") String rootId, @Param("rootLevel") int rootLevel, @Param("rootPath") String rootPath);

    @NativeQuery
    List<EsqTreeNodeJpa> findNodes(@Param("id") String id, @Param("rootLevel") int rootLevel, @Param("rootPath")
    String rootPath);

    @NativeQuery
    String findPath(@Param("id") String id);

    @NativeQuery
    EsqTreeNodeJpa findByEntityId(@Param("id") String id, @Param("rootLevel") int rootLevel, @Param("rootPath") String rootPath);

    @NativeQuery
    EsqTreeNodeJpa findByNameKind(@Param("name") String id, @Param("kind") Integer kind, @Param("rootLevel") int rootLevel, @Param("rootPath") String rootPath);

}

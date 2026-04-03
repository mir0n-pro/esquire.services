/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  created: native queries for org read/update
 *                   flushAutomatically=false on @Modifying prevents spurious
 *                   Hibernate auto-flush before native query execution
 * 03/26/2026 mir0n  insertCustomOrg, orgPath, insertOrg, deleteOrg native queries added
 * 03/28/2026 mir0n  insertOrgPath, deleteEntityPath added; insertOrg: path param removed
 * 03/31/2026 mir0n  insertOrgPath: kind param added; moveOrgPaths, moveOrgParent queries added
 * 04/02/2026 mir0n  lockEntityPathRoot, listMovedPaths added for move broadcast
 */

package pro.mir0n.esquire.enyMan.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;

import java.util.List;

@Repository
public interface EsqOrgRepository extends JpaRepository<EsqOrgJpa, String> {

    @NativeQuery
    EsqOrgJpa detailOrg(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    EsqOrgJpa detailOrgForUpdate(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    List<EsqNameValueJpa> customOrg(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateOrg(@Param("id") String id,
        @Param("name") String name,
        @Param("desc") String desc,
        @Param("fullName") String fullName,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertCustomOrg(@Param("id") long id,
        @Param("kind") int kind,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateCustomOrg(@Param("id") String id,
        @Param("name") String name,
        @Param("value") String value,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @NativeQuery
    String orgPath(@Param("parentId") String parentId, @Param("rootPath") String rootPath);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertOrgPath(@Param("pk") long pk, @Param("kind") int kind, @Param("path") String path);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertOrg(@Param("id") long id,
        @Param("kind") int kind,
        @Param("name") String name,
        @Param("desc") String desc,
        @Param("fullName") String fullName,
        @Param("parentId") String parentId,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deleteOrg(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deleteEntityPath(@Param("pk") String pk);

    @NativeQuery
    Long lockEntityPathRoot();

    @NativeQuery
    List<EsqMoveRecord> listMovedPaths(@Param("newPath") String newPath);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int moveOrgPaths(@Param("oldPath") String oldPath, @Param("newPath") String newPath);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int moveOrgParent(@Param("id") String id,
        @Param("parentId") String parentId,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

}

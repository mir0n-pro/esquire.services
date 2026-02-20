/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  created: native queries for usr read/update
 *                   flushAutomatically=false on @Modifying prevents spurious
 *                   Hibernate auto-flush before native query execution
 */

package pro.mir0n.esquire.enyMan.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;

import java.util.List;

@Repository
public interface EsqUsrRepository extends JpaRepository<EsqUsrJpa, String> {

    @NativeQuery
    EsqUsrJpa detailUsr(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    EsqUsrJpa detailUsrForUpdate(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    List<EsqAcctJpa> userAccts(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    List<EsqNameValueJpa> customUsr(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateUsr(@Param("id") String id,
        @Param("name") String name,
        @Param("registration") String registration,
        @Param("deleted") String deleted,
        @Param("desc") String desc,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId

    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateCustomUsr(@Param("id") String id,
        @Param("name") String name,
        @Param("value") String value,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

}

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: CREATE-only Spring Data repository for esq_account on enyMan side;
 *                   acctPath, insertAcctPath, insertAcct native queries (mirrors pacMan acct.xml,
 *                   CREATE subset). READ/UPDATE/DELETE stay on pacMan.
 * 07/23/2026 mir0n  v1.2.11 -- acctPath() gains a rootPath @Param (tenant scope, matching orgPath/usrPath)
 */

package pro.mir0n.esquire.enyMan.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;

@Repository
public interface EsqAcctRepository extends JpaRepository<EsqAcctJpa, String> {

    @NativeQuery
    String acctPath(@Param("parentId") String parentId, @Param("rootPath") String rootPath);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertAcctPath(@Param("pk") long pk, @Param("kind") int kind, @Param("path") String path);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertAcct(@Param("id") long id,
        @Param("kind") int kind,
        @Param("name") String name,
        @Param("desc") String desc,
        @Param("ccy") String ccy,
        @Param("status") String status,
        @Param("negativeAllowed") String negativeAllowed,
        @Param("parentId") String parentId,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );
}

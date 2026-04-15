/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath param added
 * 01/23/2026 mir0n use common library
 *                  added acctsAsNodes method
 * 02/19/2026 mir0n  base type changed from EsqEntityJpa to EsqAcctJpa
 *                  detailAcct() return type corrected from EsqEntityJpa to EsqAcctJpa
 *                  added detailAcctForUpdate (SELECT FOR UPDATE)
 *                  added updateAcct @Modifying native query (UPDATE esq_account with audit columns)
 *                  @Modifying(clearAutomatically=true, flushAutomatically=false)
 *                  removed unused EsqTreeNodeJpa / List imports
 * 03/26/2026 mir0n  acctPath, insertAcct, deleteAcct native queries added
 * 03/28/2026 mir0n  insertAcctPath, deleteEntityPath added; insertAcct: path param removed
 * 03/31/2026 mir0n  insertAcctPath: kind param added (ep_et_pk)
 * 04/09/2026 mir0n  insertAcct: negativeAllowed param added; updateAcct: ccy + negativeAllowed params added
 *                   updateAcctBalance native query added
 * 04/14/2026 mir0n  detailAcctForUpdate: kind (@Param) param removed; query: AND acc_et_pk = :kind dropped
 */

package pro.mir0n.esquire.pacMan.jpa;

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
    EsqAcctJpa detailAcct(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    EsqAcctJpa detailAcctForUpdate(@Param("id") String id, @Param("kind") int kind, @Param("rootPath") String rootPath);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateAcct(@Param("id") String id,
        @Param("desc") String desc,
        @Param("ccy") String ccy,
        @Param("status") String status,
        @Param("negativeAllowed") String negativeAllowed,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @NativeQuery
    String acctPath(@Param("parentId") String parentId);

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

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateAcctBalance(
        @Param("id") String id,
        @Param("balance") double balance,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deleteAcct(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deleteEntityPath(@Param("pk") String pk);
}

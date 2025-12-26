/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EsqEntityRepository extends JpaRepository<EsqEntityJpa, String> {

    @NativeQuery
    EsqEntityJpa detailAcct (@Param("id") String id);
    @NativeQuery
    EsqEntityJpa detailUsr (@Param("id") String id);
    @NativeQuery
    EsqEntityJpa detailOrg (@Param("id") String id);
}

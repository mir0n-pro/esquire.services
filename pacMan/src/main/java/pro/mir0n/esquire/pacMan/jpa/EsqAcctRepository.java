/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2006 mir0n rootPath param added
 * 01/23/206 mir0n  use common library
 *                  added acctsAsNodes method
 */

package pro.mir0n.esquire.pacMan.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;

import java.util.List;

@Repository
public interface EsqAcctRepository extends JpaRepository<EsqEntityJpa, String> {

    @NativeQuery
    EsqEntityJpa detailAcct (@Param("id") String id, @Param("rootPath") String rootPath);
}

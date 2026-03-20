/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: JPA repository for EsqAcctJpa; findAllForTree() native query
 */

package pro.mir0n.esquire.bizTree.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.stereotype.Repository;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;

import java.util.List;

@Repository
public interface EsqAcctRepository extends JpaRepository<EsqAcctJpa, String> {

    @NativeQuery
    List<EsqAcctJpa> findAllForTree();

}

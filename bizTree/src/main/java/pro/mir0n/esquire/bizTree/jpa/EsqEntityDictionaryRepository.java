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

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.NativeQuery;

@Repository
public interface EsqEntityDictionaryRepository extends JpaRepository<EsqCustomEntityFieldJpa, String> {

    @NativeQuery
    List<EsqCustomEntityFieldJpa> findCustom(@Param("kind") Integer kind);
}

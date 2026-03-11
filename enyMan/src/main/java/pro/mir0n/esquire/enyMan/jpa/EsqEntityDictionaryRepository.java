/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/23/2026 mir0n  use common library
 */

package pro.mir0n.esquire.enyMan.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.NativeQuery;
import pro.mir0n.esquire.backend.jpa.EsqCustomEntityFieldJpa;

@Repository
public interface EsqEntityDictionaryRepository extends JpaRepository<EsqCustomEntityFieldJpa, String> {

    @NativeQuery
    List<EsqCustomEntityFieldJpa> findCustom(@Param("kind") Integer kind);
}

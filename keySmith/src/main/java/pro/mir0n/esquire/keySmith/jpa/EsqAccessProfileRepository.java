/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.keySmith.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

import java.util.List;

@Repository
public interface EsqAccessProfileRepository extends JpaRepository<EsqAccessProfileJpa, String> {

    @NativeQuery
    EsqAccessProfileJpa access (@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    List<EsqRoleJpa> roles (@Param("id") String id);
    @NativeQuery
    List<EsqPermissionJpa> permissions(@Param("id") String id);
}

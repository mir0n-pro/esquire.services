/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  new: JPA repository for roles/permissions (native queries: roles(), permissions(id))
 */

package pro.mir0n.esquire.backend.storage.roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

import java.util.List;

@Repository
public interface JpaRolesRepository extends JpaRepository<EsqRoleJpa, String> {
    @NativeQuery
    List<EsqRoleJpa> roles();
    @NativeQuery
    List<EsqPermissionJpa> permissions(@Param("id") String id);
}

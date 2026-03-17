/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  added accessForUpdate (SELECT FOR UPDATE OF esq_user)
 *                   added updateAccess @Modifying (UPDATE esq_auth with audit columns)
 * 03/03/2026 mir0n  added deleteUserRole / insertUserRole
 * 03/03/2026 mir0n  added roleAll
 * 03/16/2026 mir0n  updateAccess(): connectFlg param added
 *                   confirmPendingFlags() added (replaces clearPwdChangeForced + confirmTfaMethod)
 */

package pro.mir0n.esquire.keySmith.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

import java.util.List;

@Repository
public interface EsqAccessProfileRepository extends JpaRepository<EsqAccessProfileJpa, String> {

    @NativeQuery
    EsqAccessProfileJpa access(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    EsqAccessProfileJpa accessForUpdate(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    List<EsqRoleJpa> roles(@Param("id") String id);
    @NativeQuery
    List<EsqRoleJpa> rolesAll(@Param("id") String id);
    @NativeQuery
    List<EsqPermissionJpa> permissions(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateAccess(@Param("id") String id,
        @Param("email") String email,
        @Param("loginId") String loginId,
        @Param("pwdChangeForced") String pwdChangeForced,
        @Param("tfaMethod") String tfaMethod,
        @Param("connectFlg") String connectFlg,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deleteUserRole(@Param("id") String id, @Param("roleId") String roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertUserRole(@Param("id") String id, @Param("roleId") String roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int confirmPendingFlags(@Param("id") String id,
        @Param("pwdChangeForced") String pwdChangeForced,
        @Param("tfaMethod") String tfaMethod);
}

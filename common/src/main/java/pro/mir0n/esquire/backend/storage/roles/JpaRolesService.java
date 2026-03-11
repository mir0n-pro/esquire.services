/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  new: JPA-backed IRolesService; maps EsqRoleJpa→EsqRole, EsqPermissionJpa→EsqPermission
 */

package pro.mir0n.esquire.backend.storage.roles;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.access.*;
import pro.mir0n.esquire.backend.jpa.access.*;
@Slf4j
public class JpaRolesService implements IRolesService {

    private final JpaRolesRepository jpaRolesRepository;

    public JpaRolesService(JpaRolesRepository jpaRolesRepository) {
        this.jpaRolesRepository = jpaRolesRepository;
    }

    public List<EsqRole>roles() {
        List<EsqRoleJpa> rs = jpaRolesRepository.roles();
        List<EsqRole> ret = new ArrayList<>(rs.size());
        for(EsqRoleJpa r : rs) {
            ret.add(new EsqRole().fill(r));
        }
        return ret;
    }

    public List<EsqPermission> permissions(String id) {
        List<EsqPermissionJpa> ps = jpaRolesRepository.permissions(id);
        List<EsqPermission> ret = new ArrayList<>(ps.size());
        for(EsqPermissionJpa p : ps) {
            ret.add(new EsqPermission().fill(p));
        }
        return ret;
    }

}


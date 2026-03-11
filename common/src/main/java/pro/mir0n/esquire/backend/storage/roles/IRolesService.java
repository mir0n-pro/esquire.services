/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  new: roles/permissions service interface (roles(); permissions(id))
 */

package pro.mir0n.esquire.backend.storage.roles;

import java.util.List;

import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.dto.access.EsqRole;

public interface IRolesService {
    public List<EsqRole>roles();
    public List<EsqPermission> permissions(String id);
    //TODO/TBD add to minimize multi round to DB
    //public Map<String, List<EsqPermission>> rolePermissions();

}

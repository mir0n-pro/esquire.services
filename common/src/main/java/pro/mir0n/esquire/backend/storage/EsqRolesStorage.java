/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  new: in-memory roles/permissions storage; findAdminPermissions(); isAdminCmdPermitted()
 * 03/10/2026 mir0n  roles() added: all roles as List<EsqRole>
 *                   fillPermissionsForRole() added: accumulates permissions for one role into a list
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug; dual error pattern; unused imports removed
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- isAdminCmdPermitted() counts esq.biz.perm.check.total (tags cmd = the
 *                   AdminCmd enum, result = allow|deny): the authorization decision itself, at the one gate every
 *                   service goes through. The gate sees allow and deny ONLY -- a self-update BYPASSES it entirely
 *                   (id.equals(uid) short-circuits at the caller), which is why there is no third tag value
 */

package pro.mir0n.esquire.backend.storage;



import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import pro.mir0n.esquire.backend.dto.access.EsqRole;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import pro.mir0n.esquire.backend.storage.roles.IRolesService;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesRepository;
import pro.mir0n.esquire.backend.storage.roles.JpaRolesService;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class EsqRolesStorage {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + EsqRolesStorage.class.getName());

    public enum AdminCmd {
        CREATE,
        UPDATE,
        DELETE,
        AUTH,
        ACCT
    }

    private static EsqRolesStorage itSelf = new EsqRolesStorage();
    private IRolesService service = null;

    private Map<String, EsqRole> roles = null;
    private Map<String, Map<Integer, EsqPermission>> permissions = new HashMap();

    public static EsqRolesStorage getInstance() {
        return itSelf;
     }

     //TODO/TBD use to minimize multi round to DB use Map<String, List<EsqPermission>> rolePermissions();
    public boolean init(JpaRolesRepository repository) {
        service = new JpaRolesService(repository);
        boolean ret = false;
        try {
            List<EsqRole> rs = service.roles();
            if (rs != null && !rs.isEmpty()) {
                roles = new HashMap<>(rs.size());
                for (EsqRole r : rs) {
                    roles.put(r.getName(), r);
                    List<EsqPermission> ps = service.permissions(r.getId());
                    if (ps != null && !ps.isEmpty()) {
                        Map<Integer, EsqPermission> mps = new HashMap<>(ps.size());
                        for (EsqPermission p : ps) {
                            mps.put(Integer.parseInt(p.getId()), p);
                        }
                        permissions.put(r.getName(),mps);
                    }
                }
                ret = true;
            }
        } catch (Exception e) {
            log.error("EsqRolesStorage: init failed: {}", e.getMessage());
            devLog.error("EsqRolesStorage: init failed: {}", e.getMessage(), e);
        }
        if (roles != null) {
            devLog.debug("EsqRolesStorage initated with {}({}) roles.", roles.size(), permissions.size());
        }
        return ret;
    }

    public Map<Integer,EsqPermission> findAdminPermissions(List<String> roleNames) {
        Map<Integer,EsqPermission> ret = null;
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String name : roleNames) {
                EsqRole r = roles.get(name);
                if (r != null && r.getKind() == EsqConstants.KIND_ADMIN_ROLE) {
                    ret = permissions.get(name);
devLog.debug("EsqRolesStorage.findAdminPermissions found role {} with permnissions {}.", name, ret);
                    break;
                }
            }
        }
        return ret;
    }

    public List<EsqRole> roles() {
        return roles != null ? new ArrayList<>(roles.values()) : new ArrayList<>();
    }

    public List<EsqPermission> fillPermissionsForRole(String roleName, List<EsqPermission> ret) {
        if (ret == null) {
            ret = new ArrayList<>();
        }
        Map<Integer, EsqPermission> perms = permissions.get(roleName);
        if (perms != null) {
            ret.addAll(perms.values());
        }
        return ret;
    }

    public static boolean isAdminCmdPermitted(EsqPermission permission, AdminCmd cmd) {
        boolean ret = false;
        if (permission != null) {
            List<String> flags = permission.getFlags();
            int idx = cmd.ordinal();
            if (flags.size() > idx) {
                ret = "Y".equals(flags.get(idx));
            }
        }
devLog.debug("EsqRolesStorage.isAdminCmdPermitted found role {} = {}.", cmd.ordinal(),  ret);
        // The authorization decision itself, counted once at the gate every service goes through (O1/T8 phase A).
        // Both tag values are bounded: cmd is the AdminCmd enum (5 values), result is allow|deny. NOTE the gate
        // sees ALLOW and DENY only -- the self-update bypass never reaches here, it short-circuits at the caller
        // (id.equals(uid) -> permitted), which is not an authorization decision to begin with.
        EsqBizMeters.count("esq.biz.perm.check.total", "cmd", cmd.name(), "result", ret ? "allow" : "deny");
        return ret;
    }

}


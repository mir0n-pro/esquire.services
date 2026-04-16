/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/03/2026 mir0n  extends EsqThing
 * 03/03/2026 mir0n  rolesAll field added; fill() extended with rolesAll param
 * 03/10/2026 mir0n  fill() DTO overload added: rolesAll as List<EsqRole>, permissions as List<EsqPermission>
 *                   original fill() renamed fillJpa() — accepts List<EsqRoleJpa>, List<EsqPermissionJpa>
 * 03/16/2026 mir0n  connectFlg field added; fill() and fillJpa() updated
 * 04/16/2026 mir0n  fill(): null-guard replaces early return
 */

package pro.mir0n.esquire.backend.dto.access;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqNameValue;
import pro.mir0n.esquire.backend.dto.EsqThing;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Schema(
        name = "EsqAccessProfile",
        description = "Holds access profile: indetity, roles and entitlements"
)
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EsqAccessProfile extends EsqThing {

    @Schema(
            description = "User Login ID", example = "mainadmin"
    )
    private String loginId;

    @Schema(
            description = "User email", example = "pasilii.pupkin@gmail.com"
    )
    private String email;

    @Schema(
            description = "Flag to force password change", example = "N"
    )
    private String pwdChangeForced;

    @Schema(
            description = "Method of 2 factor authentication, N:not set, G:google authenticator, g:google authenticator pending", example = "B"
    )
    private String tfaMethod;

    @Schema(
            description = "User connect flag", example = "Y"
    )
    private String connectFlg;

    @Schema(
            description = "List of roles assigned to user", example = "TREE, MANAGER"
    )
    private List<EsqRole> roles;

    @Schema(
            description = "List of all roles accessible to user", example = "TREE, MANAGER"
    )
    private List<EsqRole> rolesAll;

    @JsonIgnore
    private Map<String, List<EsqPermission>> permissions;

    @JsonAnyGetter
    public Map<String, Object> getAttributesAsFields() {
        // Converts List<Attribute> into a Map for serialization as fields
        Map<String, Object> allFields = new HashMap<>();
        allFields.putAll(permissions);
        return allFields;
    }


    public EsqAccessProfile fill(EsqAccessProfileJpa jpa, List<EsqRoleJpa> roles, List<EsqRole> rolesAll, List<EsqPermission> permissions) {
        setId(String.valueOf(jpa.getId()));
        setKind(jpa.getKind());
        setName(jpa.getName());
        setLoginId(jpa.getLoginId());
        setEmail(jpa.getEmail());
        setPwdChangeForced(jpa.getPwdChangeForced());
        setTfaMethod(jpa.getTfaMethod());
        setConnectFlg(jpa.getConnectFlg());
        setRoles(new ArrayList<>());
        if (roles != null) {
            roles.forEach(r -> getRoles().add(new EsqRole().fill(r)));
        }
        setRolesAll(rolesAll != null ? rolesAll : new ArrayList<>());
        setPermissions(new HashMap<>());
        if (permissions != null) {
            for (EsqPermission perm : permissions) {
                String tpy = perm.getType().toLowerCase();
                List<EsqPermission> permList = getPermissions().get(tpy);
                if (permList == null) {
                    permList = new ArrayList<>();
                    getPermissions().put(tpy, permList);
                }
                permList.add(perm);
            }
        }
        return this;
    }

    public EsqAccessProfile fillJpa (EsqAccessProfileJpa jpa, List<EsqRoleJpa> roles, List<EsqRoleJpa> rolesAll,  List<EsqPermissionJpa> permissions) {
        setId(String.valueOf(jpa.getId()));
        setKind(jpa.getKind());
        setName(jpa.getName());
        setLoginId(jpa.getLoginId());
        setEmail(jpa.getEmail());
        setPwdChangeForced(jpa.getPwdChangeForced());
        setTfaMethod(jpa.getTfaMethod());
        setConnectFlg(jpa.getConnectFlg());
        setRoles(new ArrayList<>());
        if (roles != null) {
            roles.forEach(r -> getRoles().add(new EsqRole().fill(r)));
        }
        setRolesAll(new ArrayList<>());
        if (rolesAll != null) {
            rolesAll.forEach(r -> getRolesAll().add(new EsqRole().fill(r)));
        }
        setPermissions(new HashMap<>());
        for(EsqPermissionJpa perm : permissions) {
            String tpy = perm.getType().toLowerCase();
            List<EsqPermission> permList = getPermissions().get(tpy);
            if (permList == null) {
                permList = new ArrayList<>();
                getPermissions().put(tpy, permList);
            }
            permList.add(new EsqPermission().fill(perm));
        }
        return this;
    }

}


/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.dto.access;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqNameValue;
import pro.mir0n.esquire.backend.jpa.access.EsqAccessProfileJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqPermissionJpa;
import pro.mir0n.esquire.backend.jpa.access.EsqRoleJpa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Schema(
        name = "EsqAccessProfile",
        description = "Holds access profile: indetity, roles and entitlements"
)
@SuperBuilder
public class EsqAccessProfile {
    public EsqAccessProfile() {}

    @Schema(
            description = "User ID", example = "6"
    )
    private String id;

    @Schema(
            description = "Kind  of user", example = "12 for client"
    )
    private Integer kind;

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
            description = "List of roles assigned to user", example = "TREE, MANAGER"
    )
    private List<EsqRole> roles;

    @JsonIgnore
    private Map<String, List<EsqPermission>> permissions;

    @JsonAnyGetter
    public Map<String, Object> getAttributesAsFields() {
        // Converts List<Attribute> into a Map for serialization as fields
        Map<String, Object> allFields = new HashMap<>();
        allFields.putAll(permissions);
        return allFields;
    }


    public EsqAccessProfile fill (EsqAccessProfileJpa jpa, List<EsqRoleJpa> roles, List<EsqPermissionJpa> permissions) {
        setId(jpa.getId());
        setKind(jpa.getKind());
        setLoginId(jpa.getLoginId());
        setEmail(jpa.getEmail());
        setPwdChangeForced(jpa.getPwdChangeForced());
        setTfaMethod(jpa.getTfaMethod());
        setRoles(new ArrayList<>());
        if (roles != null) {
            roles.forEach(r -> getRoles().add(new EsqRole().fill(r)));
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


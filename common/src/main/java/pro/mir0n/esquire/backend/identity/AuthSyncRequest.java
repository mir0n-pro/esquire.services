/*
 *  Esquire frameworks (tm)
 *  Esquire common
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URQ Text payload POJO
 * 08/12/2026 mir0n  v1.2.13 -- moved from kcMaster.messaging.KcSyncRequest and renamed; toMap() added,
 *                   writing the RodEvent body in declaration order and omitting what was not set
 */

package pro.mir0n.esquire.backend.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The body of an identity command: what Esquire asks the identity provider to make true about one entity.
 *
 * <p>Self-identifying: always carries id and kind. It sits beside {@link IIdentityGateway} because both ends
 * of the seam need it -- the caller fills it, the provider reads it -- and one class holding the field names
 * is what keeps a filled field and a read field the same field.
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthSyncRequest {
    private String id;
    private int kind;
    private String loginId;
    private String newLoginId;
    private String email;
    private String pwdChangeForced;
    private String tfaMethod;
    private String connectFlg;
    private String path;
    private List<String> roles;

    /**
     * The body map a {@code RodEvent} carries, in the order the fields are declared.
     *
     * <p>Only what was set goes in: a command fills the fields it is about and leaves the rest null, so a
     * delete carries a login id and a move carries a path, and neither carries the other's fields. The keys
     * are the property names, which is what reads it back on the far side.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("id", id);
        ret.put("kind", kind);
        putIfSet(ret, "loginId", loginId);
        putIfSet(ret, "newLoginId", newLoginId);
        putIfSet(ret, "email", email);
        putIfSet(ret, "pwdChangeForced", pwdChangeForced);
        putIfSet(ret, "tfaMethod", tfaMethod);
        putIfSet(ret, "connectFlg", connectFlg);
        putIfSet(ret, "path", path);
        putIfSet(ret, "roles", roles);
        return ret;
    }

    private void putIfSet(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}

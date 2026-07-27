/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/10/2026 mir0n  EsqConstants used for JWT claim keys (realm_access, roles)
 * 07/23/2026 mir0n  v1.2.11 -- single-ret pattern: realm_access.roles -> ROLE_<role> collected into one ret list
 */
package pro.mir0n.esquire.gateway.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class KeycloakRoleConverter  implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        Collection<GrantedAuthority> ret = new ArrayList<>();
        Map<String, Object> realmAccess = (Map<String, Object>) source.getClaims().get(EsqConstants.JWT_CLAIM_REALM_ACCESS);
        if (realmAccess != null) {
            List<String> roles = (List<String>) realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES);
            if (roles != null) {
                for (String roleName : roles) {
                    ret.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                }
            }
        }
        return ret;
    }

}

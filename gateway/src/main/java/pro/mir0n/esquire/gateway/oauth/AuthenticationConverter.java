/*
 *  Esquire frameworks (tm)
 *  Gateway
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/17/2026 mir0n  created (v2.0.2): pure OAuth (lab) -- for a token whose azp is on the pure list, the
 *                   authentication is built from the cached claims (ROLE_ authorities, the claims on its
 *                   details); a failed lookup becomes an AuthenticationServiceException with its cause kept; any
 *                   other token goes to the claims-mode converter
 * 11/22/2026 mir0n  v2.0.3 -- lab.pureoauth.PureOAuthAuthenticationConverter -> oauth.AuthenticationConverter;
 *                   every token's authentication is built from the cached claims -- the client list, isPure()
 *                   and the claims-mode converter out
 */

package pro.mir0n.esquire.gateway.oauth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pro.mir0n.esquire.backend.dto.access.EsqUserClaims;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class AuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    public static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    private final ClaimsCache cache;

    public AuthenticationConverter(ClaimsCache cache) {
        this.cache = cache;
    }

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        String loginId = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
        return cache.claimsOf(loginId)
                .map(claims -> authenticationOf(jwt, claims))
                .onErrorMap(AuthenticationConverter::notAuthenticated);
    }

    //xxx: the claims ride on the authentication's details; HeaderFilter turns them into headers
    private static AbstractAuthenticationToken authenticationOf(Jwt jwt, EsqUserClaims claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : claims.roles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        JwtAuthenticationToken ret = new JwtAuthenticationToken(jwt, authorities, claims.loginId());
        ret.setDetails(claims);
        return ret;
    }

    //xxx: a failed lookup goes on as an AuthenticationException with its cause kept; the gateway's error
    // handler answers by the cause -- keySmith down is a 503, the same answer a route to it gets
    private static Throwable notAuthenticated(Throwable error) {
        Throwable ret = error;
        if (!(error instanceof AuthenticationException)) {
            ret = new AuthenticationServiceException("claims lookup failed: " + error.getMessage(), error);
        }
        return ret;
    }
}

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
 */

package pro.mir0n.esquire.gateway.lab.pureoauth;

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
import java.util.Set;

public class PureOAuthAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    public static final String CLAIM_AZP = "azp";
    public static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    private final Set<String> pureClients;
    private final PureOAuthClaimsCache cache;
    private final Converter<Jwt, Mono<AbstractAuthenticationToken>> claimsMode;

    public PureOAuthAuthenticationConverter(Set<String> pureClients, PureOAuthClaimsCache cache,
                                            Converter<Jwt, Mono<AbstractAuthenticationToken>> claimsMode) {
        this.pureClients = pureClients;
        this.cache = cache;
        this.claimsMode = claimsMode;
    }

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Mono<AbstractAuthenticationToken> ret;
        if (isPure(pureClients, jwt)) {
            String loginId = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
            ret = cache.claimsOf(loginId)
                    .map(claims -> authenticationOf(jwt, claims))
                    .onErrorMap(PureOAuthAuthenticationConverter::notAuthenticated);
        } else {
            ret = claimsMode.convert(jwt);
        }
        return ret;
    }

    public static boolean isPure(Set<String> pureClients, Jwt jwt) {
        String azp = jwt.getClaimAsString(CLAIM_AZP);
        return azp != null && pureClients.contains(azp);
    }

    //xxx: the claims ride on the authentication's details; PureOAuthHeaderFilter turns them into headers
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

/*
 *  Esquire frameworks (tm)
 *  Gateway
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/17/2026 mir0n  created (v2.0.2): pure OAuth (lab) -- strips inbound X-Esq-UID / X-Esq-Root-Path /
 *                   X-Esq-Roles for every caller, and sets them from the claims on the authentication's details
 */

package pro.mir0n.esquire.gateway.lab.pureoauth;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import pro.mir0n.esquire.backend.dto.access.EsqUserClaims;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;

public class PureOAuthHeaderFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        //xxx: inbound claim headers are stripped for EVERY caller -- the services trust them as the gateway's
        ServerWebExchange stripped = exchange.mutate()
                .request(request -> request.headers(PureOAuthHeaderFilter::strip))
                .build();
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> withClaims(stripped, context))
                .defaultIfEmpty(stripped)
                .flatMap(chain::filter);
    }

    static ServerWebExchange withClaims(ServerWebExchange exchange, SecurityContext context) {
        ServerWebExchange ret = exchange;
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof EsqUserClaims claims) {
            ret = exchange.mutate()
                    .request(request -> request.headers(headers -> set(headers, claims)))
                    .build();
        }
        return ret;
    }

    static void strip(HttpHeaders headers) {
        headers.remove(EsqConstants.X_ESQ_UID);
        headers.remove(EsqConstants.X_ESQ_ROOT_PATH);
        headers.remove(EsqConstants.X_ESQ_ROLES);
    }

    static void set(HttpHeaders headers, EsqUserClaims claims) {
        headers.set(EsqConstants.X_ESQ_UID, claims.uid());
        headers.set(EsqConstants.X_ESQ_ROOT_PATH, claims.rootPath());
        headers.set(EsqConstants.X_ESQ_ROLES, String.join(",", claims.roles()));
    }
}

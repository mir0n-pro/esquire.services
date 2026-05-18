/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: single WebFilter for the Token Relay umbrella; iterates the
 *                   configured ITokenRelayVariant list and branches on the VariantAction
 *                   each returns. Replaces the previous CredentialBoundAuthenticationFilter
 *                   + PhantomTokenAuthenticationFilter pair; their shared workflow
 *                   (cache lookup, KC call, Authorization header rewrite, chain dispatch)
 *                   now lives here once.
 */
package pro.mir0n.esquire.gateway.security.tokenrelay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Single Token Relay WebFilter. Iterates the configured
 * {@link ITokenRelayVariant} list in order; the first variant that returns
 * a non-Pass {@link VariantAction} decides what happens:
 *
 *   - {@link VariantAction.Pass}   -- try the next variant.
 *   - {@link VariantAction.Reject} -- short-circuit with 401.
 *   - {@link VariantAction.Relay}  -- look up the cache key in
 *                                     {@link TokenRelayCache}; on MISS call
 *                                     KC via {@link ITokenRelayClient};
 *                                     rewrite Authorization header to
 *                                     {@code Bearer <jwt>}; continue chain.
 *
 * If every variant returns Pass, the request is forwarded unchanged
 * (Plain JWT / BFF / unauthenticated paths all flow through untouched).
 *
 * Ordering: wired by {@code SecurityConfig.springSecurityFilterChain}
 * via {@code .addFilterBefore(filter, SecurityWebFiltersOrder.AUTHENTICATION)}
 * so the rewritten Bearer is what Spring Security's
 * {@code oauth2ResourceServer} validates downstream.
 */
public class TokenRelayFilter implements WebFilter, Ordered {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + TokenRelayFilter.class.getName());

    private static final String BEARER_PREFIX = "Bearer ";

    private final List<ITokenRelayVariant> variants;
    private final TokenRelayCache          cache;
    private final int                      order;

    public TokenRelayFilter(List<ITokenRelayVariant> variants,
                            TokenRelayCache cache,
                            int order) {
        this.variants = variants == null ? List.of() : List.copyOf(variants);
        this.cache    = cache;
        this.order    = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<Void> ret = null;
        for (ITokenRelayVariant variant : variants) {
            VariantAction action = variant.examine(exchange);
            if (action instanceof VariantAction.Pass) {
                continue;
            }
            if (action instanceof VariantAction.Reject rj) {
                devLog.error("filter: variant [{}] rejected -- {}",
                        variant.getClass().getSimpleName(), rj.reason());
                ret = unauthorized(exchange);
                break;
            }
            if (action instanceof VariantAction.Relay rl) {
                devLog.debug("filter: variant [{}] relaying, cacheKey=[{}]",
                        variant.getClass().getSimpleName(), rl.cacheKey());
                ret = cache.getOrAcquire(rl.cacheKey(), rl.kcRequest())
                        .onErrorResume(ex -> {
                            devLog.error("filter: cache/acquire failed for key=[{}] -- {}",
                                    rl.cacheKey(), ex.toString());
                            return Mono.empty();
                        })
                        .flatMap(jwt -> chain.filter(swapToBearer(exchange, jwt)))
                        .switchIfEmpty(Mono.defer(() -> unauthorized(exchange)));
                break;
            }
        }
        if (ret == null) {
            ret = chain.filter(exchange);
        }
        return ret;
    }

    private ServerWebExchange swapToBearer(ServerWebExchange exchange, String jwt) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + jwt)
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setRawStatusCode(401);
        return exchange.getResponse().setComplete();
    }
}

/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: Phantom Token Relay variant. Edge credential = phantom JWT
 *                   (stripped Bearer). Examines inbound Bearer requests whose azp is
 *                   in the phantom allowlist, returns Relay with cacheKey = jti and a
 *                   KC token-exchange request authenticated as the gateway's dedicated
 *                   exchange client.
 */
package pro.mir0n.esquire.gateway.security.tokenrelay;

import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.gateway.security.JwtClaimPeek;

import java.util.Set;

/**
 * Phantom Token Relay variant. The client carries a Bearer JWT whose
 * payload has been stripped (a {@em phantom token}). The gateway runs
 * RFC 8693 OAuth 2.0 Token Exchange against KC to obtain the full
 * claim-rich JWT for the same identity, caches per source-token
 * {@code jti}, and forwards the full JWT downstream.
 *
 * The gateway authenticates to KC's {@code /token} endpoint as the
 * dedicated {@code esq-gw-exchange} client, whose protocol mappers carry
 * the principal's claims so the exchanged token comes back populated.
 *
 * {@code examine()} contract:
 *
 *   - No Authorization header, or non-Bearer -> Pass.
 *   - Bearer with {@code azp} NOT in allowlist -> Pass (Plain JWT path).
 *   - Bearer with {@code azp} in allowlist:
 *       {@code jti} present -> Relay (cacheKey = jti; KC request =
 *                              token-exchange grant + subject_token,
 *                              authenticated as esq-gw-exchange).
 *       {@code jti} missing -> Reject (cache key needed).
 */
public class PhantomTokenRelay implements ITokenRelayVariant {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String GRANT_TYPE         = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String SUBJECT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final Set<String> allowlist;
    private final String      exchangeClientId;
    private final String      exchangeClientSecret;

    public PhantomTokenRelay(Set<String> allowlist,
                             String exchangeClientId,
                             String exchangeClientSecret) {
        this.allowlist            = allowlist == null ? Set.of() : Set.copyOf(allowlist);
        this.exchangeClientId     = exchangeClientId;
        this.exchangeClientSecret = exchangeClientSecret == null ? "" : exchangeClientSecret;
    }

    @Override
    public VariantAction examine(ServerWebExchange exchange) {
        VariantAction ret;
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            ret = new VariantAction.Pass();
        } else {
            String subjectToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            String azp = JwtClaimPeek.peekAzp(subjectToken);
            if (azp == null || !allowlist.contains(azp)) {
                ret = new VariantAction.Pass();
            } else {
                String jti = JwtClaimPeek.peekJti(subjectToken);
                if (jti == null) {
                    ret = new VariantAction.Reject(
                            "Bearer JWT for azp=[" + azp + "] has no jti -- Phantom Token Relay requires jti for cache key");
                } else {
                    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                    form.add("grant_type",         GRANT_TYPE);
                    form.add("subject_token",      subjectToken);
                    form.add("subject_token_type", SUBJECT_TOKEN_TYPE);
                    KcTokenRequest req = new KcTokenRequest(form, exchangeClientId, exchangeClientSecret);
                    ret = new VariantAction.Relay(jti, req);
                }
            }
        }
        return ret;
    }
}

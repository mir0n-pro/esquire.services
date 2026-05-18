/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: WebClient-backed ITokenRelayClient. Both Vanilla and Phantom
 *                   variants funnel through this one class; per-variant differences are
 *                   encoded into the KcTokenRequest (form params + Basic auth). Parses
 *                   { access_token, expires_in } and returns ExpiringJwt with
 *                   expiresAt = now + expires_in - 30s buffer.
 */
package pro.mir0n.esquire.gateway.security.tokenrelay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Production {@link ITokenRelayClient}. POSTs to KC's {@code /token}
 * endpoint with the form params + Basic auth supplied by the variant.
 * Parses the JSON response, applies a safety buffer to the TTL, and
 * returns an {@link ExpiringJwt}.
 *
 * Variants that call this client today:
 *   - {@link VanillaTokenRelay} -- grant_type=client_credentials,
 *                                  Basic = inbound client's own creds.
 *   - {@link PhantomTokenRelay} -- grant_type=token-exchange + subject_token,
 *                                  Basic = gateway's esq-gw-exchange creds.
 */
public class WebClientTokenRelayClient implements ITokenRelayClient {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + WebClientTokenRelayClient.class.getName());

    /**
     * Safety buffer subtracted from KC's expires_in so we re-acquire before
     * the downstream service's local JWT validation would reject the token.
     */
    private static final Duration EXPIRY_BUFFER = Duration.ofSeconds(30);

    private final WebClient httpClient;
    private final String    tokenUri;

    public WebClientTokenRelayClient(WebClient httpClient, String tokenUri) {
        this.httpClient = httpClient;
        this.tokenUri   = tokenUri;
    }

    @Override
    public Mono<ExpiringJwt> acquire(KcTokenRequest request) {
        Mono<ExpiringJwt> ret;
        ret = httpClient.post()
                .uri(tokenUri)
                .headers(h -> h.setBasicAuth(request.basicAuthClientId(), request.basicAuthSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(request.formParams()))
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    String accessToken = (String) body.get("access_token");
                    Object expiresIn   = body.get("expires_in");
                    long ttlSeconds    = expiresIn instanceof Number
                            ? ((Number) expiresIn).longValue()
                            : Long.parseLong(expiresIn.toString());
                    Instant expiresAt  = Instant.now().plusSeconds(ttlSeconds).minus(EXPIRY_BUFFER);
                    devLog.debug("acquire: success, ttl={}s, expiresAt={}", ttlSeconds, expiresAt);
                    return new ExpiringJwt(accessToken, expiresAt);
                })
                .doOnError(ex -> devLog.error("acquire: failed -- {}", ex.toString()));
        return ret;
    }
}

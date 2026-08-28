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
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- acquire() counts esq.biz.gw.tokenrelay.acquire.total and times
 *                   esq.biz.gw.tokenrelay.duration (tag outcome = ok|error|cancelled) around the KC /token call --
 *                   an EXTERNAL server on the hot path that nothing measured (esq.gw.inner times the DOWNSTREAM
 *                   call, not this one). Wrapped in Mono.defer so the clock starts at SUBSCRIPTION, not assembly:
 *                   a nanoTime() outside the chain is captured when the Mono is BUILT and times the wrong window.
 *                   doOnSuccess / doOnError / doOnCancel cover every terminal signal, so a client that hangs up
 *                   mid-relay does not vanish from the count
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
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
        // esq.biz.gw.tokenrelay.acquire.total / .duration (O1/T8 phase E): the KC /token round-trip. This is a
        // call to an EXTERNAL server on the hot path of every cache-missing request, and nothing measures it --
        // the gateway's own esq.gw.inner times the DOWNSTREAM service call, not this. If KeyCloak slows down or
        // starts refusing, the symptom today is just "the gateway got slower" with no cause anywhere.
        //
        // REACTIVE: Mono.defer, so the clock starts at SUBSCRIPTION, not at assembly. A bare System.nanoTime()
        // outside the chain is captured when the Mono is BUILT and would measure the wrong window entirely.
        // doOnSuccess / doOnError / doOnCancel cover every terminal signal -- a cancelled request (the client
        // hung up mid-relay) must not silently vanish from the count.
        Mono<ExpiringJwt> ret;
        ret = Mono.defer(() -> {
            long startedAt = System.nanoTime();
            return httpClient.post()
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
                    .doOnSuccess(jwt -> meterAcquire(startedAt, "ok"))
                    .doOnCancel(() -> meterAcquire(startedAt, "cancelled"))
                    .doOnError(ex -> {
                        meterAcquire(startedAt, "error");
                        devLog.error("acquire: failed -- {}", ex.toString());
                    });
        });
        return ret;
    }

    /** One place, so the three terminal signals cannot drift apart. Outcome is bounded: ok | error | cancelled. */
    private static void meterAcquire(long startedAt, String outcome) {
        long elapsed = System.nanoTime() - startedAt;
        EsqBizMeters.count("esq.biz.gw.tokenrelay.acquire.total", "outcome", outcome);
        EsqBizMeters.time("esq.biz.gw.tokenrelay.duration", elapsed, "outcome", outcome);
    }
}

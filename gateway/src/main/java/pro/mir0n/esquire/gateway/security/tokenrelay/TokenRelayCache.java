/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: ConcurrentHashMap-backed cache of ExpiringJwt keyed by
 *                   variant-supplied String. Both Vanilla Token Relay (key = client_id)
 *                   and Phantom Token Relay (key = source-token jti) use this one cache.
 *                   On MISS, invokes the shared ITokenRelayClient. Caffeine deferred.
 */
package pro.mir0n.esquire.gateway.security.tokenrelay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe cache of {@link ExpiringJwt} keyed by the variant-derived
 * string. On HIT for a not-yet-expired entry, returns the cached JWT
 * without calling the client. On MISS, invokes the shared
 * {@link ITokenRelayClient}, stores the result, and returns it.
 *
 * Concurrency note: simultaneous MISSes for the same key currently race
 * -- both calls hit KC. The window is one-time per cache-fill, not per
 * request, and KC handles parallel grants idempotently. A single-flight
 * wrapper can be added later if measurements warrant it.
 */
public class TokenRelayCache {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + TokenRelayCache.class.getName());

    private final ITokenRelayClient delegate;
    private final ConcurrentMap<String, ExpiringJwt> cache = new ConcurrentHashMap<>();

    public TokenRelayCache(ITokenRelayClient delegate) {
        this.delegate = delegate;
    }

    /**
     * Return a not-yet-expired JWT for the given key. On cache MISS,
     * invokes the client with the supplied request, stores the result,
     * and returns the JWT.
     */
    public Mono<String> getOrAcquire(String key, KcTokenRequest request) {
        Mono<String> ret;
        ExpiringJwt existing = cache.get(key);
        if (existing != null && Instant.now().isBefore(existing.expiresAt())) {
            devLog.debug("getOrAcquire: cache HIT for key=[{}], expiresAt={}", key, existing.expiresAt());
            ret = Mono.just(existing.jwt());
        } else {
            devLog.debug("getOrAcquire: cache MISS for key=[{}] -- acquiring", key);
            ret = delegate.acquire(request)
                    .map(acquired -> {
                        cache.put(key, acquired);
                        return acquired.jwt();
                    });
        }
        return ret;
    }

    /**
     * Explicit invalidation -- callable on KC admin revoke events
     * (future webhook integration) or at shutdown. Not used on the
     * request hot path.
     */
    public void invalidate(String key) {
        cache.remove(key);
        devLog.debug("invalidate: removed key=[{}]", key);
    }

    public int size() {
        return cache.size();
    }
}

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
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- getOrAcquire() counts esq.biz.gw.tokenrelay.total (tag result = hit|miss).
 *                   A GENUINE hit/miss: a hit serves the request without touching KeyCloak, a miss is a live
 *                   /token round-trip on the hot path -- so the hit RATE is exactly how much of KeyCloak's latency
 *                   the users are spared, and a collapsing rate turns into gateway latency with no other symptom
 */
package pro.mir0n.esquire.gateway.security.tokenrelay;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
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
        // esq.biz.gw.tokenrelay.total (O1/T8 phase E): a REAL hit/miss -- a hit returns the cached JWT and never
        // touches KeyCloak; a miss is a live /token round-trip on the request's hot path. So the hit RATE is
        // directly how much of KeyCloak's latency the users are actually paying, and a collapsing hit rate (a TTL
        // change, a key that stopped being stable, an eviction storm) turns into gateway latency with no other
        // symptom. Bounded tag: hit | miss.
        //
        // NOTE this is a genuine cache, unlike bizTree's taijitu -- which is why a hit/miss meter belongs HERE
        // and was dropped there (taijitu is DB-backed and has no miss path at all).
        Mono<String> ret;
        ExpiringJwt existing = cache.get(key);
        if (existing != null && Instant.now().isBefore(existing.expiresAt())) {
            EsqBizMeters.count("esq.biz.gw.tokenrelay.total", "result", "hit");
            devLog.debug("getOrAcquire: cache HIT for key=[{}], expiresAt={}", key, existing.expiresAt());
            ret = Mono.just(existing.jwt());
        } else {
            EsqBizMeters.count("esq.biz.gw.tokenrelay.total", "result", "miss");
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

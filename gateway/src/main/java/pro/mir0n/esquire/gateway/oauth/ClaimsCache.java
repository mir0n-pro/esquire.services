/*
 *  Esquire frameworks (tm)
 *  Gateway
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/21/2026 mir0n  created (v2.0.2): pure OAuth (lab) -- the claims keyed by the lower-cased login id in an
 *                   Armoury KeyedExpiringCache (AFTER_ACCESS, evicted by uid); a miss asks keySmith through the
 *                   read-through get, and an answer an eviction overtook is asked again; meter
 *                   esq.biz.gw.pureoauth.total, result hit / miss / stale
 * 11/22/2026 mir0n  v2.0.3 -- lab.pureoauth.PureOAuthClaimsCache -> oauth.ClaimsCache; meter
 *                   esq.biz.gw.oauth.total
 */

package pro.mir0n.esquire.gateway.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.access.EsqUserClaims;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.o11y.BizMeters;
import pro.mir0n.utils.concurrent.IExpiringCache;
import pro.mir0n.utils.concurrent.KeyedExpiringCache;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaimsCache {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + ClaimsCache.class.getName());

    private final IUserClaimsClient delegate;
    //xxx: keyed by login id, evicted by uid
    private final KeyedExpiringCache<String, String, EsqUserClaims> cache;

    public ClaimsCache(IUserClaimsClient delegate, long idleMs) {
        this.delegate = delegate;
        this.cache = new KeyedExpiringCache<>(devLog, idleMs, IExpiringCache.Expiry.AFTER_ACCESS, EsqUserClaims::uid);
    }

    public void start() {
        cache.start();
    }

    public void stop() {
        cache.stop();
    }

    public Mono<EsqUserClaims> claimsOf(String tokenLoginId) {
        Mono<EsqUserClaims> ret;
        String loginId = EsqUtils.loginIdOf(tokenLoginId);
        EsqUserClaims hit = cache.get(loginId);
        if (hit != null) {
            BizMeters.count("esq.biz.gw.oauth.total", "result", "hit");
            ret = Mono.just(hit);
        } else {
            BizMeters.count("esq.biz.gw.oauth.total", "result", "miss");
            devLog.debug("claimsOf: cache MISS for [{}] -- asking keySmith", loginId);
            AtomicInteger asks = new AtomicInteger();
            ret = Mono.fromFuture(() -> cache.get(loginId, id -> ask(id, asks)));
        }
        return ret;
    }

    //xxx: a second ask for one miss means the cache refused the first answer -- the user was evicted while
    // keySmith was asked
    private CompletableFuture<EsqUserClaims> ask(String loginId, AtomicInteger asks) {
        if (asks.incrementAndGet() > 1) {
            BizMeters.count("esq.biz.gw.oauth.total", "result", "stale");
            devLog.debug("ask: [{}] was evicted while asked for -- asking keySmith again", loginId);
        }
        return delegate.fetch(loginId).toFuture();
    }

    //xxx: the event names the user by uid and the cache keys by login id, so the eviction scans the cache.
    // It runs on a save or a move only; a second map uid -> login id would make it a direct removal.
    public void evict(String uid) {
        int removed = cache.evict(uid);
        devLog.debug("evict: uid [{}], removed {}", uid, removed);
    }

    int size() {
        return cache.size();
    }
}

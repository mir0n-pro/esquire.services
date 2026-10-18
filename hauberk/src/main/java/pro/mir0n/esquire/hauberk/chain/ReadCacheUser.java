/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 09/24/2026 mir0n  created: Chain GET /esq-tree on one customer's subtree from the bizTree cache; used by CacheSmall load
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * GET /esq-tree?id={pickedUserId}. One customer's subtree from the bizTree
 * cache, against ReadTreeUser's authoritative walk of the same subtree.
 *
 * Session inputs:
 *   pickedUserId -- user id, supplied by PickRandomFromPool.userChain.
 */
public final class ReadCacheUser {

    private ReadCacheUser() {}

    public static final ChainBuilder chain =
        exec(http("GET /esq-tree (cache, customer subtree)")
            .get("/esq-tree")
            .queryParam("id", "#{pickedUserId}")
            .check(status().is(200)));
}

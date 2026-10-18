/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain GET /esq-cmd?kind=34&id=pickedUserId for a single user; used by Read load
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * GET /esq-cmd?kind=34&id={pickedUserId}. Authoritative read of a USR
 * + sub-entities. Used by SuperLoad scenario (a) to drive read load.
 * Status 200 check; no body assertion -- load is the point.
 *
 * Session inputs:
 *   pickedUserId -- user id, supplied by PickRandomFromPool.userChain.
 */
public final class ReadUserDetail {

    private ReadUserDetail() {}

    public static final ChainBuilder chain =
        exec(http("GET /esq-cmd (read user)")
            .get("/esq-cmd")
            .queryParam("kind", EntityKinds.USR_CLIENT)
            .queryParam("id",   "#{pickedUserId}")
            .check(status().is(200)));
}

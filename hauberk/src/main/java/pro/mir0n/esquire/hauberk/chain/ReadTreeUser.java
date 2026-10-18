/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 09/24/2026 mir0n  created: Chain GET /esq-cmd-tree on one client user's subtree; used by TreeSmall load
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
 * GET /esq-cmd-tree?kind=34&id={pickedUserId}. One customer's subtree --
 * the small tree read a screen makes when a user is opened, against the
 * whole-tree read of ReadTreeRoot. Status 200 check; no body assertion.
 *
 * Session inputs:
 *   pickedUserId -- user id, supplied by PickRandomFromPool.userChain.
 */
public final class ReadTreeUser {

    private ReadTreeUser() {}

    public static final ChainBuilder chain =
        exec(http("GET /esq-cmd-tree (read customer subtree)")
            .get("/esq-cmd-tree")
            .queryParam("kind", EntityKinds.USR_CLIENT)
            .queryParam("id",   "#{pickedUserId}")
            .check(status().is(200)));
}

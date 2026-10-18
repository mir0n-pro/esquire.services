/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 09/24/2026 mir0n  created: Chain GET /esq-cmd?kind=50&id=pickedAcctId for a single account; used by ReadAcct load
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
 * GET /esq-cmd?kind=50&id={pickedAcctId}. Authoritative read of one
 * account. Status 200 check; no body assertion -- load is the point.
 *
 * Session inputs:
 *   pickedAcctId -- account id, supplied by PickRandomFromPool.acctChain.
 */
public final class ReadAcctDetail {

    private ReadAcctDetail() {}

    public static final ChainBuilder chain =
        exec(http("GET /esq-cmd (read account)")
            .get("/esq-cmd")
            .queryParam("kind", EntityKinds.ACCT_CLIENT)
            .queryParam("id",   "#{pickedAcctId}")
            .check(status().is(200)));
}

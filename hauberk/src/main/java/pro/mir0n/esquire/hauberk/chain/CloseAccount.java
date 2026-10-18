/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-cmd-save with status="C" on session acctId
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
 * POST /esq-cmd-save setting status="C" on the account captured by
 * CreateAccount. Used by cleanup, after Withdrawal has zeroed the balance --
 * pacMan rejects the close if balance > 0.
 *
 * Session inputs:
 *   acctId  -- account id to close
 */
public final class CloseAccount {

    private CloseAccount() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-cmd-save (close account)")
            .post("/esq-cmd-save")
            .queryParam("kind", EntityKinds.ACCT_CLIENT)
            .queryParam("id",   "#{acctId}")
            .queryParam("cmd",  "save")
            .header("Content-Type", "application/json")
            .body(StringBody("{\"status\":\"C\"}"))
            .check(status().is(200)));
}

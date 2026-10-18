/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-cmd-new kind=50 client account under session userId; desc = userEmail; saves acctId
 * 08/26/2026 mir0n  the account body no longer carries desc
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
 * POST /esq-cmd-new for kind=50 (Client account) under the user captured by
 * CreateUser.
 *
 * Session inputs:
 *   userId     -- parent user id (from CreateUser)
 * Session outputs:
 *   acctId     -- new account id
 */
public final class CreateAccount {

    private CreateAccount() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-cmd-new (account)")
            .post("/esq-cmd-new")
            .queryParam("kind",     EntityKinds.ACCT_CLIENT)
            .queryParam("parentId", "#{userId}")
            .queryParam("cmd",      "new")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{\"name\":\"acct-#{userSlug}\","
              + "\"ccy\":\"USD\","
              + "\"status\":\"O\","
              + "\"negativeAllowed\":\"N\"}"))
            .check(status().is(200))
            .check(jsonPath("$.id").saveAs("acctId")));
}

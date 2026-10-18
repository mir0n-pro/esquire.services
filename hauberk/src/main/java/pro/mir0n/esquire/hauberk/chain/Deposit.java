/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-acct typeId=1 deposit transaction; amount = session txAmount
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
 * POST /esq-acct, transaction kind=1000 (Deposit). Posts an amount to the
 * account captured by CreateAccount.
 *
 * Session inputs:
 *   acctId    -- target account id
 *   txAmount  -- numeric amount (e.g. 100.00)
 *
 * Note: "kind" query param on /esq-acct is the *account* kind (50/52/54),
 * not the transaction kind. Transaction kind goes in the body as "typeId".
 */
public final class Deposit {

    private Deposit() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-acct (deposit)")
            .post("/esq-acct")
            .queryParam("kind", EntityKinds.ACCT_CLIENT)
            .queryParam("id",   "#{acctId}")
            .queryParam("cmd",  "acct")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{\"typeId\":1,"
              + "\"amount\":#{txAmount},"
              + "\"refCode\":\"cc\","
              + "\"refCode2\":\"TSHIRT-DEP\","
              + "\"desc\":\"hauberk deposit\"}"))
            .check(status().is(200)));
}

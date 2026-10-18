/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-acct typeId=2 withdrawal transaction; amount = session txAmount
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
 * POST /esq-acct, transaction kind=1002 (Withdrawal). Used both for normal
 * load scenarios and by cleanup to bring an account balance to zero before
 * close + delete.
 *
 * Session inputs:
 *   acctId    -- target account id
 *   txAmount  -- numeric amount
 */
public final class Withdrawal {

    private Withdrawal() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-acct (withdrawal)")
            .post("/esq-acct")
            .queryParam("kind", EntityKinds.ACCT_CLIENT)
            .queryParam("id",   "#{acctId}")
            .queryParam("cmd",  "acct")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{\"typeId\":2,"
              + "\"amount\":-#{txAmount},"
              + "\"refCode\":\"cc\","
              + "\"refCode2\":\"TSHIRT-WDR\","
              + "\"desc\":\"hauberk withdrawal\"}"))
            .check(status().is(200)));
}

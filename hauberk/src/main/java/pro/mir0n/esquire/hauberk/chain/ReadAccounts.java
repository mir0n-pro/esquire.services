/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain GET /esq?id=userId for children-of-user (account) list
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * GET /esq?id=${userId} -- list direct children of the user (i.e. the user's
 * accounts) on the tree. Asserts 200.
 *
 * Session inputs:
 *   userId  -- user whose accounts to list
 */
public final class ReadAccounts {

    private ReadAccounts() {}

    public static final ChainBuilder chain =
        exec(http("GET /esq (children of user)")
            .get("/esq")
            .queryParam("id", "#{userId}")
            .check(status().is(200)));
}

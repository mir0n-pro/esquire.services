/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain GET /esq-cmd-tree, filter kinds in {50,52,54}, save list as acctPool for random picks
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
 * Two-step pool fetch scoped to the hauberk subtree, filtered to
 * kind==50 (ACCT_CLIENT, what CreateAccount produces):
 *   1. GET /esq-enode?kind=20&name=hauberk-office-smoke  -> L1 id
 *   2. GET /esq-cmd-tree?kind=20&id={L1}                  -> subtree
 *   3. jsonPath filter for kind==50                       -> acctPool
 *
 * Scoping to L1 + kind=50 keeps the pool clean of pre-seed accounts
 * (id=11 kind=52 Merchant, id=12 kind=50 Client, id=13 kind=54 Paper
 * are seed data). Without scoping, tx chains would POST `kind=50` for
 * a kind=52/54 account and pacMan would reject with
 * ResourceNotFoundException; or worse, leak transactions onto seed accounts.
 *
 * Session outputs:
 *   acctPool -- List<String> of kind=50 account ids under hauberk-office-smoke.
 */
public final class PoolFetchAccounts {

    private PoolFetchAccounts() {}

    public static final ChainBuilder chain =
        exec(session -> session.set("officeName", "hauberk-office-smoke"))
        .exec(LookupOfficeIdByName.chain)
        .exec(http("GET /esq-cmd-tree (fetch account pool, hauberk subtree)")
            .get("/esq-cmd-tree")
            .queryParam("kind", EntityKinds.ORG)
            .queryParam("id",   "#{officeId}")
            .check(status().is(200))
            .check(jsonPath("$[?(@.kind==" + EntityKinds.ACCT_CLIENT + ")].id")
                    .findAll().saveAs("acctPool")));
}

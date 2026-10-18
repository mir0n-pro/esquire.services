/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: end-to-end entity walk -- every CRUD Chain exercised sequentially by a single VU (office, user, account, deposit, reads, cleanup)
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.*;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * End-to-end entity walk: every CRUD Chain exercised at least once
 * sequentially against a running stack, by a single virtual user.
 *
 * Flow:
 *   EnsureOffice    (idempotent: lookup-or-create office by name)
 *   CreateUser      (slug "hauberk-smoke-1", email "...@mir0n.pro")
 *   CreateAccount
 *   Deposit 100
 *   ReadUsers
 *   ReadUserDetails
 *   ReadAccounts
 *   CleanupOfficeByName  (stateless name-driven scrub:
 *                         GET /esq-cmd-tree + best-effort foreach delete)
 *
 * Account-delete inside CleanupOfficeByName succeeds because the
 * pacMan delete branch recognizes accounts in the Test House subtree
 * (ep_path startsWith "1.14.") and purges transactions + forces
 * status=C in memory. No explicit close / withdrawal before delete.
 */
@SimulationInfo("End-to-end entity walk: office/user/account create + deposit + reads + cleanup")
public class EntitySmokeSimulation extends AEsqSimulation {

    // exitHereIfFailed after each step: if a Chain returns non-2xx (or its
    // jsonPath check doesn't match), the scenario stops cleanly here instead
    // of cascading "No attribute named 'userId'" errors through every
    // downstream Chain that depended on the missing session value. The Gatling
    // HTML report still records the actual failed request + response.
    //
    // Exception: CleanupOfficeByName is best-effort by design (no .check on
    // its deletes), and does not need exitHereIfFailed.
    ScenarioBuilder scn = scenario("entity-smoke")
            .exec(session -> session
                    .set("officeName", "hauberk-office-smoke")
                    .set("txAmount",   "100.00"))
            .exec(EnsureOffice.chain)        .exitHereIfFailed()
            .exec(CompareTrees.chain)
            .exec(CreateUser.chain)          .exitHereIfFailed()
            .exec(CompareTrees.chain)
            .exec(CreateAccount.chain)       .exitHereIfFailed()
            .exec(CompareTrees.chain)
            .exec(Deposit.chain)             .exitHereIfFailed()
            .exec(CompareTrees.chain)
            .exec(ReadUsers.chain)           .exitHereIfFailed()
            .exec(ReadUserDetails.chain)     .exitHereIfFailed()
            .exec(ReadAccounts.chain)        .exitHereIfFailed()
            .exec(CleanupOfficeByName.chain)
            .exec(CompareTrees.chain);

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

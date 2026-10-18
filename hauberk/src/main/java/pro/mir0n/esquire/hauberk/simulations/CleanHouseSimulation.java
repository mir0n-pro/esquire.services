/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: stateless name-driven teardown -- find hauberk-office-smoke + delete its subtree bottom-up
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.CleanupOfficeByName;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Standalone teardown: resolves the hauberk-office-smoke office via
 * biztree, then walks its natural-tree subtree and best-effort deletes
 * everything.
 *
 * Use cases:
 *   - Before a fresh sprint test session, to clear leftover residue from
 *     prior failed runs.
 *   - After a biztree restart, when the cache has been refreshed against
 *     DB state and lookup-by-name finally points at a real entity again.
 *
 * Re-run as needed: each pass cleans one matching office (the one
 * biztree's /esq-enode returns first); if multiple matches exist in DB,
 * run the Simulation repeatedly until no more residue remains.
 */
@SimulationInfo("Standalone teardown: find hauberk-office-smoke + delete its subtree")
public class CleanHouseSimulation extends AEsqSimulation {

    ScenarioBuilder scn = scenario("clean-house")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(CleanupOfficeByName.chain);

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: load-test playground builder -- D nested offices x N users x M accounts; concurrent VUs share L1 and branch into per-VU sub-chains
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.*;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Ad-hoc setup harness with three dimensions:
 *   prepare.offices.depth       (D) -- nested offices L1->L2->...->Ld
 *   prepare.clients.per.office  (N) -- users per office (every level)
 *   prepare.accounts.per.client (M) -- accounts per user
 *
 * Total accounts per VU = D * N * M. Example "5 : 20 : 2" = 200.
 *
 * Tree shape (depth=D, parallel VUs):
 *   ROOT
 *     |-- hauberk-office-smoke  (SHARED L1, idempotent via EnsureOffice)
 *           |-- w1-l2 -> w1-l3 -> ... -> w1-l{D}    (worker 1's chain)
 *           |-- w2-l2 -> w2-l3 -> ... -> w2-l{D}    (worker 2's chain)
 *           |-- ...
 *
 * Concurrent VU support: L1 is the single shared "hauberk-office-smoke"
 * (Gatling's session.userId() is unique per VU, used to name sub-offices
 * "w{vu}-l{lvl}"). All VUs find the same L1, then branch into their own
 * chain of sub-offices -- no name collisions in DB or biztree.
 *
 * Each office (including the shared L1) gets N users. Each user gets M
 * accounts. Fat-fill: CreateUser populates every applicable USR + person +
 * addr + bizaddr field with deterministic strings -- read/update tests
 * downstream can assert against known content.
 *
 * No transactions, no cleanup. Run CleanHouse to scrub afterwards: it walks
 * the natural subtree from L1, which catches every nested level + every
 * worker chain in a single pass.
 */
@SimulationInfo("Build playground: D nested offices x N users x M accounts (knobs in props)")
public class PrepareForAnythingSimulation extends AEsqSimulation {

    private final int depth    = EsqHauberkConfig.PREPARE_OFFICES_DEPTH;
    private final int clients  = EsqHauberkConfig.PREPARE_CLIENTS_PER_OFFICE;
    private final int accounts = EsqHauberkConfig.PREPARE_ACCOUNTS_PER_CLIENT;

    ScenarioBuilder scn = scenario("prepare-for-anything")
            // L1: shared root office; every VU resolves to the same id.
            .exec(session -> session
                    .set("officeName", "hauberk-office-smoke")
                    .set("txAmount",   "100.00"))
            .exec(EnsureOffice.chain)
            // Populate L1 with users + accounts (each VU adds its own batch).
            .repeat(clients).on(
                exec(CreateUser.chain)
                .repeat(accounts).on(exec(CreateAccount.chain))
            )
            // L2..Ld: this VU's private chain of sub-offices.
            // .repeat(N, "i") iterates i = 0 .. N-1; we want layers 2..depth.
            .repeat(depth - 1, "lvlIdx").on(
                exec(session -> {
                    int lvl = session.getInt("lvlIdx") + 2;   // 2..depth
                    long vu = session.userId();
                    return session.set("officeName", "w" + vu + "-l" + lvl);
                })
                .exec(CreateSubOffice.chain)
                .repeat(clients).on(
                    exec(CreateUser.chain)
                    .repeat(accounts).on(exec(CreateAccount.chain))
                )
            );

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

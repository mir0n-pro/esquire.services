/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: /esq-move smoke -- USR and ORG re-parenting on a D-deep office chain, with verify-parent + CompareTrees after each move
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.*;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Validates /esq-move for both USR and ORG re-parenting against a
 * configurable office chain (knobs in hauberk.properties):
 *
 *   move.offices.depth        (D, >=3) -- L1->L2->...->Ld
 *   move.clients.per.office   (N)      -- users at each level
 *   move.accounts.per.client  (M)      -- accounts per user (cascade test)
 *
 * Scenario (single VU):
 *   1. Build a D-deep office chain: L1 (shared hauberk-office-smoke) +
 *      L2..Ld (w{vu}-l{n}). Each office gets N users; each user M
 *      accounts. The FIRST user at L2 is saved as `userMoveId`.
 *   2. Move USR L2 -> Ld (the "middle" to the "bottom").
 *   3. CompareTrees + verify user's parent = Ld.
 *   4. Move ORG L3 -> L1 (a "middle" office to the "top"). L3's whole
 *      subtree (L4..Ld + their users + their accounts, including the
 *      just-moved user) cascades along.
 *   5. CompareTrees + verify L3.parent = L1 and user still under Ld.
 *   6. CleanupOfficeByName from L1 (best-effort cascade scrub).
 *
 * Cleanup is one call; the natural-tree walk from L1 catches everything
 * regardless of post-move arrangement.
 *
 * Run:
 *   hauberk.cmd MoveSmoke
 */
@SimulationInfo("/esq-move smoke: USR + ORG re-parenting on a D-deep office chain")
public class MoveSmokeSimulation extends AEsqSimulation {

    private final int depth    = EsqHauberkConfig.MOVE_OFFICES_DEPTH;
    private final int clients  = EsqHauberkConfig.MOVE_CLIENTS_PER_OFFICE;
    private final int accounts = EsqHauberkConfig.MOVE_ACCOUNTS_PER_CLIENT;

    ScenarioBuilder scn = scenario("move-smoke")
            // L1 (shared).
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(EnsureOffice.chain)               .exitHereIfFailed()
            .exec(session -> session.set("l1Id", session.getString("officeId")))
            // Populate L1.
            .repeat(clients).on(
                exec(CreateUser.chain)
                .repeat(accounts).on(exec(CreateAccount.chain))
            )
            .exec(CompareTrees.chain)

            // L2..Ld, nested. Save L2/L3/Ld ids by level; capture first user at L2.
            .repeat(depth - 1, "lvlIdx").on(
                exec(session -> {
                    int level = session.getInt("lvlIdx") + 2;
                    long vu = session.userId();
                    return session.set("officeName", "w" + vu + "-l" + level);
                })
                .exec(CreateSubOffice.chain)        .exitHereIfFailed()
                .exec(session -> {
                    int lvlIdx = session.getInt("lvlIdx");
                    String id = session.getString("officeId");
                    if (lvlIdx == 0)            session = session.set("l2Id",      id);
                    if (lvlIdx == 1)            session = session.set("l3Id",      id);
                    if (lvlIdx == depth - 2)    session = session.set("bottomId",  id);
                    return session;
                })
                // Populate this level: N users * M accounts each.
                .repeat(clients, "uIdx").on(
                    exec(CreateUser.chain)
                    .exec(session -> {
                        // First user at L2 is the one we move.
                        int lvlIdx = session.getInt("lvlIdx");
                        int uIdx   = session.getInt("uIdx");
                        if (lvlIdx == 0 && uIdx == 0) {
                            return session.set("userMoveId", session.getString("userId"));
                        }
                        return session;
                    })
                    .repeat(accounts).on(exec(CreateAccount.chain))
                )
            )
            .exec(CompareTrees.chain)

            // Move 1: USR L2 -> bottom.
            .exec(session -> session
                    .set("moveKind",   EntityKinds.USR_CLIENT)
                    .set("moveId",     session.getString("userMoveId"))
                    .set("moveDestId", session.getString("bottomId")))
            .exec(MoveEntity.chain)                 .exitHereIfFailed()
            .exec(CompareTrees.chain)
            // Verify user's parent = bottom (Ld).
            .exec(http("GET /esq-cmd (verify user parent = Ld)")
                    .get("/esq-cmd")
                    .queryParam("kind", EntityKinds.USR_CLIENT)
                    .queryParam("id",   "#{userMoveId}")
                    .check(status().is(200))
                    .check(jsonPath("$.parentId").saveAs("verifiedUserParent")))
            .exec(session -> {
                String expected = session.getString("bottomId");
                String actual   = session.getString("verifiedUserParent");
                if (!expected.equals(actual)) {
                    System.err.println("[MoveSmoke] FAIL user-move: parent expected="
                            + expected + " actual=" + actual);
                    return session.markAsFailed();
                }
                return session;
            })

            // Move 2: ORG L3 -> L1. L3's subtree (L4..Ld + users + accounts) follows.
            .exec(session -> session
                    .set("moveKind",   EntityKinds.ORG)
                    .set("moveId",     session.getString("l3Id"))
                    .set("moveDestId", session.getString("l1Id")))
            .exec(MoveEntity.chain)                 .exitHereIfFailed()
            .exec(CompareTrees.chain)
            // Verify L3's parent = L1.
            .exec(http("GET /esq-cmd (verify L3 parent = L1)")
                    .get("/esq-cmd")
                    .queryParam("kind", EntityKinds.ORG)
                    .queryParam("id",   "#{l3Id}")
                    .check(status().is(200))
                    .check(jsonPath("$.parentId").saveAs("verifiedL3Parent")))
            .exec(session -> {
                String expected = session.getString("l1Id");
                String actual   = session.getString("verifiedL3Parent");
                if (!expected.equals(actual)) {
                    System.err.println("[MoveSmoke] FAIL org-move: L3 parent expected="
                            + expected + " actual=" + actual);
                    return session.markAsFailed();
                }
                return session;
            })
            // Verify user still under Ld (it followed L3's subtree).
            .exec(http("GET /esq-cmd (verify user still under Ld after org-move)")
                    .get("/esq-cmd")
                    .queryParam("kind", EntityKinds.USR_CLIENT)
                    .queryParam("id",   "#{userMoveId}")
                    .check(status().is(200))
                    .check(jsonPath("$.parentId").saveAs("verifiedUserParent2")))
            .exec(session -> {
                String expected = session.getString("bottomId");
                String actual   = session.getString("verifiedUserParent2");
                if (!expected.equals(actual)) {
                    System.err.println("[MoveSmoke] FAIL cascade: user parent after org-move expected="
                            + expected + " actual=" + actual);
                    return session.markAsFailed();
                }
                return session;
            })

            // Teardown.
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(CleanupOfficeByName.chain)
            .exec(CompareTrees.chain);

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

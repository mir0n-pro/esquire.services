/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Phase 8b race repro -- move-oscillate + concurrent USR creates in moving subtree; self-validating diff DB ep_path vs biztree-derived path, prints PASS/FAIL
 * 06/02/2026 mir0n  v1.2.6 Goal 3: bounded load -- during(dur) replaces forever() so it stops for the verifier;
 *                   200ms/100ms pacing; poll-until-quiescent verifier (5s tick, 5-min budget) replaces the
 *                   single post-load snapshot; int counters + snapshot maps replace the List<Long> collectors
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.*;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Phase 8b race repro: move + concurrent create race.
 *
 * Scenario: while an ORG oscillates between L1 and its original parent,
 * USRs are being created in the deepest office (a descendant of the
 * moving subtree). The new USR's ep_path is computed server-side from
 * the parent org's current ep_path; if the move's cascade is mid-flight,
 * the parent's path may be stale and the new USR persists with the wrong
 * path. The same wrong path propagates to biztree's tree_entity_path
 * via JMS and to KC's esq_rootpath attribute via kcMaster's URQ.
 *
 * Repro shape:
 *   1. Preconditions: PrepareForAnything has been run with depth >= 4
 *      so w1-l2, w1-l3, and w1-l4 exist.
 *   2. Load runs for super.duration.seconds:
 *      - LoadScenarios.MOVE  (super.move.workers VUs)
 *          oscillates w1-l3 between L1 and w1-l2.
 *      - LoadScenarios.CREATE-style scenario (without delete)
 *          super.create.workers VUs each loop CreateUser forever at
 *          the deepest office of the moving subtree.
 *   3. Settle pause (5s).
 *   4. Verification: for each kind=34 USR in the hauberk subtree,
 *      compare the entityPath returned by /esq-cmd-tree (authoritative,
 *      from esq_entity_path.ep_path) against the entityPath returned by
 *      /esq-tree (biztree-derived from tree_path with virtual-folder
 *      segments stripped). Any mismatch = race fired.
 *
 * Same code, two outcomes:
 *   pre-fix:  some USR entityPaths diverge -> FAIL (race reproduced)
 *   post-fix: every USR entityPath agrees -> PASS (regression test)
 *
 * Run:
 *   hauberk.cmd CleanHouse
 *   hauberk.cmd PrepareForAnything --prep-depth 4 --prep-clients 5 --prep-accounts 0
 *   hauberk.cmd RaceMoveCreate --duration 30 --move 2 --create 8
 *   hauberk.cmd CleanHouse
 */
@SimulationInfo("Race 8b: move + concurrent create; DB ep_path vs biztree path divergence")
public class RaceMoveCreateSimulation extends AEsqSimulation {

    // v1.2.6 Goal 3: bounded move scenario -- LoadScenarios.MOVE uses forever() which runs
    // until maxDuration. For the race repro we need the load to STOP at `duration` so the
    // verifier can read a quiet final state. Inline the move-oscillate body wrapped in
    // during(dur) so the load actually terminates at `duration` seconds.
    private final ScenarioBuilder moveScn = scenario("race-move-only")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTopId", session.getString("officeId")))
            .exec(session -> session.set("officeName", "w1-l2"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveOriginalParentId", session.getString("officeId")))
            .exec(session -> session.set("officeName", "w1-l3"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTargetId", session.getString("officeId")))
            // 200ms pace between oscillation cycles -> ~10 esq-move per sec per VU.
            // Matches the single move-worker's sustainable rate (each move = JPA + N cascade
            // broadcasts, ~100ms wall). Without this the unpaced loop fires moves orders of
            // magnitude faster than the worker can drain, the move queue saturates, and the
            // resulting drops + backlog dominate the test result instead of the race itself.
            .during(Duration.ofSeconds(EsqHauberkConfig.SUPER_DURATION_SECONDS)).on(
                pace(Duration.ofMillis(200))
                .exec(session -> session
                        .set("moveKind",   EntityKinds.ORG)
                        .set("moveId",     session.getString("moveTargetId"))
                        .set("moveDestId", session.getString("moveTopId")))
                .exec(MoveEntity.chain)
                .exec(session -> session
                        .set("moveDestId", session.getString("moveOriginalParentId")))
                .exec(MoveEntity.chain)
            );

    // Create-only (no delete) so survivors accumulate for path comparison.
    // Bounded with during(dur) so the load truly stops before the verifier runs.
    ScenarioBuilder createOnlyScn = scenario("race-move-create-only")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(http("GET /esq-cmd-tree (find bottom office)")
                .get("/esq-cmd-tree")
                .queryParam("kind", EntityKinds.ORG)
                .queryParam("id",   "#{officeId}")
                .check(status().is(200))
                .check(jsonPath("$").ofList().saveAs("subtree")))
            .exec(session -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tree =
                        (List<Map<String, Object>>) session.get("subtree");
                String deepestId = null;
                int maxLevel = -1;
                for (Map<String, Object> n : tree) {
                    Object kind  = n.get("kind");
                    Object level = n.get("level");
                    if (kind instanceof Number
                            && ((Number) kind).intValue() == EntityKinds.ORG
                            && level instanceof Number) {
                        int lvl = ((Number) level).intValue();
                        if (lvl > maxLevel) {
                            maxLevel = lvl;
                            deepestId = String.valueOf(n.get("id"));
                        }
                    }
                }
                if (deepestId == null) {
                    System.err.println("[RaceMoveCreate] no office in subtree -- "
                            + "run PrepareForAnything first.");
                    return session.markAsFailed();
                }
                return session.set("officeId", deepestId);
            })
            // 100ms pace between creates -> ~10 USRs/sec per VU. Plenty to fire the race
            // (CREATE arriving inside a move's window) without overwhelming the worker.
            .during(Duration.ofSeconds(EsqHauberkConfig.SUPER_DURATION_SECONDS)).on(
                pace(Duration.ofMillis(100))
                .exec(CreateUser.chain)
            );

    // v1.2.6 Goal 3: poll-until-quiescent verification. After the load stops, bizTree's
    // consumer keeps draining its queue. The verifier samples both trees on a 5s tick; once
    // mismatches drop to zero (bizTree has fully caught up to DB) it declares PASS. If it
    // doesn't converge in maxRetries ticks, it reports the residual count as FAIL.
    private static final int MAX_VERIFY_RETRIES = 60;   // 60 * 5s = 5 minutes settling budget
    private static final int VERIFY_SLEEP_SEC   = 5;

    ScenarioBuilder verifyScn = scenario("race-move-verify")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session
                    .set("convergenceRetry", 0)
                    .set("mismatchCount",    Integer.MAX_VALUE)
                    .set("bizMissingCount",  0)
                    .set("naturalUsrCount",  0)
                    .set("biztreeUsrCount",  0))
            .asLongAs(s -> s.getInt("mismatchCount") > 0 && s.getInt("convergenceRetry") < MAX_VERIFY_RETRIES)
                .on(
                    exec(http("GET /esq-cmd-tree (natural, post-load)")
                        .get("/esq-cmd-tree")
                        .queryParam("kind", EntityKinds.ORG)
                        .queryParam("id",   "#{officeId}")
                        .check(status().is(200))
                        .check(jsonPath("$").ofList().saveAs("naturalNodes")))
                    .exec(http("GET /esq-tree (biztree, post-load)")
                        .get("/esq-tree")
                        .queryParam("id", "#{officeId}")
                        .check(status().is(200))
                        .check(jsonPath("$").ofList().saveAs("biztreeNodes")))
                    .exec(session -> {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> nat =
                                (List<Map<String, Object>>) session.get("naturalNodes");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> biz =
                                (List<Map<String, Object>>) session.get("biztreeNodes");

                        Map<Long, String> natPath = new HashMap<>();
                        for (Map<String, Object> n : nat) {
                            Object k = n.get("kind");
                            Object e = n.get("entityId");
                            if (k instanceof Number
                                    && ((Number) k).intValue() == EntityKinds.USR_CLIENT
                                    && e instanceof Number) {
                                natPath.put(((Number) e).longValue(),
                                        String.valueOf(n.get("entityPath")));
                            }
                        }
                        Map<Long, String> bizPath = new HashMap<>();
                        for (Map<String, Object> n : biz) {
                            Object k = n.get("kind");
                            Object e = n.get("entityId");
                            Object linkId = n.get("linkId");
                            if (k instanceof Number
                                    && ((Number) k).intValue() == EntityKinds.USR_CLIENT
                                    && e instanceof Number
                                    && linkId == null) {
                                bizPath.put(((Number) e).longValue(),
                                        String.valueOf(n.get("entityPath")));
                            }
                        }

                        int mismatches = 0;
                        int bizMissing = 0;
                        for (Map.Entry<Long, String> entry : natPath.entrySet()) {
                            String bizP = bizPath.get(entry.getKey());
                            if (bizP == null) {
                                bizMissing++;
                            } else if (!entry.getValue().equals(bizP)) {
                                mismatches++;
                            }
                        }
                        int retry = session.getInt("convergenceRetry");
                        System.err.println("[RaceMoveCreate] poll #" + retry
                                + " natural=" + natPath.size()
                                + " biztree=" + bizPath.size()
                                + " path-mismatches=" + mismatches
                                + " missing-in-biztree=" + bizMissing);
                        return session
                                .set("mismatchCount",   mismatches)
                                .set("bizMissingCount", bizMissing)
                                .set("naturalUsrCount", natPath.size())
                                .set("biztreeUsrCount", bizPath.size())
                                .set("naturalSnapshot", natPath)
                                .set("biztreeSnapshot", bizPath);
                    })
                    .pause(Duration.ofSeconds(VERIFY_SLEEP_SEC))
                    .exec(s -> s.set("convergenceRetry", s.getInt("convergenceRetry") + 1))
                )
            .exec(session -> {
                int mismatches = session.getInt("mismatchCount");
                int bizMissing = session.getInt("bizMissingCount");
                int retry      = session.getInt("convergenceRetry");
                System.err.println("[RaceMoveCreate] final after " + retry
                        + " poll(s) (" + (retry * VERIFY_SLEEP_SEC) + "s settle): "
                        + "natural=" + session.getInt("naturalUsrCount")
                        + " biztree=" + session.getInt("biztreeUsrCount")
                        + " path-mismatches=" + mismatches
                        + " missing-in-biztree=" + bizMissing);
                if (mismatches > 0) {
                    // Dump first 5 diverging USR paths for diagnostic.
                    @SuppressWarnings("unchecked")
                    Map<Long, String> nat =
                            (Map<Long, String>) session.get("naturalSnapshot");
                    @SuppressWarnings("unchecked")
                    Map<Long, String> biz =
                            (Map<Long, String>) session.get("biztreeSnapshot");
                    int printed = 0;
                    if (nat != null && biz != null) {
                        for (Map.Entry<Long, String> entry : nat.entrySet()) {
                            String bizP = biz.get(entry.getKey());
                            if (bizP != null && !entry.getValue().equals(bizP)) {
                                System.err.println("[RaceMoveCreate]  id=" + entry.getKey()
                                        + " natural=" + entry.getValue()
                                        + " biztree=" + bizP);
                                if (++printed >= 5) break;
                            }
                        }
                    }
                    System.err.println("[RaceMoveCreate] FAIL: did not converge within "
                            + (MAX_VERIFY_RETRIES * VERIFY_SLEEP_SEC) + "s -- "
                            + mismatches + " kind=34 USRs still diverge.");
                    return session.markAsFailed();
                }
                if (bizMissing > 0) {
                    System.err.println("[RaceMoveCreate] WARN: "
                            + bizMissing + " USRs missing in biztree "
                            + "(unrelated cache-load race surface).");
                }
                System.err.println("[RaceMoveCreate] PASS: every kind=34 USR's "
                        + "entityPath agrees between DB and biztree (converged in "
                        + (retry * VERIFY_SLEEP_SEC) + "s after load).");
                return session;
            });

    {
        int mW = EsqHauberkConfig.SUPER_MOVE_WORKERS;
        int cW = EsqHauberkConfig.SUPER_CREATE_WORKERS;
        int dur = EsqHauberkConfig.SUPER_DURATION_SECONDS;
        if (mW < 1 || cW < 1) {
            throw new IllegalStateException(
                "RaceMoveCreateSimulation: both super.move.workers and "
                + "super.create.workers must be >= 1.");
        }

        System.err.println();
        System.err.println("================================================");
        System.err.println(" RACE 8b -- MOVE + CREATE PATH RACE REPRO");
        System.err.println(" Move oscillates w1-l3; concurrent creates at deepest office.");
        System.err.println("================================================");
        System.err.println();

        List<PopulationBuilder> pops = new ArrayList<>();
        pops.add(moveScn        .injectOpen(atOnceUsers(mW)));
        pops.add(createOnlyScn  .injectOpen(atOnceUsers(cW)));
        // v1.2.6 Goal 3: verifier injects right when the load ends and then poll-loops on its
        // own until the bizTree side fully converges (or the 5-min budget is exhausted).
        pops.add(verifyScn      .injectOpen(
                nothingFor(Duration.ofSeconds(dur + 5)),
                atOnceUsers(1)));

        // maxDuration must cover load (dur) + small grace (5s) + worst-case verifier poll
        // budget (MAX_VERIFY_RETRIES * VERIFY_SLEEP_SEC = 300s) + slack.
        setUp(pops)
            .maxDuration(Duration.ofSeconds(dur + 5 + (MAX_VERIFY_RETRIES * VERIFY_SLEEP_SEC) + 30))
            .protocols(httpProtocol);
    }
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: Phase 8a race repro -- heavy create-load while operator restarts biztree mid-flight; self-validating diff DB vs cache, prints PASS/FAIL
 * 05/23/2026 mir0n  automated the operator restart: a "race-restart" population fires
 *                   Cmd.run("restart-biztree") ~15s into the load (was a manual banner); the verify
 *                   population waits via WaitCacheReady before diffing; maxDuration margin raised for the boot.
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.*;
import pro.mir0n.hauberk.chain.Cmd;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Phase 8a race repro: cache-load race.
 *
 * Scenario: while heavy USR creates stream through enyMan, the operator
 * restarts biztree. Some creates land in the timing window where biztree
 * is rebuilding its H2 cache from PG and not yet consuming JMS broadcasts.
 * Those entities exist in DB but go missing from the cache.
 *
 * Repro shape:
 *   1. The sim prints a prominent banner at start:
 *        ">>> NOW: docker compose restart biztree  <<<"
 *      Operator runs that command in a separate terminal.
 *   2. Create load runs for super.duration.seconds: super.create.workers VUs
 *      each loop CreateUser at the deepest office of hauberk subtree.
 *   3. Settling pause (5s) for any in-flight JMS messages.
 *   4. Verification: pull /esq-cmd-tree (authoritative natural tree) and
 *      /esq-tree (biztree cache), filter both for kind=34 USRs in the
 *      hauberk subtree. Compare entityId sets.
 *        cmpMissingInBiztree > 0  =>  RACE REPRODUCED (pre-fix expected)
 *        cmpMissingInBiztree == 0 =>  CLEAN (post-fix expected)
 *
 * Self-validating: prints PASS/FAIL line to stderr. The same sim doubles
 * as the Phase 9 regression test -- pre-fix it fails, post-fix it passes.
 *
 * Run:
 *   hauberk.cmd PrepareForAnything --prep-depth 4 --prep-clients 5 --prep-accounts 0
 *   hauberk.cmd RaceCacheLoad --duration 30 --create 8
 *   # ... when banner shows, in another terminal:
 *   #     docker compose restart biztree
 *   hauberk.cmd CleanHouse
 */
@SimulationInfo("Race 8a: cache-load race; create-load + manual biztree restart mid-flight")
public class RaceCacheLoadSimulation extends AEsqSimulation {

    // Use LoadScenarios.CREATE for the load phase: it already finds the
    // deepest office in the hauberk subtree and loops create+delete forever.
    // For the race repro we deliberately do NOT delete in the loop -- we
    // want survivors in DB so the missing-in-biztree set is non-trivial.
    // So we build a custom variant inline.
    ScenarioBuilder createOnlyScn = scenario("race-create-only")
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
                    System.err.println("[RaceCacheLoad] no office in subtree -- "
                            + "run PrepareForAnything first.");
                    return session.markAsFailed();
                }
                return session.set("officeId", deepestId);
            })
            // No delete -- want survivors in DB for the diff.
            .forever().on(exec(CreateUser.chain));

    // Restart trigger: fires the biztree restart mid-load (was the manual operator step).
    ScenarioBuilder restartScn = scenario("race-restart")
            .exec(Cmd.run("restart-biztree"));

    // Verification scenario: single VU, runs AFTER the load (separate
    // PopulationBuilder with nothingFor delay).
    ScenarioBuilder verifyScn = scenario("race-verify")
            // biztree was restarted mid-load -- wait until it is serving again before reading.
            .exec(WaitCacheReady.chain)
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(http("GET /esq-cmd-tree (natural, post-load)")
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

                // Collect kind=34 USR entityIds from each side.
                Set<Long> natUsers = new HashSet<>();
                for (Map<String, Object> n : nat) {
                    Object k = n.get("kind");
                    Object e = n.get("entityId");
                    if (k instanceof Number
                            && ((Number) k).intValue() == EntityKinds.USR_CLIENT
                            && e instanceof Number) {
                        natUsers.add(((Number) e).longValue());
                    }
                }
                Set<Long> bizUsers = new HashSet<>();
                for (Map<String, Object> n : biz) {
                    Object k = n.get("kind");
                    Object e = n.get("entityId");
                    Object linkId = n.get("linkId");
                    if (k instanceof Number
                            && ((Number) k).intValue() == EntityKinds.USR_CLIENT
                            && e instanceof Number
                            && linkId == null) {
                        bizUsers.add(((Number) e).longValue());
                    }
                }

                Set<Long> missingInBiz = new HashSet<>(natUsers);
                missingInBiz.removeAll(bizUsers);
                Set<Long> staleInBiz = new HashSet<>(bizUsers);
                staleInBiz.removeAll(natUsers);

                System.err.println("[RaceCacheLoad] naturalUsers=" + natUsers.size()
                        + " biztreeUsers=" + bizUsers.size()
                        + " missingInBiztree=" + missingInBiz.size()
                        + " staleInBiztree=" + staleInBiz.size());
                if (!missingInBiz.isEmpty()) {
                    List<Long> sample = new ArrayList<>(missingInBiz);
                    int cap = Math.min(20, sample.size());
                    System.err.println("[RaceCacheLoad] FAIL: RACE REPRODUCED -- "
                            + missingInBiz.size()
                            + " kind=34 USRs in DB but NOT in biztree cache. "
                            + "Sample ids: " + sample.subList(0, cap)
                            + (sample.size() > cap ? "..." : ""));
                    return session.markAsFailed();
                }
                if (!staleInBiz.isEmpty()) {
                    System.err.println("[RaceCacheLoad] WARN: " + staleInBiz.size()
                            + " kind=34 USRs in biztree cache but NOT in DB "
                            + "(orphan entries; not the race we're looking for, "
                            + "but worth noting).");
                }
                System.err.println("[RaceCacheLoad] PASS: cache and DB agree on "
                        + "kind=34 USR set.");
                return session;
            });

    {
        int cW = EsqHauberkConfig.SUPER_CREATE_WORKERS;
        int dur = EsqHauberkConfig.SUPER_DURATION_SECONDS;
        if (cW < 1) {
            throw new IllegalStateException(
                "RaceCacheLoadSimulation: super.create.workers must be >= 1.");
        }

        // Fully automated: the race-restart population fires `cmd restart-biztree` mid-load
        // (~15s in) -- no operator. The verify population waits for biztree to come back.
        System.err.println();
        System.err.println("================================================");
        System.err.println(" RACE 8a -- CACHE-LOAD RACE (automated restart)");
        System.err.println("================================================");
        System.err.println();

        // Load runs for the full duration; verification starts after a
        // 5s settle pause (gives biztree time to fully come back online
        // and process any in-flight JMS messages).
        List<PopulationBuilder> pops = new ArrayList<>();
        pops.add(createOnlyScn.injectOpen(atOnceUsers(cW)));
        // restart biztree mid-load (~15s in) -- the automated former-operator step.
        pops.add(restartScn.injectOpen(
                nothingFor(Duration.ofSeconds(15)),
                atOnceUsers(1)));
        pops.add(verifyScn.injectOpen(
                nothingFor(Duration.ofSeconds(dur + 5)),
                atOnceUsers(1)));

        setUp(pops)
            // maxDuration covers load + restart + biztree boot + verify with margin.
            .maxDuration(Duration.ofSeconds(dur + 90))
            .protocols(httpProtocol);
    }
}

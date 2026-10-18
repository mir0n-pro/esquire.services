/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 06/01/2026 mir0n  created: Phase 8c race repro -- move-oscillate + concurrent USR creates;
 *                   self-validating diff DB ep_path vs KC user attribute esq_rootpath. Hits
 *                   the race-8c silent-skip site at KcIdentityService.updateEntityPath: an
 *                   EVENT_UPDATE_PATH URQ that arrives at kcMaster BEFORE the corresponding
 *                   user-create URQ does no-ops (devLog "no KC user found, skipping"), leaving
 *                   the KC user's esq_rootpath frozen at the pre-move parent path forever.
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
import pro.mir0n.hauberk.config.AuthConfig;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Phase 8c race repro: move + concurrent create, *KC side*.
 *
 * Race 8b says the DB-side path-mint race fires when a CREATE reads a parent
 * path mid-move and persists a stale value to esq_entity_path. Race 8c is the
 * downstream KC variant: even when 8b is closed (DB ep_path correct, biztree
 * tree_path correct), the KC user's esq_rootpath attribute can still be stale.
 *
 * The race in plain steps:
 *   1. Hauberk POSTs /esq-cmd-new for a USR while a parent ORG move is mid-flight.
 *   2. enyMan publishes EVENT_CREATE (for the new USR) on the entity broadcast.
 *   3. enyMan publishes EVENT_UPDATE_PATH(s) for descendants of the moved ORG --
 *      the new USR may or may not be in that list depending on the v1.2.6 Goal 3
 *      reconcile (see RaceMoveCreate / 8b for the DB-side picture).
 *   4. kcMaster's URQ listener picks up the messages. If EVENT_UPDATE_PATH for the
 *      new USR arrives BEFORE the CREATE URQ for that same user has been processed,
 *      KcIdentityService.updateEntityPath finds no KC user yet and logs:
 *          devLog.debug("KC | MOVE | entityId='{}' : no KC user found, skipping")
 *      then exits. The CREATE URQ arrives later and creates the user, but the
 *      esq_rootpath set on creation is the path the CREATE URQ carried -- which
 *      is whatever enyMan computed at CREATE time, NOT the post-move path. The
 *      reconcile's EVENT_UPDATE_PATH has already been silently dropped.
 *
 * Repro shape:
 *   Same load as RaceMoveCreate (8b): one VU oscillates a parent ORG, several VUs
 *   POST /esq-cmd-new under the moving subtree. The verifier diffs DB ep_path
 *   (authoritative -- from /esq-cmd-tree) against KC's esq_rootpath attribute
 *   (queried via the master-realm admin REST API). Poll-until-stable: kcMaster's
 *   URQ queue and KC writes both run async, so the verifier polls on a 5s tick
 *   for up to 5 minutes before declaring final state.
 *
 * Authentication: business reads go through the gateway with the standard hauberk
 * KC bearer (HauberkSimulation handles that). The verifier additionally fetches
 * a master-realm admin token (KcAdminAuth chain) so it can call
 * /admin/realms/esquire/users on the KC admin REST API.
 *
 * Same code, two outcomes:
 *   pre-fix:  some USR esq_rootpath stays at the pre-move value -> FAIL
 *   post-fix: every USR esq_rootpath matches DB ep_path           -> PASS
 *
 * Cleanup note: this sim leaves both DB rows AND KC users behind in the
 * playground. Standard CleanupOfficeByName handles the DB side. Use the
 * CleanupKcOrphans chain (or KcCleanupSimulation) to remove the KC users.
 *
 * Run:
 *   hauberk.cmd CleanHouse
 *   hauberk.cmd PrepareForAnything --prep-depth 4 --prep-clients 5 --prep-accounts 0
 *   hauberk.cmd RaceMoveCreateKc --duration 30 --move 2 --create 8
 *   hauberk.cmd CleanHouse
 *   hauberk.cmd KcCleanup           (or call CleanupKcOrphans from another sim)
 */
@SimulationInfo("Race 8c: move + concurrent create; DB ep_path vs KC esq_rootpath divergence")
public class RaceMoveCreateKcSimulation extends AEsqSimulation {

    // Move-oscillate load: same shape as RaceMoveCreate (8b). Paced 200ms per oscillation.
    private final ScenarioBuilder moveScn = scenario("race-kc-move-only")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTopId", session.getString("officeId")))
            .exec(session -> session.set("officeName", "w1-l2"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveOriginalParentId", session.getString("officeId")))
            .exec(session -> session.set("officeName", "w1-l3"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTargetId", session.getString("officeId")))
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

    // Create USR + Connect (keySmith /esq-key-save) so a KC user is actually minted.
    // race-8c targets the ordering between keySmith's CREATE URQ and enyMan's
    // MOVE EVENT_UPDATE_PATH URQ; without the connect step kcMaster never creates
    // a KC user and there is nothing to mismatch. Slow pace: race-8c is about
    // ordering, not throughput -- a handful of users is enough to fire the race.
    private final ScenarioBuilder createOnlyScn = scenario("race-kc-create-only")
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
                    System.err.println("[RaceMoveCreateKc] no office in subtree -- "
                            + "run PrepareForAnything first.");
                    return session.markAsFailed();
                }
                return session.set("officeId", deepestId);
            })
            .during(Duration.ofSeconds(EsqHauberkConfig.SUPER_DURATION_SECONDS)).on(
                pace(Duration.ofMillis(1000))
                .exec(CreateUser.chain)
                .exec(ConnectUser.chain)
            );

    // Poll-until-stable verifier (5s tick * 60 = 5 minute budget).
    private static final int MAX_VERIFY_RETRIES = 60;
    private static final int VERIFY_SLEEP_SEC   = 5;

    private final ScenarioBuilder verifyScn = scenario("race-kc-verify")
            .exec(KcAdminAuth.chain)
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session
                    .set("convergenceRetry", 0)
                    // Seed: mismatchCount sentinel so first iteration always runs;
                    // kcUsrCount/prevKcUsrCount drive the KC-stability check.
                    .set("mismatchCount",    Integer.MAX_VALUE)
                    .set("kcMissingCount",   0)
                    .set("naturalUsrCount",  0)
                    .set("kcUsrCount",       0)
                    .set("prevKcUsrCount",  -1)
                    .set("kcStableTicks",    0))
            // Keep polling while EITHER any DB-vs-KC drift remains OR the KC count
            // is still growing (kcMaster URQ backlog draining). race-8c is only a
            // real verdict once kcMaster has caught up; until then a zero-mismatch
            // reading is vacuous. "Caught up" = KC count stable across 3 ticks.
            .asLongAs(s -> (s.getInt("mismatchCount") > 0 || s.getInt("kcStableTicks") < 3)
                        && s.getInt("convergenceRetry") < MAX_VERIFY_RETRIES)
                .on(
                    // Refresh the master-realm admin token every tick. It is a 60s-TTL
                    // token and the settle-poll can run for minutes; without refresh the
                    // GET KC users below 401s, the saveAs never updates, and the verifier
                    // compares DB against a FROZEN mid-drain snapshot -- a false mismatch.
                    // (KcAdminAuth's own doc says to re-call it for long-running sims.)
                    exec(KcAdminAuth.chain)
                    .exec(http("GET /esq-cmd-tree (natural, post-load)")
                        .get("/esq-cmd-tree")
                        .queryParam("kind", EntityKinds.ORG)
                        .queryParam("id",   "#{officeId}")
                        .check(status().is(200))
                        .check(jsonPath("$").ofList().saveAs("naturalNodes")))
                    .exec(http("GET KC users (hauberk-* w/ attributes)")
                        .get(AuthConfig.BASE
                            + "/admin/realms/" + AuthConfig.REALM
                            + "/users?search=hauberk-&briefRepresentation=false&max=1000")
                        .header("Authorization", "Bearer #{kcAdminToken}")
                        // Token-relay-agnostic: this hits KC directly, not the gateway,
                        // so HauberkSimulation's Authorization header doesn't apply.
                        .check(status().is(200))
                        .check(jsonPath("$").ofList().saveAs("kcUsers")))
                    .exec(session -> {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> nat =
                                (List<Map<String, Object>>) session.get("naturalNodes");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> kc =
                                (List<Map<String, Object>>) session.get("kcUsers");

                        // Natural tree: USR id -> ep_path.
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
                        // KC: esq_uid -> esq_rootpath. attributes is Map<String,List<String>>.
                        Map<Long, String> kcPath = new HashMap<>();
                        for (Map<String, Object> u : kc) {
                            Object usernameObj = u.get("username");
                            if (usernameObj == null) continue;
                            String username = usernameObj.toString();
                            if (!username.startsWith("hauberk-")) continue;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> attrs =
                                    (Map<String, Object>) u.get("attributes");
                            if (attrs == null) continue;
                            Long uid = null;
                            String rootpath = null;
                            Object uidAttr  = attrs.get("esq_uid");
                            Object pathAttr = attrs.get("esq_rootpath");
                            if (uidAttr instanceof List
                                    && !((List<?>) uidAttr).isEmpty()) {
                                try {
                                    uid = Long.parseLong(
                                            ((List<?>) uidAttr).get(0).toString());
                                } catch (NumberFormatException ignore) {}
                            }
                            if (pathAttr instanceof List
                                    && !((List<?>) pathAttr).isEmpty()) {
                                rootpath = ((List<?>) pathAttr).get(0).toString();
                            }
                            if (uid != null && rootpath != null) {
                                kcPath.put(uid, rootpath);
                            }
                        }

                        // Race-8c verdict comparison: iterate KC users (the KC user
                        // is the artifact we are checking). For each KC user, look
                        // up the DB ep_path by esq_uid; if it differs from the KC
                        // esq_rootpath, race-8c has stranded a stale path in KC.
                        // natural USRs not yet in KC are reported as kcMissing (they
                        // ARE coming -- kcMaster is still draining) and gate the
                        // convergence check below.
                        int mismatches = 0;
                        int kcMissing  = 0;
                        for (Map.Entry<Long, String> entry : natPath.entrySet()) {
                            if (!kcPath.containsKey(entry.getKey())) {
                                kcMissing++;
                            }
                        }
                        for (Map.Entry<Long, String> entry : kcPath.entrySet()) {
                            String dbP = natPath.get(entry.getKey());
                            if (dbP == null) continue; // KC user with no DB row -- orphan, unrelated
                            if (!entry.getValue().equals(dbP)) {
                                mismatches++;
                            }
                        }
                        int prevKc       = session.getInt("prevKcUsrCount");
                        int currKc       = kcPath.size();
                        int stableTicks  = session.getInt("kcStableTicks");
                        // KC stability gate: prev == -1 is the first tick (no prior to
                        // compare). Otherwise, if count grew, reset; if equal, tick++.
                        if (prevKc < 0 || currKc != prevKc) {
                            stableTicks = 0;
                        } else {
                            stableTicks++;
                        }
                        int retry = session.getInt("convergenceRetry");
                        System.err.println("[RaceMoveCreateKc] poll #" + retry
                                + " natural=" + natPath.size()
                                + " kc=" + currKc
                                + " (prev=" + prevKc + " stable-ticks=" + stableTicks + "/3)"
                                + " path-mismatches=" + mismatches
                                + " missing-in-kc=" + kcMissing);
                        return session
                                .set("mismatchCount",    mismatches)
                                .set("kcMissingCount",   kcMissing)
                                .set("naturalUsrCount",  natPath.size())
                                .set("kcUsrCount",       currKc)
                                .set("prevKcUsrCount",   currKc)
                                .set("kcStableTicks",    stableTicks)
                                .set("naturalSnapshot",  natPath)
                                .set("kcSnapshot",       kcPath);
                    })
                    .pause(Duration.ofSeconds(VERIFY_SLEEP_SEC))
                    .exec(s -> s.set("convergenceRetry", s.getInt("convergenceRetry") + 1))
                )
            .exec(session -> {
                int mismatches = session.getInt("mismatchCount");
                int kcMissing  = session.getInt("kcMissingCount");
                int retry      = session.getInt("convergenceRetry");
                System.err.println("[RaceMoveCreateKc] final after " + retry
                        + " poll(s) (" + (retry * VERIFY_SLEEP_SEC) + "s settle): "
                        + "natural=" + session.getInt("naturalUsrCount")
                        + " kc=" + session.getInt("kcUsrCount")
                        + " path-mismatches=" + mismatches
                        + " missing-in-kc=" + kcMissing);
                if (mismatches > 0) {
                    @SuppressWarnings("unchecked")
                    Map<Long, String> nat =
                            (Map<Long, String>) session.get("naturalSnapshot");
                    @SuppressWarnings("unchecked")
                    Map<Long, String> kc =
                            (Map<Long, String>) session.get("kcSnapshot");
                    int printed = 0;
                    if (nat != null && kc != null) {
                        for (Map.Entry<Long, String> entry : kc.entrySet()) {
                            String dbP = nat.get(entry.getKey());
                            if (dbP != null && !entry.getValue().equals(dbP)) {
                                System.err.println("[RaceMoveCreateKc]  id=" + entry.getKey()
                                        + " db=" + dbP
                                        + " kc=" + entry.getValue());
                                if (++printed >= 5) break;
                            }
                        }
                    }
                    System.err.println("[RaceMoveCreateKc] FAIL: race-8c reproduced -- "
                            + mismatches + " KC users have esq_rootpath stale relative to DB ep_path.");
                    return session.markAsFailed();
                }
                if (kcMissing > 0) {
                    System.err.println("[RaceMoveCreateKc] WARN: "
                            + kcMissing + " DB USRs have no KC counterpart "
                            + "(connect step likely did not run for them, or kcMaster CREATE URQ was dropped).");
                }
                System.err.println("[RaceMoveCreateKc] PASS: every kind=34 USR's "
                        + "esq_rootpath in KC agrees with DB ep_path (converged in "
                        + (retry * VERIFY_SLEEP_SEC) + "s after load).");
                return session;
            });

    {
        int mW = EsqHauberkConfig.SUPER_MOVE_WORKERS;
        int cW = EsqHauberkConfig.SUPER_CREATE_WORKERS;
        int dur = EsqHauberkConfig.SUPER_DURATION_SECONDS;
        if (mW < 1 || cW < 1) {
            throw new IllegalStateException(
                "RaceMoveCreateKcSimulation: both super.move.workers and "
                + "super.create.workers must be >= 1.");
        }

        System.err.println();
        System.err.println("================================================");
        System.err.println(" RACE 8c -- MOVE + CREATE / KC esq_rootpath RACE REPRO");
        System.err.println(" Move oscillates w1-l3; concurrent creates at deepest office;");
        System.err.println(" verifies DB ep_path == KC user attribute esq_rootpath.");
        System.err.println("================================================");
        System.err.println();

        List<PopulationBuilder> pops = new ArrayList<>();
        pops.add(moveScn       .injectOpen(atOnceUsers(mW)));
        pops.add(createOnlyScn .injectOpen(atOnceUsers(cW)));
        pops.add(verifyScn     .injectOpen(
                nothingFor(Duration.ofSeconds(dur + 5)),
                atOnceUsers(1)));

        setUp(pops)
            .maxDuration(Duration.ofSeconds(dur + 5 + (MAX_VERIFY_RETRIES * VERIFY_SLEEP_SEC) + 30))
            .protocols(httpProtocol);
    }
}

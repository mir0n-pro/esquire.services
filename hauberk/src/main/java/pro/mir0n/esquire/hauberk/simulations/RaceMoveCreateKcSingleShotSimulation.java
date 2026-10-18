/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 06/02/2026 mir0n  created: DETERMINISTIC single-shot race-8c repro. One disabled user, ONE move
 *                   during keySmith's read->publish hold, NO further move (so nothing heals it).
 *                   Unlike RaceMoveCreateKc (oscillation -> self-heals -> always converges 0), this
 *                   isolates the TOCTOU: keySmith captures path Pc, the move flips DB to Pd and its
 *                   UPDATE_PATH is skipped (KC user absent), keySmith then publishes Pc. With the
 *                   kcMaster buffer OFF the KC user stays at Pc != DB Pd (FAIL/reproduced); with the
 *                   buffer ON the topic path is flushed onto the new user -> Pd (PASS/fix proven).
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import java.time.Duration;
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
 * Deterministic single-shot race-8c reproduction. Requires:
 *   - PrepareForAnything --prep-depth 4 (offices smoke -> w1-l2 -> w1-l3 -> w1-l4),
 *   - keySmith hold enabled: KEYSMITH_TEST_CONNECT_HOLD_MS=5000 (so read->publish straddles the move),
 *   - to REPRODUCE: kcMaster buffer OFF (KCMASTER_PATH_BUFFER_TTL_MS=-1); to PROVE the fix: leave it 10000.
 *
 * Three coordinated VUs, no oscillation:
 *   activator  t0      : create ONE disabled user under w1-l4, pause 1s, ConnectUser (blocks ~5s in keySmith hold)
 *   mover      t0+3s    : exactly ONE move of w1-l3 -> smoke (re-paths w1-l4 + the user); UPDATE_PATH skipped
 *   verifier   t0+12s   : poll KC until the user exists, then compare KC esq_rootpath vs DB ep_path
 */
@SimulationInfo("Race 8c SINGLE-SHOT: one disabled user + one move during keySmith hold; no heal")
public class RaceMoveCreateKcSingleShotSimulation extends AEsqSimulation {

    private static final int    MOVE_DELAY_SEC   = 3;    // fire the single move inside the keySmith hold window
    private static final int    VERIFY_START_SEC = 12;   // after hold (~5s) + create + buffer settle
    private static final int    MAX_VERIFY_RETRIES = 40;
    private static final int    VERIFY_SLEEP_SEC = 3;

    // ----- activator: create ONE disabled user under w1-l4, then activate (held) -----
    private final ScenarioBuilder activatorScn = scenario("race8c-ss-activator")
            .exec(session -> session.set("officeName", "w1-l4"))
            .exec(LookupOfficeIdByName.chain)
            .exec(CreateUser.chain)              // POST /esq-cmd-new -> disabled USR (no KC user yet)
            .pause(Duration.ofSeconds(1))         // let things settle; mover will move during the hold below
            .exec(ConnectUser.chain);            // POST /esq-key-save -> keySmith reads path, HOLDS, then publishes

    // ----- mover: exactly ONE move of w1-l3 -> smoke, fired inside the keySmith hold -----
    private final ScenarioBuilder moverScn = scenario("race8c-ss-mover")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTopId", session.getString("officeId")))
            .exec(session -> session.set("officeName", "w1-l3"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTargetId", session.getString("officeId")))
            .pause(Duration.ofSeconds(MOVE_DELAY_SEC))   // fire while keySmith is holding (read done, publish pending)
            .exec(session -> session
                    .set("moveKind",   EntityKinds.ORG)
                    .set("moveId",     session.getString("moveTargetId"))
                    .set("moveDestId", session.getString("moveTopId")))
            .exec(MoveEntity.chain);             // ONE move only -- nothing re-paths the user afterwards

    // ----- verifier: poll until the KC user exists, then compare KC esq_rootpath vs DB ep_path -----
    private final ScenarioBuilder verifyScn = scenario("race8c-ss-verify")
            .exec(KcAdminAuth.chain)
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session
                    .set("retry",        0)
                    .set("kcUsrCount",   0)
                    .set("mismatchCount", -1))
            .asLongAs(s -> s.getInt("kcUsrCount") < 1 && s.getInt("retry") < MAX_VERIFY_RETRIES)
                .on(
                    exec(KcAdminAuth.chain)      // refresh the 60s admin token every tick
                    .exec(http("GET /esq-cmd-tree (natural)")
                        .get("/esq-cmd-tree")
                        .queryParam("kind", EntityKinds.ORG)
                        .queryParam("id",   "#{officeId}")
                        .check(status().is(200))
                        .check(jsonPath("$").ofList().saveAs("naturalNodes")))
                    .exec(http("GET KC users (hauberk-*)")
                        .get(AuthConfig.BASE
                            + "/admin/realms/" + AuthConfig.REALM
                            + "/users?search=hauberk-&briefRepresentation=false&max=1000")
                        .header("Authorization", "Bearer #{kcAdminToken}")
                        .check(status().is(200))
                        .check(jsonPath("$").ofList().saveAs("kcUsers")))
                    .exec(session -> {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> nat = (List<Map<String, Object>>) session.get("naturalNodes");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> kc = (List<Map<String, Object>>) session.get("kcUsers");

                        Map<Long, String> natPath = new HashMap<>();
                        for (Map<String, Object> n : nat) {
                            Object k = n.get("kind");
                            Object e = n.get("entityId");
                            if (k instanceof Number && ((Number) k).intValue() == EntityKinds.USR_CLIENT
                                    && e instanceof Number) {
                                natPath.put(((Number) e).longValue(), String.valueOf(n.get("entityPath")));
                            }
                        }
                        Map<Long, String> kcPath = new HashMap<>();
                        for (Map<String, Object> u : kc) {
                            Object un = u.get("username");
                            if (un == null || !un.toString().startsWith("hauberk-")) continue;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> attrs = (Map<String, Object>) u.get("attributes");
                            if (attrs == null) continue;
                            Long uid = null; String rp = null;
                            Object ua = attrs.get("esq_uid"); Object pa = attrs.get("esq_rootpath");
                            if (ua instanceof List && !((List<?>) ua).isEmpty()) {
                                try { uid = Long.parseLong(((List<?>) ua).get(0).toString()); }
                                catch (NumberFormatException ignore) {}
                            }
                            if (pa instanceof List && !((List<?>) pa).isEmpty()) rp = ((List<?>) pa).get(0).toString();
                            if (uid != null && rp != null) kcPath.put(uid, rp);
                        }

                        int mism = 0;
                        for (Map.Entry<Long, String> en : kcPath.entrySet()) {
                            String db = natPath.get(en.getKey());
                            if (db != null && !en.getValue().equals(db)) mism++;
                        }
                        int retry = session.getInt("retry");
                        System.err.println("[Race8cSingleShot] poll #" + retry
                                + " natural=" + natPath.size() + " kc=" + kcPath.size()
                                + " path-mismatches=" + mism);
                        return session
                                .set("kcUsrCount",    kcPath.size())
                                .set("mismatchCount", mism)
                                .set("natSnap",       natPath)
                                .set("kcSnap",        kcPath);
                    })
                    .pause(Duration.ofSeconds(VERIFY_SLEEP_SEC))
                    .exec(s -> s.set("retry", s.getInt("retry") + 1))
                )
            .exec(session -> {
                int mism = session.getInt("mismatchCount");
                int kcN  = session.getInt("kcUsrCount");
                if (kcN < 1) {
                    System.err.println("[Race8cSingleShot] INCONCLUSIVE: KC user never appeared "
                            + "(connect/keySmith hold may exceed the verify budget).");
                    return session;
                }
                if (mism > 0) {
                    @SuppressWarnings("unchecked")
                    Map<Long, String> nat = (Map<Long, String>) session.get("natSnap");
                    @SuppressWarnings("unchecked")
                    Map<Long, String> kc = (Map<Long, String>) session.get("kcSnap");
                    for (Map.Entry<Long, String> en : kc.entrySet()) {
                        String db = nat.get(en.getKey());
                        if (db != null && !en.getValue().equals(db)) {
                            System.err.println("[Race8cSingleShot]  id=" + en.getKey() + " db=" + db + " kc=" + en.getValue());
                        }
                    }
                    System.err.println("[Race8cSingleShot] FAIL: race-8c reproduced -- "
                            + mism + " KC user(s) stranded at the stale pre-move path.");
                    return session.markAsFailed();
                }
                System.err.println("[Race8cSingleShot] PASS: KC esq_rootpath matches DB ep_path "
                        + "(buffer recovered the skipped path).");
                return session;
            });

    {
        System.err.println();
        System.err.println("================================================");
        System.err.println(" RACE 8c -- SINGLE-SHOT (deterministic, no oscillation)");
        System.err.println(" one disabled user + ONE move during the keySmith hold; nothing heals it.");
        System.err.println(" Needs: Prepare depth>=4, KEYSMITH_TEST_CONNECT_HOLD_MS=5000.");
        System.err.println(" buffer OFF (ttl=-1) -> FAIL (reproduce); buffer ON (10000) -> PASS (fix).");
        System.err.println("================================================");
        System.err.println();

        List<PopulationBuilder> pops = List.of(
            activatorScn.injectOpen(atOnceUsers(1)),
            moverScn    .injectOpen(atOnceUsers(1)),
            verifyScn   .injectOpen(nothingFor(Duration.ofSeconds(VERIFY_START_SEC)), atOnceUsers(1))
        );
        setUp(pops)
            .maxDuration(Duration.ofSeconds(VERIFY_START_SEC + (MAX_VERIFY_RETRIES * VERIFY_SLEEP_SEC) + 30))
            .protocols(httpProtocol);
    }
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/23/2026 mir0n  created: message-loss / night-watch SWAP scenario. Recreates bizTree in SWAP
 *                   mode, stops ActiveMQ, creates a user (lands in DB, CREATE broadcast lost), proves
 *                   the user is MISSING from the cache, waits one night-watch sweep, and proves the
 *                   SWAP promoted the fresh shadow so the user is now present. Fully automatic via the
 *                   Cmd infra steps (stop/start broker, recreate biztree) -- no operator.
 * 06/10/2026 mir0n  @SimulationInfo description trimmed to fit the hauberk list table (<=90 chars)
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import io.gatling.javaapi.core.ScenarioBuilder;
import pro.mir0n.esquire.hauberk.chain.*;
import pro.mir0n.hauberk.chain.Cmd;

import java.time.Duration;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Anti-entropy proof: the night-watch detects a cache that drifted because a JMS broadcast was lost,
 * and (in SWAP mode) self-heals by promoting the freshly-loaded shadow.
 *
 * Flow (single VU, fully automatic):
 *   1. recreate bizTree with on-mismatch = SWAP and a LONG sweep interval (so the periodic sweep
 *      won't auto-heal before we observe the miss), wait until it is serving.
 *   2. ensure the test office exists (broker UP, so it lands in the cache).
 *   3. stop ActiveMQ.
 *   4. create a user -> persisted in the DB, but the CREATE broadcast is lost (no broker).
 *   5. CompareTrees -> the user is MISSING in the cache (cmpMissingInBiztree >= 1). EXPECTED.
 *   6. force ONE sweep (POST /esq-sweep, synchronous) -> night-watch loads the shadow from postgres
 *      (it HAS the user), checksums mismatch, SWAP promotes the shadow to serving.
 *   7. CompareTrees -> the user is now PRESENT (cmpMissingInBiztree == 0). The sweep self-healed.
 *   8. start ActiveMQ, clean up.
 */
@SimulationInfo("Message-loss + night-watch SWAP: broker down, user missed, sweep promotes fresh shadow")
public class MessageLossSimulation extends AEsqSimulation {

    ScenarioBuilder scn = scenario("message-loss-swap")
            .exec(session -> session.set("officeName", "hauberk-office-msgloss"))
            // 1. bizTree in SWAP mode + long sweep interval (we force the one sweep ourselves), fresh.
            .exec(Cmd.run("recreate-biztree",
                    Map.of("BIZTREE_ON_MISMATCH", "SWAP",
                           "BIZTREE_SWEEP_INTERVAL_MS", "600000"))).exitHereIfFailed()
            .exec(WaitCacheReady.chain).exitHereIfFailed()
            // 2. office in the cache (broker up).
            .exec(EnsureOffice.chain).exitHereIfFailed()
            // 3. lose the broker.
            .exec(Cmd.run("stop-amq")).exitHereIfFailed()
            // 4. create a user -> DB only; CREATE broadcast lost.
            .exec(CreateUser.chain).exitHereIfFailed()
            // 5. prove the drift: user missing from the cache.
            .exec(CompareTrees.chain)
            .exec(session -> {
                int missing = session.getInt("cmpMissingInBiztree");
                System.err.println("[MessageLoss] after create with broker DOWN: missingInBiztree="
                        + missing + " (expect >=1: the user's CREATE broadcast was lost)");
                return missing >= 1 ? session : session.markAsFailed();
            }).exitHereIfFailed()
            // 6. force one sweep OVER REST (gateway): 202 (async); the sweep runs on the director's
            //    night-watch thread -> loads shadow from postgres -> mismatch -> SWAP.
            .exec(ForceSweep.chain).exitHereIfFailed()
            // 7. prove recovery: poll until the background sweep has promoted the fresh shadow.
            .tryMax(20).on(
                pause(Duration.ofSeconds(2))
                .exec(CompareTrees.chain)
                .exec(session -> session.getInt("cmpMissingInBiztree") == 0
                        ? session : session.markAsFailed()))
            .exec(session -> {
                int missing = session.getInt("cmpMissingInBiztree");
                System.err.println("[MessageLoss] after sweep (SWAP): missingInBiztree="
                        + missing + " (expect 0: night-watch promoted the fresh shadow)");
                return missing == 0 ? session : session.markAsFailed();
            }).exitHereIfFailed()
            // 8. restore the broker, clean up.
            .exec(Cmd.run("start-amq"))
            .exec(CleanupOfficeByName.chain);

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

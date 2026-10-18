/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/23/2026 mir0n  created: message-loss / night-watch TERMINATE scenario. Recreates bizTree in
 *                   TERMINATE mode, stops ActiveMQ, creates a user (DB only, CREATE broadcast lost),
 *                   proves the cache drifted, forces a sweep -> the mismatch makes bizTree System.exit
 *                   -- and asserts the container is DOWN (k8s would relaunch it in prod). Restores the
 *                   stack at the end. Fully automatic via the Cmd infra steps.
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

import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Proves the night-watch TERMINATE reaction: on a checksum mismatch the director exits the process so
 * an orchestrator (k8s in prod) relaunches it from a clean load. Here, in docker, the test just
 * confirms bizTree went DOWN.
 *
 * Flow (single VU, fully automatic):
 *   1. recreate bizTree with on-mismatch = TERMINATE + long sweep interval; wait until serving.
 *   2. ensure the test office exists (broker UP -> in the cache).
 *   3. stop ActiveMQ.
 *   4. create a user -> persisted in DB, CREATE broadcast lost.
 *   5. CompareTrees -> user MISSING in cache (cmpMissingInBiztree >= 1). EXPECTED.
 *   6. force one sweep over REST (gateway POST /esq-sweep -> 202, async).
 *   7. poll GET /esq-tree over REST until the gateway returns 5xx -> assert DOWN. The TERMINATE fired.
 *   8. restore: start ActiveMQ, recreate bizTree in SWAP mode, wait until serving again.
 */
@SimulationInfo("Message-loss + night-watch TERMINATE: broker down, user missed, sweep mismatch, exits")
public class MessageLossTerminateSimulation extends AEsqSimulation {

    ScenarioBuilder scn = scenario("message-loss-terminate")
            .exec(session -> session.set("officeName", "hauberk-office-msgloss-t"))
            // 1. bizTree in TERMINATE mode + long interval (we force the one sweep ourselves), fresh.
            .exec(Cmd.run("recreate-biztree",
                    Map.of("BIZTREE_ON_MISMATCH", "TERMINATE",
                           "BIZTREE_SWEEP_INTERVAL_MS", "600000"))).exitHereIfFailed()
            .exec(WaitCacheReady.chain).exitHereIfFailed()
            // 2. office in the cache (broker up).
            .exec(EnsureOffice.chain).exitHereIfFailed()
            // 3. lose the broker.
            .exec(Cmd.run("stop-amq")).exitHereIfFailed()
            // 4. create a user -> DB only; CREATE broadcast lost.
            .exec(CreateUser.chain).exitHereIfFailed()
            // 5. prove the drift.
            .exec(CompareTrees.chain)
            .exec(session -> {
                int missing = session.getInt("cmpMissingInBiztree");
                System.err.println("[MessageLoss-Terminate] after create with broker DOWN: missingInBiztree="
                        + missing + " (expect >=1: the user's CREATE broadcast was lost)");
                return missing >= 1 ? session : session.markAsFailed();
            }).exitHereIfFailed()
            // 6. force one sweep OVER REST (gateway): 202 (async); the background sweep then hits the
            //    mismatch and biztree System.exit(1)s.
            .exec(ForceSweep.chain).exitHereIfFailed()
            // 7. confirm OVER REST that biztree went down (gateway answers 5xx).
            .exec(WaitBizTreeDown.chain).exitHereIfFailed()
            .exec(session -> {
                System.err.println("[MessageLoss-Terminate] biztree is DOWN (gateway 5xx) -- TERMINATE fired "
                        + "(k8s would relaunch the pod from a clean load in prod)");
                return session;
            })
            // 8. restore the stack: broker + biztree (SWAP, normal interval).
            .exec(Cmd.run("start-amq"))
            .exec(Cmd.run("recreate-biztree",
                    Map.of("BIZTREE_ON_MISMATCH", "SWAP",
                           "BIZTREE_SWEEP_INTERVAL_MS", "10000")))
            .exec(WaitCacheReady.chain);

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

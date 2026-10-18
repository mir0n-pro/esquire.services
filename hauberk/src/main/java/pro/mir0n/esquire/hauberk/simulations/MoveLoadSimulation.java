/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: standalone move-oscillate load -- the (d) scenario from LoadScenarios, super.move.workers VUs all hitting the same shared office
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import java.time.Duration;


import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Standalone move oscillate load -- just the (d) scenario from
 * {@link LoadScenarios}. All VUs hit the SAME shared office (w1-l3); the
 * point is move-concurrency stress on a single target. Uses
 * super.move.workers + super.duration.seconds.
 */
@SimulationInfo("Move-concurrency stress: VUs oscillate w1-l3 between L1 and w1-l2")
public class MoveLoadSimulation extends AEsqSimulation {

    {
        int w = EsqHauberkConfig.SUPER_MOVE_WORKERS;
        if (w < 1) {
            throw new IllegalStateException(
                "MoveLoadSimulation: super.move.workers must be >= 1.");
        }
        setUp(LoadScenarios.MOVE.injectOpen(atOnceUsers(w)))
            .maxDuration(Duration.ofSeconds(EsqHauberkConfig.SUPER_DURATION_SECONDS))
            .protocols(httpProtocol);
    }
}

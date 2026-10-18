/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: standalone read load -- the (a) scenario from LoadScenarios, super.read.workers VUs
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
 * Standalone read load -- just the (a) scenario from {@link LoadScenarios}.
 * Uses super.read.workers + super.duration.seconds.
 */
@SimulationInfo("Read load: random GET /esq-cmd?kind=34 over a user pool")
public class ReadLoadSimulation extends AEsqSimulation {

    {
        int w = EsqHauberkConfig.SUPER_READ_WORKERS;
        if (w < 1) {
            throw new IllegalStateException(
                "ReadLoadSimulation: super.read.workers must be >= 1.");
        }
        setUp(LoadScenarios.READ.injectOpen(atOnceUsers(w)))
            .maxDuration(Duration.ofSeconds(EsqHauberkConfig.SUPER_DURATION_SECONDS))
            .protocols(httpProtocol);
    }
}

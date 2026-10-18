/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: standalone create+delete user load -- the (c) scenario from LoadScenarios, super.create.workers VUs
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
 * Standalone create+delete load -- just the (c) scenario from
 * {@link LoadScenarios}. Uses super.create.workers + super.duration.seconds.
 */
@SimulationInfo("Create+delete user loop at deepest office (super.create.workers VUs)")
public class CreateLoadSimulation extends AEsqSimulation {

    {
        int w = EsqHauberkConfig.SUPER_CREATE_WORKERS;
        if (w < 1) {
            throw new IllegalStateException(
                "CreateLoadSimulation: super.create.workers must be >= 1.");
        }
        setUp(LoadScenarios.CREATE.injectOpen(atOnceUsers(w)))
            .maxDuration(Duration.ofSeconds(EsqHauberkConfig.SUPER_DURATION_SECONDS))
            .protocols(httpProtocol);
    }
}

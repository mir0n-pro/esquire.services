/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: standalone tx-cycle load -- the (e) scenario from LoadScenarios; random account deposit+withdrawal net-zero per iter
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
 * Standalone tx cycle load -- just the (e) scenario from
 * {@link LoadScenarios}. Each iteration: pick random account, deposit,
 * withdrawal (net-zero). Uses super.tx.workers + super.duration.seconds.
 */
@SimulationInfo("Tx cycle load: random account, Deposit + Withdrawal (net-zero per iter)")
public class TxLoadSimulation extends AEsqSimulation {

    {
        int w = EsqHauberkConfig.SUPER_TX_WORKERS;
        if (w < 1) {
            throw new IllegalStateException(
                "TxLoadSimulation: super.tx.workers must be >= 1.");
        }
        setUp(LoadScenarios.TX.injectOpen(atOnceUsers(w)))
            .maxDuration(Duration.ofSeconds(EsqHauberkConfig.SUPER_DURATION_SECONDS))
            .protocols(httpProtocol);
    }
}

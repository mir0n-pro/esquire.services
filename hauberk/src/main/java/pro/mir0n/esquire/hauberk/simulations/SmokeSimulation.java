/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: single-VU wiring check -- KC token + one GET /esq-kinds against gateway
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Wiring check: one virtual user, one GET /esq-kinds request. Confirms KC
 * token fetch, gateway reachability, and bearer-header injection are all
 * wired up. No load, no concurrency.
 */
@SimulationInfo("Wiring check: KC token + single GET /esq-kinds against gateway")
public class SmokeSimulation extends AEsqSimulation {

    ScenarioBuilder scn = scenario("smoke-get-esq-kinds")
            .exec(http("GET /esq-kinds")
                    .get("/esq-kinds")
                    .check(status().is(200)));

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

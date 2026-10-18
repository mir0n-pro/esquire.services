/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 06/29/2026 mir0n  created: R6 query-timeout HA smoke -- hits enyMan's flag-gated /test slow-query hook
 *                   DIRECTLY (not via the gateway): a capped slow query is cancelled at the cap, an opt-out
 *                   slow query (the move / cache-load mechanism) completes. Run against a stack deployed with
 *                   ESQ_TX_TIMEOUT_S>0 + esq.test.slow-query-enabled=true (compose/compose.ha-smoke.yaml).
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;


import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * R6 request-path query-timeout cap, end-to-end on a live stack. Calls enyMan's flag-gated test hook on its
 * OWN port (the gateway is not involved); the hook runs a deliberately long DB statement on two paths:
 * <ul>
 *   <li>{@code /test/slow-query} -- the capped path: with the cap on (ESQ_TX_TIMEOUT_S&gt;0) the query is
 *       cancelled at the cap, so the hook reports {@code timedOut=true}.</li>
 *   <li>{@code /test/slow-query-optout} -- the opt-out path (the move / cache-load mechanism): it is never
 *       capped, so it runs to completion and reports {@code timedOut=false}.</li>
 * </ul>
 * Precondition: enyMan deployed with ESQ_TX_TIMEOUT_S&gt;0 AND esq.test.slow-query-enabled=true. The hook is
 * permitAll and needs no identity, so the smoke sends no auth header.
 */
@SimulationInfo("R6 cap: a capped slow query is cancelled at the cap; an opt-out slow query completes")
public class HaTimeoutSmokeSimulation extends AEsqSimulation {

    // enyMan DIRECTLY (not via the gateway); the /test hook is permitAll + needs no identity -> no auth header.
    private final HttpProtocolBuilder enyman = http
            .baseUrl(EsqHauberkConfig.ENYMAN_BASE)
            .acceptHeader("application/json");

    ScenarioBuilder scn = scenario("ha-timeout-smoke")
            .exec(http("capped slow query is cancelled at the cap")
                    .get("/test/slow-query?seconds=20")
                    .check(status().is(200))
                    .check(jsonPath("$.timedOut").is("true")))
            .exec(http("opt-out slow query completes despite the cap")
                    .get("/test/slow-query-optout?seconds=12")
                    .check(status().is(200))
                    .check(jsonPath("$.timedOut").is("false")));

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(enyman);
    }
}

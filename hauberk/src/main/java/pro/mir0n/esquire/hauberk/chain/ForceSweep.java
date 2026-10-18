/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/23/2026 mir0n  created: POST /esq-sweep through the gateway (authenticated) to force a
 *                   night-watch sweep. The director sweeps ASYNC, so 202 returns immediately
 *                   (the sweep runs on the night-watch thread); the caller polls for the outcome.
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Forces a bizTree night-watch sweep via the gateway: POST /esq-sweep. The director dispatches the
 * sweep onto its night-watch thread and returns 202 at once -- so this does NOT wait for the sweep
 * to finish. Callers observe the result afterwards (CompareTrees for SWAP recovery, WaitBizTreeDown
 * for TERMINATE).
 */
public final class ForceSweep {

    private ForceSweep() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-sweep (force night-watch)")
            .post("/esq-sweep")
            .check(status().is(202)));
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/23/2026 mir0n  created: poll GET /esq-tree through the gateway until it returns 5xx -- i.e. until
 *                   bizTree is unreachable. Used by the TERMINATE message-loss test to confirm OVER
 *                   REST that the night-watch's TERMINATE reaction took bizTree down.
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import io.gatling.javaapi.core.ChainBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Blocks until bizTree is DOWN, observed over REST: retries a GET on the playground-root subtree
 * until the gateway answers 5xx (bizTree unreachable). Used after a TERMINATE-mode forced sweep to
 * assert -- through the gateway, not docker -- that bizTree exited.
 */
public final class WaitBizTreeDown {

    private WaitBizTreeDown() {}

    private static final int MAX_TRIES = 30;   // x2s = up to ~60s for the sweep to fire + biztree to exit

    public static final ChainBuilder chain =
        tryMax(MAX_TRIES).on(
            pause(Duration.ofSeconds(2))
            .exec(http("check biztree down (expect 5xx)")
                .get("/esq-tree")
                .queryParam("id", EsqHauberkConfig.PLAYGROUND_PARENT_ID)
                .check(status().in(500, 502, 503, 504))));
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 09/24/2026 mir0n  created: Chain GET /esq-cmd-tree on the driver's own root office; used by Tree load
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * GET /esq-cmd-tree?kind=20&id={playground parent}. The tree read an
 * administrative screen makes on every navigation. Status 200 check; no
 * body assertion -- load is the point.
 *
 * No session inputs: the id is fixed for the run.
 */
public final class ReadTreeRoot {

    private ReadTreeRoot() {}

    //xxx: the driver's token carries esq_rootpath "1.14.", so its root is the playground parent, not entity 1
    public static final ChainBuilder chain =
        exec(http("GET /esq-cmd-tree (read tree from root)")
            .get("/esq-cmd-tree")
            .queryParam("kind", EntityKinds.ORG)
            .queryParam("id",   EsqHauberkConfig.PLAYGROUND_PARENT_ID)
            .check(status().is(200)));
}

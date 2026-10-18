/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-move?kind=&id=&dist_id= for entity re-parenting (USR or ORG)
 * 06/01/2026 mir0n  accept HTTP 202 in addition to 200 -- v1.2.6 Goal 3 makes /esq-move async-ack
 *                   (submits to enyMan's move queue and returns 202 Accepted at submit time).
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * POST /esq-move?kind={moveKind}&id={moveId}&dist_id={moveDestId} --
 * re-parents an ORG or USR under a destination ORG. enyMan validates
 * UPDATE permission on both source + dest; cascades subtree path
 * updates via JMS broadcast (biztree path sync) and KC user-attribute
 * URQ (kcMaster) where applicable. Returns 200 with an empty body.
 *
 * Session inputs:
 *   moveKind    -- entity kind (20 ORG, 34 USR_CLIENT, etc.).
 *   moveId      -- id of the entity being moved.
 *   moveDestId  -- id of the destination ORG (must be kind=20).
 *
 * Response: v1.2.5 returned 200 with an empty body. v1.2.6 Goal 3 made /esq-move async-ack:
 *   it submits to enyMan's in-process move queue and returns 202 Accepted at submit time.
 *   The body stays empty. We accept either to keep the chain compatible with both versions.
 */
public final class MoveEntity {

    private MoveEntity() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-move")
            .post("/esq-move")
            .queryParam("kind",    "#{moveKind}")
            .queryParam("id",      "#{moveId}")
            .queryParam("dist_id", "#{moveDestId}")
            .check(status().in(200, 202)));
}

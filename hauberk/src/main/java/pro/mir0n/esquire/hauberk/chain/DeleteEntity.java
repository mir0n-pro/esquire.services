/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: generic Chain POST /esq-cmd-del?kind=delKind&id=delId; used by cleanup chains for account / user / office deletes
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * POST /esq-cmd-del with arbitrary kind+id. Generic building block for the
 * cleanup walk: deletes accounts (kind=50/52/54), users (30/32/34/36), and
 * offices (kind=20) the same way.
 *
 * Session inputs:
 *   delKind  -- entity kind to delete (integer)
 *   delId    -- entity id to delete (string)
 *
 * Note for accounts: this Chain assumes the account is already closed
 * (status=C, balance=0).
 */
public final class DeleteEntity {

    private DeleteEntity() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-cmd-del")
            .post("/esq-cmd-del")
            .queryParam("kind", "#{delKind}")
            .queryParam("id",   "#{delId}")
            .queryParam("cmd",  "delete")
            .check(status().is(200)));
}

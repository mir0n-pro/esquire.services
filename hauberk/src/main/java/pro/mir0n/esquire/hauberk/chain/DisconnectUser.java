/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-key-save connectFlg=N to deactivate access profile; no-op if already disconnected
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * POST /esq-key-save?id={apId} -- disable access profile, releasing the KC user.
 * keySmith persists connectFlg Y->N, publishes URQ EVENT_DELETE; kcMaster
 * consumes and removes the user from KC via usersResource.delete().
 *
 * Session inputs:
 *   apId -- entity id of the USR whose access profile to disable.
 *
 * No session outputs. Used by the cleanup pre-pass in CleanupOfficeByName
 * to release KC users before the USR entity gets deleted (else enyMan's
 * delete-USR validator throws "connectFlg=Y" 409).
 */
public final class DisconnectUser {

    private DisconnectUser() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-key-save (disconnect user)")
            .post("/esq-key-save")
            .queryParam("id", "#{apId}")
            .header("Content-Type", "application/json")
            .body(StringBody("{\"connectFlg\":\"N\"}"))
            .check(status().is(200)));
}

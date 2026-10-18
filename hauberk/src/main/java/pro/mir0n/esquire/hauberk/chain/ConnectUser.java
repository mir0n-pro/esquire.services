/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-key-save connectFlg=Y + roles=[TREE, CLIENT] to activate access profile; keySmith publishes URQ; kcMaster creates the KC user
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * POST /esq-key-save?id={userId} -- activate access profile.
 * keySmith persists to esq_auth (connectFlg N->Y), publishes URQ
 * EVENT_CREATE to ActiveMQ; kcMaster consumes and creates the KC user
 * with temp password "changeit", setTemporary(true), and required action
 * UPDATE_PASSWORD; assigns realm roles TREE + CLIENT.
 *
 * Session inputs:
 *   userId    -- entity id (created by CreateUser).
 *   userEmail -- email address; doubles as loginId.
 *
 * No session outputs. After this returns, kcMaster needs ~ms-s to actually
 * create the KC user (async JMS). LoginViaBff handles the race with
 * tryMax-retry, so no explicit wait here.
 *
 * Roles hardcoded: TREE (id=8, kind=982) + CLIENT (id=6, kind=980) --
 * canonical roles for kind=34 client users per esq_role_et + KC realm seed.
 */
public final class ConnectUser {

    private ConnectUser() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-key-save (connect user)")
            .post("/esq-key-save")
            .queryParam("id", "#{userId}")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{"
              +   "\"loginId\":\"#{userEmail}\","
              +   "\"email\":\"#{userEmail}\","
              +   "\"connectFlg\":\"Y\","
              +   "\"pwdChangeForced\":\"Y\","
              +   "\"tfaMethod\":\"N\","
              +   "\"roles\":["
              +     "{\"id\":\"8\",\"kind\":982,\"name\":\"TREE\"},"
              +     "{\"id\":\"6\",\"kind\":980,\"name\":\"CLIENT\"}"
              +   "]"
              + "}"))
            .check(status().is(200)));
}

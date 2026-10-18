/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 06/01/2026 mir0n  created: fetch KC master-realm admin token; used by race-8c verify
 *                   (RaceMoveCreateKc) and CleanupKcOrphans chains.
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpDsl;
import pro.mir0n.hauberk.config.AuthConfig;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * POST master-realm token endpoint with grant_type=password against the
 * compose bootstrap admin (admin / q). Returns the access_token saved as
 * session attribute "kcAdminToken". Subsequent requests against the KC
 * admin REST API (e.g. /admin/realms/esquire/users) carry that token as
 * Authorization: Bearer.
 *
 * Why master-realm and not the esquire realm's hauberk client: race-8c
 * verification needs to read arbitrary users' attributes (esq_rootpath)
 * which requires realm-management permissions. The esq-hauberk client is
 * a SUPERVISOR-role business client, not a realm admin. Master-realm admin
 * has cross-realm rights via the standard /admin/realms/{realm}/... path.
 *
 * Token TTL on master admin is typically 1m -- the verify scenarios call
 * this chain once at start and reuse the token; if a sim runs longer than
 * the token TTL, call this chain again to refresh.
 *
 * Session outputs:
 *   kcAdminToken -- the access token value (no "Bearer " prefix).
 */
public final class KcAdminAuth {

    private KcAdminAuth() {}

    public static final ChainBuilder chain =
        exec(http("POST master-realm admin token")
            .post(AuthConfig.BASE + "/realms/master/protocol/openid-connect/token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .formParam("grant_type", "password")
            .formParam("client_id",  "admin-cli")
            .formParam("username",   AuthConfig.ADMIN_USER)
            .formParam("password",   AuthConfig.ADMIN_PASSWORD)
            .check(HttpDsl.status().is(200))
            .check(jsonPath("$.access_token").saveAs("kcAdminToken")));
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 06/01/2026 mir0n  created: KC orphan cleanup -- search hauberk-* loginIds in the esquire realm
 *                   and DELETE each via admin REST. Run AFTER the DB cleanup (CleanupOfficeByName)
 *                   so KC and DB end in matching empty state. Required because race-8c can leave
 *                   KC users whose corresponding esq_user row is already removed by the DB sweep.
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpDsl;
import pro.mir0n.hauberk.config.AuthConfig;

import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Page through the esquire realm's users searching for the "hauberk-" loginId
 * prefix, then DELETE each matching user via the KC admin REST API. Best-effort:
 * 404 on the delete is acceptable (another VU may have already removed it).
 *
 * Session inputs:
 *   kcAdminToken -- the admin bearer token from KcAdminAuth chain.
 *
 * Session outputs:
 *   kcOrphansDeleted -- count of users deleted (for the cleanup log line).
 */
public final class CleanupKcOrphans {

    private CleanupKcOrphans() {}

    private static final String ADMIN_USERS_BASE =
        AuthConfig.BASE + "/admin/realms/" + AuthConfig.REALM + "/users";

    public static final ChainBuilder chain =
        // Search for users whose loginId starts with "hauberk-". KC search does substring
        // match on username/email/firstName/lastName; "hauberk-" is unique enough for our
        // test scope (we use both hauberk-N email prefixes and hauberk firstName).
        exec(http("GET KC users (hauberk-* search)")
                .get(ADMIN_USERS_BASE + "?search=hauberk-&max=500")
                .header("Authorization", "Bearer #{kcAdminToken}")
                .check(HttpDsl.status().is(200))
                .check(jsonPath("$").ofList().saveAs("kcHauberkUsers")))
        .exec(session -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users = (List<Map<String, Object>>) session.get("kcHauberkUsers");
            List<String> ids = new java.util.ArrayList<>();
            if (users != null) {
                for (Map<String, Object> u : users) {
                    Object id = u.get("id");
                    if (id != null) ids.add(id.toString());
                }
            }
            System.err.println("[CleanupKcOrphans] found " + ids.size() + " hauberk-* KC users");
            return session.set("kcOrphanIds", ids).set("kcOrphansDeleted", 0);
        })
        .foreach("#{kcOrphanIds}", "kcOrphanId").on(
            exec(http("DELETE KC user (orphan)")
                .delete(ADMIN_USERS_BASE + "/#{kcOrphanId}")
                .header("Authorization", "Bearer #{kcAdminToken}")
                // Accept 204 (deleted), 404 (already gone), 200 (some KC versions).
                .check(HttpDsl.status().in(200, 204, 404)))
            .exec(s -> s.set("kcOrphansDeleted", s.getInt("kcOrphansDeleted") + 1))
        )
        .exec(session -> {
            System.err.println("[CleanupKcOrphans] deleted "
                    + session.getInt("kcOrphansDeleted") + " KC users.");
            return session;
        });
}

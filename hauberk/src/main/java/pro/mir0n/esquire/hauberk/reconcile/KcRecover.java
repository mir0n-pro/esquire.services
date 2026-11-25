/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/02/2026 mir0n  created: KC<->Esquire data-recover core. Reads the authoritative connected-user state
 *                   from esq2025 DIRECTLY over PG/JDBC and the mirror state from KeyCloak DIRECTLY over the
 *                   REST admin API -- OUT of the Esquire services, so recovery works when the services / bus
 *                   are down. Diffs and (in repair mode) fixes a stale KC esq_rootpath in place.
 * 09/07/2026 mir0n  adminToken() takes a client_credentials token on the realm admin client instead of a
 *                   master-realm password grant, so one mechanism reads every environment
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 * 11/24/2026 mir0n  v2.0.3 -- the stale-path check and its repair removed (KeyCloak holds no esq_rootpath): run()
 *                   reports MISSING_IN_KC and ORPHAN_IN_KC only; EsqUser / KcUser carry no path; the connected-user
 *                   query reads esq_auth alone; the report counts in a loop
 */
package pro.mir0n.esquire.hauberk.reconcile;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pro.mir0n.hauberk.config.AuthConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KC &lt;-&gt; Esquire data reconciliation.
 *
 * <p>Two decoupled stores drift: {@code esq2025} (SQL, the source of truth) and KeyCloak (the accounts of the
 * connected users, each one carrying its {@code esq_uid}, written only by kcMaster). A lost sync request or an
 * out-of-band KC edit leaves the two disagreeing about who exists. This tool detects that drift and reports it.
 *
 * <p>It talks to BOTH stores DIRECTLY via their native APIs -- PG/JDBC for esq2025, the KC REST admin API for
 * KeyCloak -- and does NOT route through the Esquire services or the bus, so it works even when they are
 * down. That is also why it lives in hauberk (out of the services), not as a service endpoint.
 *
 * <p>Drift kinds, both REPORTED only:
 * <ul>
 *   <li>MISSING_IN_KC -- a connected Esquire user with no KC account (re-creating a KC user needs credential /
 *       activation state that is kcMaster's job -- not replicated here).</li>
 *   <li>ORPHAN_IN_KC -- a KC user carrying an {@code esq_uid} that is not a connected Esquire user (deletion is
 *       destructive).</li>
 * </ul>
 */
public final class KcRecover {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int KC_PAGE_MAX = 5000;   // single page; the seed + hauberk playground fit well within

    private static final String MISSING_IN_KC = "MISSING_IN_KC";
    private static final String ORPHAN_IN_KC = "ORPHAN_IN_KC";

    private KcRecover() {}

    /** The authoritative connected user: entity id (esq_uid) and login id. */
    public record EsqUser(String esqUid, String loginId) {}

    /** The mirror user in KC: the KC user id and username. */
    public record KcUser(String id, String username) {}

    /** One detected drift row. */
    public record Drift(String kind, String esqUid, String detail) {}

    /**
     * Run the reconciliation. Reads both stores, diffs, and prints a report. Returns the number of drifts found
     * (0 = in sync).
     */
    public static int run() throws Exception {
        int ret;
        Map<String, EsqUser> esq = readEsquire();
        String token = adminToken();
        Map<String, KcUser> kc = readKc(token);
        System.err.printf("[kc-reconcile] esquire connected users=%d, KC users (with esq_uid)=%d%n",
                esq.size(), kc.size());

        List<Drift> drifts = new ArrayList<>();
        for (EsqUser u : esq.values()) {
            if (!kc.containsKey(u.esqUid())) {
                drifts.add(new Drift(MISSING_IN_KC, u.esqUid(),
                        "connected user login=" + u.loginId() + " has no KC account"));
            }
        }
        // The KC map is keyed by esq_uid.
        for (Map.Entry<String, KcUser> e : kc.entrySet()) {
            if (!esq.containsKey(e.getKey())) {
                drifts.add(new Drift(ORPHAN_IN_KC, e.getKey(),
                        "KC user " + e.getValue().username() + " (esq_uid=" + e.getKey()
                                + ") is not a connected Esquire user"));
            }
        }

        report(drifts);
        ret = drifts.size();
        return ret;
    }

    // ------------------------------------------------------------------ Esquire (PG / JDBC)

    /** Read the connected users from esq2025 over JDBC. */
    private static Map<String, EsqUser> readEsquire() throws Exception {
        Map<String, EsqUser> ret = new LinkedHashMap<>();
        String url = EsqHauberkConfig.PG_URL;
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("kc-reconcile: pg.url is not set in the hauberk config -- "
                    + "point it at esq2025 (jdbc:postgresql://host:port/db)");
        }
        String sql = "SELECT a.au_usr_pk, a.au_login_id"
                + " FROM esq_auth a"
                + " WHERE a.au_connect_flg = 'Y'";
        try (Connection c = DriverManager.getConnection(url, EsqHauberkConfig.PG_USER, EsqHauberkConfig.PG_PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String esqUid = String.valueOf(rs.getLong("au_usr_pk"));
                ret.put(esqUid, new EsqUser(esqUid, rs.getString("au_login_id")));
            }
        }
        return ret;
    }

    // ------------------------------------------------------------------ KeyCloak (REST admin)

    private static String adminToken() throws Exception {
        String ret;
        if (AuthConfig.ADMIN_SECRET.isBlank()) {
            throw new IllegalStateException("KC_ADMIN_SECRET (or KCMASTER_ADMIN_SECRET) is not set. It is the "
                    + AuthConfig.ADMIN_CLIENT_ID + " client secret, which kc-reconcile needs to read KeyCloak.");
        }
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(AuthConfig.ADMIN_CLIENT_ID, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(AuthConfig.ADMIN_SECRET, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(AuthConfig.BASE + "/realms/" + AuthConfig.REALM
                        + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("KC realm-admin token returned " + resp.statusCode() + ": " + resp.body());
        }
        ret = MAPPER.readTree(resp.body()).path("access_token").asText(null);
        if (ret == null || ret.isBlank()) {
            throw new IllegalStateException("KC admin token response missing access_token: " + resp.body());
        }
        return ret;
    }

    /** Read every KC user that carries an {@code esq_uid} attribute -> keyed by that esq_uid. */
    private static Map<String, KcUser> readKc(String token) throws Exception {
        Map<String, KcUser> ret = new LinkedHashMap<>();
        String uri = AuthConfig.BASE + "/admin/realms/" + AuthConfig.REALM
                + "/users?briefRepresentation=false&max=" + KC_PAGE_MAX;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("KC list users returned " + resp.statusCode() + ": " + resp.body());
        }
        for (JsonNode u : MAPPER.readTree(resp.body())) {
            String esqUid = firstAttr(u.path("attributes"), "esq_uid");
            //xxx: a user without esq_uid is not an Esquire-managed identity (e.g. the bootstrap admin)
            if (esqUid != null) {
                ret.put(esqUid, new KcUser(u.path("id").asText(), u.path("username").asText()));
            }
        }
        return ret;
    }

    private static String firstAttr(JsonNode attrs, String name) {
        String ret = null;
        JsonNode arr = attrs.path(name);
        if (arr.isArray() && !arr.isEmpty()) {
            ret = arr.get(0).asText();
        }
        return ret;
    }

    private static void report(List<Drift> drifts) {
        System.err.println();
        System.err.println("================ KC <-> Esquire reconciliation ================");
        if (drifts.isEmpty()) {
            System.err.println("IN SYNC -- no drift between esq2025 and KeyCloak.");
        } else {
            int missing = 0;
            int orphan = 0;
            for (Drift d : drifts) {
                if (MISSING_IN_KC.equals(d.kind())) {
                    missing++;
                } else {
                    orphan++;
                }
            }
            System.err.printf("DRIFT: missing-in-KC=%d, orphan-in-KC=%d%n", missing, orphan);
            for (Drift d : drifts) {
                System.err.println("  [" + d.kind() + "] esq_uid=" + d.esqUid() + " : " + d.detail());
            }
            System.err.println("\n(REPORTED only -- create / delete are not auto-applied.)");
        }
        System.err.println("===============================================================");
    }
}

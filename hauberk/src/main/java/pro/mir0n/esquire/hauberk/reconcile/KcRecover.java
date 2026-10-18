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
 */
package pro.mir0n.esquire.hauberk.reconcile;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * <p>Two decoupled stores drift: {@code esq2025} (SQL, the source of truth) and KeyCloak (a mirror of the
 * connected users + their {@code esq_rootpath} + roles, written only by kcMaster). A lost sync request, a
 * broker outage that dropped a path update, or an out-of-band KC edit leaves KC stale. This tool detects
 * that drift and, in repair mode, fixes a stale {@code esq_rootpath} back to the DB {@code ep_path}.
 *
 * <p>It talks to BOTH stores DIRECTLY via their native APIs -- PG/JDBC for esq2025, the KC REST admin API for
 * KeyCloak -- and does NOT route through the Esquire services or the bus, so it recovers even when they are
 * down. That is also why it lives in hauberk (out of the services), not as a service endpoint.
 *
 * <p>Drift kinds:
 * <ul>
 *   <li>STALE_PATH -- a KC user whose {@code esq_rootpath} != the DB {@code ep_path} (the race-8c drift). REPAIRED
 *       in place (PUT the corrected attribute).</li>
 *   <li>MISSING_IN_KC -- a connected Esquire user with no KC account. REPORTED only (re-creating a KC user needs
 *       credential / activation state that is kcMaster's job -- not replicated here).</li>
 *   <li>ORPHAN_IN_KC -- a KC user carrying an {@code esq_uid} that is not a connected Esquire user. REPORTED only
 *       (deletion is destructive).</li>
 * </ul>
 */
public final class KcRecover {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int KC_PAGE_MAX = 5000;   // single page; the seed + hauberk playground fit well within

    private KcRecover() {}

    /** The authoritative connected user: entity id (esq_uid), login id, and the current DB entity path. */
    public record EsqUser(String esqUid, String loginId, String path) {}

    /** The mirror user in KC: the KC user id, username, and the esq_rootpath attribute (may be null). */
    public record KcUser(String id, String username, String rootPath) {}

    /** One detected drift row. */
    public record Drift(String kind, String esqUid, String detail) {}

    /**
     * Run the reconciliation. Reads both stores, diffs, prints a report, and -- when {@code repair} -- fixes a
     * stale esq_rootpath in place. Returns the number of drifts found (0 = in sync).
     */
    public static int run(boolean repair) throws Exception {
        int ret;
        Map<String, EsqUser> esq = readEsquire();
        String token = adminToken();
        Map<String, KcUser> kc = readKc(token);
        System.err.printf("[kc-reconcile] esquire connected users=%d, KC users (with esq_uid)=%d%n",
                esq.size(), kc.size());

        List<Drift> drifts = new ArrayList<>();
        int repaired = 0;
        // Esquire-side pass: missing-in-KC + stale-path.
        for (EsqUser u : esq.values()) {
            KcUser k = kc.get(u.esqUid());
            if (k == null) {
                drifts.add(new Drift("MISSING_IN_KC", u.esqUid(),
                        "connected user login=" + u.loginId() + " path=" + u.path() + " has no KC account"));
            } else if (!u.path().equals(k.rootPath())) {
                drifts.add(new Drift("STALE_PATH", u.esqUid(),
                        "KC esq_rootpath=" + k.rootPath() + " != DB ep_path=" + u.path()
                                + " (kcUser=" + k.username() + ")"));
                if (repair) {
                    updateKcRootPath(token, k.id(), u.path());
                    repaired++;
                    System.err.printf("[kc-reconcile] REPAIRED esq_rootpath for esq_uid=%s -> %s%n",
                            u.esqUid(), u.path());
                }
            }
        }
        // KC-side pass: orphan KC users (an esq_uid not in the connected set). The KC map is keyed by esq_uid.
        for (Map.Entry<String, KcUser> e : kc.entrySet()) {
            if (!esq.containsKey(e.getKey())) {
                drifts.add(new Drift("ORPHAN_IN_KC", e.getKey(),
                        "KC user " + e.getValue().username() + " (esq_uid=" + e.getKey()
                                + ") is not a connected Esquire user"));
            }
        }

        report(drifts, repair, repaired);
        ret = drifts.size();
        return ret;
    }

    // ------------------------------------------------------------------ Esquire (PG / JDBC)

    /** Read the connected users and their DB entity path from esq2025 over JDBC. */
    private static Map<String, EsqUser> readEsquire() throws Exception {
        Map<String, EsqUser> ret = new LinkedHashMap<>();
        String url = EsqHauberkConfig.PG_URL;
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("kc-reconcile: pg.url is not set in the hauberk config -- "
                    + "point it at esq2025 (jdbc:postgresql://host:port/db)");
        }
        String sql = "SELECT a.au_usr_pk, a.au_login_id, ep.ep_path"
                + " FROM esq_auth a"
                + " JOIN esq_entity_path ep ON ep.ep_pk = a.au_usr_pk"
                + " WHERE a.au_connect_flg = 'Y'";
        try (Connection c = DriverManager.getConnection(url, EsqHauberkConfig.PG_USER, EsqHauberkConfig.PG_PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String esqUid = String.valueOf(rs.getLong("au_usr_pk"));
                ret.put(esqUid, new EsqUser(esqUid, rs.getString("au_login_id"), rs.getString("ep_path")));
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
            JsonNode attrs = u.path("attributes");
            String esqUid = firstAttr(attrs, "esq_uid");
            if (esqUid == null) {
                continue;   // a user without esq_uid is not an Esquire-managed identity (e.g. the bootstrap admin)
            }
            ret.put(esqUid, new KcUser(u.path("id").asText(), u.path("username").asText(),
                    firstAttr(attrs, "esq_rootpath")));
        }
        return ret;
    }

    /** Fix a KC user's esq_rootpath in place: GET the full representation, replace the attribute, PUT it back
     *  (KC's PUT replaces the whole representation, so we preserve everything else the GET returned). */
    private static void updateKcRootPath(String token, String kcUserId, String path) throws Exception {
        String base = AuthConfig.BASE + "/admin/realms/" + AuthConfig.REALM + "/users/" + kcUserId;
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(base)).header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> gr = HTTP.send(get, HttpResponse.BodyHandlers.ofString());
        if (gr.statusCode() != 200) {
            throw new IllegalStateException("KC get user " + kcUserId + " returned " + gr.statusCode() + ": " + gr.body());
        }
        ObjectNode user = (ObjectNode) MAPPER.readTree(gr.body());
        ObjectNode attrs = user.has("attributes") && user.get("attributes").isObject()
                ? (ObjectNode) user.get("attributes") : MAPPER.createObjectNode();
        ArrayNode rp = MAPPER.createArrayNode();
        rp.add(path);
        attrs.set("esq_rootpath", rp);
        user.set("attributes", attrs);
        HttpRequest put = HttpRequest.newBuilder()
                .uri(URI.create(base)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(user))).build();
        HttpResponse<String> pr = HTTP.send(put, HttpResponse.BodyHandlers.ofString());
        if (pr.statusCode() != 204 && pr.statusCode() != 200) {
            throw new IllegalStateException("KC update user " + kcUserId + " returned " + pr.statusCode() + ": " + pr.body());
        }
    }

    private static String firstAttr(JsonNode attrs, String name) {
        String ret = null;
        JsonNode arr = attrs.path(name);
        if (arr.isArray() && !arr.isEmpty()) {
            ret = arr.get(0).asText();
        }
        return ret;
    }

    private static void report(List<Drift> drifts, boolean repair, int repaired) {
        System.err.println();
        System.err.println("================ KC <-> Esquire reconciliation ================");
        if (drifts.isEmpty()) {
            System.err.println("IN SYNC -- no drift between esq2025 and KeyCloak.");
        } else {
            long stale   = drifts.stream().filter(d -> d.kind().equals("STALE_PATH")).count();
            long missing = drifts.stream().filter(d -> d.kind().equals("MISSING_IN_KC")).count();
            long orphan  = drifts.stream().filter(d -> d.kind().equals("ORPHAN_IN_KC")).count();
            System.err.printf("DRIFT: stale-path=%d, missing-in-KC=%d, orphan-in-KC=%d%n", stale, missing, orphan);
            for (Drift d : drifts) {
                System.err.println("  [" + d.kind() + "] esq_uid=" + d.esqUid() + " : " + d.detail());
            }
            if (repair) {
                System.err.printf("%nREPAIRED %d stale esq_rootpath value(s). missing-in-KC / orphan-in-KC are "
                        + "REPORTED only (create / delete are not auto-applied).%n", repaired);
            } else {
                System.err.println("\n(--check only; re-run with --repair to fix stale esq_rootpath values.)");
            }
        }
        System.err.println("===============================================================");
    }
}

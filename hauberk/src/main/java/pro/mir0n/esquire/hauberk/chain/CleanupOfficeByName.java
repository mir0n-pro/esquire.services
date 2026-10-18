/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: stateless name-driven scrub: LookupOfficeIdByName + GET /esq-cmd-tree + foreach delete bottom-up; pre-pass disconnects connected USRs (kind 34/36)
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import java.util.Map;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Stateless, name-driven teardown of an office and everything beneath.
 * Given only an office name, looks it up, fetches the natural-tree
 * subtree via GET /esq-cmd-tree (FK-based, biztree-cache-independent),
 * and walks the rows top-to-bottom (leaves first), best-effort deleting
 * each entity.
 *
 * Two-pass cleanup:
 *   Pass 1 (disconnect): for each USR (kind 34/36) in the subtree, GET
 *     /esq-key to read connectFlg; if "Y", POST /esq-key-save with
 *     connectFlg=N. Releases the KC user via kcMaster so enyMan will
 *     permit USR delete (otherwise 409: "user is connected").
 *   Pass 2 (delete): foreach tree node, best-effort POST /esq-cmd-del.
 *     Leaves-first ordering preserves FK constraints.
 *
 * Session inputs:
 *   officeName -- the office name to scrub.
 *
 * No session outputs.
 *
 * Best-effort semantics: no .check(status()) on the delete call; failures
 * are visible as red bars in the Gatling report but do NOT halt the run.
 * One stuck account never blocks teardown of the rest of the office.

 *
 * Idempotent: if the office does not exist, the lookup leaves officeId
 * unset and the rest of the chain is skipped.
 */
public final class CleanupOfficeByName {

    private CleanupOfficeByName() {}

    public static final ChainBuilder chain =
        exec(LookupOfficeIdByName.chain)
        .doIf(session -> session.contains("officeId")).then(
            // Fetch entire subtree as a list of maps {id, kind, ...}; leaves first.
            exec(http("GET /esq-cmd-tree (cleanup subtree)")
                .get("/esq-cmd-tree")
                .queryParam("kind", "20")
                .queryParam("id",   "#{officeId}")
                .check(status().is(200))
                .check(jsonPath("$").ofList().saveAs("treeNodes"))
            )
            // Pass 1 -- disconnect any connected USRs (release KC user first).
            //   Skip kinds outside USR range; skip USRs already disconnected.
            //   Lookup -> conditional disconnect; never aborts on missing access
            //   profile (LookupAccessProfile accepts 200/400/404).
            .foreach("#{treeNodes}", "node").on(
                exec(session -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> n = (Map<String, Object>) session.get("node");
                    int kind = ((Number) n.get("kind")).intValue();
                    String id = String.valueOf(n.get("id"));
                    return session
                            .set("apKind", kind)
                            .set("apId",   id);
                })
                .doIf(session -> {
                    int k = session.getInt("apKind");
                    return k == 34 || k == 36;
                }).then(
                    exec(LookupAccessProfile.chain)
                    .doIf(session -> "Y".equals(session.getString("apConnectFlg"))).then(
                        exec(DisconnectUser.chain)
                    )
                )
            )
            // Pass 2 -- best-effort delete walk.
            .foreach("#{treeNodes}", "node").on(
                exec(session -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> n = (Map<String, Object>) session.get("node");
                    String id   = String.valueOf(n.get("id"));
                    String kind = String.valueOf(n.get("kind"));
                    return session.set("delId", id).set("delKind", kind);
                })
                .exec(http("POST /esq-cmd-del (cleanup, best-effort)")
                    .post("/esq-cmd-del")
                    .queryParam("kind", "#{delKind}")
                    .queryParam("id",   "#{delId}")
                    .queryParam("cmd",  "delete"))
            )
        );
}

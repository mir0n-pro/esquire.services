/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain GET /esq-cmd-tree, filter kind=34 USRs, save list as userPool for random picks
 * 07/13/2026 mir0n  userPool EXCLUDES users parented by the deepest office -- the CREATE scenario's churn ground.
 *                   The pool used to include them, so read/update kept picking users that create+delete removed
 *                   underneath, reporting ResourceNotFoundException as a load failure that was the harness's own
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Two-step pool fetch scoped to the hauberk subtree:
 *   1. GET /esq-enode?kind=20&name=hauberk-office-smoke  -> L1 id
 *   2. GET /esq-cmd-tree?kind=20&id={L1}                  -> subtree
 *   3. keep kind==34 entities, MINUS the deepest office's -> userPool
 *
 * Scoping to L1 keeps the pool free of pre-seed users (e.g. "Cli Ent" id=10), so SuperLoad's update
 * scenario won't mutate canned data.
 *
 * WHY THE DEEPEST OFFICE IS EXCLUDED. The CREATE scenario (LoadScenarios.CREATE) runs its
 * create-then-delete loop against the DEEPEST office in this same subtree, and it names its users exactly
 * as PrepareForAnything does -- so nothing about a user tells you whether it is a stable fixture or a
 * create-loop ephemeral about to be deleted.
 *
 * When the pool contained them, read and update kept drawing ids that the create loop deleted a moment
 * later, and the server correctly answered ResourceNotFoundException. Those showed up as load failures
 * (1,769 KO in a 200-VU run, ~1.4%) that said nothing whatever about the SYSTEM -- and the count swung
 * run to run purely on how many ephemerals happened to exist at the instant the pool was fetched, which
 * makes every KO figure from a mixed run untrustworthy. With create disabled the same load gave 52 KO.
 *
 * Excluding by parentId (not by name, which cannot discriminate) leaves a pool of users that only the
 * update scenario touches, and update never deletes. The read/update KO rate then measures the system.
 *
 * Session outputs:
 *   userPool -- List&lt;String&gt; of STABLE kind=34 user ids under hauberk-office-smoke.
 *
 * Session side effect:
 *   officeName / officeId -- temporarily set by the L1 lookup; downstream chains that depend on those
 *   should set them explicitly anyway.
 */
public final class PoolFetchUsers {

    private PoolFetchUsers() {}

    public static final ChainBuilder chain =
        exec(session -> session.set("officeName", "hauberk-office-smoke"))
        .exec(LookupOfficeIdByName.chain)
        .exec(http("GET /esq-cmd-tree (fetch user pool, hauberk subtree)")
            .get("/esq-cmd-tree")
            .queryParam("kind", EntityKinds.ORG)
            .queryParam("id",   "#{officeId}")
            .check(status().is(200))
            .check(jsonPath("$").ofList().saveAs("userPoolTree")))
        .exec(session -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tree = (List<Map<String, Object>>) session.get("userPoolTree");

            // The create loop's ground: the deepest ORG. Same rule LoadScenarios.CREATE uses to pick it,
            // so the two agree on which office is volatile.
            String churnOfficeId = null;
            int maxLevel = -1;
            for (Map<String, Object> n : tree) {
                Object kind  = n.get("kind");
                Object level = n.get("level");
                if (kind instanceof Number k && k.intValue() == EntityKinds.ORG
                        && level instanceof Number l && l.intValue() > maxLevel) {
                    maxLevel = l.intValue();
                    churnOfficeId = String.valueOf(n.get("id"));
                }
            }

            List<String> pool = new ArrayList<>();
            for (Map<String, Object> n : tree) {
                Object kind = n.get("kind");
                if (kind instanceof Number k && k.intValue() == EntityKinds.USR_CLIENT) {
                    String parent = String.valueOf(n.get("parentId"));
                    if (churnOfficeId == null || !churnOfficeId.equals(parent)) {
                        pool.add(String.valueOf(n.get("id")));
                    }
                }
            }

            if (pool.isEmpty()) {
                // Every user sits in the create loop's office, so there is no stable one to read. Say so --
                // a silent empty pool would come back as a storm of "no attribute pickedUserId" instead.
                System.err.println("[PoolFetchUsers] no STABLE users (all of them are under the deepest "
                        + "office, which the create loop churns) -- prepare with offices.depth >= 2");
                return session.markAsFailed();
            }
            return session.set("userPool", pool);
        });
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain GET /esq-cmd-tree + /esq-tree; diffs biztree cache vs natural-FK subtree; emits "[CompareTrees]" stderr lines and marks session failed on mismatch
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Diffs the biztree H2 cache (GET /esq-tree) against the authoritative
 * natural tree (GET /esq-cmd-tree) for a given seed subtree. Both endpoints
 * return EsqTreeNode-shaped objects; this chain pulls both, builds maps,
 * and reports mismatches.
 *
 * Session inputs:
 *   compareSeedId   -- subtree root (optional; falls back to officeId, then PLAYGROUND_PARENT_ID).
 *   compareSeedKind -- seed kind (optional; defaults to ORG=20).
 *
 * Session outputs (counters; >0 means a mismatch was found):
 *   cmpMissingInBiztree -- entities in DB not represented in biztree cache.
 *   cmpStaleInBiztree   -- biztree real-entity nodes not in DB.
 *   cmpFieldMismatch    -- entities in both with differing name/kind/desc.
 *   cmpParentMismatch   -- entities in both whose biztree parent doesn't
 *                          resolve to the same real parent entity in DB
 *                          (after traversing folder nodes).
 *   cmpShortcutMissing  -- accounts in biztree real nodes lacking a shortcut
 *                          under their org's FOLDER_ACCOUNT.
 *   cmpShortcutDangling -- biztree shortcut nodes whose linkId points to
 *                          nothing in biztree real nodes.
 *
 * Output mechanism: mismatches are printed to stderr with "[CompareTrees]"
 * prefix, one line per category, followed by sample ids (capped at 20).
 * Counters in session let callers conditionally halt or just observe.
 *
 * Run cost: two HTTP calls per invocation. Cheap enough to call after every
 * tree-modifying step in a smoke simulation.
 */
public final class CompareTrees {

    private CompareTrees() {}

    // bizTree virtual folder kinds; these have entityId==null and don't
    // appear in the natural tree.
    private static final Set<Integer> FOLDER_KINDS = new HashSet<>();
    static {
        FOLDER_KINDS.add(2);   // sys admins
        FOLDER_KINDS.add(4);   // admin-s
        FOLDER_KINDS.add(6);   // all accounts
        FOLDER_KINDS.add(8);   // clients
        FOLDER_KINDS.add(10);  // merchants
    }

    // Account kinds in the natural tree -- their shortcut copies in biztree
    // appear under FOLDER_ACCOUNT (kind=6) with kind = realKind + 1.
    private static final Set<Integer> ACCT_KINDS = new HashSet<>();
    static {
        ACCT_KINDS.add(50);
        ACCT_KINDS.add(52);
        ACCT_KINDS.add(54);
    }

    public static final ChainBuilder chain =
        // Resolve seed id/kind from session with fallbacks.
        exec(session -> {
            String seedId = session.contains("compareSeedId")
                ? session.getString("compareSeedId")
                : session.contains("officeId")
                    ? session.getString("officeId")
                    : EsqHauberkConfig.PLAYGROUND_PARENT_ID;
            int seedKind = session.contains("compareSeedKind")
                ? session.getInt("compareSeedKind")
                : EntityKinds.ORG;
            return session.set("cmpSeedId", seedId).set("cmpSeedKind", seedKind);
        })
        // Pull biztree's view (cache).
        .exec(http("GET /esq-tree (biztree cache subtree)")
            .get("/esq-tree")
            .queryParam("id", "#{cmpSeedId}")
            .check(status().is(200))
            .check(jsonPath("$").ofList().saveAs("cmpBiztreeNodes"))
        )
        // Pull natural tree (authoritative).
        .exec(http("GET /esq-cmd-tree (natural subtree)")
            .get("/esq-cmd-tree")
            .queryParam("kind", "#{cmpSeedKind}")
            .queryParam("id",   "#{cmpSeedId}")
            .check(status().is(200))
            .check(jsonPath("$").ofList().saveAs("cmpNaturalNodes"))
        )
        // Run diff in Java; store counters + emit stderr lines per category.
        .exec(session -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bizList = (List<Map<String, Object>>) session.get("cmpBiztreeNodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> natList = (List<Map<String, Object>>) session.get("cmpNaturalNodes");
            String seedId = session.getString("cmpSeedId");

            // Build maps keyed by entityId for diff.
            //   bizReal:      real-entity biztree nodes (linkId == null, entityId != null)
            //   bizShortcuts: shortcut biztree nodes  (linkId != null) -> linkId -> list of shortcut nodes
            //   bizFolders:   folder nodes (entityId == null)          -> by id
            Map<Long, Map<String, Object>>       bizReal      = new HashMap<>();
            Map<Long, List<Map<String, Object>>> bizShortcuts = new HashMap<>();
            Map<String, Map<String, Object>>     bizFolders   = new HashMap<>();
            Map<String, Map<String, Object>>     bizById      = new HashMap<>();
            for (Map<String, Object> n : bizList) {
                bizById.put(String.valueOf(n.get("id")), n);
                Object linkId   = n.get("linkId");
                Object entityId = n.get("entityId");
                if (entityId == null) {
                    bizFolders.put(String.valueOf(n.get("id")), n);
                } else if (linkId != null) {
                    bizShortcuts.computeIfAbsent(toLong(linkId), k -> new ArrayList<>()).add(n);
                } else {
                    bizReal.put(toLong(entityId), n);
                }
            }
            Map<Long, Map<String, Object>> natByEntityId = new HashMap<>();
            for (Map<String, Object> n : natList) {
                Object entityId = n.get("entityId");
                if (entityId != null) {
                    natByEntityId.put(toLong(entityId), n);
                }
            }

            // 1. Missing in biztree: entities in natural not represented as real biztree nodes.
            List<Long> missingInBiztree = new ArrayList<>();
            for (Long eid : natByEntityId.keySet()) {
                if (!bizReal.containsKey(eid)) missingInBiztree.add(eid);
            }

            // 2. Stale in biztree: real biztree nodes whose entity isn't in natural.
            List<Long> staleInBiztree = new ArrayList<>();
            for (Long eid : bizReal.keySet()) {
                if (!natByEntityId.containsKey(eid)) staleInBiztree.add(eid);
            }

            // 3. Field mismatches: name, kind, desc.
            List<Long> fieldMismatch = new ArrayList<>();
            for (Map.Entry<Long, Map<String, Object>> e : natByEntityId.entrySet()) {
                Long eid = e.getKey();
                Map<String, Object> nat = e.getValue();
                Map<String, Object> biz = bizReal.get(eid);
                if (biz == null) continue;
                if (!Objects.equals(nat.get("name"), biz.get("name"))) { fieldMismatch.add(eid); continue; }
                if (!Objects.equals(nat.get("kind"), biz.get("kind"))) { fieldMismatch.add(eid); continue; }
                if (!Objects.equals(nat.get("desc"), biz.get("desc"))) { fieldMismatch.add(eid); continue; }
            }

            // 4. Parent mismatches. bizTree's parentId may be a folder; resolve
            //    upward through folders until we hit a real entity id, then
            //    compare to the natural-tree parentId.
            List<Long> parentMismatch = new ArrayList<>();
            for (Map.Entry<Long, Map<String, Object>> e : natByEntityId.entrySet()) {
                Long eid = e.getKey();
                Map<String, Object> nat = e.getValue();
                Map<String, Object> biz = bizReal.get(eid);
                if (biz == null) continue;
                String bizParentId = String.valueOf(biz.get("parentId"));
                Long bizParentEntityId = resolveToRealEntity(bizParentId, bizById);
                Object natParentId = nat.get("parentId");
                Long natParentEntityId = natParentId == null ? null : toLong(natParentId);
                // If the seed itself, natural-tree parent may sit outside the subtree
                // -- skip comparison for the seed row.
                if (String.valueOf(eid).equals(seedId)) continue;
                if (!Objects.equals(bizParentEntityId, natParentEntityId)) {
                    parentMismatch.add(eid);
                }
            }

            // 5. Shortcut missing: every account in bizReal should have at least one
            //    shortcut node (under its org's FOLDER_ACCOUNT).
            List<Long> shortcutMissing = new ArrayList<>();
            for (Map.Entry<Long, Map<String, Object>> e : bizReal.entrySet()) {
                Object kindObj = e.getValue().get("kind");
                int kind = kindObj instanceof Number ? ((Number) kindObj).intValue() : -1;
                if (ACCT_KINDS.contains(kind) && !bizShortcuts.containsKey(e.getKey())) {
                    shortcutMissing.add(e.getKey());
                }
            }

            // 6. Shortcut dangling: shortcut's linkId points at no real biztree node.
            List<Long> shortcutDangling = new ArrayList<>();
            for (Long linkedEid : bizShortcuts.keySet()) {
                if (!bizReal.containsKey(linkedEid)) shortcutDangling.add(linkedEid);
            }

            // One-line summary -- always printed so the diff's execution is
            // visible in stderr even when everything matches. Mismatch lines
            // (from report() below) only appear when a category is non-empty.
            System.err.println("[CompareTrees] seed=" + seedId
                    + " biztreeNodes=" + bizList.size()
                    + " naturalNodes=" + natList.size()
                    + " bizReal=" + bizReal.size()
                    + " bizShortcuts=" + bizShortcuts.size()
                    + " bizFolders=" + bizFolders.size()
                    + " natByEntity=" + natByEntityId.size());

            report("missing in biztree (real-entity nodes absent)",        missingInBiztree);
            report("stale in biztree (real-entity nodes not in DB)",       staleInBiztree);
            report("field mismatch (name/kind/desc differ)",               fieldMismatch);
            report("parent mismatch (resolved real parent differs)",       parentMismatch);
            report("shortcut missing (account without FOLDER_ACCOUNT copy)", shortcutMissing);
            report("shortcut dangling (linkId points at nothing real)",    shortcutDangling);

            return session
                .set("cmpMissingInBiztree", missingInBiztree.size())
                .set("cmpStaleInBiztree",   staleInBiztree.size())
                .set("cmpFieldMismatch",    fieldMismatch.size())
                .set("cmpParentMismatch",   parentMismatch.size())
                .set("cmpShortcutMissing",  shortcutMissing.size())
                .set("cmpShortcutDangling", shortcutDangling.size());
        });

    private static long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : Long.parseLong(o.toString());
    }

    // Walks biztree parentId chain through folder nodes; returns the first
    // real-entity ancestor's entityId, or null if the chain runs off the
    // top of the subtree without hitting a real entity.
    private static Long resolveToRealEntity(String startId, Map<String, Map<String, Object>> bizById) {
        Long ret = null;
        String cur = startId;
        int hops = 0;
        while (cur != null && hops++ < 50) {
            Map<String, Object> node = bizById.get(cur);
            if (node == null) break;
            Object entityId = node.get("entityId");
            Object linkId   = node.get("linkId");
            if (entityId != null && linkId == null) {
                ret = toLong(entityId);
                break;
            }
            cur = node.get("parentId") == null ? null : String.valueOf(node.get("parentId"));
        }
        return ret;
    }

    private static void report(String label, List<Long> ids) {
        if (ids.isEmpty()) return;
        StringBuilder sb = new StringBuilder("[CompareTrees] ")
                .append(ids.size())
                .append(" ")
                .append(label)
                .append(": ");
        int cap = Math.min(20, ids.size());
        for (int i = 0; i < cap; i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        if (ids.size() > cap) sb.append("...");
        System.err.println(sb);
    }
}

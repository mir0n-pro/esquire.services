/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain GET /esq-enode?name=officeName via biztree cache for O(1) office-id lookup
 * 08/15/2026 mir0n  the note on the accepted statuses states that an absent office is a 404; 400 stays accepted
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * GET /esq-enode?kind=20&name=#{officeName}. Resolves an office name to its
 * id via the biztree node lookup -- a cheap O(1) cache hit, in contrast to
 * a whole-subtree fetch from authoritative source.
 *
 * Cache trade-off accepted: biztree may occasionally hold a stale id for
 * an office that no longer exists in esq_org. That is fine for the cleanup
 * use case -- the v1.2.4 goal is "remove ALL test data from DB", and a
 * stale id passed into /esq-cmd-tree returns an empty subtree (which is
 * the correct cleanup answer: nothing in DB to remove). Biztree cache
 * invalidation is a separate concern, out of scope here.
 *
 * Session inputs:
 *   officeName -- the office name to look up.
 *
 * Session outputs:
 *   officeId   -- set on 200; not set on 404 (caller checks .contains()
 *                 to distinguish "found" from "missing").
 *
 * Status filtering: both 200 and 404 are acceptable here; the chain does
 * not fail on a missing office. Callers branch on whether officeId was set.
 */
public final class LookupOfficeIdByName {

    private LookupOfficeIdByName() {}

    public static final ChainBuilder chain =
        exec(http("GET /esq-enode (lookup office by name)")
            .get("/esq-enode")
            .queryParam("kind", EntityKinds.ORG)
            .queryParam("name", "#{officeName}")
            // An absent office is a 404; 400 stays accepted so a run works against an older deployment too.
            .check(status().in(200, 400, 404))
            .check(
                jsonPath("$.id").optional().saveAs("officeId")
            ));
}

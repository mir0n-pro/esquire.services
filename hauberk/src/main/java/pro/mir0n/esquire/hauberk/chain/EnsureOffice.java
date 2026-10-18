/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: idempotent find-or-create office by name: LookupOfficeIdByName then verify via /esq-cmd; CreateOffice if missing or stale
 * 08/15/2026 mir0n  the note on the accepted statuses states that an absent office is a 404; 400 stays accepted
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Idempotent find-or-create for an office. Three-step flow:
 *   1. Lookup by name via biztree-cache /esq-enode (fast).
 *   2. If lookup returned an id, VERIFY against authoritative source
 *      (/esq-cmd) -- biztree may hold stale entries from older SQL-surgery
 *      cleanups that didn't broadcast deletes. A 404 on verify busts the
 *      stale id so step 3 creates fresh.
 *   3. If no id is set (lookup miss OR verify bust), POST /esq-cmd-new.
 *
 * Session inputs:
 *   officeName -- the office name to ensure exists.
 *
 * Session outputs:
 *   officeId   -- id of the found or newly-created office, guaranteed
 *                 to exist in esq_org (verified or freshly created).
 *
 * Designed so reruns over residue work without operator intervention.
 */
public final class EnsureOffice {

    private EnsureOffice() {}

    public static final ChainBuilder chain =
        // Step 1: lookup via biztree
        exec(LookupOfficeIdByName.chain)
        // Step 2: if we got an id, verify it exists in the authoritative source.
        //   An office that is not there is a 404; 400 stays accepted so a run
        //   works against an older deployment too. A bust-the-stale-officeId
        //   follows when verification doesn't return a body with id.
        .doIf(session -> session.contains("officeId")).then(
            exec(http("GET /esq-cmd (verify office exists in DB)")
                .get("/esq-cmd")
                .queryParam("kind", EntityKinds.ORG)
                .queryParam("id",   "#{officeId}")
                .check(status().in(200, 400, 404))
                .check(jsonPath("$.id").optional().saveAs("verifiedOfficeId")))
            .exec(session -> session.contains("verifiedOfficeId")
                ? session
                : session.remove("officeId"))
        )
        // Step 3: still no officeId -> create fresh.
        .doIf(session -> !session.contains("officeId")).then(
            exec(http("POST /esq-cmd-new (office, ensure)")
                .post("/esq-cmd-new")
                .queryParam("kind",     EntityKinds.ORG)
                .queryParam("parentId", EsqHauberkConfig.PLAYGROUND_PARENT_ID)
                .queryParam("cmd",      "new")
                .header("Content-Type", "application/json")
                .body(StringBody(
                    "{\"name\":\"#{officeName}\",\"desc\":\"hauberk sprint test office\"}"))
                .check(status().is(200))
                .check(jsonPath("$.id").saveAs("officeId")))
        );
}

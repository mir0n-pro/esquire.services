/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-cmd-new kind=20 under parent office (session officeId); replaces session officeId with the new sub-office id
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
 * POST /esq-cmd-new kind=20 parentId={current officeId} -- creates a
 * sub-office UNDER an existing office. Replaces session.officeId with the
 * new office's id, so a sequence of CreateSubOffice exec()s chain into
 * a nested tree: L1 (from EnsureOffice) -> CreateSubOffice -> L2 ->
 * CreateSubOffice -> L3 -> ...
 *
 * Session inputs:
 *   officeName -- the new sub-office's name (caller sets per level,
 *                 typically "w{vu}-l{n}" so concurrent workers stay
 *                 collision-free under a shared root).
 *   officeId   -- the parent office's id (set by EnsureOffice or the
 *                 previous CreateSubOffice).
 *
 * Session outputs:
 *   officeId   -- replaced with the just-created sub-office's id.
 */
public final class CreateSubOffice {

    private CreateSubOffice() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-cmd-new (sub-office)")
            .post("/esq-cmd-new")
            .queryParam("kind",     EntityKinds.ORG)
            .queryParam("parentId", "#{officeId}")
            .queryParam("cmd",      "new")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{\"name\":\"#{officeName}\",\"desc\":\"hauberk nested office\"}"))
            .check(status().is(200))
            .check(jsonPath("$.id").saveAs("officeId")));
}

/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-cmd-new kind=20 under playground root; saves officeId to session
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
 * POST /esq-cmd-new for kind=20 (Organization). Creates an office under the
 * root entity. The office name is taken from session attribute "officeName"
 * (caller seeds it via feeder or .exec(session -> ...)). On 200 the new
 * office id is captured into session as "officeId".
 */
public final class CreateOffice {

    private CreateOffice() {}

    public static final ChainBuilder chain =
        exec(http("POST /esq-cmd-new (office)")
            .post("/esq-cmd-new")
            .queryParam("kind",     EntityKinds.ORG)
            .queryParam("parentId", EsqHauberkConfig.PLAYGROUND_PARENT_ID)
            .queryParam("cmd",      "new")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{\"name\":\"#{officeName}\",\"desc\":\"hauberk sprint test office\"}"))
            .check(status().is(200))
            .check(jsonPath("$.id").saveAs("officeId")));
}

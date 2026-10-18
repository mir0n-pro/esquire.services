/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain GET /esq-key?id=usrId; saves connectFlg / loginId / email; tolerates 400/404 (no access profile yet)
 * 08/15/2026 mir0n  the class note states that "no access profile yet" is a 404; 400 stays accepted
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * GET /esq-key?id={usrId} -- read access profile from keySmith.
 * Saves the user's connectFlg + loginId + email to session for downstream
 * decisions (e.g. cleanup pre-pass: only call DisconnectUser when
 * connectFlg=="Y"; LoginViaBff: use loginId/email for KC form post).
 *
 * Session inputs:
 *   apId  -- entity id of the USR whose access profile to read.
 *
 * Session outputs:
 *   apConnectFlg     -- "Y" / "N"; absent if no access profile yet.
 *   apLoginId        -- KC username; absent if no access profile yet.
 *   apEmail          -- email; absent if no access profile yet.
 *   apPwdChangeForced -- "Y" / "N"; absent if no access profile yet.
 *
 * Status filtering: 200 + 400 + 404 accepted. 404 means "no access
 * profile yet" (a ResourceNotFoundException response), which is normal
 * for newly-created users that haven't been activated yet -- downstream
 * chains should branch on apConnectFlg.exists(). 400 stays accepted so a
 * run works against an older deployment too.
 */
public final class LookupAccessProfile {

    private LookupAccessProfile() {}

    public static final ChainBuilder chain =
        exec(http("GET /esq-key (lookup access profile)")
            .get("/esq-key")
            .queryParam("id", "#{apId}")
            .check(status().in(200, 400, 404))
            .check(jsonPath("$.connectFlg").optional().saveAs("apConnectFlg"))
            .check(jsonPath("$.loginId").optional().saveAs("apLoginId"))
            .check(jsonPath("$.email").optional().saveAs("apEmail"))
            .check(jsonPath("$.pwdChangeForced").optional().saveAs("apPwdChangeForced")));
}

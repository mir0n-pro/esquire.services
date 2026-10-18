/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: access-profile end-to-end -- enyMan + keySmith + kcMaster + KC + BFF login handshake; ensures a fresh kind=34 user can OIDC-authenticate via the BFF
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.*;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Access-profile end-to-end smoke: enyMan + keySmith + kcMaster + KC + BFF
 * (logon handshake only). Validates that a freshly-created client user can
 * actually log in through the production-style auth path.
 *
 * Deliberately excludes the account workflow (CreateAccount / Deposit /
 * etc.) -- those are covered by EntitySmokeSimulation. This sim is the
 * identity path only.
 *
 * Flow:
 *   EnsureOffice    -- find-or-create test office
 *   CompareTrees    -- diff biztree vs natural after office
 *   CreateUser      -- kind=34 client user (writes userEmail to session)
 *   CompareTrees    -- diff after user create
 *   ConnectUser     -- POST /esq-key-save connectFlg=Y, roles=TREE+CLIENT
 *                      -> keySmith publishes URQ -> kcMaster creates KC user
 *   CompareTrees    -- diff after access profile activation
 *   LoginViaBff     -- drive OIDC handshake: GET /auth/login -> KC form
 *                      -> KC update-password -> BFF callback -> /auth/me
 *   CleanupOfficeByName -- two-pass: disconnect all USRs, then delete tree
 *   CompareTrees    -- diff after cleanup; should match (both empty)
 */
@SimulationInfo("Access-profile path: enyMan + keySmith + kcMaster + KC + BFF login")
public class KcIntegrationSmokeSimulation extends AEsqSimulation {

    ScenarioBuilder scn = scenario("kc-integration-smoke")
            .exec(session -> session
                    .set("officeName", "hauberk-office-smoke"))
            .exec(EnsureOffice.chain)        .exitHereIfFailed()
            .exec(CompareTrees.chain)
            .exec(CreateUser.chain)          .exitHereIfFailed()
            .exec(CompareTrees.chain)
            // ConnectUser uses userId/userEmail set by CreateUser.
            .exec(ConnectUser.chain)         .exitHereIfFailed()
            .exec(CompareTrees.chain)
            .exec(LoginViaBff.chain)         .exitHereIfFailed()
            // CleanupOfficeByName disconnects + deletes; best-effort.
            .exec(CleanupOfficeByName.chain)
            .exec(CompareTrees.chain);

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

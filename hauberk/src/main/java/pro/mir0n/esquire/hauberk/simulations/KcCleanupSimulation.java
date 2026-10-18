/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 06/01/2026 mir0n  created: KC-side orphan cleanup -- master-realm admin auth + DELETE every
 *                   hauberk-* user in the esquire realm. Runs against the KC admin REST API
 *                   directly (not the gateway), so it is independent of TokenRelay mode.
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.CleanupKcOrphans;
import pro.mir0n.esquire.hauberk.chain.KcAdminAuth;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Standalone teardown for the KC side of the hauberk playground.
 *
 * After a race-8c run (RaceMoveCreateKc), KC may be left with hauberk-* users
 * whose DB counterparts have been deleted by the standard CleanHouse run. The
 * /esq-cmd-del DELETE flow does best-effort KC removal via kcMaster, but the
 * race-8c silent-skip means kcMaster may have missed the original CREATE URQ
 * for a user, leaving an orphan with no DB row to delete-from.
 *
 * This sim authenticates with the KC master-realm bootstrap admin
 * (hauberk.properties: kc.admin.user / kc.admin.password) and walks the
 * esquire realm /admin/realms/esquire/users?search=hauberk- list, DELETE-ing
 * each. Safe to run idempotently -- 404s on already-deleted users are
 * accepted (other VUs / prior runs may have removed them).
 *
 * Run pattern:
 *   hauberk.cmd CleanHouse        (DB-side teardown -- removes esq_user rows)
 *   hauberk.cmd KcCleanup         (this sim -- removes KC users left behind)
 *
 * Run order matters only in one direction: KcCleanup AFTER CleanHouse, so
 * the standard /esq-cmd-del path has already had its chance to remove KC
 * users via kcMaster. Anything left is by definition an orphan.
 */
@SimulationInfo("Standalone teardown: DELETE hauberk-* KC users via master-realm admin")
public class KcCleanupSimulation extends AEsqSimulation {

    ScenarioBuilder scn = scenario("kc-cleanup")
            .exec(KcAdminAuth.chain)
            .exec(CleanupKcOrphans.chain);

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

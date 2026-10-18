/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: one-shot targeted residue purge -- walks the playground for any hauberk-office-smoke leftovers and deletes its subtree bottom-up
 * 06/02/2026 mir0n  name-prefix driven: finds ALL offices directly under Test House whose name starts with
 *                   -Dcleanup.prefix (default "hauberk-office-smoke") and runs each through CleanupOfficeByName
 *                   (disconnect-then-delete, /esq-cmd-tree FK walk). Catches msgloss / other-named leftovers too.
 * 06/10/2026 mir0n  @SimulationInfo description trimmed to fit the hauberk list table (<=90 chars)
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.CleanupOfficeByName;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * One-shot residue cleanup: lists the offices directly under Test House and,
 * for each whose name starts with the prefix {@code -Dcleanup.prefix}
 * (default {@code hauberk-office-smoke}), runs the full
 * {@link CleanupOfficeByName} teardown -- disconnect connected USRs (release
 * their KC identity), then delete the subtree bottom-up via the FK-based
 * /esq-cmd-tree (biztree-cache-independent). Best-effort and idempotent.
 *
 * Examples:
 *   residue-cleanup                                   -> purge hauberk-office-smoke* offices
 *   -Dcleanup.prefix=hauberk-office-msgloss residue-cleanup
 *                                                     -> purge the message-loss leftovers
 *   -Dcleanup.prefix=hauberk-office residue-cleanup   -> purge ALL hauberk test offices
 *
 * The seeded Test Driver users (uid 15/16/17) and Test House (pk 14) itself are
 * never matched (they are not offices named with the prefix), so they are safe.
 */
@SimulationInfo("Targeted residue purge: offices under Test House matching -Dcleanup.prefix")
public class ResidueCleanupSimulation extends AEsqSimulation {

    private static final String PREFIX = System.getProperty("cleanup.prefix", "hauberk-office-smoke");

    ScenarioBuilder scn = scenario("residue-cleanup")
            .exec(session -> {
                System.err.println("[ResidueCleanup] purging offices under Test House with name prefix '" + PREFIX + "'");
                return session;
            })
            .exec(http("GET /esq (Test House children)")
                    .get("/esq")
                    .queryParam("id", EsqHauberkConfig.PLAYGROUND_PARENT_ID)
                    .check(status().is(200))
                    .check(jsonPath("$").ofList().saveAs("rootChildren")))
            .exec(session -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) session.get("rootChildren");
                List<String> names = new ArrayList<>();
                if (children != null) {
                    for (Map<String, Object> c : children) {
                        Object kind = c.get("kind");
                        Object name = c.get("name");
                        if (kind instanceof Number && ((Number) kind).intValue() == EntityKinds.ORG
                                && name != null && name.toString().startsWith(PREFIX)) {
                            names.add(name.toString());
                        }
                    }
                }
                System.err.println("[ResidueCleanup] matched " + names.size() + " office(s): " + names);
                return session.set("officeNames", names);
            })
            .foreach("#{officeNames}", "officeName").on(
                    exec(CleanupOfficeByName.chain)
            );

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol);
    }
}

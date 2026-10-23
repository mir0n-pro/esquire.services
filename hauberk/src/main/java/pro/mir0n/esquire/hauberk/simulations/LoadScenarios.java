/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: shared scenario library (READ/UPDATE/CREATE/MOVE/TX) used by SuperLoad and the five standalone *LoadSimulation variants
 * 07/14/2026 mir0n  exitHereIfFailed() between the setup and the .forever() loop in all five scenarios -- a VU whose
 *                   setup failed used to walk into the loop with no session attributes, so its requests were never
 *                   SENT and the loop spun at full CPU issuing nothing
 * 08/15/2026 mir0n  the in-loop transient-failure example no longer names a status code
 * 09/24/2026 mir0n  v1.2.16 -- five read scenarios added: TREE (ReadTreeRoot), TREE_SMALL (PoolFetchUsers +
 *                   PickRandomFromPool.userChain + ReadTreeUser), CACHE (ReadCacheRoot), CACHE_SMALL
 *                   (PoolFetchUsers + PickRandomFromPool.userChain + ReadCacheUser) and READ_ACCT
 *                   (PoolFetchAccounts + PickRandomFromPool.acctChain + ReadAcctDetail). The three pooled
 *                   ones carry the same exitHereIfFailed() between the setup and the .forever() loop
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 * 10/19/2026 mir0n  v2.0.1 -- the tx scenario on two random accounts: deposit A, transfer A->B, transfer B->A,
 *                   withdraw A (net-zero; the transfers skipped when A and B are the same)
 */
package pro.mir0n.esquire.hauberk.simulations;

import java.util.List;
import java.util.Map;

import io.gatling.javaapi.core.ScenarioBuilder;

import pro.mir0n.esquire.hauberk.chain.*;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Reusable ScenarioBuilders for the 5 load shapes. Each is a public
 * static .forever() loop intended to be driven by an outer setUp(...)
 * call with maxDuration. Used by:
 *   - SuperLoadSimulation (all 5 in parallel)
 *   - {Read,Update,Create,Move,Tx}LoadSimulation (one at a time)
 *
 * Constants:
 *   TX_AMOUNT -- fixed amount for every step of the tx scenario; the cycle
 *                (deposit, transfer there and back, withdrawal) is net-zero
 *                so balance stays predictable under concurrent load.
 *
 * THE SETUP GUARD -- exitHereIfFailed() between the setup and the loop.
 * Every scenario resolves its working set ONCE before .forever(): the user /
 * account pool, or the office ids it will oscillate. Those ids are then read
 * from the session on every iteration.
 *
 * A failed setup does NOT stop a VU by itself -- markAsFailed() and a KO'd
 * check both leave the session running, so the VU walks straight into the
 * loop with `officeId` / `userPool` / `acctPool` never set. Gatling then
 * cannot resolve `#{officeId}`, so the request is NOT SENT -- and an
 * iteration that sends nothing takes no time at all. The forever loop
 * becomes a hot spin: a single VU that lost its setup burns a core producing
 * "No attribute named 'officeId'" as fast as the CPU allows, issuing ZERO
 * requests.
 *
 * That is not a hypothetical. One 60-second super-load run (2026-07-13, T10)
 * logged 3.5 MILLION such errors behind only 116 real HTTP failures, after a
 * gateway circuit breaker briefly 503'd the /esq-enode lookup that every
 * setup depends on. The generator spent the run spinning instead of loading,
 * which silently CAPPED the measured throughput -- the numbers looked like a
 * server-side ceiling and were nothing of the kind.
 *
 * So a VU whose setup failed must LEAVE. The guard belongs here, between the
 * setup and the loop -- and NOT inside the loop, where a transient failure
 * (two movers racing the same office) is normal and must not kill the VU.
 * Fewer VUs is an honest, visible degradation; a spin is a lie.
 */
public final class LoadScenarios {

    private LoadScenarios() {}

    public static final String TX_AMOUNT = "10.00";

    // (a) read random user details
    public static final ScenarioBuilder READ = scenario("load-read")
            .exec(PoolFetchUsers.chain)
            .exitHereIfFailed()                 // no pool -> no work; leave rather than spin
            .forever().on(
                exec(PickRandomFromPool.userChain)
                .exec(ReadUserDetail.chain)
            );

    // (b) update random user's address
    public static final ScenarioBuilder UPDATE = scenario("load-update")
            .exec(PoolFetchUsers.chain)
            .exitHereIfFailed()                 // no pool -> no work; leave rather than spin
            .forever().on(
                exec(PickRandomFromPool.userChain)
                .exec(UpdateUserAddress.chain)
            );

    // (c) create + delete user at deepest office in the hauberk subtree
    public static final ScenarioBuilder CREATE = scenario("load-create")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(http("GET /esq-cmd-tree (find bottom office in hauberk subtree)")
                .get("/esq-cmd-tree")
                .queryParam("kind", EntityKinds.ORG)
                .queryParam("id",   "#{officeId}")
                .check(status().is(200))
                .check(jsonPath("$").ofList().saveAs("createSubtree")))
            .exec(session -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tree =
                        (List<Map<String, Object>>) session.get("createSubtree");
                String deepestId = null;
                int maxLevel = -1;
                for (Map<String, Object> n : tree) {
                    Object kind  = n.get("kind");
                    Object level = n.get("level");
                    if (kind instanceof Number
                            && ((Number) kind).intValue() == EntityKinds.ORG
                            && level instanceof Number) {
                        int lvl = ((Number) level).intValue();
                        if (lvl > maxLevel) {
                            maxLevel = lvl;
                            deepestId = String.valueOf(n.get("id"));
                        }
                    }
                }
                if (deepestId == null) {
                    System.err.println("[LoadScenarios] CREATE: no office found "
                            + "-- did PrepareForAnything run?");
                    return session.markAsFailed();
                }
                return session.set("officeId", deepestId);
            })
            .exitHereIfFailed()                 // no office -> every create would fire blind; leave
            .forever().on(
                exec(CreateUser.chain)
                .exec(session -> session
                        .set("delKind", EntityKinds.USR_CLIENT)
                        .set("delId",   session.getString("userId")))
                .exec(DeleteEntity.chain)
            );

    // (d) move oscillate -- all VUs hit the SAME shared office w1-l3
    public static final ScenarioBuilder MOVE = scenario("load-move")
            .exec(session -> session.set("officeName", "hauberk-office-smoke"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTopId",
                    session.getString("officeId")))
            .exec(session -> session.set("officeName", "w1-l2"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveOriginalParentId",
                    session.getString("officeId")))
            .exec(session -> session.set("officeName", "w1-l3"))
            .exec(LookupOfficeIdByName.chain)
            .exec(session -> session.set("moveTargetId",
                    session.getString("officeId")))
            .exitHereIfFailed()                 // a lookup missed -> moveId would be null; leave
            .forever().on(
                exec(session -> session
                        .set("moveKind",   EntityKinds.ORG)
                        .set("moveId",     session.getString("moveTargetId"))
                        .set("moveDestId", session.getString("moveTopId")))
                .exec(MoveEntity.chain)
                .exec(session -> session
                        .set("moveDestId", session.getString("moveOriginalParentId")))
                .exec(MoveEntity.chain)
            );

    // (e) tx cycle on random accounts A and B -- deposit A, transfer A->B, transfer B->A, withdraw A; net-zero
    public static final ScenarioBuilder TX = scenario("load-tx")
            .exec(PoolFetchAccounts.chain)
            .exitHereIfFailed()                 // no accounts -> no work; leave rather than spin
            .forever().on(
                exec(PickRandomFromPool.acctChain)
                .exec(session -> session
                        .set("acctId",   session.getString("pickedAcctId"))
                        .set("txAmount", TX_AMOUNT))
                .exec(Deposit.chain)
                .exec(PickRandomFromPool.acctChain)
                .exec(session -> session.set("acctId2", session.getString("pickedAcctId")))
                //xxx: the transfers sit between the deposit and the withdrawal, so A never goes below its start
                .doIf(session -> !session.getString("acctId").equals(session.getString("acctId2"))).then(
                    exec(Transfer.chain)
                    .exec(session -> session
                            .set("acctId",  session.getString("acctId2"))
                            .set("acctId2", session.getString("acctId")))
                    .exec(Transfer.chain)
                    .exec(session -> session
                            .set("acctId",  session.getString("acctId2"))
                            .set("acctId2", session.getString("acctId")))
                )
                .exec(Withdrawal.chain)
            );

    // (f) read the tree from the driver's own root
    public static final ScenarioBuilder TREE = scenario("load-tree")
            .forever().on(
                exec(ReadTreeRoot.chain)
            );

    // (h) read one customer's subtree
    public static final ScenarioBuilder TREE_SMALL = scenario("load-tree-small")
            .exec(PoolFetchUsers.chain)
            .exitHereIfFailed()                 // no pool -> no work; leave rather than spin
            .forever().on(
                exec(PickRandomFromPool.userChain)
                .exec(ReadTreeUser.chain)
            );

    // (i) read the tree from the cache, root
    public static final ScenarioBuilder CACHE = scenario("load-cache")
            .forever().on(
                exec(ReadCacheRoot.chain)
            );

    // (j) read one customer's subtree from the cache
    public static final ScenarioBuilder CACHE_SMALL = scenario("load-cache-small")
            .exec(PoolFetchUsers.chain)
            .exitHereIfFailed()                 // no pool -> no work; leave rather than spin
            .forever().on(
                exec(PickRandomFromPool.userChain)
                .exec(ReadCacheUser.chain)
            );

    // (g) read random account details
    public static final ScenarioBuilder READ_ACCT = scenario("load-read-acct")
            .exec(PoolFetchAccounts.chain)
            .exitHereIfFailed()                 // no accounts -> no work; leave rather than spin
            .forever().on(
                exec(PickRandomFromPool.acctChain)
                .exec(ReadAcctDetail.chain)
            );
}

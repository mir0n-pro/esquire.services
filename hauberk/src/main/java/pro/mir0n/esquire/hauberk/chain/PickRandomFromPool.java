/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: session lambda picking a random id from userPool / acctPool into pickedUserId / pickedAcctId; used inside Load loops
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Session-only chains that draw a random element from a pre-fetched pool
 * and write it into a typed session attribute.
 *
 * - userChain: reads `userPool` (List<String>) -> writes `pickedUserId`.
 * - acctChain: reads `acctPool` (List<String>) -> writes `pickedAcctId`.
 *
 * Empty pool marks the VU as failed (the simulation expects the
 * playground to be prepared).
 */
public final class PickRandomFromPool {

    private PickRandomFromPool() {}

    public static final ChainBuilder userChain =
        exec(session -> {
            List<?> pool = session.getList("userPool");
            if (pool == null || pool.isEmpty()) {
                System.err.println("[PickRandomFromPool] userPool empty -- "
                        + "did PrepareForAnything run?");
                return session.markAsFailed();
            }
            Object picked = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            return session.set("pickedUserId", String.valueOf(picked));
        });

    public static final ChainBuilder acctChain =
        exec(session -> {
            List<?> pool = session.getList("acctPool");
            if (pool == null || pool.isEmpty()) {
                System.err.println("[PickRandomFromPool] acctPool empty -- "
                        + "did PrepareForAnything run with accounts.per.client > 0?");
                return session.markAsFailed();
            }
            Object picked = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            return session.set("pickedAcctId", String.valueOf(picked));
        });
}

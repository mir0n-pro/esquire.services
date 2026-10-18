/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: Chain POST /esq-cmd-save with random postal-address fields on pickedUserId; used by Update load
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import io.gatling.javaapi.core.ChainBuilder;
import pro.mir0n.esquire.hauberk.config.EntityKinds;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * POST /esq-cmd-save?kind=34&id={pickedUserId} with a postal-address-only
 * update body. Every postal field (addr/addr2/city/country/postalCode/
 * province) is overwritten with values containing a per-iteration random
 * slug so each write actually mutates columns (not a no-op).
 *
 * Used by SuperLoad scenario (b) to drive update load.
 *
 * Session inputs:
 *   pickedUserId -- user id, supplied by PickRandomFromPool.userChain.
 *
 * Session outputs:
 *   updSlug -- per-iteration short random hex; embedded in the new addr.
 */
public final class UpdateUserAddress {

    private UpdateUserAddress() {}

    public static final ChainBuilder chain =
        exec(session -> {
            String slug = "u" + Long.toHexString(
                    ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);
            return session.set("updSlug", slug);
        })
        .exec(http("POST /esq-cmd-save (update user address)")
            .post("/esq-cmd-save")
            .queryParam("kind", EntityKinds.USR_CLIENT)
            .queryParam("id",   "#{pickedUserId}")
            .header("Content-Type", "application/json")
            .body(StringBody(
                "{"
              +   "\"addr\":{"
              +     "\"addr\":\"#{updSlug} Update Ave\","
              +     "\"addr2\":\"Suite #{updSlug}\","
              +     "\"city\":\"City-#{updSlug}\","
              +     "\"country\":\"CA\","
              +     "\"postalCode\":\"V6B 1J8\","
              +     "\"province\":\"BC\""
              +   "}"
              + "}"))
            .check(status().is(200)));
}

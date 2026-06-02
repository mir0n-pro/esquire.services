/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: entity-id minter moved out of common.EsqUtils into enyMan;
 *                   decimal-positional BIGINT shape (ms since esquireEpoch) * 10000
 *                   + EsqUtils.instanceNo() * 1000 + (AtomicInteger sequence % 1000);
 *                   esquireEpoch = 26 Jun 2025 13:20 EDT (kept from v1.2.5);
 *                   IllegalStateException at mint time if instanceNo outside [0, 9].
 */

package pro.mir0n.esquire.enyMan.service;

import java.util.concurrent.atomic.AtomicInteger;

import pro.mir0n.esquire.common.EsqUtils;

/**
 * Entity-id minter. enyMan is the only service that mints entity PKs
 * (org / user / account); all CREATE paths route through enyMan so id
 * generation lives in exactly one place.
 *
 * v1.2.6 shape -- decimal-positional long, still a single BIGINT
 * (preserves the numeric globally-unique-PK invariant and the
 * ESQ_ENTITY_PATH shared-PK satellite):
 *
 *   id = (ms since esquire-era) * 10000
 *      + instanceNo                * 1000
 *      + sequence % 1000
 *
 * The bottom 4 decimal digits split as 1 instance digit + 3 sequence
 * digits: up to 10 enyMan instances, up to 1000 ids per millisecond
 * per instance. instanceNo source lives in common.EsqUtils.instanceNo()
 * (env / sysprop / 0). Sequence is a process-local AtomicInteger so
 * minting is thread-safe under concurrent CREATEs.
 */
public final class EntityIdGenerator {

    private EntityIdGenerator() {}

    private static final long esquireEpoch = new java.util.Date("26 Jun 2025 13:20 EDT").getTime();
    //**1,750,958,400,000**

    private static final AtomicInteger sequence = new AtomicInteger();

    // Decimal-positional encoding allocates exactly one digit for the instance
    // (the 1000s position of the bottom 4 digits), so values >= 10 would
    // overflow into the time portion and corrupt the id. Fail fast at the
    // first mint rather than emit silently-broken ids.
    public static long generateEntityId() {
        int inst = EsqUtils.instanceNo();
        if (inst < 0 || inst > 9) {
            throw new IllegalStateException(
                "instance number " + inst + " out of allowed range 0-9; "
              + "v1.2.6 id encoding allocates one decimal digit for instance. "
              + "Cap deployment replicas at 10.");
        }
        return (System.currentTimeMillis() - esquireEpoch) * 10000L
             + inst * 1000L
             + (sequence.getAndIncrement() % 1000);
    }
}

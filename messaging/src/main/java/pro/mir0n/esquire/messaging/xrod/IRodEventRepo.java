/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: the x-Rod repository contract -- how to apply ONE RodEvent to the DB
 *                   (its *_log table). The ONLY service-specific piece of the xx-Rod: each asset-owning
 *                   service implements an IRodEventRepo per *_log table and registers it by kind. The
 *                   xx-Rod worker pool calls apply() concurrently for distinct events, so impls MUST be
 *                   safe to run on many threads at once.
 */
package pro.mir0n.esquire.messaging.xrod;

/**
 * Applies a single {@link RodEvent} to a sink (the impl decides what the sink is).
 *
 * <p>Invoked concurrently by the x-rod receive pool for distinct events -- an impl must be
 * thread-safe. Any de-duplication / exactly-once guarantee across redelivery / concurrency is the
 * impl's concern.
 */
public interface IRodEventRepo {

    /** Apply the event to the sink. CREATE/UPDATE carry the body; DELETE carries the id + kind
     *  (body is empty). */
    void apply(RodEvent event);
}

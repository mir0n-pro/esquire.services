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
 * Applies a single {@link RodEvent} to the database (insert into the asset's {@code *_log} table).
 *
 * <p>Invoked concurrently by the xx-Rod worker pool for distinct events -- an impl must be
 * thread-safe (typically stateless, one JDBC insert per call). Exactly-once across redelivery /
 * concurrency is handled by the {@code (crl_id, entity_id, kind, sub_id)} {@code ON CONFLICT} /
 * {@code MERGE} on the {@code *_log} table: a duplicate insert is a no-op.
 */
public interface IRodEventRepo {

    /** Apply the event to its {@code *_log} table. CREATE/UPDATE write the body row; DELETE writes
     *  the id + kind tombstone (body is empty). */
    void apply(RodEvent event);
}

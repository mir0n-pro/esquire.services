/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the xxRod pluggable consumer strategy. The transport (JMS consumer) hands each
 *                   decoded RodEvent to a director; the audit director writes the *_log tables with no
 *                   ordering/grouping (parallel, bounded by the worker pool). Future directors (replication,
 *                   doc-DB) plug in behind the same seam.
 * 06/06/2026 mir0n  TYPE_* selection ids: the active director is chosen by xxrod.director.type (each id
 *                   backed by its own gated @Component); each director reads its own xxrod.director.<id>.* config.
 * 06/06/2026 mir0n  lifecycle for the generic xRod host (not audit-only): type() declares the selection id;
 *                   init(Environment) lets the director read its OWN properties and wire its sink; the host
 *                   calls init() at startup and shutdown() at stop. accept() handles one relayed event.
 * 06/15/2026 mir0n  RodEvent import moved common.xrod -> messaging.xrod (shared bus catalog package).
 */
package pro.mir0n.esquire.xxRod.director;

import org.springframework.core.env.Environment;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

/**
 * The pluggable consumer-side strategy of the generic xRod host. The host selects one director by
 * {@code xxrod.director.type}, calls {@link #init(Environment)} once at startup (where the director reads
 * its OWN {@code xxrod.director.<type>.*} properties and wires its sink), feeds it each decoded event via
 * {@link #accept(RodEvent)}, and calls {@link #shutdown()} at stop. Audit is the first director; future
 * directors (replication, doc-DB) implement the same contract -- the host stays director-agnostic.
 */
public interface IRodDirector {

    /** Director selection ids (the {@code xxrod.director.type} config value). */
    String TYPE_AUDIT = "audit";

    /** This director's selection id (matched against {@code xxrod.director.type}). */
    String type();

    /** Read this director's own {@code xxrod.director.<type>.*} properties and wire its sink. Called once
     *  by the host at startup, before any {@link #accept(RodEvent)}. */
    void init(Environment env);

    /** Process one relayed event (apply / route / sink it). */
    void accept(RodEvent event);

    /** Release this director's resources. Called once by the host at stop. */
    default void shutdown() {
    }
}

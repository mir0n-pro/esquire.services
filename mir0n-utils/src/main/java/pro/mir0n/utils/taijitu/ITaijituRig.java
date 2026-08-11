/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: the Taijitu DIRECTOR contract -- controls the cache monad(s).
 *                   Bean-blind, REST-free: bootstrap, event intake, shutdown. Domain reads are
 *                   added by the consumer's sub-interface (e.g. bizTree IBizTreeDirector).
 * 05/22/2026 mir0n  bootstrap() renamed start() (symmetric with shutdown()).
 * 05/23/2026 mir0n  added isReady() (the readiness gate -- loaded + serving) and the sweepAsync()
 *                   default no-op (the REST force-sweep; overridden by the dark director).
 * 06/15/2026 mir0n  pass(...) contract changed: the raw (messageEncoding, text) pair replaced by a single
 *                   already-parsed body Map<String,Object> (wire decode moved upstream; null body = bodiless
 *                   event e.g. DELETE); import java.util.Map added.
 * 08/11/2026 mir0n  v1.2.12 -- onEntityBroadcast gains a changeNo parameter, with an unnumbered default
 *                   overload for producers that report none
 */
package pro.mir0n.utils.taijitu;

import java.util.Map;

/**
 * The Taijitu director: the single controller over the cache monad(s). Implementations range from
 * a one-monad legacy pass-through to the full two-monad rig ({@link ATaijituRig}) -- a serving
 * "yang" monad plus a shadow "yin" monad reconciled by a periodic night-watch sweep (load the
 * shadow fresh, checksum both legs, react to drift per the configured mismatch mode).
 *
 * This is the generic, bean-blind face. Domain reads live on a consumer sub-interface that
 * routes to the active monad.
 */
public interface ITaijituRig {

    /** Bring the cache to a serving state: start the monad(s), load the serving leg (retry until
     *  LOADED), open the gates. A two-monad rig also starts the shadow leg, left idle until the sweep. */
    void start();

    /** Stop the monad(s). */
    void shutdown();

    /** Hand one entity event to the active monad as its already-parsed {@code body} map (the wire decode
     *  happened upstream, off the worker). {@code body} is null for a bodiless event (e.g. DELETE).
     *  {@code traceparent} is an opaque correlation token (like {@code correlationId}) carried onto the item so
     *  the caller's trace can continue on the monad worker; null when there is none. */
    void onEntityBroadcast(String eventType, String entityId, int entityKind,
                           String requestId, String correlationId, Map<String, Object> body, String traceparent,
                           Long changeNo);

    /** Unnumbered intake -- a producer with no change number to report. Applies with no freshness guard. */
    default void onEntityBroadcast(String eventType, String entityId, int entityKind,
                                   String requestId, String correlationId, Map<String, Object> body,
                                   String traceparent) {
        onEntityBroadcast(eventType, entityId, entityKind, requestId, correlationId, body, traceparent, null);
    }

    /** Whether the cache is loaded and serving reads -- the k8s readiness gate (false during the
     *  blocking bootstrap load, true once serving). Kept out of liveness so a slow load can't crashloop. */
    boolean isReady();

    /**
     * Trigger a night-watch sweep asynchronously -- the REST force-sweep, which must not hold the
     * request for a full sweep. Default no-op (a director without a night-watch, e.g. legacy, has
     * nothing to sweep); the dark director overrides it to dispatch onto its night-watch thread.
     */
    default void sweepAsync() {
    }
}

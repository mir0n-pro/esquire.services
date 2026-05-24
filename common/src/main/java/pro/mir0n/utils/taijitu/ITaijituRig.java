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
 */
package pro.mir0n.utils.taijitu;

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

    /** Hand one entity-broadcast event to the active monad (raw body; the monad parses it). */
    void onEntityBroadcast(String eventType, String entityId, int entityKind,
                           String requestId, String correlationId,
                           String messageEncoding, String text);

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

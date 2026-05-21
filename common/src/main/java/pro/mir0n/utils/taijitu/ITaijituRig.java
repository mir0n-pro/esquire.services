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
 */
package pro.mir0n.utils.taijitu;

/**
 * The Taijitu director: the single controller over the cache monad(s). Currently controls
 * one monad (the active half); the dark-side (Yin) monad joins the same director later, with
 * a night-watch routine across the two.
 *
 * This is the generic, bean-blind face. Domain reads live on a consumer sub-interface that
 * routes to the active monad.
 */
public interface ITaijituRig {

    /** Bring the cache to a serving state: start the monad, load it, open the gates. */
    void bootstrap();

    /** Stop the monad(s). */
    void shutdown();

    /** Hand one entity-broadcast event to the active monad (raw body; the monad parses it). */
    void onEntityBroadcast(String eventType, String entityId, int entityKind,
                           String requestId, String correlationId,
                           String messageEncoding, String text);
}

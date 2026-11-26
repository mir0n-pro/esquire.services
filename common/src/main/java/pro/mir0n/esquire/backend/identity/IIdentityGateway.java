/*
 *  Esquire frameworks (tm)
 *  Esquire common
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/12/2026 mir0n  created: the way into an identity provider -- start / stop, postRequest(RodEvent),
 *                   postMessage(RodEvent) for PATH broadcasts and setResultHandler(Consumer<RodEvent>); the
 *                   implementation is named in each process's wiring
 * 11/24/2026 mir0n  v2.0.3 -- postMessage(RodEvent) out; the doc no longer names the moved-path command (op X)
 *                   or the path change number
 */
package pro.mir0n.esquire.backend.identity;

import pro.mir0n.messaging.RodEvent;

import java.util.function.Consumer;

/**
 * The way into the identity provider, for a caller that is told nothing about what is on the other side.
 *
 * <p>One entry point. The event says everything the provider needs: {@code op} says what to do (C, U or D),
 * {@code body} carries the {@link AuthSyncRequest} fields, and the header already holds the correlation id and
 * the request id. A caller posts and returns -- the work is queued
 * either way, and the caller's transaction is already committed by the time it calls.
 *
 * <p>The implementation decides how far the work travels. Over the bus the event is transmitted on the kc
 * request leg; in one process it goes onto an in-memory queue and is served there. Same call either way, and
 * the implementation is named in the wiring of each process.
 *
 * <p>An implementation builds and configures everything it needs itself, from the environment it is given.
 * A process that wires one names the class and nothing else -- the same deal the bus makes with a transport
 * provider.
 */
public interface IIdentityGateway {

    /**
     * Open the way in. Called once, before anything is posted -- the wiring declares it as the bean's init
     * method. What it opens is the implementation's business: a queue and its worker, a leg on the bus, a
     * connection to a provider.
     */
    void start();

    /**
     * Close it again, at shutdown. An implementation says in its own javadoc what happens to work that was
     * posted and not yet served.
     */
    void stop();

    /**
     * Post one request and let the provider act on it: op, kind, entity id, the ids of the originating call,
     * and the {@link AuthSyncRequest} fields in the body. This is the imperative channel -- what the caller is
     * asking to have made true.
     *
     * @param event  the identity command
     */
    void postRequest(RodEvent event);

    /**
     * Set what receives the answers. One handler per gateway; setting a second one replaces the first. A
     * gateway with no handler set reports its answers to the log. The answer is a {@code MSG_TYPE_RESPONSE}
     * event on success and a {@code MSG_TYPE_REJECT} carrying the error on failure.
     *
     * @param handler  the answer sink
     */
    void setResultHandler(Consumer<RodEvent> handler);
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the x-Rod pod SPI -- a transmit/receive fan-out substrate. configure() PREPAREs the
 *                   pod from its XRodParams + Role; start() RUNs it; the transmit leg (post / transmit) buffers a
 *                   change in the current transaction and feeds it out after commit, the receive leg (submit)
 *                   applies an event on a bounded pool. Class-name-driven impl (see XRods); the default is XRod.
 * 06/17/2026 mir0n  post() removed (the producer's transactional post is AuditBusBridge now); submit() ->
 *                   receive(); usesOutboundTransport() / bindInbound() removed; isEnabled() is a default true;
 *                   validate(XRodParams) added (the fail-fast hook)
 * 06/22/2026 mir0n  moved to messaging (was messaging.xrod). Lifecycle split: start(name,devLog,worker) ->
 *                   init(name,devLog) (CREATE the legs paused) + setWorker(worker) (set/reset the receive
 *                   callback after configure) + start() (RUN). Role is CLIENT/SERVER/BOTH (BROADCAST removed).
 * 06/22/2026 mir0n  health() default added = TransportHealth.UP (an in-process / disabled / log-only rod has no
 *                   broker connection that can drop); a transport-backed rod overrides it (worst of its legs).
 * 06/23/2026 mir0n  idle() default no-op added -- the per-rod maintenance hook the MessagingBus idle ticker fires
 *                   (drives the alive-protocol heartbeat cadence today)
 * 06/24/2026 mir0n  configure() javadoc: role list CLIENT/SERVER (BOTH removed)
 * 06/27/2026 mir0n  setWorker(subscription, worker) default added -- a broker-side selector narrowing what the
 *                   receive leg consumes; the default ignores the subscription (R&R / non-transport rods too).
 *                   rodId() default null added -- the leg's <app>.<instanceNo>, null for in-process/disabled/info
 */
package pro.mir0n.esquire.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.TransportHealth;

import java.util.function.Consumer;

/**
 * The x-Rod SPI: a generic fan-out substrate with two legs. The lifecycle is four steps -- construct (no-arg,
 * class-name resolved), {@link #configure} (PREPARE: the x-rod treats its {@link XRodParams} -- identity, wire,
 * knobs, its own sub-blocks), {@link #init} (CREATE: open the leg connections + build the engine -- the receive
 * listener is created here, but nothing flows yet), then {@link #start} (RUN: facade-driven, once the whole bus
 * is wired -- run the engine threads and begin transport delivery). {@link #setWorker} sets/resets the receive
 * callback at any point after {@code configure}. The transmit leg ({@link #transmit}) sends a pre-built event
 * out; the receive leg ({@link #receive}) applies an event on a bounded worker pool. An x-rod with no transmit
 * leg treats {@code transmit} as a no-op; one with no receive worker drops any {@code receive}.
 */
public interface IXRod {

    /** Fail-fast config check, called by the frontend BEFORE {@link #configure} / {@link #init}: an x-rod that
     *  REQUIRES certain leg params overrides this to throw a clear, early error instead of a late no-op / NPE --
     *  XRod a complete transport, XRodRR the R&R nodes, XRodInProcess the {@code datasource} url. Default: no requirement
     *  (the OFF x-rod, a log-only x-rod). */
    default void validate(XRodParams params) {
    }

    /** PREPARE the x-rod from its leg params (the x-rod treats only what it needs: XRod the transport + knobs;
     *  XRodInProcess its {@code datasource} sub-block; etc.). {@code role} (CLIENT/SERVER) picks the legs --
     *  the R&R node/selector, or transmit/receive on a single-node bus; {@code objectMapper} is the wire codec.
     *  Call before {@link #init}. */
    void configure(XRodParams params, Role role, ObjectMapper objectMapper);

    /** Set/reset the receive worker (the listener's callback): null = no worker (received events are dropped);
     *  non-null = each received event is applied on the bounded pool by {@code worker}. May be called at any
     *  point after {@link #configure} -- the receive listener is created at {@link #init} and idles until a
     *  worker is set, and the worker can be replaced thereafter. */
    void setWorker(Consumer<RodEvent> worker);

    /** Set the receive worker AND a SUBSCRIPTION selector on the receive leg: a broker-side message selector that
     *  narrows what this leg consumes (e.g. {@code "EventType = 'I'"}). The {@code subscription} is the caller's
     *  predicate ALONE -- own-exclusion is NOT part of it: a rod that runs both legs on ONE shared connection lets
     *  the broker's {@code noLocal} (a transport param) drop the connection's own publications. Not a DURABLE
     *  subscription -- a plain selector, and the receive consumer is re-opened only when the selector CHANGES. Only
     *  the single-node broadcast x-rod ({@link pro.mir0n.esquire.messaging.xrod.impl.XRod}) applies it; the selector
     *  syntax does not apply to R&R (which already selects by rod-id / slot-id), so an R&R rod logs a warning and
     *  ignores it; a non-transport rod ignores it. Default: ignore the subscription, just set the worker. */
    default void setWorker(String subscription, Consumer<RodEvent> worker) {
        setWorker(worker);
    }

    /** This leg's rod-id (the per-instance id {@code <app>.<instanceNo>} from the leg identity), or null when the
     *  rod has no identity (an in-process / disabled / info rod). A broadcast consumer reads it to tell its OWN
     *  publications apart from a peer instance's: a received event's {@link RodEvent#rodId()} is the publishing
     *  leg's rod-id, so {@code event.rodId().equals(thisRod.rodId())} marks a self-message. */
    default String rodId() {
        return null;
    }

    /** CREATE the legs for the role -- a transmit leg (if {@code transmits()}) and/or a receive leg (if
     *  {@code receives()}): open the publisher connection, CREATE the receive listener (PAUSED -- it delivers
     *  nothing yet), and build the engine. Nothing flows until {@link #start}. So one rod can do both legs (an
     *  R&R CLIENT opens a request publisher + a response listener). {@link #configure} runs first. */
    void init(String name, Logger devLog);

    /** RUN the x-rod (facade-driven, called once the WHOLE bus is init'd and workers are wired): start the engine
     *  threads (the transmit feed + the receive pool) and begin transport delivery on the receive listener
     *  created at {@link #init}. After this the rod transmits and/or receives for real. */
    void start();

    /** Periodic MAINTENANCE pass, fired by the single MessagingBus-level idle ticker on every rod (so the bus
     *  runs ONE maintenance thread per service, not one per rod). Today it drives the alive-protocol heartbeat
     *  cadence; it is the seam for any future per-rod / transport housekeeping (R&R reply-timeout sweeps, drop /
     *  metric collection, ...). Default: no-op (an in-process / disabled rod has nothing to maintain). */
    default void idle() {
    }

    /** Stop the wired legs (in-flight work finishes) and close the inbound transport this rod opened, if any. */
    void shutdown();

    /** Whether this x-rod is a real leg. Default {@code true}; only the OFF x-rod ({@code XRodDisabled})
     *  returns {@code false}. A caller (the producer) guards expensive payload assembly on this. */
    default boolean isEnabled() {
        return true;
    }

    /** This x-rod's connection health, forwarded to the bus health indicator. Default {@code UP}: an in-process,
     *  disabled, or log-only rod has no broker connection that can drop. A transport-backed rod ({@code XRod})
     *  overrides this to report the worst of its transmit + receive legs. */
    default TransportHealth health() {
        return TransportHealth.UP;
    }

    /** Transmit: send a pre-built event (it carries its own {@code msgType}) out the transmit leg. The caller
     *  (the producer) builds the event; the x-rod just relays it. No-op if the transmit leg is not wired. */
    void transmit(RodEvent event);

    /** Receive: apply one arrived event on the receive pool (called by a bus consumer or the in-process feed). */
    void receive(RodEvent event);
}

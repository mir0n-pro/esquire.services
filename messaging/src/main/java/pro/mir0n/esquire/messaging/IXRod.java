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
     *  XRodInProcess its {@code datasource} sub-block; etc.). {@code role} (CLIENT/SERVER/BOTH) picks the legs --
     *  the R&R node/selector, or transmit/receive on a single-node bus; {@code objectMapper} is the wire codec.
     *  Call before {@link #init}. */
    void configure(XRodParams params, Role role, ObjectMapper objectMapper);

    /** Set/reset the receive worker (the listener's callback): null = no worker (received events are dropped);
     *  non-null = each received event is applied on the bounded pool by {@code worker}. May be called at any
     *  point after {@link #configure} -- the receive listener is created at {@link #init} and idles until a
     *  worker is set, and the worker can be replaced thereafter. */
    void setWorker(Consumer<RodEvent> worker);

    /** CREATE the legs for the role -- a transmit leg (if {@code transmits()}) and/or a receive leg (if
     *  {@code receives()}): open the publisher connection, CREATE the receive listener (PAUSED -- it delivers
     *  nothing yet), and build the engine. Nothing flows until {@link #start}. So one rod can do both legs (an
     *  R&R CLIENT opens a request publisher + a response listener). {@link #configure} runs first. */
    void init(String name, Logger devLog);

    /** RUN the x-rod (facade-driven, called once the WHOLE bus is init'd and workers are wired): start the engine
     *  threads (the transmit feed + the receive pool) and begin transport delivery on the receive listener
     *  created at {@link #init}. After this the rod transmits and/or receives for real. */
    void start();

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

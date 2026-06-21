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
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;

import java.util.function.Consumer;

/**
 * The x-Rod SPI: a generic fan-out substrate with two legs. The lifecycle is three distinct phases --
 * construct (no-arg, class-name resolved), {@link #configure} (PREPARE: the x-rod treats its {@link XRodParams}
 * -- identity, wire, knobs, its own sub-blocks), then {@link #start} (RUN: begin transmitting / receiving). The
 * transmit leg ({@link #transmit}) sends a pre-built event out; the receive leg ({@link #receive}) applies an
 * event on a bounded worker pool. An x-rod with no transmit leg treats {@code transmit} as a no-op; one with no
 * receive worker ignores any {@code receive}.
 */
public interface IXRod {

    /** Fail-fast config check, called by the frontend BEFORE {@link #configure} / {@link #start}: an x-rod that
     *  REQUIRES certain leg params overrides this to throw a clear, early error instead of a late no-op / NPE --
     *  XRod a complete transport, XRodRR the R&R nodes, XRodInProcess the {@code datasource} url. Default: no requirement
     *  (the OFF x-rod, a log-only x-rod). */
    default void validate(XRodParams params) {
    }

    /** PREPARE the x-rod from its leg params (the x-rod treats only what it needs: XRod the transport + knobs;
     *  XRodInProcess its {@code datasource} sub-block; etc.). {@code role} picks the R&R node/selector (CLIENT/SERVER/
     *  BROADCAST); {@code objectMapper} is the wire codec. Call before {@link #start}. */
    void configure(XRodParams params, Role role, ObjectMapper objectMapper);

    /** RUN the x-rod. {@code worker} is the receive callback: null = a producer (transmit only); non-null = a
     *  consumer (each received event is applied on the bounded pool by {@code worker}). */
    void start(String name, Logger devLog, Consumer<RodEvent> worker);

    /** Stop the wired legs (in-flight work finishes) and close the inbound transport this rod opened, if any. */
    void shutdown();

    /** Whether this x-rod is a real leg. Default {@code true}; only the OFF x-rod ({@code XRodDisabled})
     *  returns {@code false}. A caller (the producer) guards expensive payload assembly on this. */
    default boolean isEnabled() {
        return true;
    }

    /** Transmit: send a pre-built event (it carries its own {@code msgType}) out the transmit leg. The caller
     *  (the producer) builds the event; the x-rod just relays it. No-op if the transmit leg is not wired. */
    void transmit(RodEvent event);

    /** Receive: apply one arrived event on the receive pool (called by a bus consumer or the in-process feed). */
    void receive(RodEvent event);
}

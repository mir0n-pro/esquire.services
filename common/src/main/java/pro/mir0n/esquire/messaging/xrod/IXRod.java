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
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The x-Rod SPI: a generic fan-out substrate with two legs. The lifecycle is three distinct phases --
 * construct (no-arg, class-name resolved), {@link #configure} (PREPARE: the x-rod treats its {@link XRodParams}
 * -- identity, wire, knobs, its own sub-blocks), then {@link #start} (RUN: begin transmitting / receiving). The
 * transmit leg ({@link #post}) buffers a change in the current transaction and feeds it out after commit; the
 * receive leg ({@link #submit}) applies an event on a bounded worker pool. An x-rod with no transmit leg treats
 * {@code post} as a no-op; one with no receive worker is never {@code submit}ted to.
 */
public interface IXRod {

    /** PREPARE the x-rod from its leg params (the x-rod treats only what it needs: XRod the transport + knobs;
     *  XRodLogDb its {@code log-db} sub-block; etc.). {@code role} picks the R&R node/selector (CLIENT/SERVER/
     *  BROADCAST); {@code objectMapper} is the wire codec. Call before {@link #start}. */
    void configure(XRodParams params, Role role, ObjectMapper objectMapper);

    /** RUN the x-rod. {@code worker} is the receive callback: null = a producer (transmit only); non-null = a
     *  consumer (each received event is applied on the bounded pool by {@code worker}). */
    void start(String name, Logger devLog, Consumer<RodEvent> worker);

    /** Bind the inbound transport handle (the open consumer) to this rod so {@link #shutdown} closes it along
     *  with the legs -- the rod OWNS its messaging lifecycle, so a consumer needs no separate close wrapper. */
    void bindInbound(AutoCloseable inbound);

    /** Stop the wired legs (in-flight work finishes) and close the bound inbound transport, if any. */
    void shutdown();

    /** Transmit-leg option gate. Disabled (or transmit not wired) -> {@link #post} is a no-op. */
    boolean isEnabled();

    /** Whether this x-rod sends out a transport leg (so the frontend opens a publisher for it). The default
     *  transceiver does; an in-process x-rod (writes a *_log, or only logs) overrides this to {@code false}. */
    default boolean usesOutboundTransport() {
        return true;
    }

    /** Transmit: post a CREATE/UPDATE change taking the source object (it fills its own body via IMappable). The
     *  caller stamps the {@code msgType} on the event (e.g. {@code UA} for audit) -- the producer is type-agnostic. */
    void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source, String msgType);

    /** Transmit: post with no body -- id + kind ride the header (DELETE). {@code msgType} stamped by the caller. */
    void post(RodEvent.Op op, int kind, String entityId, String subId, String msgType);

    /** Transmit: post with an explicit body. {@code msgType} stamped by the caller. */
    void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body, String msgType);

    /** Transmit: send a pre-built event (it carries its own {@code msgType}) out the transmit leg -- the
     *  producer path for the request/response + broadcast buses (vs {@link #post}, which builds the event
     *  from the request context for the audit feed). No-op if the transmit leg is not wired. */
    void transmit(RodEvent event);

    /** Receive: apply one event on the receive pool (called by the in-process feed or a bus consumer). */
    void submit(RodEvent event);
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the default x-Rod transceiver pod -- one IXRod with BOTH legs. TRANSMIT (post):
 *                   write sites buffer a change in the current transaction, a single feed worker stamps one
 *                   actionTime + the audit triple after commit and sends each event to the outbound. RECEIVE
 *                   (submit): a Semaphore-bounded pool hands each event to the receive worker. The pod builds its
 *                   OWN publisher / consumer from the leg transport; non-final so XRodRR can extend it.
 * 06/17/2026 mir0n  extends AXRod (the feed / pool engine lifted out); keeps the transport (publisher /
 *                   openConsumer / legTransport / consumeSelector); shutdown() closes the inbound consumer,
 *                   drains via super, then closes the outbound publisher; validate() requires a complete transport
 * 06/21/2026 mir0n  publisher() / openConsumer() build the Publish / ConsumeSettings without the topic argument
 * 06/22/2026 mir0n  start(name,devLog,worker) split into init(name,devLog) (CREATE the legs by role -- transmit
 *                   iff transmits(), receive iff receives() -- the transport consumer created PAUSED) + start()
 *                   (runEngine via super, then inbound.start() begins consumer delivery). transmits()/receives()
 *                   added (legs from role). validate() now REQUIRES a complete transport (was optional). inbound
 *                   is a TransportConsumer; role is CLIENT/SERVER/BOTH. import Role/XRodParams/BusTransport from
 *                   messaging.catalog and RodEvent from messaging.
 * 06/22/2026 mir0n  health() = worst of the transmit (outboundCloser) + receive (inbound) legs, each ignored
 *                   when null (a leg the role does not run); outboundCloser retyped AutoCloseable -> RodPublisher.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.catalog.BusTransport;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportProviders;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodPublisher;
import pro.mir0n.esquire.messaging.xrod.RodTransportAdapter;

import java.util.function.Consumer;

/** The default x-Rod transceiver: a transmitter/receiver over a transport. It adds the transport to the
 *  {@link AXRod} engine -- {@link #init} resolves the leg's provider, opens the publisher and/or creates the
 *  consumer (paused) by role, and builds the engine; {@link #start} runs the engine and begins consumer
 *  delivery. Non-final so a specialised x-rod (e.g. {@link XRodRR}, the Request/Response variant) can extend it. */
public class XRod extends AXRod {

    private Role role;                  // CLIENT/SERVER/BOTH -- picks the R&R node (request vs response); single-node ignores it
    private ObjectMapper objectMapper;  // for the RodEvent <-> wire codec
    private TransportConsumer inbound;    // the transport consumer this rod owns (created paused, started in start()); closed on shutdown
    private RodPublisher outboundCloser;  // the open transport publisher (RodPublisher) this rod owns; closed on shutdown

    /** No-arg: x-rods are class-name-resolved + reflectively instantiated, then {@link #configure}d. */
    public XRod() {
    }

    @Override
    public void validate(XRodParams params) {
        BusTransport t = params != null ? params.transport() : null;
        // XRod IS the transport transceiver -> a complete transport is MANDATORY. A role-declared ref that
        // resolves to no transport (e.g. a bogus bus-id whose only x-rod is a knobs-only service override) is a
        // misconfiguration, not a silent no-op rod -- fail fast here, at the init phase, before any leg opens.
        require(t != null, "transport", params);
        require(t.provider() != null,    "transport.provider", params);
        require(t.endpoint() != null,    "transport.endpoint", params);
        require(t.destination() != null, "transport.destination", params);
    }

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        super.configure(params, role, objectMapper);   // identity + engine knobs (feed / pool)
        this.role         = role;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void init(String name, Logger devLog) {
        // CREATE the legs by ROLE (no traffic yet): a transmit leg (publisher) iff transmits(), a receive leg
        // (listener) iff receives() -- so ONE rod can do both (an R&R CLIENT sends requests + listens for
        // responses). The role picks the NODE per leg (R&R request/response) + the selector. The receive
        // listener is created PAUSED here; the feed/pool are built but idle. start() (facade-driven) runs the
        // engine and begins delivery. Single-node XRod: SERVER transmits, CLIENT receives.
        BusTransport transport = params != null ? params.transport() : null;
        boolean transportBacked = transport != null && objectMapper != null;
        ITransportProvider provider = transportBacked ? TransportProviders.resolve(transport.provider()) : null;
        boolean doTransmit = transmits() && transportBacked;
        boolean doReceive  = receives();   // build the receive pool; the transport consumer (below) also needs a transport

        Consumer<RodEvent> outbound = null;
        Consumer<RodEvent> poolJob  = doReceive ? this::applyWorker : null;   // the pool applies the live worker
        if (doTransmit) {
            RodPublisher publisher = publisher(provider, legTransport(true, role));
            this.outboundCloser = publisher;            // close its broker connection on shutdown
            int pubPool = params.publisherPoolSizeOr(0);
            if (pubPool > 0 && !doReceive) {            // pooled async publish (tx-only -- a dual-leg rod's pool
                outbound      = this::receive;          // runs the receive worker, so it publishes directly instead)
                poolJob       = publisher;
                this.poolSize = pubPool;
            } else {
                outbound = publisher;                   // direct publish on the feed thread
            }
        }

        buildEngine(name, devLog, outbound, poolJob);

        // CREATE the transport consumer PAUSED (delivery begins at start(), after the pool is live); a
        // transport-less receive (e.g. a test) builds only the pool -- events arrive via a direct receive() call.
        if (doReceive && transportBacked && provider.supportsConsume()) {
            this.inbound = openConsumer(provider, legTransport(false, role));
        } else if (doReceive && transportBacked && devLog != null) {
            devLog.info("x-rod[{}]: transport '{}' is producer-only -- no consumer opened", name, transport.provider());
        }
    }

    @Override
    public synchronized void start() {
        super.start();              // RUN the engine (pool live, feed pumping)
        if (inbound != null) {
            inbound.start();        // begin transport delivery on the consumer created (paused) at init
        }
    }

    /** This rod's connection health = the worst of its transmit (publisher) + receive (consumer) transport legs;
     *  a leg the role does not run is absent (null) and ignored. UNKNOWN when neither leg can observe its state. */
    @Override
    public TransportHealth health() {
        TransportHealth tx = outboundCloser != null ? outboundCloser.health() : null;
        TransportHealth rx = inbound != null ? inbound.health() : null;
        return TransportHealth.worst(tx, rx);
    }

    /** Whether this rod runs a TRANSMIT leg, by role. Single-node: SERVER / BOTH transmit, CLIENT does not.
     *  {@link XRodRR} (R&R) always transmits -- the request node for CLIENT, the response node for SERVER. */
    protected boolean transmits() {
        return role == Role.SERVER || role == Role.BOTH;
    }

    /** Whether this rod runs a RECEIVE leg, by role (gated at open() by a worker being set). Single-node:
     *  CLIENT / BOTH receive, SERVER does not. {@link XRodRR} (R&R) always receives -- the response node for
     *  CLIENT, the request node for SERVER. */
    protected boolean receives() {
        return role == Role.CLIENT || role == Role.BOTH;
    }

    /** Build the transmit-leg outbound: encode each event to the wire envelope + hand it to the transport sink.
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (base = the single leg;
     *  XRodRR = the produce node). */
    private RodPublisher publisher(ITransportProvider provider, BusTransport leg) {
        PublishSettings ps = new PublishSettings(objectMapper, leg.endpoint(),
                identity, leg.paramsOrEmpty(), params.publisherPoolSizeOr(0));
        return RodTransportAdapter.publisher(provider, leg.destination(), ps);
    }

    /** Open the receive-leg transport consumer: decode each message + receive it to this rod's pool. A CLIENT
     *  consuming responses filters to its own rod-id (so each instance only gets the responses it requested).
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (XRodRR = the consume node). */
    private TransportConsumer openConsumer(ITransportProvider provider, BusTransport leg) {
        ConsumeSettings cs = new ConsumeSettings(objectMapper, leg.endpoint(),
                identity, leg.paramsOrEmpty(), params.concurrencyOr(1), consumeSelector(role, identity));
        return provider.openConsumer(leg.destination(), cs, RodTransportAdapter.handler(this::receive, objectMapper));
    }

    /** The effective wire for THIS leg (produce or consume). Base XRod is SINGLE-NODE -- always the leg's one
     *  {@code transport} (broadcast); it never touches request/response nodes. {@link XRodRR} overrides
     *  this to resolve the request vs response NODE by role and refine the base transport with it -- the two-node
     *  R&R behaviour lives there, not in the base x-rod. */
    protected BusTransport legTransport(boolean produce, Role role) {
        return params.transport();
    }

    /** The JMS selector for this x-rod's receive node. Base XRod (broadcast / single-node) consumes the
     *  WHOLE node -- null. {@link XRodRR} overrides this for the role-driven R&R selector (CLIENT filters its own
     *  responses by rod-id; SERVER filters its service's requests by slot-id). A service-level broadcast
     *  selector is a future addition. */
    protected String consumeSelector(Role role, BusIdentity identity) {
        return null;
    }

    @Override
    public synchronized void shutdown() {
        if (inbound != null) {        // stop the transport delivering before the legs wind down
            try {
                inbound.close();
            } catch (Exception ignore) {
                // best-effort
            }
        }
        super.shutdown();             // reject receives, stop the feed, DRAIN the pool (awaitTermination)
        if (outboundCloser != null) { // release the publisher's broker connection AFTER the pool drains
            // NOTE: a pooled-async publish (publisher-pool-size>0) drains here -- super.shutdown() awaits the pool.
            // A DIRECT producer (publisher-pool-size=0, the live legs) sends on the feed thread, which feed.shutdown()
            // only interrupts (BoundedQueueRig does not join its worker), so a send in-flight at shutdown can race
            // this close. Accepted within the async-send loss boundary (a clean-shutdown event may be lost); the
            // feed is deliberately not drained here (see code-review.2).
            try {
                outboundCloser.close();
            } catch (Exception ignore) {
                // best-effort
            }
        }
    }
}

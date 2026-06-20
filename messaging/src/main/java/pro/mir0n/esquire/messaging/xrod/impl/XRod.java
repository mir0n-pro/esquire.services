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
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.BusTransport;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportProviders;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodPublisher;
import pro.mir0n.esquire.messaging.xrod.RodTransportAdapter;

import java.util.function.Consumer;

/** The default x-Rod transceiver: a transmitter/receiver over a transport. It adds the transport to the
 *  {@link AXRod} engine -- {@link #start} resolves the leg's provider and decides the shape (producer / consumer /
 *  in-process), then wires the engine. Non-final so a specialised x-rod (e.g. {@link XRodRR}, the Request/Response
 *  variant) can extend it. */
public class XRod extends AXRod {

    private Role role;                  // CLIENT/SERVER/BROADCAST -- picks the R&R node (request vs response)
    private ObjectMapper objectMapper;  // for the RodEvent <-> wire codec
    private AutoCloseable inbound;        // the open transport consumer this rod owns; closed on shutdown
    private AutoCloseable outboundCloser; // the open transport publisher this rod owns; closed on shutdown

    /** No-arg: x-rods are class-name-resolved + reflectively instantiated, then {@link #configure}d. */
    public XRod() {
    }

    @Override
    public void validate(XRodParams params) {
        BusTransport t = params != null ? params.transport() : null;
        if (t != null) {   // a transport is declared -> it must be complete (a single-node leg's whole wire)
            require(t.provider() != null,    "transport.provider", params);
            require(t.endpoint() != null,    "transport.endpoint", params);
            require(t.destination() != null, "transport.destination", params);
        }
    }

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        super.configure(params, role, objectMapper);   // identity + engine knobs (feed / pool)
        this.role         = role;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void start(String name, Logger devLog, Consumer<RodEvent> worker) {
        BusTransport transport = params != null ? params.transport() : null;
        boolean transportBacked = transport != null && objectMapper != null;
        ITransportProvider provider = transportBacked ? TransportProviders.resolve(transport.provider()) : null;

        Consumer<RodEvent> outbound  = null;
        Consumer<RodEvent> effWorker = worker;
        if (worker == null) {
            // PRODUCER (transmit only): a bus leg builds a publisher; no transport = no transmit leg (a no-op x-rod).
            if (transportBacked) {
                RodPublisher publisher = publisher(provider, legTransport(true, role));
                this.outboundCloser = publisher;   // close its broker connection on shutdown
                int pubPool = params.publisherPoolSizeOr(0);
                if (pubPool > 0) {                 // pooled async publish: feed -> own pool -> publish
                    outbound      = this::receive;
                    effWorker     = publisher;
                    this.poolSize = pubPool;
                } else {
                    outbound = publisher;
                }
            }
        } else if (!transportBacked) {
            // IN-PROCESS (no transport): feed -> own pool -> worker.
            outbound = this::receive;
        }
        // else: a bus CONSUMER -- the pool applies `worker`; the transport consumer is opened below.

        startEngine(name, devLog, outbound, effWorker);

        // open the transport consumer AFTER the engine runs (receive needs the pool); a producer-only transport idles.
        if (transportBacked && worker != null && provider.supportsConsume()) {
            this.inbound = openConsumer(provider, legTransport(false, role));
        } else if (transportBacked && worker != null && devLog != null) {
            devLog.info("x-rod[{}]: transport '{}' is producer-only -- no consumer opened", name, transport.provider());
        }
    }

    /** Build the transmit-leg outbound: encode each event to the wire envelope + hand it to the transport sink.
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (base = the single leg;
     *  XRodRR = the produce node). */
    private RodPublisher publisher(ITransportProvider provider, BusTransport leg) {
        PublishSettings ps = new PublishSettings(objectMapper, leg.endpoint(), leg.topicOrFalse(),
                identity, leg.paramsOrEmpty(), params.publisherPoolSizeOr(0));
        return RodTransportAdapter.publisher(provider, leg.destination(), ps);
    }

    /** Open the receive-leg transport consumer: decode each message + receive it to this rod's pool. A CLIENT
     *  consuming responses filters to its own rod-id (so each instance only gets the responses it requested).
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (XRodRR = the consume node). */
    private AutoCloseable openConsumer(ITransportProvider provider, BusTransport leg) {
        ConsumeSettings cs = new ConsumeSettings(objectMapper, leg.endpoint(), leg.topicOrFalse(),
                identity, leg.paramsOrEmpty(), params.concurrencyOr(1), consumeSelector(role, identity));
        return provider.openConsumer(leg.destination(), cs, RodTransportAdapter.handler(this::receive, objectMapper));
    }

    /** The effective wire for THIS leg (produce or consume). Base XRod is SINGLE-NODE -- always the leg's one
     *  {@code transport} (broadcast / audit); it never touches request/response nodes. {@link XRodRR} overrides
     *  this to resolve the request vs response NODE by role and refine the base transport with it -- the two-node
     *  R&R behaviour lives there, not in the base x-rod. */
    protected BusTransport legTransport(boolean produce, Role role) {
        return params.transport();
    }

    /** The JMS selector for this x-rod's receive node. Base XRod (broadcast / audit / single-node) consumes the
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
            // this close. Accepted within the async-audit loss boundary (a clean-shutdown event may be lost); the
            // feed is deliberately not drained here (see code-review.2).
            try {
                outboundCloser.close();
            } catch (Exception ignore) {
                // best-effort
            }
        }
    }
}

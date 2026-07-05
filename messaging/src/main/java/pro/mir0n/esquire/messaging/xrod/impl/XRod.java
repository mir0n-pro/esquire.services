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
 * 06/23/2026 mir0n  init builds the AliveSession (heartbeat-interval / alive-timeout / alive-fail-fast); transmits()
 *                   now always true (a broadcast CLIENT auto-opens a producer leg to self-heartbeat); health() =
 *                   session.health(); buildKeepAlive()/onSessionMsg()/newCorrelationId() hooks; role protected
 * 06/24/2026 mir0n  alive is OPT-IN: init builds the AliveSession only when the 'alive' param is set; health() =
 *                   worst(transport indicator [worst of the transmit + receive legs], alive metric when enabled);
 *                   transmits() role+alive-aware (a single-node CLIENT opens a producer leg only to self-heartbeat,
 *                   i.e. when alive on); receives() = role==CLIENT (BOTH removed); init FAILS FAST when a receiving
 *                   role hits a transport that cannot run the needed legs (supportsBothLegs / supportsConsume)
 * 06/27/2026 mir0n  dual-leg on ONE connection: a single-node CLIENT that shares its connection opens a producer
 *                   leg too and ADDs the consumer onto it (openConsumerOn), so the broker's noLocal drops this
 *                   connection's own publications; setWorker(subscription) re-opens the receive consumer with a
 *                   broker selector (effectiveSelector; re-open only when it CHANGES); a separate-connection
 *                   fallback drops own events in code (filterOwnInCode); the raw transportPublisher is kept so the
 *                   shared consumer can reuse its connection
 * 06/30/2026 mir0n  the inline AliveSession build + the alive constants + buildKeepAlive / onSessionMsg /
 *                   newCorrelationId removed -- init calls installSessionStack (the base builds the broadcast
 *                   sublayer stack); health() = worst(transport indicator, sessionHealth())
 * 07/01/2026 mir0n  the async-publish pool's thread model reads publisher-pool.mode -- init sets poolMode via
 *                   WorkerPool.Mode.of(publisherPoolMode())
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.BusConstants;
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
import pro.mir0n.esquire.messaging.transport.TransportPublisher;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodPublisher;
import pro.mir0n.esquire.messaging.xrod.RodTransportAdapter;
import pro.mir0n.utils.concurrent.WorkerPool;

import java.util.function.Consumer;

/** The default x-Rod transceiver: a transmitter/receiver over a transport. It adds the transport to the
 *  {@link AXRod} engine -- {@link #init} resolves the leg's provider, opens the publisher and/or creates the
 *  consumer (paused) by role, and builds the engine; {@link #start} runs the engine and begins consumer
 *  delivery. Non-final so a specialised x-rod (e.g. {@link XRodRR}, the Request/Response variant) can extend it. */
public class XRod extends AXRod {

    protected Role role;               // CLIENT/SERVER -- picks the R&R node (request vs response); single-node ignores it
    private ObjectMapper objectMapper;  // for the RodEvent <-> wire codec
    private TransportConsumer inbound;    // the transport consumer this rod owns (created paused, started in start()); closed on shutdown
    private RodPublisher outboundCloser;  // the open transport publisher (RodPublisher) this rod owns; closed on shutdown
    private TransportPublisher transportPublisher;  // the raw transport publisher, kept so a shared consumer leg reuses ITS connection
    private ITransportProvider receiveProvider;   // saved at init so setWorker(subscription) can RE-OPEN the consumer
    private BusTransport      receiveLeg;         // the resolved receive leg, saved at init for that re-open
    private boolean           sharedConsumer;     // the receive leg shares the publisher's connection (opened via openConsumerOn)
    private volatile String   subscriptionSelector;  // the caller's broadcast subscription selector (own-exclusion is the transport's noLocal, not folded here); null = none
    private volatile String   openedSelector;        // the selector the live consumer was actually opened with -- re-open only when it CHANGES
    private boolean           filterOwnInCode;       // separate-connection own-exclusion fallback (no shared connection / JMS noLocal): drop a received event whose rod-id == self

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
        boolean doReceive  = receives();   // build the receive pool; the transport consumer (below) also needs a transport
        // A single-node CLIENT that SHARES one connection also opens a producer leg -- so it can publish AND listen
        // on the same topic over ONE connection (the broker's noLocal then drops its own publications). The x-rod
        // decides this; the transport only advertises it CAN run both legs on one connection (supportsBothLegs()).
        boolean shared     = doReceive && transportBacked && sharesConnection() && provider.supportsBothLegs();
        boolean doTransmit = transportBacked && (transmits() || shared);

        // FAIL-FAST on an impossible role over this transport, BEFORE any leg opens: a rod that RECEIVES needs a
        // transport that can consume; if it ALSO runs a producer leg on the same node (a single-node CLIENT
        // self-heartbeating with alive ON) it needs BOTH legs. A produce-only transport (e.g. the XADD-only Redis
        // stream) can be a SERVER but never a CLIENT -- caught here as an unsupported config, not a silent
        // never-delivering rod.
        if (provider != null && doReceive) {
            boolean ok = doTransmit ? provider.supportsBothLegs() : provider.supportsConsume();
            if (!ok) {
                throw new IllegalStateException("x-rod[" + name + "] bus-id=" + (params != null ? params.busId() : null)
                        + ": transport '" + transport.provider() + "' cannot run "
                        + (doTransmit ? "both legs (consume + the alive producer leg)" : "a receive leg")
                        + " for a " + role + " role -- unsupported config");
            }
        }

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
                this.poolMode = WorkerPool.Mode.of(params.publisherPoolMode());   // the publisher-pool mode
            } else {
                outbound = publisher;                   // direct publish on the feed thread
            }
        }

        buildEngine(name, devLog, outbound, poolJob);

        // install the producer session-sublayer stack AFTER the feed exists (the alive heartbeat is PUT on it). The
        // factory builds the ALIVE-PROTOCOL session (OPT-IN via the 'alive' param, transport-agnostic) per role from
        // the keep-alive factory -- a producing leg ({@code doTransmit}) drives the cadence -- plus the SEND-RETRY
        // policy. With alive OFF the rod runs no session and health() rests on the transport indicator alone.
        installSessionStack(doTransmit);

        // CREATE the transport consumer PAUSED (delivery begins at start(), after the pool is live); a
        // transport-less receive (e.g. a test) builds only the pool -- events arrive via a direct receive() call.
        if (doReceive && transportBacked) {   // supportsConsume() is guaranteed by the fail-fast above
            this.receiveProvider = provider;                 // kept so setWorker(subscription) can re-open with a selector
            this.receiveLeg      = legTransport(false, role);
            this.sharedConsumer  = shared;
            if (shared) {
                // ADD the consumer leg onto the publisher's EXISTING connection (one connection, two legs); the
                // broker's noLocal (a transport param) drops this connection's own publications.
                this.inbound = openConsumerOn(provider, transportPublisher, receiveLeg);
            } else {
                this.inbound = openConsumer(provider, receiveLeg);
                // own-exclusion FALLBACK: with two separate connections the broker's noLocal cannot see this rod's
                // own publications, so drop them in code instead (broadcast / XRod only; R&R never sets noLocal).
                this.filterOwnInCode = doTransmit && noLocalConfigured(receiveLeg);
            }
        }
    }

    @Override
    public synchronized void start() {
        super.start();              // RUN the engine (pool live, feed pumping)
        if (inbound != null) {
            inbound.start();        // begin transport delivery on the consumer created (paused) at init
        }
    }

    /** This rod's health is the WORSE of two sources, each when applicable: the always-on TRANSPORT indicator
     *  (the worst of the transmit + receive legs -- a leg absent, or a transport that cannot observe its
     *  connection, reads UNKNOWN, which is benign), and -- ONLY when the ALIVE PROTOCOL is enabled -- the session
     *  metric (producer-leg timestamp age + the fail-fast send outcome). The x-rod stays transport-agnostic: it
     *  just takes the worse of the two. With no session, health is the transport indicator alone. */
    @Override
    public TransportHealth health() {
        TransportHealth transmit = outboundCloser != null ? outboundCloser.health() : null;
        TransportHealth receive  = inbound != null ? inbound.health() : null;
        TransportHealth transport = TransportHealth.worst(transmit, receive);
        return TransportHealth.worst(transport, sessionHealth());
    }

    /** A SERVER transmits as its ROLE (its producer carries the application broadcasts). A single-node
     *  CLIENT (consumer) opens a producer leg ONLY to self-heartbeat -- so ONLY when the ALIVE PROTOCOL is on;
     *  with alive off a CLIENT is a pure consumer (no producer leg). {@link XRodRR} (R&R) always transmits -- a
     *  producer leg is its ROLE there (CLIENT sends requests, SERVER sends responses), not just an alive add-on. */
    protected boolean transmits() {
        return role != Role.CLIENT || (params != null && params.aliveOr(false));
    }

    /** Whether this rod runs a RECEIVE leg, by role (gated at open() by a worker being set). Single-node:
     *  CLIENT receives, SERVER does not. {@link XRodRR} (R&R) always receives -- the response node for
     *  CLIENT, the request node for SERVER. */
    protected boolean receives() {
        return role == Role.CLIENT;
    }

    /** Build the transmit-leg outbound: encode each event to the wire envelope + hand it to the transport sink.
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (base = the single leg;
     *  XRodRR = the produce node). */
    private RodPublisher publisher(ITransportProvider provider, BusTransport leg) {
        PublishSettings ps = new PublishSettings(objectMapper, leg.endpoint(),
                identity, leg.paramsOrEmpty(), params.publisherPoolSizeOr(0));
        // open the raw transport publisher HERE and keep the handle, so a shared consumer leg (openConsumerOn)
        // can reuse ITS connection (one connection, two legs).
        this.transportPublisher = provider.openPublisher(leg.destination(), ps);
        return RodTransportAdapter.publisher(transportPublisher, objectMapper, identity);
    }

    /** Open the receive-leg transport consumer: decode each message + receive it to this rod's pool. A CLIENT
     *  consuming responses filters to its own rod-id (so each instance only gets the responses it requested).
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (XRodRR = the consume node). */
    private TransportConsumer openConsumer(ITransportProvider provider, BusTransport leg) {
        String sel = effectiveSelector(role, identity);
        this.openedSelector = sel;
        ConsumeSettings cs = new ConsumeSettings(objectMapper, leg.endpoint(),
                identity, leg.paramsOrEmpty(), params.concurrencyOr(1), sel);
        return provider.openConsumer(leg.destination(), cs, RodTransportAdapter.handler(this::receive, objectMapper));
    }

    /** Open the receive leg on the publisher's EXISTING connection (the shared, one-connection dual leg). Same as
     *  {@link #openConsumer} but via {@link ITransportProvider#openConsumerOn} so the consumer reuses {@code pub}'s
     *  connection -- the broker's noLocal can then drop this connection's own publications. */
    private TransportConsumer openConsumerOn(ITransportProvider provider, TransportPublisher pub, BusTransport leg) {
        String sel = effectiveSelector(role, identity);
        this.openedSelector = sel;
        ConsumeSettings cs = new ConsumeSettings(objectMapper, leg.endpoint(),
                identity, leg.paramsOrEmpty(), params.concurrencyOr(1), sel);
        return provider.openConsumerOn(pub, leg.destination(), cs, RodTransportAdapter.handler(this::receive, objectMapper));
    }

    /** The selector applied to the receive consumer: a subscription set via {@link #setWorker(String,
     *  java.util.function.Consumer)} wins (the caller's predicate ALONE -- own-exclusion is the transport's noLocal
     *  param, not folded here); otherwise the role's base {@link #consumeSelector} (null for broadcast, the rod-id /
     *  slot-id filter for R&R). */
    private String effectiveSelector(Role role, BusIdentity identity) {
        return subscriptionSelector != null ? subscriptionSelector : consumeSelector(role, identity);
    }

    /** Set the receive worker AND a broadcast subscription selector (single-node only). The {@code subscription} is
     *  the caller's predicate ALONE -- own-exclusion is the transport's {@code noLocal} param (the broker drops the
     *  shared connection's own publications), NOT folded into the selector here. A plain selector, not a durable
     *  subscription. The receive consumer (created at {@link #init}) is re-opened with the new selector ONLY when it
     *  actually CHANGES from the one the live consumer already holds; otherwise just the worker is attached. */
    @Override
    public synchronized void setWorker(String subscription, Consumer<RodEvent> worker) {
        String desired = (subscription != null && !subscription.isBlank())
                ? subscription : consumeSelector(role, identity);
        if (!java.util.Objects.equals(desired, openedSelector)
                && inbound != null && receiveProvider != null && receiveLeg != null) {
            this.subscriptionSelector = subscription;   // effectiveSelector() picks it up on the re-open
            try {
                inbound.close();                         // drop the consumer built with the previous selector (not started yet)
            } catch (Exception ignore) {
                // best-effort: it has not started delivering yet
            }
            // re-open exactly as init did -- on the shared connection (dual leg) or a separate one.
            this.inbound = sharedConsumer
                    ? openConsumerOn(receiveProvider, transportPublisher, receiveLeg)
                    : openConsumer(receiveProvider, receiveLeg);
        }
        setWorker(worker);
    }

    /** Whether the receive leg may share the publisher's connection (the single-node dual leg). Base XRod: yes.
     *  {@link XRodRR} overrides to NO -- its request and response legs are different nodes / connections. */
    protected boolean sharesConnection() {
        return true;
    }

    /** Whether this leg's transport declares the {@code noLocal} param (own-exclusion requested). Read from the
     *  consume leg's vendor params; drives the in-code fallback when the connection is NOT shared. */
    private boolean noLocalConfigured(BusTransport leg) {
        return leg != null && Boolean.parseBoolean(leg.paramsOrEmpty().getOrDefault(BusConstants.PARAM_NO_LOCAL, "false"));
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

    /** Receive override for the own-exclusion FALLBACK: when this rod runs both legs on SEPARATE connections (no
     *  shared connection, so the broker's noLocal cannot see its own publications) and {@code noLocal} is set, drop
     *  an event this very instance published (its rod-id == ours). The shared / JMS-noLocal path leaves
     *  {@code filterOwnInCode} false (the broker already dropped own); R&R never enables it. */
    @Override
    public void receive(RodEvent event) {
        boolean own = filterOwnInCode && event != null && identity != null
                && identity.rodId() != null && identity.rodId().equals(event.rodId());
        if (own) {
            if (devLog != null) {
                devLog.debug("x-rod[{}]: dropping own publication (rod-id={}) -- noLocal in-code fallback", name, event.rodId());
            }
        } else {
            super.receive(event);
        }
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

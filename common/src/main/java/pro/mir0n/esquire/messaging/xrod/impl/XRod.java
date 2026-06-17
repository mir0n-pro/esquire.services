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
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.messaging.BusTransport;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportProviders;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.RodEventCodec;
import pro.mir0n.utils.concurrent.BoundedQueueRig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/** The default x-Rod transceiver: a transmitter/receiver. {@link #configure} treats the {@link XRodParams} (identity,
 *  wire, knobs); {@link #start} runs it -- a producer (worker null) or a consumer/in-process x-rod (worker set).
 *  Non-final so a specialised x-rod (e.g. {@link XRodRR}, the Request/Response variant) can extend it. */
public class XRod implements IXRod {

    // --- transmit leg (the producer feed) ---
    private Consumer<RodEvent> outbound;     // null = transmit leg not wired
    private int feedCapacity;
    private BoundedQueueRig<RodEvent> feed;
    private final ThreadLocal<List<Entry>> buffer = new ThreadLocal<>();

    // --- receive leg (the bounded apply/worker pool) ---
    private Consumer<RodEvent> receiveWorker; // null = receive leg not wired
    private int poolSize;
    private boolean useVirtualThreads;
    private Semaphore permits;
    private ThreadFactory factory;
    private volatile boolean running = false;

    // --- message-audit (msgLog on both legs) ---
    private BusIdentity identity;       // names the leg -> the msg-audit logger
    private Logger msgLog;              // msg.<bus-id>.<slot-id>; null = no msg-audit

    // --- transport (the x-rod builds its OWN publisher/consumer from the leg, when the leg has a transport) ---
    protected XRodParams params;        // the leg config (transport + knobs); null = the caller wires the legs
    private Role role;                  // CLIENT/SERVER/BROADCAST -- picks the R&R node (request vs response)
    private ObjectMapper objectMapper;  // for the RodEvent <-> wire codec
    private AutoCloseable inbound;       // the open transport consumer this rod owns; closed on shutdown

    private String name = "x-rod";
    private Logger devLog;

    private static final int DEFAULT_FEED_CAPACITY = 4096;
    private static final int DEFAULT_POOL_SIZE     = 4;

    /** No-arg: x-rods are class-name-resolved + reflectively instantiated, then {@link #configure}d. */
    public XRod() {
    }

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        // PREPARE: the x-rod treats its params. Identity + engine knobs are read here; the legs are wired at start().
        this.params            = params;
        this.role              = role;
        this.objectMapper      = objectMapper;
        this.identity          = params != null
                ? new BusIdentity(params.busId(), params.slotId(), params.rodId()) : null;
        this.feedCapacity      = params != null ? Math.max(1, params.feedCapacityOr(DEFAULT_FEED_CAPACITY)) : DEFAULT_FEED_CAPACITY;
        this.poolSize          = params != null ? Math.max(1, params.poolSizeOr(DEFAULT_POOL_SIZE)) : DEFAULT_POOL_SIZE;
        this.useVirtualThreads = params != null && params.virtualThreadsOrFalse();
    }

    @Override
    public synchronized void start(String name, Logger devLogger, Consumer<RodEvent> worker) {
        this.name          = name;
        this.devLog        = devLogger;
        this.receiveWorker = worker;
        this.msgLog = identity != null && identity.busId() != null
                ? LoggerFactory.getLogger("msg." + identity.busId() + "." + identity.slotId())
                : null;

        BusTransport transport = params != null ? params.transport() : null;
        boolean transportBacked = transport != null && objectMapper != null;
        ITransportProvider provider = transportBacked ? TransportProviders.resolve(transport.provider()) : null;

        if (worker == null) {
            // PRODUCER (transmit only): a bus leg builds a publisher; no transport = no transmit leg (a no-op x-rod).
            if (transportBacked) {
                Consumer<RodEvent> publisher = publisher(provider, legTransport(true, role));
                int pubPool = params.publisherPoolSizeOr(0);
                if (pubPool > 0) {            // pooled async publish: feed -> own pool -> publish
                    this.outbound      = this::submit;
                    this.receiveWorker = publisher;
                    this.poolSize      = pubPool;
                } else {
                    this.outbound = publisher;
                }
            }
        } else if (!transportBacked) {
            // IN-PROCESS (no transport): post -> feed -> own pool -> worker (e.g. XRodLogDb writing the *_log).
            this.outbound = this::submit;
        }
        // else: a bus CONSUMER -- the receive pool (below) applies each event; the transport consumer is opened below.

        // receive leg first, so an in-process feed (outbound = this::submit) can submit as soon as it runs.
        if (receiveWorker != null) {
            this.factory = useVirtualThreads
                    ? Thread.ofVirtual().name(name + "-", 0).factory()
                    : Thread.ofPlatform().daemon(true).name(name + "-", 0).factory();
            this.permits = new Semaphore(poolSize);
            this.running = true;
        }
        if (outbound != null) {
            // the transmit leg: the feed worker logs the msg-audit (TX) then hands the event to the outbound.
            this.feed = new BoundedQueueRig<>(this::sendOut);
            feed.init(name, devLogger, feedCapacity);
            feed.start();
            feed.setProcessing(true);
        }
        // open the transport consumer AFTER the pool is running (submit needs it); a producer-only transport idles.
        if (transportBacked && worker != null && provider.supportsConsume()) {
            this.inbound = openConsumer(provider, legTransport(false, role));
        } else if (transportBacked && worker != null && devLog != null) {
            devLog.info("x-rod[{}]: transport '{}' is producer-only -- no consumer opened", name, transport.provider());
        }
        if (devLog != null) {
            devLog.info("x-rod[{}]: started (transmit={}, receive={}, poolSize={}, {})",
                    name, outbound != null, receiveWorker != null, poolSize,
                    useVirtualThreads ? "virtual" : "platform");
        }
    }

    /** Build the transmit-leg outbound: encode each event to the wire envelope + hand it to the transport sink.
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (base = the single leg;
     *  XRodRR = the produce node). */
    private Consumer<RodEvent> publisher(ITransportProvider provider, BusTransport leg) {
        PublishSettings ps = new PublishSettings(objectMapper, leg.endpoint(), null, leg.topicOrFalse(),
                identity, leg.paramsOrEmpty(), params.publisherPoolSizeOr(0));
        Consumer<TransportMessage> sink = provider.openPublisher(leg.destination(), ps);
        return e -> sink.accept(new TransportMessage(RodEventCodec.toProps(e, objectMapper, identity), e.entityId()));
    }

    /** Open the receive-leg transport consumer: decode each message + submit it to this rod's pool. A CLIENT
     *  consuming responses filters to its own rod-id (so each instance only gets the responses it requested).
     *  The effective per-leg wire ({@code leg}) is what {@link #legTransport} resolved (XRodRR = the consume node). */
    private AutoCloseable openConsumer(ITransportProvider provider, BusTransport leg) {
        ConsumeSettings cs = new ConsumeSettings(objectMapper, leg.endpoint(), null, leg.topicOrFalse(),
                identity, leg.paramsOrEmpty(), params.concurrencyOr(1), consumeSelector(role, identity));
        return provider.openConsumer(leg.destination(), cs, msg -> submit(RodEventCodec.fromProps(msg.headers(), objectMapper)));
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
    public void bindInbound(AutoCloseable inbound) {
        this.inbound = inbound;
    }

    @Override
    public synchronized void shutdown() {
        running = false;          // in-flight applies finish; new submits are rejected
        if (inbound != null) {    // stop the transport delivering before the legs wind down
            try {
                inbound.close();
            } catch (Exception ignore) {
                // best-effort
            }
        }
        if (feed != null) {
            feed.shutdown();
        }
    }

    // --------------------------------------------------------------------- transmit leg

    @Override
    public boolean isEnabled() {
        return feed != null;
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source, String msgType) {
        if (feed != null) {
            Map<String, Object> body = null;
            if (source != null && op != RodEvent.Op.DELETE) {
                body = new HashMap<>();
                source.fillMap(body);
            }
            post(op, kind, entityId, subId, body, msgType);
        }
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, String msgType) {
        post(op, kind, entityId, subId, (Map<String, Object>) null, msgType);
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body, String msgType) {
        if (feed != null) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                feed.put(new RodEvent(op, kind, entityId, subId, System.currentTimeMillis(),
                        crl(), req(), uid(), null, msgType, normalizeBody(op, body)));
            } else {
                List<Entry> buf = buffer.get();
                if (buf == null) {
                    buf = new ArrayList<>();
                    buffer.set(buf);
                    TransactionSynchronizationManager.registerSynchronization(new FlushOnCommit());
                }
                buf.add(new Entry(op, kind, entityId, subId, normalizeBody(op, body), msgType));
            }
        }
    }

    @Override
    public void transmit(RodEvent event) {
        if (feed != null) {
            feed.put(event);   // the feed worker (sendOut) logs the TX msg-audit, then hands to the outbound
        }
    }

    /** Transmit-leg send point (the feed worker): log the msg-audit, then hand the event to the outbound. */
    private void sendOut(RodEvent e) {
        logMsg("TX", e);
        outbound.accept(e);
    }

    /** Message-audit on a leg: {@code msg.<bus-id>.<slot-id>} (the msg appender). null logger = disabled. */
    private void logMsg(String dir, RodEvent e) {
        if (msgLog != null && msgLog.isInfoEnabled()) {
            msgLog.info("{} | {} | {} | {} | {} | {} | {} | {}",
                    dir, e.msgType(), e.opCode(), e.kind(), e.entityId(), e.subId(), e.rodId(), e.requestId());
        }
    }

    private static String crl() {
        EsqRequestContext ctx = RequestContextUtils.getContext();
        return ctx != null ? ctx.correlationId() : null;
    }

    private static String req() {
        EsqRequestContext ctx = RequestContextUtils.getContext();
        return ctx != null ? ctx.requestId() : null;
    }

    private static String uid() {
        EsqRequestContext ctx = RequestContextUtils.getContext();
        return ctx != null ? ctx.uid() : null;
    }

    private static Map<String, Object> normalizeBody(RodEvent.Op op, Map<String, Object> body) {
        return (op == RodEvent.Op.DELETE) ? Map.of() : (body != null ? body : Map.of());
    }

    /** One buffered change in the current transaction (one entry per posted entity), flushed after commit. */
    private record Entry(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body,
                         String msgType) { }

    /** After the entity transaction commits: stamp one actionTime, snapshot the audit triple, and feed the
     *  buffered events OUT of the transaction. Cleared on completion (commit OR rollback). */
    private final class FlushOnCommit implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            List<Entry> buf = buffer.get();
            if (buf != null && !buf.isEmpty()) {
                long actionTime = System.currentTimeMillis();
                String crl = crl();
                String req = req();
                String uid = uid();
                for (Entry e : buf) {
                    feed.put(new RodEvent(e.op(), e.kind(), e.entityId(), e.subId(), actionTime,
                            crl, req, uid, null, e.msgType(), e.body()));
                }
            }
        }

        @Override
        public void afterCompletion(int status) {
            buffer.remove();   // never leak the buffer onto a pooled thread
        }
    }

    // --------------------------------------------------------------------- receive leg

    @Override
    public void submit(RodEvent event) {
        if (!running) {
            throw new IllegalStateException("x-rod[" + name + "]: submit before start() / after shutdown() (or receive leg not wired)");
        }
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (acquired) {
            logMsg("RX", event);
            factory.newThread(() -> {
                try {
                    receiveWorker.accept(event);
                } catch (Throwable t) {
                    if (devLog != null) {
                        devLog.error("x-rod[{}]: receive worker failed for kind={}, entityId={}, subId={}: {}",
                                name, event.kind(), event.entityId(), event.subId(), t.getMessage(), t);
                    }
                } finally {
                    permits.release();
                }
            }).start();
        }
    }
}

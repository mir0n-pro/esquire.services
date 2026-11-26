/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/12/2026 mir0n  created: THE identity workflow and the only copy of it. Builds its own KeyCloak admin
 *                   client, path park, KcIdentityService and KcRequestHandler from keycloak.admin.* and
 *                   kcmaster.path-buffer.*; serve(RodEvent) routes by msgType -- a request to the handler, a
 *                   broadcast to the park; postRequest / postMessage queue onto a BoundedQueueRig (one worker,
 *                   FIFO); start()/stop() drive the park pruner and the queue gate
 * 08/26/2026 mir0n  builds the Keycloak admin client itself from KcConnectionSettings -- its own JAX-RS Client
 *                   with JacksonProvider and connect / read timeouts -- and implements
 *                   IQueueRig.IErrorListener so a worker throw is recorded rather than lost
 * 10/19/2026 mir0n  v2.0.1 -- the trace comment names herald.keep.apply and herald.keep.write.*
 * 11/21/2026 mir0n  v2.0.2 -- esq.pure-oauth read and logged; on, a moved path is not parked; storeIfGreater ->
 *                   putIfGreater
 * 11/23/2026 mir0n  v2.0.3 -- pure OAuth only: the path park (ExpiringCache, kcmaster.path-buffer.*),
 *                   serveBroadcast() and kcUserExists() out; serve() handles a request only; postMessage()
 *                   empty; esq.pure-oauth no longer read; KcRequestHandler and KcIdentityService built without
 *                   the flag and the park
 * 11/24/2026 mir0n  v2.0.3 -- the empty postMessage() out
 */
package pro.mir0n.esquire.kcMaster.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import pro.mir0n.esquire.backend.identity.IIdentityGateway;
import pro.mir0n.esquire.backend.identity.AuthSyncRequest;
import pro.mir0n.esquire.backend.identity.KcConnectionSettings;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.kcMaster.messaging.KcRequestHandler;
import pro.mir0n.esquire.kcMaster.service.impl.KcIdentityService;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.RodEvent;
import pro.mir0n.utils.concurrent.BoundedQueueRig;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * kcMaster's own way in: the identity gateway a process gets when kcMaster runs inside it.
 *
 * <p>It is the same workflow the bus serves, entered by a method call instead of a message. The event that
 * arrives is the event the bus would have carried, so {@link KcRequestHandler} does the work in both cases --
 * the operations, the meters and the KeyCloak calls are one implementation, not two.
 *
 * <p><b>It configures itself.</b> Everything it needs -- the KeyCloak admin client, the identity service, the
 * request handler -- is built here from {@code keycloak.admin.*}. A process that wires this gateway names the
 * class and hands over the environment; it is told nothing about what a KeyCloak gateway is made of, the same deal the messaging bus
 * makes with a transport provider.
 *
 * <p><b>One queue, one worker, in arrival order.</b> The queue is a {@link BoundedQueueRig}: fixed capacity,
 * one daemon worker, FIFO, with the processing gate holding posted work untouched until the gateway starts.
 * That single worker is what makes the composition worth building -- a caller posts only after its own
 * transaction has committed, so the queue replays commit order, and a create that follows a move finds the
 * move already applied.
 *
 * <p>KeyCloak holds no root path and no roles: a move is not the identity's business, so a posted
 * broadcast is taken and dropped.
 */
@Slf4j
public class KcIdentityGateway implements IIdentityGateway, IQueueRig.IQueueWorker<RodEvent>,
                                          IQueueRig.IErrorListener<RodEvent> {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + KcIdentityGateway.class.getName());

    private static final String PROP_KC_PREFIX      = "keycloak.admin";
    private static final String PROP_QUEUE_CAPACITY = "kcmaster.identity-queue.capacity";

    private final Keycloak keycloak;
    private final KcConnectionSettings kcConnection;
    private final KcRequestHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final BoundedQueueRig<RodEvent> rig = new BoundedQueueRig<>(this);
    private final int capacity;

    private volatile Consumer<RodEvent> resultHandler;

    /**
     * Builds the whole identity stack from the environment. Nothing here is handed in, because nothing
     * outside kcMaster should have to know what is in it.
     */
    public KcIdentityGateway(Environment env) {
        this.kcConnection = KcConnectionSettings.from(env, PROP_KC_PREFIX);

        // I39 (COVERED, 2026-07-16): the KC-admin client is left UN-instrumented at the wire ON PURPOSE.
        //
        // MEASURED is not the same as SPANNED -- and I39 was filed as "KC calls are not measured", which is FALSE.
        // The KC-sync duration IS measured, at the OPERATION grain: KcRequestHandler times esq.biz.kc.sync.duration
        // (tagged by op, in a finally so a FAILED sync counts too) and KcIdentityService carries @EsqTraced
        // ("esq.kc.*"). The KC round-trip DOMINATES that number, so it is the KC cost in all but name. Same shape as
        // dataKeep's RodEventDbWriter (herald.keep.apply span + herald.keep.write.* meters): KC is a TRACE LEAF here
        // exactly as Postgres is under the keep writer -- no per-call CLIENT span, no traceparent propagated in.
        //
        // What a wire-level span would ADD is only sub-operation granularity: createUser is create + setPassword
        // (2 round-trips) collapsed into one number, so today you learn "the createUser sync was
        // slow", not "the setPassword call was slow". A want, not a gap. And the other half -- injecting a traceparent
        // -- buys NOTHING: stock KeyCloak is not OTel-traced, so there is nobody on the far side to continue the
        // trace. If per-call spans are ever wanted, they belong to this gateway's own unified instrumentation,
        // NOT a one-off request filter bolted onto KeycloakBuilder here.
        //
        // Do NOT re-file this as "the KC calls are untraced / unmeasured". The duration is accounted for.
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(kcConnection.getBaseUrl())
                .realm(kcConnection.getRealm())
                .clientId(kcConnection.getClientId())
                .clientSecret(kcConnection.getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .resteasyClient(buildAdminHttpClient(kcConnection))
                .build();

        this.handler  = new KcRequestHandler(new KcIdentityService(keycloak, kcConnection));
        this.capacity = env.getProperty(PROP_QUEUE_CAPACITY, Integer.class, 4096);

        log.info("KC | GATEWAY | realm={} connectTimeoutMs={} readTimeoutMs={} queueCapacity={}",
                kcConnection.getRealm(), kcConnection.getConnectTimeoutMs(), kcConnection.getReadTimeoutMs(),
                capacity);
    }

    /**
     * The admin client, with the configured deadlines actually on it.
     *
     */
    private static Client buildAdminHttpClient(KcConnectionSettings connection) {
        Client ret = ClientBuilder.newBuilder()
                .connectTimeout(connection.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(connection.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .register(JacksonProvider.class)
                .build();
        return ret;
    }

    /** Opens the queue's processing gate. */
    @Override
    public void start() {
        rig.init("mesnie.kc-identity", devLog, capacity);
        rig.setErrorListener(this);
        rig.start();
        rig.setProcessing(true);
        log.info("KC | GATEWAY | in-process identity gateway started");
    }

    /** Closes the gate, drops what is still queued and closes the admin client. */
    @Override
    public void stop() {
        rig.setProcessing(false);
        int left = rig.size();
        rig.shutdown();
        keycloak.close();
        log.info("KC | GATEWAY | in-process identity gateway stopped (dropped={})", left);
    }

    @Override
    public void setResultHandler(Consumer<RodEvent> handler) {
        this.resultHandler = handler;
    }

    @Override
    public void postRequest(RodEvent event) {
        if (!rig.tryPut(event)) {
            // Bounded on purpose: a silent unbounded backlog is how an identity sync goes missing without a line.
            log.error("kcMaster: identity queue FULL at {} -- request dropped: entityId={}, command={}, requestId={}",
                    capacity, event.entityId(), event.opCode(), event.requestId());
        }
    }

    /** The rig's worker: serve one event that was posted onto the queue. */
    @Override
    public void process(RodEvent event) {
        serve(event);
    }

    /**
     * THE identity workflow, and the only copy of it. Whoever holds a request calls this: the rig's worker when
     * it was queued, a bus receive worker when kcMaster runs as its own service.
     *
     * <p>The thread is the caller's. kcMaster serves on the bus worker pool, which is what keeps its syncs as
     * concurrent as they have always been; a composed process serves on the rig's single worker, which is what
     * gives it the ordering it cannot get any other way.
     *
     * <p>It does not catch: a failure propagates to whoever ran it, and the rig's error listener answers the
     * REJECT. Only the context is finalized here.
     */
    public void serve(RodEvent event) {
        EsqContextHolder.applyMessage(event.requestId(), event.correlationId());
        try {
            log.info("KC | URQ | {} | {} | {} | {}", event.opCode(), event.kind(), event.entityId(), event.rodId());
            AuthSyncRequest req = objectMapper.convertValue(event.body(), AuthSyncRequest.class);
            handler.handle(event.opCode(), req, event.correlationId(), event.requestId());
            log.info("KC | URS | {} | {} | {} | {}", event.opCode(), event.kind(), event.entityId(), event.requestId());
            answer(event, BusConstants.MSG_TYPE_RESPONSE, Map.of());
        } finally {
            EsqContextHolder.clear();
        }
    }

    @Override
    public RodEvent onError(Throwable error, RodEvent event) {
        EsqContextHolder.applyMessage(event.requestId(), event.correlationId());
        try {
            log.error("kcMaster: identity request failed: entityId={}, command={}, error={}",
                    event.entityId(), event.opCode(), error.getMessage());
            devLog.error("kcMaster: identity request failed: entityId={}, command={}, requestId={}, correlationId={}, error={}",
                    event.entityId(), event.opCode(), event.requestId(), event.correlationId(), error.getMessage(), error);
            answer(event, BusConstants.MSG_TYPE_REJECT, failureBody(error, event.body()));
        } finally {
            EsqContextHolder.clear();
        }
        return event;
    }

    /** Hand the answer to whoever asked for it -- the same URS / URR event the bus would have carried back. */
    private void answer(RodEvent request, String msgType, Map<String, Object> body) {
        Consumer<RodEvent> sink = this.resultHandler;
        if (sink != null) {
            sink.accept(new RodEvent(request.op(), request.kind(), request.entityId(), null, null,
                    System.currentTimeMillis(), request.correlationId(), request.requestId(), null,
                    request.rodId(), msgType, body));
        }
    }

    private Map<String, Object> failureBody(Throwable ex, Map<String, Object> requestBody) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type",   "about:blank");
        error.put("title",  "KC_SYNC_ERROR");
        error.put("status", 500);
        error.put("detail", ex.getMessage());
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("error", error);
        if (requestBody != null) {
            ret.put("request", requestBody);
        }
        return ret;
    }

}

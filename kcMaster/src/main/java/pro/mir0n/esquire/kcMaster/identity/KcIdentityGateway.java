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
 */
package pro.mir0n.esquire.kcMaster.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import pro.mir0n.esquire.backend.identity.IIdentityGateway;
import pro.mir0n.esquire.backend.identity.AuthSyncRequest;
import pro.mir0n.esquire.backend.identity.KcConnectionSettings;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.kcMaster.messaging.KcRequestHandler;
import pro.mir0n.esquire.kcMaster.messaging.ParkedPath;
import pro.mir0n.esquire.kcMaster.service.impl.KcIdentityService;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.utils.concurrent.BoundedQueueRig;
import pro.mir0n.utils.concurrent.ExpiringCache;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.LinkedHashMap;
import java.util.List;
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
 * <p><b>It configures itself.</b> Everything it needs -- the KeyCloak admin client, the path park, the
 * identity service, the request handler -- is built here from {@code keycloak.admin.*} and
 * {@code kcmaster.path-buffer.*}. A process that wires this gateway names the class and hands over the
 * environment; it is told nothing about what a KeyCloak gateway is made of, the same deal the messaging bus
 * makes with a transport provider.
 *
 * <p><b>One queue, one worker, in arrival order.</b> The queue is a {@link BoundedQueueRig}: fixed capacity,
 * one daemon worker, FIFO, with the processing gate holding posted work untouched until the gateway starts.
 * That single worker is what makes the composition worth building -- a caller posts only after its own
 * transaction has committed, so the queue replays commit order, and a create that follows a move finds the
 * move already applied. Widening this to a pool would put the two back in a race with each other and bring
 * the path park into play for a single process, which is exactly what the bus side has to live with.
 *
 * <p><b>The park stays.</b> A path that arrives before its KeyCloak user exists is held in the
 * {@link ExpiringCache} exactly as the entity-broadcast worker holds it on the bus side, and
 * {@code KcIdentityService.createUser} drains it. In one process nothing fills it; across two copies of the
 * process it still can, because copy A can be creating the user while copy B handles the move.
 */
@Slf4j
public class KcIdentityGateway implements IIdentityGateway, IQueueRig.IQueueWorker<RodEvent>,
                                          IQueueRig.IErrorListener<RodEvent> {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + KcIdentityGateway.class.getName());

    private static final String PROP_KC_PREFIX      = "keycloak.admin";
    private static final String PROP_PARK_TTL       = "kcmaster.path-buffer.ttl-ms";
    private static final String PROP_PARK_PRUNE     = "kcmaster.path-buffer.prune-interval-ms";
    private static final String PROP_QUEUE_CAPACITY = "kcmaster.identity-queue.capacity";

    private final Keycloak keycloak;
    private final KcConnectionSettings kcConnection;
    /** The race-8c park, shared with KcIdentityService.createUser, which drains it. */
    private final ExpiringCache<String, ParkedPath> pathBuffer;
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
        // dataKeep's RodEventDbWriter (esq.keep.apply span + esq.biz.keep.write.* meters): KC is a TRACE LEAF here
        // exactly as Postgres is under the keep writer -- no per-call CLIENT span, no traceparent propagated in.
        //
        // What a wire-level span would ADD is only sub-operation granularity: createUser is create + setPassword +
        // applyBufferedPath (3+ round-trips) collapsed into one number, so today you learn "the createUser sync was
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

        long ttlMs   = env.getProperty(PROP_PARK_TTL, Long.class, 10000L);
        long pruneMs = env.getProperty(PROP_PARK_PRUNE, Long.class, 30000L);
        this.pathBuffer = new ExpiringCache<>(
                LoggerFactory.getLogger("develop.kcmaster.path-buffer"), ttlMs, pruneMs);

        this.handler  = new KcRequestHandler(new KcIdentityService(keycloak, kcConnection, pathBuffer));
        this.capacity = env.getProperty(PROP_QUEUE_CAPACITY, Integer.class, 4096);

        // A component whose behaviour is switched by config must say what config it got: with the park
        // disabled (ttl <= 0) there is otherwise no way to confirm the knob reached the gateway at all.
        String parkNote = "";
        if (ttlMs <= 0) {
            parkNote = "  (PARK DISABLED -- every consume returns null)";
        }
        log.info("KC | GATEWAY | realm={} connectTimeoutMs={} readTimeoutMs={} parkTtlMs={} parkPruneMs={} queueCapacity={}{}",
                kcConnection.getRealm(), kcConnection.getConnectTimeoutMs(), kcConnection.getReadTimeoutMs(),
                ttlMs, pruneMs, capacity, parkNote);
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

    /** Starts the park pruner and opens the queue's processing gate. */
    @Override
    public void start() {
        pathBuffer.start();
        rig.init("mesnie.kc-identity", devLog, capacity);
        rig.setErrorListener(this);
        rig.start();
        rig.setProcessing(true);
        log.info("KC | GATEWAY | in-process identity gateway started");
    }

    /** Closes the gate, drops what is still queued, stops the park pruner and closes the admin client. */
    @Override
    public void stop() {
        rig.setProcessing(false);
        int left = rig.size();
        rig.shutdown();
        pathBuffer.stop();
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

    /**
     * Takes a relayed PATH broadcast and queues it. Moves are all this arm is given -- the seam says so -- so
     * there is nothing to sort out here. Whether the moved entity has a KeyCloak identity at all is settled on
     * the queue's own thread, where a call to KeyCloak belongs.
     */
    @Override
    public void postMessage(RodEvent event) {
        if (event != null && event.op() == RodEvent.Op.UPDATE_PATH) {
            if (rig.tryPut(event)) {
                // The one line that says this arm was REACHED. Without it, "never called" and "called and
                // found nothing to hold" read identically in the log -- and the only visible sign of either
                // is a BUFFERED line that is absent for both reasons. That ambiguity cost a wrong diagnosis
                // once already: a stale build meant the call never happened, and the log looked the same as
                // a healthy run over an entity whose identity already existed.
                devLog.debug("KC | TOPIC-X | entityId={} | changeNo={} | queued (depth={})",
                        event.entityId(), event.changeNo(), rig.size());
            } else {
                log.error("kcMaster: identity queue FULL at {} -- broadcast dropped: entityId={}, requestId={}",
                        capacity, event.entityId(), event.requestId());
            }
        }
    }

    /** The rig's worker: serve one event that was posted onto the queue. */
    @Override
    public void process(RodEvent event) {
        serve(event);
    }

    /**
     * THE identity workflow, and the only copy of it. Whoever holds an event calls this: the rig's worker when
     * it was queued, a bus receive worker when kcMaster runs as its own service. The event says which of the two
     * things it is -- a broadcast is a safety net, anything else is a command -- so the caller decides nothing.
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
            if (BusConstants.MSG_TYPE_ENTITY_BROADCASTS.equals(event.msgType())) {
                serveBroadcast(event);
            } else {
                log.info("KC | URQ | {} | {} | {} | {}", event.opCode(), event.kind(), event.entityId(), event.rodId());
                AuthSyncRequest req = objectMapper.convertValue(event.body(), AuthSyncRequest.class);
                handler.handle(event.opCode(), req, event.correlationId(), event.requestId());
                log.info("KC | URS | {} | {} | {} | {}", event.opCode(), event.kind(), event.entityId(), event.requestId());
                answer(event, BusConstants.MSG_TYPE_RESPONSE, Map.of());
            }
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

    /**
     * The safety net, off a relayed move broadcast: park the new path when the KeyCloak user does not exist
     * yet. It never updates KeyCloak -- the request owns that, and answers nothing.
     *
     * <p>This is the only thing that survives a caller and a creator being in DIFFERENT copies of the process:
     * a request lands on one copy, a broadcast reaches them all, so whichever copy ends up creating the
     * identity has the path parked and {@code KcIdentityService.createUser} drains it.
     */
    private void serveBroadcast(RodEvent event) {
        String newPath = event.body() != null && event.body().get(EsqConstants.TEXT_PATH) != null
                ? event.body().get(EsqConstants.TEXT_PATH).toString() : null;
        if (event.op() != RodEvent.Op.UPDATE_PATH) {
            // Only a MOVE drives the park. Other broadcasts carry a path too -- a create's path is the path it
            // was born at -- and parking one would hold a path nothing is waiting for.
            devLog.debug("KC | TOPIC-X | entityId={} : op {} is not a move, skipping", event.entityId(), event.opCode());
        } else if (newPath == null) {
            devLog.debug("KC | TOPIC-X | entityId={} : no path in body, skipping", event.entityId());
        } else if (kcUserExists(event.entityId())) {
            // The request owns the update for an identity that exists. The broadcast side stays passive.
            devLog.debug("KC | TOPIC-X | entityId={} : KC user exists, the request owns the update", event.entityId());
        } else {
            // storeIfGreater orders by the PATH change number the event carries, so two moves of the same
            // entity settle on the newest in one atomic step rather than on the last to arrive.
            boolean parked = pathBuffer.storeIfGreater(event.entityId(), new ParkedPath(newPath, event.changeNo()));
            if (parked) {
                log.info("KC | TOPIC-X | entityId={} | path={} | changeNo={} | BUFFERED (no KC user yet, parked={})",
                        event.entityId(), newPath, event.changeNo(), pathBuffer.size());
            } else {
                log.info("KC | TOPIC-X | entityId={} | path={} | changeNo={} | NOT BUFFERED (a newer path is already parked)",
                        event.entityId(), newPath, event.changeNo());
            }
        }
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

    private boolean kcUserExists(String entityId) {
        boolean ret = false;
        if (entityId != null) {
            RealmResource realm = keycloak.realm(kcConnection.getRealm());
            UsersResource users = realm.users();
            List<UserRepresentation> found = users.searchByAttributes(
                    EsqConstants.JWT_CLAIM_ENTITY_ID + ":" + entityId, true);
            ret = found != null && !found.isEmpty();
        }
        return ret;
    }
}

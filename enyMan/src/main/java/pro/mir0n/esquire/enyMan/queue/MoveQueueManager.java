/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: the move-queue orchestrator (v1.2.6 Goal 3). Owns the
 *                   BoundedQueueRig + the "move in progress" AtomicInteger. EnyManService's
 *                   /esq-move handler hands the MoveCommandItem here and returns 202 Accepted
 *                   immediately; the single worker thread drains the queue FIFO and serialises
 *                   move-window CREATEs against the move itself. Constructs its own private
 *                   OrgService / UsrService instances (same pattern EnyManService uses) so the
 *                   manager is self-contained and the wiring stays free of circular dependencies.
 *                   Worker MDC contract: each MoveCommandItem SETS MDC at process start; the
 *                   value is left in place across subsequent items, so a following
 *                   CreateReconcileItem inherits the move's CID/RID -- the post-effect
 *                   attribution mir0n called out.
 * 06/04/2026 mir0n  processMove hydrates EsqContextHolder from the queued item (crl/req/uid/rootPath),
 *                   cleared in finally; calls esquireCommandMove without rootPath / uid
 * 06/05/2026 mir0n  XYRod ctor param added + threaded into the OrgService / UsrService it builds
 *                   (move parent-ref audit on the worker thread)
 * 06/15/2026 mir0n  audit ctor param XYRod -> IXRod (import retargeted common.xrod -> messaging.xrod).
 * 06/17/2026 mir0n  audit ctor param IXRod -> AuditBusBridge; post() drops the trailing MSG_TYPE_AUDIT arg
 * 06/18/2026 mir0n  audit module left common: AuditBusBridge moved to pro.mir0n.esquire.audit
 * 06/22/2026 mir0n  bus-adapter rename: broadcastPublisher EsqEntityBroadcastPublisher -> EntityBusAdapter,
 *                   kcRequestPublisher KcRequestPublisher -> KcBusAdapter (fields + ctor params + imports).
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 * 06/27/2026 mir0n  registers broadcastPublisher.onPeerCreate(this::submitReconcile) once the rig is live -- a
 *                   peer enyMan instance's CREATE feeds the reconcile intake; wired here (not via a ctor dep) so
 *                   MoveQueueManager -> EntityBusAdapter stays one-way. Its own reconcile/move worker instances
 *                   never run the create path, so the test create-delay is inert on them
 * 06/29/2026 mir0n  move worker runs on a dedicated TransactionTemplate that opts out of the request-path cap via
 *                   QueryTimeouts.resolveOptOut (enyman.move-queue.tx-timeout-s, 0 = uncapped, pre-HA default) (R6)
 * 07/09/2026 mir0n  v1.2.11 -- processMove() runs inside EsqAsyncTrace.continueIn(item.traceparent(),
 *                   item.correlationId(), "move (async)", ...)
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- start() registers the esq.biz.move.queue.depth gauge (queueSize);
 *                   processMove() counts esq.biz.move.processed.total / .failed.total (tag kind) from a boolean
 *                   flag in the existing finally -- the exception flow is untouched. The move's REAL outcome:
 *                   /esq-move answers 202 at submit time, so a move that fails on the worker is invisible to the
 *                   caller and to every HTTP meter
 * 07/15/2026 mir0n  v1.2.11 T11 -- processMove() binds the worker context with EsqContextHolder.set(), which now
 *                   stamps MDC itself; the separate, now-redundant MDC apply was dropped (I10)
 * 07/23/2026 mir0n  v1.2.11 -- "elastic end of move": inMove() stays true for a grace window
 *                   (enyman.move-queue.in-move-grace-ms, default 200, 0 disables) after the last move drains, so a
 *                   create racing that move is still caught by the reconcile queue; stamped on decrement-to-zero
 * 08/11/2026 mir0n  v1.2.12 -- the move broadcast carries the PATH change number: taken from the move
 *                   record, and read back with pathChangeNoFor after a reconcile repair so the reissue is
 *                   not skipped by the receiver's guard
 * 08/12/2026 mir0n  v1.2.13 -- field/ctor param KcBusAdapter -> IIdentityGateway; publishKcMoveRequest builds an
 *                   AuthSyncRequest + RodEvent and calls postRequest, carrying the PATH change number
 * 08/26/2026 mir0n  implements ISuccessListener and IErrorListener and registers both on the rig, so a move
 *                   outcome is counted on either side; submitMove answers false when the queue refuses
 *                   the item. The move broadcast carries pathChangeNo in the body and the entity number
 *                   in the header, and the reconcile reissue reads both back
 */

package pro.mir0n.esquire.enyMan.queue;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.o11y.EsqAsyncTrace;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.QueryTimeouts;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.enyMan.jpa.EntityPathLookup;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.messaging.EntityBusAdapter;
import pro.mir0n.esquire.backend.identity.IIdentityGateway;
import pro.mir0n.esquire.backend.identity.AuthSyncRequest;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
import pro.mir0n.esquire.enyMan.service.impl.OrgService;
import pro.mir0n.esquire.enyMan.service.impl.UsrService;
import pro.mir0n.utils.concurrent.BoundedQueueRig;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class MoveQueueManager implements IQueueRig.IQueueWorker<MoveQueueItem>,
                                         IQueueRig.ISuccessListener<MoveQueueItem>,
                                         IQueueRig.IErrorListener<MoveQueueItem> {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + MoveQueueManager.class.getName());

    private final AtomicInteger counter = new AtomicInteger(0);
    private final BoundedQueueRig<MoveQueueItem> rig;

    // "Elastic end of move": inMove() stays true for a grace window AFTER the last move drains, so a CREATE
    // that lands just behind a move it raced is still CAUGHT by the move queue (gets a reconcile) instead of
    // slipping past. Set on the decrement-to-zero; everMoved guards the startup window (no false positive before
    // the first move). graceNanos <= 0 disables the grace (inMove() is then exactly counter > 0).
    private final long graceNanos;
    private volatile boolean everMoved = false;
    private volatile long lastMoveDoneNanos = 0L;

    private final IEnyManService orgService;
    private final IEnyManService usrService;
    private final EntityBusAdapter broadcastPublisher;
    private final IIdentityGateway identityGateway;
    private final EntityPathLookup pathLookup;

    private final int capacity;

    public MoveQueueManager(EsqEntityDictionaryRepository entityDictionaryRepository,
                            EsqOrgRepository orgRepository,
                            EsqUsrRepository usrRepository,
                            TransactionTemplate transactionTemplate,
                            EntityManager em,
                            EntityBusAdapter broadcastPublisher,
                            IIdentityGateway identityGateway,
                            EntityPathLookup pathLookup,
                            AuditBusBridge audit,
                            @Value("${enyman.move-queue.capacity:1024}") int capacity,
                            @Value("${enyman.move-queue.tx-timeout-s:0}") int moveTxTimeoutS,
                            @Value("${enyman.move-queue.in-move-grace-ms:200}") long inMoveGraceMs) {
        // The move worker runs the move on a DEDICATED transaction template that opts out of the request-path
        // query-timeout cap (R6): an explicit positive enyman.move-queue.tx-timeout-s caps it; 0/negative
        // (the default) leaves the move uncapped so it never inherits the global default-timeout.
        TransactionTemplate moveTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        moveTx.setTimeout(QueryTimeouts.resolveOptOut(moveTxTimeoutS));
        // These instances serve the reconcile/move worker, which never runs the createOrg/createUsr path,
        // so the test-only create-window delay (ENYMAN_TEST_CREATE_DELAY_MS) is inert here even when set.
        this.orgService = new OrgService(entityDictionaryRepository, orgRepository, moveTx, em, audit);
        this.usrService = new UsrService(entityDictionaryRepository, usrRepository, moveTx, em, audit);
        this.broadcastPublisher = broadcastPublisher;
        this.identityGateway = identityGateway;
        this.pathLookup = pathLookup;
        this.capacity = capacity;
        this.graceNanos = inMoveGraceMs * 1_000_000L;
        this.rig = new BoundedQueueRig<>(this);
    }

    @PostConstruct
    public void start() {
        rig.init("enyman.move-queue", devLog, capacity);
        rig.setSuccessListener(this);
        rig.setErrorListener(this);
        rig.start();
        rig.setProcessing(true);
        // Bind the entity-bus receive leg now the rig is live: a peer instance's CREATE -> reconcile intake.
        // Registered here (not via a constructor dependency) so this manager -> EntityBusAdapter stays one-way.
        broadcastPublisher.onPeerCreate(this::submitReconcile);
        // esq.biz.move.queue.depth (O1/T8 phase B): the pending move backlog, read live at scrape time. Registered
        // ONCE here, at start-up of the thing that owns the value -- a gauge is registered, never incremented.
        // Held strongly by EsqGauge (via EsqBizMeters), so the supplier cannot be collected and read NaN.
        EsqBizMeters.gauge("esq.biz.move.queue.depth", this::queueSize);
        devLog.info("MoveQueueManager started: capacity={}", capacity);
    }

    @PreDestroy
    public void stop() {
        rig.setProcessing(false);
        rig.shutdown();
        devLog.info("MoveQueueManager stopped.");
    }

    // ---- public API used by EnyManService ----

    /** True iff at least one MoveCommandItem is queued or in flight, OR a move drained less than the grace
     *  window (enyman.move-queue.in-move-grace-ms, default 200) ago. The grace is the "elastic end of move":
     *  a CREATE races a move by reading the pre-move parent path, then the move can finish and drain before
     *  the CREATE reaches its inMove() check -- without the grace that CREATE slips past the queue (no
     *  reconcile) and its stale DB path is left stranded (the night-watch heals cache-vs-DB, but here the DB
     *  itself is wrong, so it cannot). Lingering true for graceNanos after the drain keeps catching such a
     *  late CREATE, so its reconcile still fixes the DB and rebroadcasts. Not a hard guarantee (a CREATE that
     *  stalls longer than the grace between its read and its check would still miss) -- the residual falls to
     *  a subsequent move / the night-watch once the DB is corrected. graceNanos <= 0 disables it. */
    public boolean inMove() {
        return counter.get() > 0
                || (everMoved && System.nanoTime() - lastMoveDoneNanos < graceNanos);
    }

    /** Enqueue a move command. Increments the counter on the handler thread BEFORE put,
     *  so a CREATE that runs the instant after this call already sees inMove() == true.
     *  Uses non-blocking tryPut: if the queue is full the counter is rolled back so a
     *  capacity-exhaustion scenario does not leave inMove() stuck true forever.
     */
    public boolean submitMove(MoveCommandItem item) {
        boolean ret = true;
        counter.incrementAndGet();
        if (!rig.tryPut(item)) {
            counter.decrementAndGet();

            String reason = (rig.size() >= capacity) ? "full" : "stopped";
            log.error("submitMove: move queue {} (size={}, capacity={}) -- REFUSED kind={}, id={}, distId={}",
                    reason, rig.size(), capacity, item.kind(), item.id(), item.distId());
            devLog.error("submitMove: move queue {} (size={}, capacity={}) -- REFUSED {}",
                    reason, rig.size(), capacity, item);
            EsqBizMeters.count("esq.biz.move.refused.total", "kind", String.valueOf(item.kind()), "reason", reason);
            ret = false;
        }
        return ret;
    }

    /** Enqueue a post-publish reconciliation task for a CREATE that fired during a move.
     *  Non-blocking: on capacity exhaustion the reconcile is dropped (CREATE was already
     *  broadcast; the residual is that one entity may keep a stale path until something
     *  else (a subsequent move or a manual sweep) touches it). */
    public void submitReconcile(CreateReconcileItem item) {
        if (!rig.tryPut(item)) {
            devLog.warn("submitReconcile: move queue is FULL (size={}, capacity={}) -- DROPPED {}",
                    rig.size(), capacity, item);
        }
    }

    /** Snapshot of pending+in-flight queue size, for monitoring. */
    public int queueSize() {
        return rig.size();
    }

    // ---- worker ----

    @Override
    public void process(MoveQueueItem item) {
        if (item instanceof MoveCommandItem mci) {
            processMove(mci);
        } else if (item instanceof CreateReconcileItem cri) {
            processReconcile(cri);
        } else {
            log.error("MoveQueueManager: unknown item type: {}", item);
        }
    }

    private void processMove(MoveCommandItem item) {
        // Re-establish the unified per-request context on this worker thread from the queued item, and with it
        // the MDC ids -- set() carries both. The request thread's EsqContextHolder / SecurityContext do not follow
        // here, so without this the service-layer RequestContextUtils.getUid()/getRootPath()/getCorrelationId()
        // and the log lines would all read empty. Cleared in the finally so this move's identity does not linger on
        // the worker thread; a reconcile item carries its OWN create's cid/rid and stamps them itself.
        EsqContextHolder.set(new EsqRequestContext(
                item.correlationId(), item.requestId(), item.uid(), item.rootPath()));
        try {
            // Continue the request's trace on this worker thread (the "move entity" span was captured at submit):
            // the move + its broadcasts nest under it, so the async move shows in the request's trace (O2/T3).
            EsqAsyncTrace.continueIn(item.traceparent(), item.correlationId(), "move (async)", () -> {
                devLog.debug("processMove: kind={}, id={}, distId={}, rootPath={}, uid={}",
                        item.kind(), item.id(), item.distId(), item.rootPath(), item.uid());
                EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(item.kind());
                List<EsqMoveRecord> records;
                if (eek.isOrg()) {
                    records = orgService.esquireCommandMove(item.kind(), item.id(), item.distId(), item.roles());
                } else {
                    records = usrService.esquireCommandMove(item.kind(), item.id(), item.distId(), item.roles());
                }
                // Transaction has committed by the time esquireCommandMove returns -- publish outside it.
                for (EsqMoveRecord r : records) {
                    publishMoveEvent(r, item.requestId(), item.correlationId());
                    publishKcMoveRequest(r, item.requestId(), item.correlationId());
                }
            });
        } finally {
            EsqContextHolder.clear();     // do not leak this move's identity onto the next item
            if (counter.decrementAndGet() == 0) {   // ALWAYS decrement, even if the move threw
                // Last move drained -- open the "elastic end of move" grace so a CREATE that lands just behind
                // this move is still caught by the queue (see inMove()).
                lastMoveDoneNanos = System.nanoTime();
                everMoved = true;
            }
        }
    }

    @Override
    public void onSuccess(MoveQueueItem item) {
        String kind = moveKind(item);
        if (kind != null) {
            EsqBizMeters.count("esq.biz.move.processed.total", "kind", kind);
        }
    }

    @Override
    public MoveQueueItem onError(Throwable error, MoveQueueItem item) {
        String kind = moveKind(item);
        if (kind != null) {
            EsqBizMeters.count("esq.biz.move.failed.total", "kind", kind);
        }
        devLog.error("move-queue: item failed: {}", item, error);
        return item;
    }

    private String moveKind(MoveQueueItem item) {
        String ret = null;
        if (item instanceof MoveCommandItem move) {
            ret = String.valueOf(move.kind());
        }
        return ret;
    }

    private void processReconcile(CreateReconcileItem item) {
        // The item carries the originating CREATE's ids (captured at submit off the peer-create receive leg).
        // Stamp them into MDC so this worker's log lines -- and the reissued EVENT_UPDATE_PATH broadcast -- all
        // identify with the create being reconciled. applyMessage is the MDC-only path (this worker has ids to
        // apply but no full request context to set); paired with clear() in the finally so nothing lingers.
        String cid = item.correlationId();
        String rid = item.requestId();
        EsqContextHolder.applyMessage(rid, cid);
        try {
            devLog.debug("processReconcile: entityId={}, kind={}, parentId={}, pathAtPublish={}, cid={}, rid={}",
                    item.entityId(), item.kind(), item.parentId(), item.pathAtPublish(), cid, rid);

            String currentParentPath = pathLookup.pathFor(item.parentId());
            if (currentParentPath == null) {
                devLog.warn("processReconcile: parent {} has no ep_path -- skip", item.parentId());
                return;
            }
            String expectedPath = PathRule.expectedFor(item.kind(), currentParentPath, item.entityId());
            if (expectedPath.equals(item.pathAtPublish())) {
                devLog.debug("processReconcile: no drift (entityId={}, path={})", item.entityId(), expectedPath);
                return;
            }

            // Drift detected -- fix DB and reissue.
            int rows = pathLookup.updatePath(item.entityId(), expectedPath);
            // updatePath raised ep_change_no inline (the path table is not read for update per row), so the
            // new number is read back here -- the reissued broadcast has to carry it or bizTree's path guard
            // would compare this repair against a stale number and skip it.
            Long pathChangeNo   = pathLookup.pathChangeNoFor(item.entityId());
            Long entityChangeNo = pathLookup.entityChangeNoFor(item.entityId());
            devLog.info("processReconcile: drift fixed entityId={}, was={}, now={}, rows={}, pathChangeNo={}",
                    item.entityId(), item.pathAtPublish(), expectedPath, rows, pathChangeNo);

            Map<String, Object> text = new LinkedHashMap<>();
            text.put(EsqConstants.TEXT_ID,   item.entityId());
            text.put(EsqConstants.TEXT_KIND, item.kind());
            text.put(EsqConstants.TEXT_PATH, expectedPath);
            text.put(EsqConstants.TEXT_PATH_CHANGE_NO, pathChangeNo);
            try {
                broadcastPublisher.publish(item.kind(), item.entityId(), BusConstants.EVENT_UPDATE_PATH,
                        rid, cid, text, entityChangeNo);
            } catch (Exception e) {
                log.error("processReconcile: broadcast failed for kind={}, id={}: {}",
                        item.kind(), item.entityId(), e.getMessage());
                devLog.error("processReconcile: broadcast failed for kind={}, id={}: {}",
                        item.kind(), item.entityId(), e.getMessage(), e);
            }
        } finally {
            EsqContextHolder.clear();
        }
    }

    // ---- publish helpers (lifted out of EnyManService -- only the worker uses them now) ----

    private void publishMoveEvent(EsqMoveRecord record, String requestId, String correlationId) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put(EsqConstants.TEXT_ID,   record.getId());
        text.put(EsqConstants.TEXT_KIND, record.getKind());
        text.put(EsqConstants.TEXT_PATH, record.getPath());
        text.put(EsqConstants.TEXT_PATH_CHANGE_NO, record.getPathChangeNo());
        try {
            broadcastPublisher.publish(record.getKind(), record.getId(), BusConstants.EVENT_UPDATE_PATH,
                    requestId, correlationId, text, record.getEntityChangeNo());
        } catch (Exception e) {
            log.error("publishMoveEvent: broadcast failed for kind={}, id={}: {}",
                    record.getKind(), record.getId(), e.getMessage());
            devLog.error("publishMoveEvent: broadcast failed for kind={}, id={}: {}",
                    record.getKind(), record.getId(), e.getMessage(), e);
        }
    }

    // The moved path goes to the identity provider only for USR entities -- only USRs have an identity there.
    private void publishKcMoveRequest(EsqMoveRecord record, String requestId, String correlationId) {
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(record.getKind());
        if (eek.isUsr()) {
            try {
                AuthSyncRequest req = new AuthSyncRequest();
                req.setId(record.getId());
                req.setKind(record.getKind());
                req.setPath(record.getPath());
                // guarantee a non-null tracking id (the former testReqId; it rides as the requestId on the wire).
                String reqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
                // The PATH change number rides along, on both legs: a gateway that has to hold the path keeps
                // the newest move rather than the last to arrive. It is the ep_change_no the move just raised.
                identityGateway.postRequest(new RodEvent(RodEvent.Op.UPDATE_PATH, record.getKind(), record.getId(), null,
                        record.getPathChangeNo(), System.currentTimeMillis(), correlationId, reqId, null, null,
                        BusConstants.MSG_TYPE_REQUEST, req.toMap()));
            } catch (Exception e) {
                log.error("publishKcMoveRequest: failed for id={}: {}", record.getId(), e.getMessage());
                devLog.error("publishKcMoveRequest: failed for id={}: {}", record.getId(), e.getMessage(), e);
            }
        }
    }
}

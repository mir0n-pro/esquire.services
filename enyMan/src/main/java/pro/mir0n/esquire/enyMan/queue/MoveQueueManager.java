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
 */

package pro.mir0n.esquire.enyMan.queue;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.enyMan.jpa.EntityPathLookup;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.messaging.EntityBusAdapter;
import pro.mir0n.esquire.enyMan.messaging.KcBusAdapter;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
import pro.mir0n.esquire.enyMan.service.impl.OrgService;
import pro.mir0n.esquire.enyMan.service.impl.UsrService;
import pro.mir0n.utils.concurrent.BoundedQueueRig;
import pro.mir0n.utils.concurrent.IQueueRig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class MoveQueueManager implements IQueueRig.IQueueWorker<MoveQueueItem> {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + MoveQueueManager.class.getName());

    private final AtomicInteger counter = new AtomicInteger(0);
    private final BoundedQueueRig<MoveQueueItem> rig;

    private final IEnyManService orgService;
    private final IEnyManService usrService;
    private final EntityBusAdapter broadcastPublisher;
    private final KcBusAdapter kcRequestPublisher;
    private final EntityPathLookup pathLookup;

    private final int capacity;

    public MoveQueueManager(EsqEntityDictionaryRepository entityDictionaryRepository,
                            EsqOrgRepository orgRepository,
                            EsqUsrRepository usrRepository,
                            TransactionTemplate transactionTemplate,
                            EntityManager em,
                            EntityBusAdapter broadcastPublisher,
                            KcBusAdapter kcRequestPublisher,
                            EntityPathLookup pathLookup,
                            AuditBusBridge audit,
                            @Value("${enyman.move-queue.capacity:1024}") int capacity,
                            @Value("${enyman.move-queue.tx-timeout-s:0}") int moveTxTimeoutS) {
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
        this.kcRequestPublisher = kcRequestPublisher;
        this.pathLookup = pathLookup;
        this.capacity = capacity;
        this.rig = new BoundedQueueRig<>(this);
    }

    @PostConstruct
    public void start() {
        rig.init("enyman.move-queue", devLog, capacity);
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

    /** True iff at least one MoveCommandItem is queued or in flight. */
    public boolean inMove() {
        return counter.get() > 0;
    }

    /** Enqueue a move command. Increments the counter on the handler thread BEFORE put,
     *  so a CREATE that runs the instant after this call already sees inMove() == true.
     *  Uses non-blocking tryPut: if the queue is full the counter is rolled back so a
     *  capacity-exhaustion scenario does not leave inMove() stuck true forever. */
    public void submitMove(MoveCommandItem item) {
        counter.incrementAndGet();
        if (!rig.tryPut(item)) {
            counter.decrementAndGet();
            log.error("submitMove: move queue is FULL (size={}, capacity={}) -- DROPPED kind={}, id={}, distId={}",
                    rig.size(), capacity, item.kind(), item.id(), item.distId());
            devLog.error("submitMove: move queue is FULL (size={}, capacity={}) -- DROPPED {}",
                    rig.size(), capacity, item);
        }
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
        // and the log lines would all read empty. Left set across subsequent reconcile items so they inherit the
        // move's CID/RID (the "events get the last move command IDs" rule).
        EsqContextHolder.set(new EsqRequestContext(
                item.correlationId(), item.requestId(), item.uid(), item.rootPath()));

        // esq.biz.move.processed / failed (O1/T8 phase B): the move's REAL outcome, which nothing on the request
        // side can see -- /esq-move answers 202 Accepted at submit time and the work happens here, off-request.
        // A flag, not a catch: the exception flow above is left exactly as it was.
        boolean moved = false;
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
            moved = true;
        } finally {
            EsqContextHolder.clear();     // do not leak this move's identity onto the next item
            counter.decrementAndGet();    // ALWAYS decrement, even if the move threw
            EsqBizMeters.count(moved ? "esq.biz.move.processed.total" : "esq.biz.move.failed.total",
                    "kind", String.valueOf(item.kind()));
        }
    }

    private void processReconcile(CreateReconcileItem item) {
        // Read MDC -- set by the preceding MoveCommandItem. FIFO guarantees that
        // by the time we get here at least one MoveCommandItem ahead of us has run.
        String moveRid = MDC.get(EsqConstants.PD_REQUEST_ID);
        String moveCid = MDC.get(EsqConstants.PD_CORRELATION_ID);

        devLog.debug("processReconcile: entityId={}, kind={}, parentId={}, pathAtPublish={}, moveCid={}, moveRid={}",
                item.entityId(), item.kind(), item.parentId(), item.pathAtPublish(), moveCid, moveRid);

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
        devLog.info("processReconcile: drift fixed entityId={}, was={}, now={}, rows={}",
                item.entityId(), item.pathAtPublish(), expectedPath, rows);

        Map<String, Object> text = new LinkedHashMap<>();
        text.put(EsqConstants.TEXT_ID,   item.entityId());
        text.put(EsqConstants.TEXT_KIND, item.kind());
        text.put(EsqConstants.TEXT_PATH, expectedPath);
        try {
            broadcastPublisher.publish(item.kind(), item.entityId(), BusConstants.EVENT_UPDATE_PATH,
                    moveRid, moveCid, text);
        } catch (Exception e) {
            log.error("processReconcile: broadcast failed for kind={}, id={}: {}",
                    item.kind(), item.entityId(), e.getMessage());
            devLog.error("processReconcile: broadcast failed for kind={}, id={}: {}",
                    item.kind(), item.entityId(), e.getMessage(), e);
        }
    }

    // ---- publish helpers (lifted out of EnyManService -- only the worker uses them now) ----

    private void publishMoveEvent(EsqMoveRecord record, String requestId, String correlationId) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put(EsqConstants.TEXT_ID,   record.getId());
        text.put(EsqConstants.TEXT_KIND, record.getKind());
        text.put(EsqConstants.TEXT_PATH, record.getPath());
        try {
            broadcastPublisher.publish(record.getKind(), record.getId(), BusConstants.EVENT_UPDATE_PATH,
                    requestId, correlationId, text);
        } catch (Exception e) {
            log.error("publishMoveEvent: broadcast failed for kind={}, id={}: {}",
                    record.getKind(), record.getId(), e.getMessage());
            devLog.error("publishMoveEvent: broadcast failed for kind={}, id={}: {}",
                    record.getKind(), record.getId(), e.getMessage(), e);
        }
    }

    // KC URQ (EVENT_UPDATE_PATH) only for USR entities -- only USRs have a KC identity.
    private void publishKcMoveRequest(EsqMoveRecord record, String requestId, String correlationId) {
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(record.getKind());
        if (eek.isUsr()) {
            try {
                kcRequestPublisher.publishPathUpdate(record.getId(), record.getKind(), record.getPath(),
                        requestId, correlationId);
            } catch (Exception e) {
                log.error("publishKcMoveRequest: failed for id={}: {}", record.getId(), e.getMessage());
                devLog.error("publishKcMoveRequest: failed for id={}: {}", record.getId(), e.getMessage(), e);
            }
        }
    }
}

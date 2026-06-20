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
 */

package pro.mir0n.esquire.enyMan.queue;

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
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.enyMan.jpa.EntityPathLookup;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.messaging.EsqEntityBroadcastPublisher;
import pro.mir0n.esquire.enyMan.messaging.KcRequestPublisher;
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
    private final EsqEntityBroadcastPublisher broadcastPublisher;
    private final KcRequestPublisher kcRequestPublisher;
    private final EntityPathLookup pathLookup;

    private final int capacity;

    public MoveQueueManager(EsqEntityDictionaryRepository entityDictionaryRepository,
                            EsqOrgRepository orgRepository,
                            EsqUsrRepository usrRepository,
                            TransactionTemplate transactionTemplate,
                            EntityManager em,
                            EsqEntityBroadcastPublisher broadcastPublisher,
                            KcRequestPublisher kcRequestPublisher,
                            EntityPathLookup pathLookup,
                            AuditBusBridge audit,
                            @Value("${enyman.move-queue.capacity:1024}") int capacity) {
        this.orgService = new OrgService(entityDictionaryRepository, orgRepository, transactionTemplate, em, audit);
        this.usrService = new UsrService(entityDictionaryRepository, usrRepository, transactionTemplate, em, audit);
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
        // Set MDC for this move; leave it set across subsequent reconcile items so they
        // inherit the move's CID/RID (the "events get the last move command IDs" rule).
        if (item.requestId()     != null) MDC.put(EsqConstants.PD_REQUEST_ID,     item.requestId());
        if (item.correlationId() != null) MDC.put(EsqConstants.PD_CORRELATION_ID, item.correlationId());

        // Re-establish the unified per-request context on this worker thread from the queued item.
        // The request thread's EsqContextHolder / SecurityContext do not follow here, so without
        // this the service-layer RequestContextUtils.getUid()/getRootPath()/getCorrelationId() would
        // all read empty -- the same reason MDC has to be re-set above.
        EsqContextHolder.set(new EsqRequestContext(
                item.correlationId(), item.requestId(), item.uid(), item.rootPath()));

        try {
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
        } finally {
            EsqContextHolder.clear();     // do not leak this move's identity onto the next item
            counter.decrementAndGet();    // ALWAYS decrement, even if the move threw
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
        text.put(EsqMsgConstants.TEXT_ID,   item.entityId());
        text.put(EsqMsgConstants.TEXT_KIND, item.kind());
        text.put(EsqMsgConstants.TEXT_PATH, expectedPath);
        try {
            broadcastPublisher.publish(item.kind(), item.entityId(), EsqMsgConstants.EVENT_UPDATE_PATH,
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
        text.put(EsqMsgConstants.TEXT_ID,   record.getId());
        text.put(EsqMsgConstants.TEXT_KIND, record.getKind());
        text.put(EsqMsgConstants.TEXT_PATH, record.getPath());
        try {
            broadcastPublisher.publish(record.getKind(), record.getId(), EsqMsgConstants.EVENT_UPDATE_PATH,
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

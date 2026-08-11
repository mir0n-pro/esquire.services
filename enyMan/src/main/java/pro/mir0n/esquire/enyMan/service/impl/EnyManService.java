/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 * 01/12/2026 mir0n added "profile" command
 * 01/12/2026 mir0n BizTreeConstants moved to common package
 *                  Error handling with rfc9457 compliance
 *                  Debug logs added
 * 01/23/2026 mir0n use common library
 *                  no more EsqTreeNode methods  
 *                  use entityRepository.acctsAsNodes()
 * 01/24/2026 mir0n  ResourceNotFoundException moved to common lib
 *                   detailAcct() removed (moved to pacMan)
 * 02/12/2026 mir0n  EsqObjectKind instead if EsqEntityKind
 *                   removed "profile" command
 * 02/13/2026 mir0n userAccts() instead of acctsAsNodes
 * 02/19/2026 mir0n EntityManager + TransactionTemplate injected
 *                  FlushModeType.COMMIT prevents Hibernate auto-flush before native queries
 *                  esquireCommandSave() with saveOrg() / saveUsr() helpers
 *                  EsqEntityRepository/EsqCustomFieldRepository replaced by
 *                  EsqOrgRepository / EsqUsrRepository
 * 02/28/2026 mir0n  extends AEnyManService; delegates to OrgService/UsrService
 *                   saveOrg/saveUsr/applyFields moved to OrgService/UsrService
 * 03/09/2026 mir0n  roles param added; isAdminCmdPermitted(UPDATE) permission check
 *                   self-update bypass for USR (id.equals(uid)); PermissionDeniedException thrown
 * 03/10/2026 mir0n  import: RequestContextUtils updated to backend.service package
 * 03/17/2026 mir0n  messaging: broadcastPublisher injected; publishEntityEvent() on name/desc update
 * 03/20/2026 mir0n  status broadcast: "deleted" (usr_deleted_flg) added to isBroadcastableUpdate()
 *                   publishEntityEvent() emits raw "deleted" field value; publisher decoupling rule
 * 03/21/2026 mir0n  devLog added; dual error pattern (publishEntityEvent catch: log.warn→log.error+devLog.error)
 * 03/26/2026 mir0n  esquireCommandNew/Delete(): delegates to OrgService/UsrService;
 *                   TEXT_* constants replace raw strings; parentId added to broadcast
 * 03/31/2026 mir0n  esquireCommandMove(): orgRepository injected; dual UPDATE permission check;
 *                   self-move guard (USR cannot move themselves); dest org validation; dispatches to OrgService/UsrService
 * 04/02/2026 mir0n  esquireCommandMove(): collects List<EsqMoveRecord>, publishMoveEvent()
 * 04/06/2026 mir0n  KC path sync: KcRequestPublisher injected; publishKcMoveRequest() sends EVENT_UPDATE_PATH URQ per USR move record
 * 04/07/2026 mir0n  all kind params Integer → int; kind normalization removed;
 *                   upfront applicability check (!isOrg && !isUsr → ResourceNotFoundException) at all entry points
 * 04/16/2026 mir0n  ret declarations moved to top in detailEntity/saveEntity/newEntity
 * 05/14/2026 mir0n  esquireCommandTree(): EsqSubtreeRepository.findSubtree() + project to EsqTreeNode list;
 *                   leaves-first order via level DESC; populates entityPath from esq_entity_path.ep_path
 * 06/01/2026 mir0n  acct CREATE wiring -- EsqAcctRepository injected; new AcctService field constructed
 *                   alongside orgService / usrService; esquireCommandNew() applicability widened from
 *                   (isOrg || isUsr) to (isOrg || isUsr || isAcct) and routes isAcct to acctService;
 *                   publishEntityEvent() now also forwards TEXT_STATUS so acct CREATE / UPDATE events
 *                   carry status to bizTree (parity with the pacMan publisher this branch replaces).
 * 06/02/2026 mir0n  /esq-move async-ack (v1.2.6 Goal 3): MoveQueueManager injected (replaces the
 *                   KcRequestPublisher field); esquireCommandMove() runs pre-checks on the request
 *                   thread, submits a MoveCommandItem to the move queue, returns null (202 Accepted);
 *                   publishMoveEvent() / publishKcMoveRequest() moved to MoveQueueManager;
 *                   submitReconcileIfInMove() enqueues a CreateReconcileItem after each CREATE
 *                   broadcast when inMove(), gated by enyman.move-queue.validate-create-during-move
 * 06/04/2026 mir0n  rootPath / uid dropped from the public signatures; read via RequestContextUtils where
 *                   needed (self-update + self-move guards, MoveCommandItem); delegates called without them
 * 06/05/2026 mir0n  XYRod ctor param added + passed to OrgService / UsrService / AcctService (x-Rod audit)
 * 06/15/2026 mir0n  audit ctor param XYRod -> IXRod (import retargeted common.xrod -> messaging.xrod).
 * 06/17/2026 mir0n  audit ctor param IXRod -> AuditBusBridge
 * 06/18/2026 mir0n  audit module left common: AuditBusBridge moved to pro.mir0n.esquire.audit
 * 06/22/2026 mir0n  bus-adapter rename: broadcastPublisher EsqEntityBroadcastPublisher -> EntityBusAdapter
 *                   (field + ctor param + import).
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 * 07/02/2026 mir0n  write commands (esquireCommandSave / New / Delete / Move) read requestId via
 *                   requireRequestId() -- X-Request-ID mandatory on writes
 * 07/08/2026 mir0n  @EsqTraced on esquireCommand / Save / New / Delete / Move / Tree
 *                   (esq.svc.read / save / create / delete / move / tree)
 * 07/09/2026 mir0n  v1.2.11 -- esquireCommandMove() captures the traceparent (EsqAsyncTrace.capture) inside the
 *                   traced move and passes it to MoveCommandItem
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- esquireCommandNew / Delete / Move count esq.biz.entity.ops.total (tags op,
 *                   kind, outcome = ok|denied|error) via the private meterEntityOp(); each body is wrapped in
 *                   try / catch (PermissionDeniedException, rethrown) / finally, otherwise unchanged. For a MOVE
 *                   this records that the command was ACCEPTED, not that the move succeeded -- the work happens
 *                   off-request on the queue worker (esq.biz.move.processed / failed)
 * 07/23/2026 mir0n  v1.2.11 -- submitReconcileIfInMove passes the create's cid/rid onto the CreateReconcileItem
 * 08/11/2026 mir0n  v1.2.12 -- esquireCommandDelete returns the number the delete raised and
 *                   publishDeleteEvent carries it; the entity broadcast carries the change number on
 *                   create, save and move
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import java.util.*;

import pro.mir0n.esquire.backend.o11y.EsqAsyncTrace;
import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import pro.mir0n.esquire.backend.o11y.EsqTraced;
import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.enyMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqSubtreeRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqSubtreeRow;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.messaging.EntityBusAdapter;
import pro.mir0n.esquire.enyMan.queue.CreateReconcileItem;
import pro.mir0n.esquire.enyMan.queue.MoveCommandItem;
import pro.mir0n.esquire.enyMan.queue.MoveQueueManager;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;

@Slf4j
@Service
public class EnyManService  extends AEnyManService {

    private static final org.slf4j.Logger devLog = org.slf4j.LoggerFactory.getLogger("develop." + EnyManService.class.getName());

    private final IEnyManService orgService;
    private final IEnyManService usrService;
    private final IEnyManService acctService;
    private final EsqOrgRepository orgRepository;
    private final EsqSubtreeRepository subtreeRepository;
    private final EntityBusAdapter broadcastPublisher;
    private final MoveQueueManager moveQueue;

    // v1.2.6 Goal 3 toggle. true (default) -> CREATE-during-move path reconciliation runs;
    // race 8b is fixed. false -> reconciliation is skipped; the race reproduces. Used by the
    // race-move-create sim to prove the fix actually closes the race when ON and the race
    // still fires when OFF. Production default ON.
    @org.springframework.beans.factory.annotation.Value("${enyman.move-queue.validate-create-during-move:true}")
    private boolean validateCreateDuringMove;

    public EnyManService(EsqEntityDictionaryRepository entityDictionaryRepository,
                         EsqOrgRepository orgRepository,
                         EsqUsrRepository usrRepository,
                         EsqAcctRepository acctRepository,
                         EsqSubtreeRepository subtreeRepository,
                         TransactionTemplate transactionTemplate,
                         EntityManager em,
                         EntityBusAdapter broadcastPublisher,
                         MoveQueueManager moveQueue,
                         AuditBusBridge audit) {
        super(entityDictionaryRepository);
        this.orgService  = new OrgService(entityDictionaryRepository, orgRepository, transactionTemplate, em, audit);
        this.usrService  = new UsrService(entityDictionaryRepository, usrRepository, transactionTemplate, em, audit);
        this.acctService = new AcctService(entityDictionaryRepository, acctRepository, transactionTemplate, em, audit);
        this.orgRepository = orgRepository;
        this.subtreeRepository = subtreeRepository;
        this.broadcastPublisher = broadcastPublisher;
        this.moveQueue = moveQueue;
    }

    @Override
    @EsqTraced(name = "esq.svc.read", label = "read entity")
    public EsqEntity esquireCommand(int kind, String id, String cmd) {
        EsqEntity ret = null;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        if (!eek.isOrg() && !eek.isUsr()) {
            throw new ResourceNotFoundException("esquireCommand", "kind", String.valueOf(kind));
        }
        int k = eek.getId();
        if (eek.isOrg()) {
            ret = orgService.esquireCommand(k, id, cmd);
        } else if (eek.isUsr()) {
            ret = usrService.esquireCommand(k, id, cmd);
        }
        return  ret;
    }

    @Override
    @EsqTraced(name = "esq.svc.save", label = "save entity")
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, List<String> roles) {
        EsqEntity ret = null;
        String requestId = RequestContextUtils.requireRequestId();
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        if (!eek.isOrg() && !eek.isUsr()) {
            throw new ResourceNotFoundException("esquireCommandSave", "kind", String.valueOf(kind));
        }
        int k = eek.getId();
        Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
        boolean permitted = false;
        if (permissions != null) {
            permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                permissions.get(k),
                EsqRolesStorage.AdminCmd.UPDATE
            );
        }
        // Capture trace context before delegate call (still on request thread)
        String correlationId = RequestContextUtils.getCorrelationId();
        String uid           = RequestContextUtils.getUid();
        if (eek.isOrg()) {
            if (permitted) {
                ret = orgService.esquireCommandSave(k, id, cmd, fields, roles);
                if (isBroadcastableUpdate(fields)) {
                    publishEntityEvent(ret, k, BusConstants.EVENT_UPDATE, requestId, correlationId, fields);
                }
            }
        } else if (eek.isUsr()) {
            if (id != null && id.equals(uid)) {
                permitted = true;
            }
            if (permitted) {
                ret = usrService.esquireCommandSave(k, id, cmd, fields, roles);
                if (isBroadcastableUpdate(fields)) {
                    publishEntityEvent(ret, k, BusConstants.EVENT_UPDATE, requestId, correlationId, fields);
                }
            }
        }
        if (ret == null && !permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "modify");
        }
        return  ret;
    }

    @Override
    @EsqTraced(name = "esq.svc.create", label = "create entity")
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, List<String> roles) {
        EsqEntity ret = null;
        String outcome = OUTCOME_ERROR;   // esq.biz.entity.ops.total -- see meterEntityOp()
        try {
            String requestId = RequestContextUtils.requireRequestId();
            EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
            if (!eek.isOrg() && !eek.isUsr() && !eek.isAcct()) {
                throw new ResourceNotFoundException("esquireCommandNew", "kind", String.valueOf(kind));
            }
            int k = eek.getId();
            Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
            boolean permitted = false;
            if (permissions != null) {
                permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(k),
                    EsqRolesStorage.AdminCmd.CREATE
                );
            }
            String correlationId = RequestContextUtils.getCorrelationId();
            if (eek.isOrg()) {
                if (permitted) {
                    ret = orgService.esquireCommandNew(k, parentId, cmd, fields, roles);
                    publishEntityEvent(ret, k, BusConstants.EVENT_CREATE, requestId, correlationId, fields);
                    submitReconcileIfInMove(ret, k, parentId, requestId, correlationId, fields);
                }
            } else if (eek.isUsr()) {
                if (permitted) {
                    ret = usrService.esquireCommandNew(k, parentId, cmd, fields, roles);
                    publishEntityEvent(ret, k, BusConstants.EVENT_CREATE, requestId, correlationId, fields);
                    submitReconcileIfInMove(ret, k, parentId, requestId, correlationId, fields);
                }
            } else if (eek.isAcct()) {
                if (permitted) {
                    ret = acctService.esquireCommandNew(k, parentId, cmd, fields, roles);
                    publishEntityEvent(ret, k, BusConstants.EVENT_CREATE, requestId, correlationId, fields);
                    submitReconcileIfInMove(ret, k, parentId, requestId, correlationId, fields);
                }
            }
            if (ret == null && !permitted) {
                throw new PermissionDeniedException(eek.getTitle(), "create");
            }
            outcome = OUTCOME_OK;
        } catch (PermissionDeniedException e) {
            outcome = OUTCOME_DENIED;
            throw e;
        } finally {
            meterEntityOp("create", kind, outcome);
        }
        return ret;
    }

    // esq.biz.entity.ops.total (O1/T8 phase B) -- the domain result of a create / delete / move, which no free
    // meter can see: http.server.requests knows the endpoint and the HTTP status, not WHICH KIND of entity was
    // acted on nor whether the refusal was an authorization decision. Both tag values are bounded: op is one of
    // three literals, kind is the EsqObjectKind code (a small fixed set), outcome is ok | denied | error.
    private static final String OUTCOME_OK = "ok";
    private static final String OUTCOME_DENIED = "denied";
    private static final String OUTCOME_ERROR = "error";

    private static void meterEntityOp(String op, int kind, String outcome) {
        EsqBizMeters.count("esq.biz.entity.ops.total",
                "op", op, "kind", String.valueOf(kind), "outcome", outcome);
    }

    // v1.2.6 Goal 3: when a move is in flight, enqueue a path-reconciliation task for the
    // CREATE we just broadcast. The worker will re-read the parent path on its turn and emit
    // an EVENT_UPDATE_PATH to bizTree if it sees drift. Gated by the validateCreateDuringMove
    // toggle so the race-8b sim can flip the fix off and prove the race still fires.
    private void submitReconcileIfInMove(EsqEntity entity, int kind, String parentId,
                                         String requestId, String correlationId, Map<String, Object> fields) {
        if (entity == null || !validateCreateDuringMove || !moveQueue.inMove()) {
            return;
        }
        Object pathObj = (fields != null) ? fields.get(EsqConstants.TEXT_PATH) : null;
        String pathAtPublish = (pathObj instanceof String s) ? s : null;
        // Carry THIS create's cid/rid onto the reconcile item so the worker stamps them itself and the
        // path-fix broadcast stays correlated to the create it repairs (no leftover-MDC dependency).
        moveQueue.submitReconcile(new CreateReconcileItem(entity.getId(), kind, parentId, pathAtPublish,
                correlationId, requestId));
    }

    @Override
    @EsqTraced(name = "esq.svc.delete", label = "delete entity")
    public Long esquireCommandDelete(int kind, String id, String cmd, List<String> roles) {
        Long ret = null;
        String outcome = OUTCOME_ERROR;   // esq.biz.entity.ops.total -- see meterEntityOp()
        try {
            String requestId = RequestContextUtils.requireRequestId();
            EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
            if (!eek.isOrg() && !eek.isUsr()) {
                throw new ResourceNotFoundException("esquireCommandDelete", "kind", String.valueOf(kind));
            }
            int k = eek.getId();
            Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
            boolean permitted = false;
            if (permissions != null) {
                permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(k),
                    EsqRolesStorage.AdminCmd.DELETE
                );
            }
            if (!permitted) {
                throw new PermissionDeniedException(eek.getTitle(), "delete");
            }
            String correlationId = RequestContextUtils.getCorrelationId();
            if (eek.isOrg()) {
                ret = orgService.esquireCommandDelete(k, id, cmd, roles);
            } else if (eek.isUsr()) {
                ret = usrService.esquireCommandDelete(k, id, cmd, roles);
            }
            publishDeleteEvent(id, k, BusConstants.EVENT_DELETE, requestId, correlationId, ret);
            outcome = OUTCOME_OK;
        } catch (PermissionDeniedException e) {
            outcome = OUTCOME_DENIED;
            throw e;
        } finally {
            meterEntityOp("delete", kind, outcome);
        }
        return ret;
    }

    @Override
    @EsqTraced(name = "esq.svc.move", label = "move entity")
    public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, List<String> roles) {
        // v1.2.6 Goal 3: pre-checks stay on the request thread; actual move work happens on the
        // move-queue worker thread. Method returns null because the records are no longer surfaced
        // to the caller -- /esq-move's controller returns 202 Accepted at submit time.
        // The move is ASYNC: this records whether the command was ACCEPTED (pre-checks passed, handed to the
        // queue), not whether the move itself succeeded -- that is esq.biz.move.processed/failed, counted on the
        // worker where the work actually happens.
        String outcome = OUTCOME_ERROR;
        try {
            String requestId = RequestContextUtils.requireRequestId();
            String rootPath = RequestContextUtils.getRootPath();
            String uid      = RequestContextUtils.getUid();
            EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
            int k = eek.getId();
            if (!eek.isOrg() && !eek.isUsr()) {
                throw new ResourceNotFoundException("esquireDictionary", "kind", String.valueOf(kind));
            }
            Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
            boolean permitted = false;
            if (permissions != null) {
                permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(k),
                    EsqRolesStorage.AdminCmd.UPDATE
                );
            }
            if (!permitted) {
                throw new PermissionDeniedException(eek.getTitle(), "move");
            }
            if (eek.isUsr() && id.equals(uid)) {
                throw new PermissionDeniedException(eek.getTitle(), "cannot move yourself");
            }
            EsqOrgJpa destOrg = orgRepository.detailOrg(distId, rootPath);
            if (destOrg == null) {
                throw new ResourceNotFoundException("esq-move", "dist_id", distId);
            }
            boolean destPermitted = false;
            if (permissions != null) {
                destPermitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(destOrg.getKind()),
                    EsqRolesStorage.AdminCmd.UPDATE
                );
            }
            if (!destPermitted) {
                throw new PermissionDeniedException(destOrg.getName(), "move target");
            }
            // Pre-checks passed -- hand the command to the move queue. Counter increments here so a
            // CREATE that runs the next instant already sees inMove() == true.
            String correlationId = RequestContextUtils.getCorrelationId();
            // Capture the trace HERE (inside the traced "move entity", span current) so the async move worker can
            // continue this request's trace when it later emits the move broadcasts (O2/T3). null when tracing off.
            String traceparent = EsqAsyncTrace.capture(correlationId);
            moveQueue.submitMove(new MoveCommandItem(k, id, distId, rootPath, uid, roles, requestId, correlationId, traceparent));
            devLog.debug("esquireCommandMove: submitted to move queue (kind={}, id={}, distId={}, queueSize={})",
                    k, id, distId, moveQueue.queueSize());
            outcome = OUTCOME_OK;
        } catch (PermissionDeniedException e) {
            outcome = OUTCOME_DENIED;
            throw e;
        } finally {
            meterEntityOp("move", kind, outcome);
        }
        return null;
    }

    // Broadcast UPDATE only when fields that affect the entity's public identity or status change.
    // name / desc / deleted (usr_deleted_flg) are the current scope.
    // Note: for USR, "name" is not in the original request — UsrService.saveUsr() injects it
    // into the fields map when person (firstName/middleName/lastName) is updated, so this
    // check correctly fires for person-driven name changes too.
    private boolean isBroadcastableUpdate(Map<String, Object> fields) {
        return fields != null && (fields.containsKey(EsqConstants.TEXT_NAME) || fields.containsKey(EsqConstants.TEXT_DESC)
                               || fields.containsKey(EsqConstants.TEXT_DELETED));
    }

    // publishMoveEvent and publishKcMoveRequest moved into MoveQueueManager (v1.2.6 Goal 3):
    // only the move-queue worker thread emits move broadcasts now, since the /esq-move
    // request thread returns 202 Accepted at submit time without running the move itself.

    private void publishDeleteEvent(String id, int entityKind, String eventType,
                                    String requestId, String correlationId, Long changeNo) {
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        text.put(EsqConstants.TEXT_ID,   id);
        text.put(EsqConstants.TEXT_KIND, entityKind);
        try {
            broadcastPublisher.publish(entityKind, id, eventType,
                    requestId, correlationId, text, changeNo);
        } catch (Exception e) {
            log.error("publishDeleteEvent: broadcast failed for kind={}, id={}: {}", entityKind, id, e.getMessage());
            devLog.error("publishDeleteEvent: broadcast failed for kind={}, id={}, requestId={}, correlationId={}: {}", entityKind, id, requestId, correlationId, e.getMessage(), e);
        }
    }

    // Runs synchronously on the request thread — publish failure is absorbed (log.warn),
    // so broker unavailability cannot fail the HTTP response.
    // If broker latency becomes observable in production, promote to @Async with an MDC
    // task decorator to preserve correlationId/requestId in the async thread.
    @Override
    @EsqTraced(name = "esq.svc.tree", label = "read subtree")
    public List<EsqTreeNode> esquireCommandTree(int kind, String id) {
        String rootPath = RequestContextUtils.getRootPath();
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        if (!eek.isOrg() && !eek.isUsr() && !eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommandTree", "kind", String.valueOf(kind));
        }
        List<EsqSubtreeRow> rows;
        if (eek.isOrg()) {
            rows = subtreeRepository.subtreeFromOrg(id, rootPath);
        } else if (eek.isUsr()) {
            rows = subtreeRepository.subtreeFromUsr(id, rootPath);
        } else {
            rows = subtreeRepository.subtreeFromAcct(id, rootPath);
        }
        List<EsqTreeNode> ret = new ArrayList<>(rows.size());
        for (EsqSubtreeRow r : rows) {
            EsqTreeNode n = EsqTreeNode.builder()
                    .id(r.getId())
                    .entityId(r.getEntityId())
                    .kind(r.getKind())
                    .name(r.getName())
                    .desc(r.getDesc())
                    .parentId(r.getParentId())
                    .level(r.getLevel())
                    .entityPath(r.getEntityPath())
                    .moreRemaining(false)
                    .build();
            ret.add(n);
        }
        devLog.debug("esquireCommandTree: kind:{}, id:{}, rootPath:{}, rows:{}", kind, id, rootPath, ret.size());
        return ret;
    }

    private void publishEntityEvent(EsqEntity entity, int entityKind, String eventType,
                                    String requestId, String correlationId, Map<String, Object> fields) {
        if (entity == null) return;
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        text.put(EsqConstants.TEXT_ID,        entity.getId());
        text.put(EsqConstants.TEXT_KIND,      entityKind);
        text.put(EsqConstants.TEXT_PARENT_ID, entity.getParentId());
        if (fields.containsKey(EsqConstants.TEXT_PATH))    text.put(EsqConstants.TEXT_PATH,    fields.get(EsqConstants.TEXT_PATH));
        if (fields.containsKey(EsqConstants.TEXT_NAME))    text.put(EsqConstants.TEXT_NAME,    fields.get(EsqConstants.TEXT_NAME));
        if (fields.containsKey(EsqConstants.TEXT_DESC))    text.put(EsqConstants.TEXT_DESC,    fields.get(EsqConstants.TEXT_DESC));
        if (fields.containsKey(EsqConstants.TEXT_DELETED)) text.put(EsqConstants.TEXT_DELETED, fields.get(EsqConstants.TEXT_DELETED));
        if (fields.containsKey(EsqConstants.TEXT_STATUS))  text.put(EsqConstants.TEXT_STATUS,  fields.get(EsqConstants.TEXT_STATUS));
        try {
            // the number rides on the entity the service just built from the row it wrote
            broadcastPublisher.publish(entityKind, entity.getId(), eventType,
                    requestId, correlationId, text, entity.getChangeNo());
        } catch (Exception e) {
            log.error("publishEntityEvent: broadcast failed for kind={}, id={}: {}", entityKind, entity.getId(), e.getMessage());
            devLog.error("publishEntityEvent: broadcast failed for kind={}, id={}, requestId={}, correlationId={}: {}", entityKind, entity.getId(), requestId, correlationId, e.getMessage(), e);
        }
    }
}

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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import java.util.*;

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
import pro.mir0n.esquire.enyMan.messaging.EsqEntityBroadcastPublisher;
import pro.mir0n.esquire.enyMan.messaging.KcRequestPublisher;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqMsgConstants;
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
    private final EsqEntityBroadcastPublisher broadcastPublisher;
    private final KcRequestPublisher kcRequestPublisher;

    public EnyManService(EsqEntityDictionaryRepository entityDictionaryRepository,
                         EsqOrgRepository orgRepository,
                         EsqUsrRepository usrRepository,
                         EsqAcctRepository acctRepository,
                         EsqSubtreeRepository subtreeRepository,
                         TransactionTemplate transactionTemplate,
                         EntityManager em,
                         EsqEntityBroadcastPublisher broadcastPublisher,
                         KcRequestPublisher kcRequestPublisher) {
        super(entityDictionaryRepository);
        this.orgService  = new OrgService(entityDictionaryRepository, orgRepository, transactionTemplate, em);
        this.usrService  = new UsrService(entityDictionaryRepository, usrRepository, transactionTemplate, em);
        this.acctService = new AcctService(entityDictionaryRepository, acctRepository, transactionTemplate, em);
        this.orgRepository = orgRepository;
        this.subtreeRepository = subtreeRepository;
        this.broadcastPublisher = broadcastPublisher;
        this.kcRequestPublisher = kcRequestPublisher;
    }

    @Override
    public EsqEntity esquireCommand(int kind, String id, String cmd, String rootPath, String uid) {
        EsqEntity ret = null;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        if (!eek.isOrg() && !eek.isUsr()) {
            throw new ResourceNotFoundException("esquireCommand", "kind", String.valueOf(kind));
        }
        int k = eek.getId();
        if (eek.isOrg()) {
            ret = orgService.esquireCommand(k, id, cmd, rootPath, uid);
        } else if (eek.isUsr()) {
            ret = usrService.esquireCommand(k, id, cmd, rootPath, uid);
        }
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        EsqEntity ret = null;
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
        String requestId     = RequestContextUtils.getRequestId();
        String correlationId = RequestContextUtils.getCorrelationId();
        if (eek.isOrg()) {
            if (permitted) {
                ret = orgService.esquireCommandSave(k, id, cmd, fields, rootPath, uid, roles);
                if (isBroadcastableUpdate(fields)) {
                    publishEntityEvent(ret, k, EsqMsgConstants.EVENT_UPDATE, requestId, correlationId, fields);
                }
            }
        } else if (eek.isUsr()) {
            if (id != null && id.equals(uid)) {
                permitted = true;
            }
            if (permitted) {
                ret = usrService.esquireCommandSave(k, id, cmd, fields, rootPath, uid, roles);
                if (isBroadcastableUpdate(fields)) {
                    publishEntityEvent(ret, k, EsqMsgConstants.EVENT_UPDATE, requestId, correlationId, fields);
                }
            }
        }
        if (ret == null && !permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "modify");
        }
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        EsqEntity ret = null;
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
        String requestId     = RequestContextUtils.getRequestId();
        String correlationId = RequestContextUtils.getCorrelationId();
        if (eek.isOrg()) {
            if (permitted) {
                ret = orgService.esquireCommandNew(k, parentId, cmd, fields, rootPath, uid, roles);
                publishEntityEvent(ret, k, EsqMsgConstants.EVENT_CREATE, requestId, correlationId, fields);
            }
        } else if (eek.isUsr()) {
            if (permitted) {
                ret = usrService.esquireCommandNew(k, parentId, cmd, fields, rootPath, uid, roles);
                publishEntityEvent(ret, k, EsqMsgConstants.EVENT_CREATE, requestId, correlationId, fields);
            }
        } else if (eek.isAcct()) {
            if (permitted) {
                ret = acctService.esquireCommandNew(k, parentId, cmd, fields, rootPath, uid, roles);
                publishEntityEvent(ret, k, EsqMsgConstants.EVENT_CREATE, requestId, correlationId, fields);
            }
        }
        if (ret == null && !permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "create");
        }
        return ret;
    }

    @Override
    public void esquireCommandDelete(int kind, String id, String cmd, String rootPath, String uid, List<String> roles) {
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
        String requestId     = RequestContextUtils.getRequestId();
        String correlationId = RequestContextUtils.getCorrelationId();
        if (eek.isOrg()) {
            orgService.esquireCommandDelete(k, id, cmd, rootPath, uid, roles);
            publishDeleteEvent(id, k, EsqMsgConstants.EVENT_DELETE, requestId, correlationId);
        } else if (eek.isUsr()) {
            usrService.esquireCommandDelete(k, id, cmd, rootPath, uid, roles);
            publishDeleteEvent(id, k, EsqMsgConstants.EVENT_DELETE, requestId, correlationId);
        }
    }

    @Override
    public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, String rootPath, String uid, List<String> roles) {
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
        //int dk = (int)Math.floor( (double) destOrg.getKind()/2 ) * 2;
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
        // Capture trace context before delegate call (still on request thread)
        String requestId     = RequestContextUtils.getRequestId();
        String correlationId = RequestContextUtils.getCorrelationId();
        List<EsqMoveRecord> records;
        if (eek.isOrg()) {
            records = orgService.esquireCommandMove(k, id, distId, rootPath, uid, roles);
        } else {
            records = usrService.esquireCommandMove(k, id, distId, rootPath, uid, roles);
        }
        // publish outside DB transaction (transaction already committed by service)
        for (EsqMoveRecord r : records) {
            devLog.debug("esquireCommandMove: publish move event reqId={}, r={}", requestId, r);
            publishMoveEvent(r, requestId, correlationId);
            publishKcMoveRequest(r, requestId, correlationId);
        }
        return records;
    }

    // Broadcast UPDATE only when fields that affect the entity's public identity or status change.
    // name / desc / deleted (usr_deleted_flg) are the current scope.
    // Note: for USR, "name" is not in the original request — UsrService.saveUsr() injects it
    // into the fields map when person (firstName/middleName/lastName) is updated, so this
    // check correctly fires for person-driven name changes too.
    private boolean isBroadcastableUpdate(Map<String, Object> fields) {
        return fields != null && (fields.containsKey(EsqMsgConstants.TEXT_NAME) || fields.containsKey(EsqMsgConstants.TEXT_DESC)
                               || fields.containsKey(EsqMsgConstants.TEXT_DELETED));
    }

    private void publishMoveEvent(EsqMoveRecord record, String requestId, String correlationId) {
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        text.put(EsqMsgConstants.TEXT_ID,   record.getId());
        text.put(EsqMsgConstants.TEXT_KIND, record.getKind());
        text.put(EsqMsgConstants.TEXT_PATH, record.getPath());
        try {
            broadcastPublisher.publish(record.getKind(), record.getId(), EsqMsgConstants.EVENT_UPDATE_PATH,
                    requestId, correlationId, text);
        } catch (Exception e) {
            log.error("publishMoveEvent: broadcast failed for kind={}, id={}: {}", record.getKind(), record.getId(), e.getMessage());
            devLog.error("publishMoveEvent: broadcast failed for kind={}, id={}, requestId={}, correlationId={}: {}",
                    record.getKind(), record.getId(), requestId, correlationId, e.getMessage(), e);
        }
    }

    // Sends a KC URQ (EVENT_UPDATE_PATH) only for USR entities — only USRs have a KC identity.
    // ORG and ACCT move events are ignored here; bizTree handles them via broadcast.
    private void publishKcMoveRequest(EsqMoveRecord record, String requestId, String correlationId) {

        pro.mir0n.esquire.backend.dto.EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(record.getKind());
        if (eek.isUsr()) {
            try {
                kcRequestPublisher.publishPathUpdate(record.getId(), record.getKind(), record.getPath(),
                        requestId, correlationId);
            } catch (Exception e) {
                log.error("publishKcMoveRequest: failed for id={}: {}", record.getId(), e.getMessage());
                devLog.error("publishKcMoveRequest: failed for id={}, requestId={}, correlationId={}: {}",
                        record.getId(), requestId, correlationId, e.getMessage(), e);
            }
        } else {
            devLog.debug("publishKcMoveRequest: skip move request for kind={}, id={}, requestId={}, correlationId={}",
                    record.getKind(), record.getId(), requestId, correlationId);

        }
    }

    private void publishDeleteEvent(String id, int entityKind, String eventType,
                                    String requestId, String correlationId) {
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        text.put(EsqMsgConstants.TEXT_ID,   id);
        text.put(EsqMsgConstants.TEXT_KIND, entityKind);
        try {
            broadcastPublisher.publish(entityKind, id, eventType,
                    requestId, correlationId, text);
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
    public List<EsqTreeNode> esquireCommandTree(int kind, String id, String rootPath, String uid) {
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
        devLog.debug("esquireCommandTree: kind:{}, id:{}, rootPath:{}, uid:{}, rows:{}", kind, id, rootPath, uid, ret.size());
        return ret;
    }

    private void publishEntityEvent(EsqEntity entity, int entityKind, String eventType,
                                    String requestId, String correlationId, Map<String, Object> fields) {
        if (entity == null) return;
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        text.put(EsqMsgConstants.TEXT_ID,        entity.getId());
        text.put(EsqMsgConstants.TEXT_KIND,      entityKind);
        text.put(EsqMsgConstants.TEXT_PARENT_ID, entity.getParentId());
        if (fields.containsKey(EsqMsgConstants.TEXT_PATH))    text.put(EsqMsgConstants.TEXT_PATH,    fields.get(EsqMsgConstants.TEXT_PATH));
        if (fields.containsKey(EsqMsgConstants.TEXT_NAME))    text.put(EsqMsgConstants.TEXT_NAME,    fields.get(EsqMsgConstants.TEXT_NAME));
        if (fields.containsKey(EsqMsgConstants.TEXT_DESC))    text.put(EsqMsgConstants.TEXT_DESC,    fields.get(EsqMsgConstants.TEXT_DESC));
        if (fields.containsKey(EsqMsgConstants.TEXT_DELETED)) text.put(EsqMsgConstants.TEXT_DELETED, fields.get(EsqMsgConstants.TEXT_DELETED));
        if (fields.containsKey(EsqMsgConstants.TEXT_STATUS))  text.put(EsqMsgConstants.TEXT_STATUS,  fields.get(EsqMsgConstants.TEXT_STATUS));
        try {
            broadcastPublisher.publish(entityKind, entity.getId(), eventType,
                    requestId, correlationId, text);
        } catch (Exception e) {
            log.error("publishEntityEvent: broadcast failed for kind={}, id={}: {}", entityKind, entity.getId(), e.getMessage());
            devLog.error("publishEntityEvent: broadcast failed for kind={}, id={}, requestId={}, correlationId={}: {}", entityKind, entity.getId(), requestId, correlationId, e.getMessage(), e);
        }
    }
}

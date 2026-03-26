/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.LinkedHashMap;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.messaging.EsqEntityBroadcastPublisher;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqMsgConstants;

@Slf4j
@Service
public class EnyManService  extends AEnyManService {

    private static final org.slf4j.Logger devLog = org.slf4j.LoggerFactory.getLogger("develop." + EnyManService.class.getName());

    private final IEnyManService orgService;
    private final IEnyManService usrService;
    private final EsqEntityBroadcastPublisher broadcastPublisher;

    public EnyManService(EsqEntityDictionaryRepository entityDictionaryRepository,
                         EsqOrgRepository orgRepository,
                         EsqUsrRepository usrRepository,
                         TransactionTemplate transactionTemplate,
                         EntityManager em,
                         EsqEntityBroadcastPublisher broadcastPublisher) {
        super(entityDictionaryRepository);
        this.orgService = new OrgService(entityDictionaryRepository, orgRepository, transactionTemplate, em);
        this.usrService = new UsrService(entityDictionaryRepository, usrRepository, transactionTemplate, em);
        this.broadcastPublisher = broadcastPublisher;
    }

    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
        EsqEntity ret = null;
        if (eek.isOrg()) {
            ret = orgService.esquireCommand(k, id, cmd, rootPath, uid);
        } else if (eek.isUsr()) {
            ret = usrService.esquireCommand(k, id, cmd, rootPath, uid);
        } else {
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
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
        EsqEntity ret = null;
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
        } else {
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
        if (ret == null && !permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "modify");
        }
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandNew(Integer kind, String parentId, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
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
        EsqEntity ret = null;
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
        } else {
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
        if (ret == null && !permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "create");
        }
        return ret;
    }

    @Override
    public void esquireCommandDelete(Integer kind, String id, String cmd, String rootPath, String uid, List<String> roles) {
        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
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
        } else {
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
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
        try {
            broadcastPublisher.publish(entityKind, entity.getId(), eventType,
                    requestId, correlationId, text);
        } catch (Exception e) {
            log.error("publishEntityEvent: broadcast failed for kind={}, id={}: {}", entityKind, entity.getId(), e.getMessage());
            devLog.error("publishEntityEvent: broadcast failed for kind={}, id={}, requestId={}, correlationId={}: {}", entityKind, entity.getId(), requestId, correlationId, e.getMessage(), e);
        }
    }
}

/*
 *  Esquire frameworks (tm)
 *  PacMan service
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
 * 02/12/2026 mir0n EsqObjectKind instead if EsqEntityKind
 *                  removed "profile" command
 * 02/13/2026 mir0n removed unused variables
 * 02/19/2026 mir0n EntityManager + TransactionTemplate injected
 *                  FlushModeType.COMMIT prevents Hibernate auto-flush before native queries
 *                  esquireCommandSave() with saveAcct() helper
 *                  ACCT_WRITABLE = {desc, status}
 * 03/06/2026 mir0n ACCT_WRITABLE removed; applyFields() dict-driven via ValidatorFactory
 * 03/08/2026 mir0n  validate() calls pass personal=false (interface alignment)
 * 03/09/2026 mir0n  roles param added; isAdminCmdPermitted(UPDATE) permission check
 *                   PermissionDeniedException thrown; stray debug comment removed
 * 03/10/2026 mir0n  import: RequestContextUtils updated to backend.service package
 * 03/10/2026 mir0n  fillКindFieldLayer() call updated to fillKindFieldLayer() — Cyrillic К → ASCII K
 * 03/17/2026 mir0n  messaging: broadcastPublisher injected; publishEntityEvent() on name/desc update
 * 03/20/2026 mir0n  status broadcast: "status" (acc_status) added to isBroadcastableUpdate()
 *                   publishEntityEvent() emits raw "status" field value; publisher decoupling rule
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug; dual error pattern (publishEntityEvent catch: log.warn→log.error+devLog.error)
 * 03/26/2026 mir0n  createAcct(), deleteAcct(), esquireCommandNew(), esquireCommandDelete() added;
 *                   publishDeleteEvent added; TEXT_* constants replace raw strings
 * 03/28/2026 mir0n  deleteAcct(): status != "C" → DeleteRestrictedException (account must be closed before delete)
 * 03/28/2026 mir0n  createAcct(): dict-driven defaults via EsqEntityDictionaryStorage.injectDefaults; replaces hardcoded ccy/status ternaries
 * 03/28/2026 mir0n  createAcct(): insertAcctPath before insertAcct; deleteAcct(): deleteEntityPath after deleteAcct
 * 03/31/2026 mir0n  insertAcctPath call: kind param added
 * 04/07/2026 mir0n  all kind params Integer → int; kind normalization removed;
 *                   upfront applicability check (!isAcct → ResourceNotFoundException) at all entry points
 * 04/09/2026 mir0n  applyFields() and enforceDefaults() delegated to EntityFieldUtils
 * 04/14/2026 mir0n  saveAcct(), deleteAcct(): kind param removed (detailAcctForUpdate aligned)
 */

package pro.mir0n.esquire.pacMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.service.EntityFieldUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.pacMan.messaging.EsqEntityBroadcastPublisher;
import pro.mir0n.esquire.pacMan.service.IPacManService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.EsqUtils;

@Slf4j
@Service
@AllArgsConstructor
public class PacManService  implements IPacManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + PacManService.class.getName());

    private EsqAcctRepository entityRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;
    private EsqEntityBroadcastPublisher broadcastPublisher;


    @Override
    public EsqEntity esquireCommand(int kind, String id, String cmd, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        devLog.debug("srvc: esquireCommand: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);

        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        if (!eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommand", "kind", String.valueOf(kind));
        }

        EsqEntityJpa jpa = entityRepository.detailAcct(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, null, null);
        devLog.debug("srvc: esquireCommand(2): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
//        devLog.debug("srvc: esquireCommandSave: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        int k = eek.getId();
        if (!eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommandSave", "kind", String.valueOf(kind));
        }

        Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
        boolean permitted = false;
//devLog.debug("srvc: esquireCommandSave: permissions:{} ", permissions);
        if (permissions != null) {
            permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(k),
                    EsqRolesStorage.AdminCmd.UPDATE
            );
            devLog.debug("srvc: esquireCommandSave: permitted:{} ", permitted);
        }
        if (!permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "modify");
        }

        EsqEntityJpa[] updated = {null};

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,ha
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveAcct(k, id, fields, rootPath, uid, correlationId, requestId, updated);
            return null;
        }); // ← transaction commits here

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(updated[0], null, null);
        if (isBroadcastableUpdate(fields)) {
            publishEntityEvent(ret, k, EsqMsgConstants.EVENT_UPDATE, requestId, correlationId, fields);
        }
        devLog.debug("srvc: esquireCommandSave(2): entity:{}", ret);
        return ret;
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

    // Broadcast UPDATE only when fields that affect the account's public identity or status change.
    // name / desc / status (acc_status) are the current scope.
    private boolean isBroadcastableUpdate(Map<String, Object> fields) {
        return fields != null && (fields.containsKey(EsqMsgConstants.TEXT_NAME) || fields.containsKey(EsqMsgConstants.TEXT_DESC)
                               || fields.containsKey(EsqMsgConstants.TEXT_STATUS));
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
        if (fields.containsKey(EsqMsgConstants.TEXT_NAME))   text.put(EsqMsgConstants.TEXT_NAME,   fields.get(EsqMsgConstants.TEXT_NAME));
        if (fields.containsKey(EsqMsgConstants.TEXT_DESC))   text.put(EsqMsgConstants.TEXT_DESC,   fields.get(EsqMsgConstants.TEXT_DESC));
        if (fields.containsKey(EsqMsgConstants.TEXT_STATUS)) text.put(EsqMsgConstants.TEXT_STATUS, fields.get(EsqMsgConstants.TEXT_STATUS));
        if (fields.containsKey(EsqMsgConstants.TEXT_PATH))   text.put(EsqMsgConstants.TEXT_PATH,   fields.get(EsqMsgConstants.TEXT_PATH));
        try {
            broadcastPublisher.publish(entityKind, entity.getId(), eventType,
                    requestId, correlationId, text);
        } catch (Exception e) {
            log.error("publishEntityEvent: broadcast failed for kind={}, id={}: {}", entityKind, entity.getId(), e.getMessage());
            devLog.error("publishEntityEvent: broadcast failed for kind={}, id={}, requestId={}, correlationId={}: {}", entityKind, entity.getId(), requestId, correlationId, e.getMessage(), e);
        }
    }

    @Override
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        devLog.debug("srvc: esquireCommandNew: kind:{}, parentId:{}, cmd:{}, fields:{}, rootPath:{}, uid:{}", kind, parentId, cmd, fields, rootPath, uid);

        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        int k = eek.getId();
        if (!eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommandNew", "kind", String.valueOf(kind));
        }

        Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
        boolean permitted = false;
        if (permissions != null) {
            permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(k),
                    EsqRolesStorage.AdminCmd.CREATE
            );
        }
        if (!permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "create");
        }

        EsqEntityJpa[] created = {null};

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            createAcct(k, parentId, fields, uid, correlationId, requestId, created);
            return null;
        });

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(created[0], null, null);
        publishEntityEvent(ret, k, EsqMsgConstants.EVENT_CREATE, requestId, correlationId, fields);
        devLog.debug("srvc: esquireCommandNew(2): entity:{}", ret);
        return ret;
    }

    @Override
    public void esquireCommandDelete(int kind, String id, String cmd, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        devLog.debug("srvc: esquireCommandDelete: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);

        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        int k = eek.getId();
        if (!eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommandDelete", "kind", String.valueOf(kind));
        }

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

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            deleteAcct(k, id, rootPath);
            return null;
        });

        publishDeleteEvent(id, k, EsqMsgConstants.EVENT_DELETE, requestId, correlationId);
        devLog.debug("srvc: esquireCommandDelete(2): kind:{}, id:{}", k, id);
    }

    private void createAcct(int kind, String parentId, Map<String, Object> fields,
                             String uid, String correlationId, String requestId,
                             EsqEntityJpa[] created) {
        String parentPath = entityRepository.acctPath(parentId);
        if (parentPath == null) {
            throw new ResourceNotFoundException("createAcct", "parentId", parentId);
        }
        long   newId  = EsqUtils.generateEntityId();
        String path   = parentPath;
        String prefix = EsqObjectKindStorage.getInstance().get(kind).getName().substring(0, 1).toUpperCase();
        String name   = prefix + newId;

        fields.put(EsqMsgConstants.TEXT_NAME, name);
        fields.put(EsqMsgConstants.TEXT_PATH, path);

        EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(kind);
        if (dict != null) {
            for (EsqEntityLayer layer : dict.getLayers()) {
                layer.injectDefaults(fields);
            }
        }

        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setKind(kind);
        EntityFieldUtils.applyFields(acct, fields);
        if (dict != null) {
            for (EsqEntityLayer layer : dict.getLayers()) {
                EntityFieldUtils.enforceDefaults(layer, acct);
            }
        }

        acct.setId(String.valueOf(newId));
        acct.setName(name);
        acct.setPath(path);
        acct.setParentId(parentId);

        entityRepository.insertAcctPath(newId, kind, path);
        entityRepository.insertAcct(newId, kind, name, acct.getDesc(), acct.getCcy(), acct.getStatus(), acct.getNegativeAllowed(), parentId, uid, correlationId, requestId);

        created[0] = acct;
    }

    private void deleteAcct(int kind, String id, String rootPath) {
        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(id, kind, rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("deleteAcct", "id", id);
        }
        ValidatorFactory.getInstance().validateDelete(acct);
        entityRepository.deleteAcct(id);
        entityRepository.deleteEntityPath(id);
    }

    private void saveAcct(int kind, String id, Map<String, Object> fields, String rootPath,
                          String uid, String correlationId, String requestId,
                          EsqEntityJpa[] updated) {
        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(id, kind, rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("saveAcct", "id", id);
        }
        if (EntityFieldUtils.applyFields(acct, fields)) {
            entityRepository.updateAcct(id, acct.getDesc(), acct.getCcy(), acct.getStatus(), acct.getNegativeAllowed(), uid, correlationId, requestId);
        }
        //note: if a DB trigger or default value modifies the row, saveAcct won't reflect it.
        updated[0] = acct;
    }

    private int rootLevel(List<String> path, String uid) {
        int ret = 0;
        if (path.size() > 1) {
            ret = path.size() -1;
            if (path.get(ret).equals(uid)) {
                ret++; // xxx: non-admin user, root is the current user
            }
        }
        //devLog.debug("rootLevel: path={}, uid={}, ret={}", path, uid, ret);
        return ret;
    }

}

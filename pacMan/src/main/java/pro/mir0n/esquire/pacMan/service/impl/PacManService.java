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
 */

package pro.mir0n.esquire.pacMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
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

@Slf4j
@Service
@AllArgsConstructor
public class PacManService  implements IPacManService {

    private EsqAcctRepository entityRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;
    private EsqEntityBroadcastPublisher broadcastPublisher;


    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommand: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);

        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);

        EsqEntityJpa jpa = null;
        // xxx: path is safe
        if (eek.isAcct()) {
            jpa = entityRepository.detailAcct(id, rootPath);
        }
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, null, null);
        log.debug("srvc: esquireCommand(2): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
//        log.debug("srvc: esquireCommandSave: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);
        int k = ((int)Math.floor((double) kind/2)) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
        if (!eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommandSave", "kind", kind.toString());
        }

        Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
        boolean permitted = false;
//log.debug("srvc: esquireCommandSave: permissions:{} ", permissions);
        if (permissions != null) {
            permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(k),
                    EsqRolesStorage.AdminCmd.UPDATE
            );
            log.debug("srvc: esquireCommandSave: permitted:{} ", permitted);
        }
        if (!permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "modify");
        }

        EsqEntityJpa[] updated = {null};

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveAcct(id, fields, rootPath, uid, correlationId, requestId, updated);
            return null;
        }); // ← transaction commits here

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(updated[0], null, null);
        if (isBroadcastableUpdate(fields)) {
            publishEntityEvent(ret, k, EsqMsgConstants.EVENT_UPDATE, requestId, correlationId, fields);
        }
        log.debug("srvc: esquireCommandSave(2): entity:{}", ret);
        return ret;
    }

    // Broadcast UPDATE only when fields that affect the account's public identity change.
    // desc is the current scope; more fields will be added as the protocol evolves.
    private boolean isBroadcastableUpdate(Map<String, Object> fields) {
        return fields != null && (fields.containsKey("name") || fields.containsKey("desc"));
    }

    // Runs synchronously on the request thread — publish failure is absorbed (log.warn),
    // so broker unavailability cannot fail the HTTP response.
    // If broker latency becomes observable in production, promote to @Async with an MDC
    // task decorator to preserve correlationId/requestId in the async thread.
    private void publishEntityEvent(EsqEntity entity, int entityKind, String eventType,
                                    String requestId, String correlationId, Map<String, Object> fields) {
        if (entity == null) return;
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        text.put("id",   entity.getId());
        text.put("kind", entityKind);
        if (fields.containsKey("name")) text.put("name", fields.get("name"));
        if (fields.containsKey("desc")) text.put("desc", fields.get("desc"));
        try {
            broadcastPublisher.publish(entityKind, entity.getId(), eventType,
                    requestId, correlationId, text);
        } catch (Exception e) {
            log.warn("publishEntityEvent: broadcast failed for kind={}, id={}: {}", entityKind, entity.getId(), e.getMessage());
        }
    }

    private void saveAcct(String id, Map<String, Object> fields, String rootPath,
                          String uid, String correlationId, String requestId,
                          EsqEntityJpa[] updated) {
        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(id, rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("saveAcct", "id", id);
        }
        if (applyFields(acct, fields)) {
            entityRepository.updateAcct(id, acct.getDesc(), acct.getStatus(), uid, correlationId, requestId);
        }
        //note: if a DB trigger or default value modifies the row, saveAcct won't reflect it.
        updated[0] = acct;
    }

    private boolean applyFields(EsqEntityJpa jpa, Map<String, Object> fields) {
        if (jpa == null || fields == null) {
            return false;
        }
        EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(jpa.getKind());
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        boolean changed = false;
        EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            String name = pd.getName();
            if (fields.containsKey(name)) {
                Object value = fields.get(name);
                kfl = dict.fillKindFieldLayer(name, kfl);
                EsqEntityField field = kfl.getField();
                if (field != null) {
                    if (field.getReadwrite() != null && (field.getReadwrite() & 2) == 2) {
                        value = ValidatorFactory.getInstance().validate(jpa, kfl, false, value);
//log.debug("pacMan:PacManService:applyFields: {} value:{}", name, value);
                        wrapper.setPropertyValue(name, value);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private int rootLevel(List<String> path, String uid) {
        int ret = 0;
        if (path.size() > 1) {
            ret = path.size() -1;
            if (path.get(ret).equals(uid)) {
                ret++; // xxx: non-admin user, root is the current user
            }
        }
        //log.debug("rootLevel: path={}, uid={}, ret={}", path, uid, ret);
        return ret;
    }

}

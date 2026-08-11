/*
 *  Esquire frameworks (tm)
 *  PacMan service
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
 * 06/01/2026 mir0n  esquireCommandNew() and private createAcct() helper removed -- account CREATE
 *                   moved to enyMan.
 * 06/04/2026 mir0n  esquireCommand / Save / Delete read rootPath / uid via RequestContextUtils instead of
 *                   params; dropped from the IPacManService signatures (passed to saveAcct / deleteAcct)
 * 06/05/2026 mir0n  XYRod injected; saveAcct posts an x-Rod account UPDATE and deleteAcct a DELETE audit event
 * 06/15/2026 mir0n  audit producer retyped messaging.xrod.IXRod (was common.xrod.XYRod); saveAcct / deleteAcct
 *                   post() calls carry an explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT)
 * 06/17/2026 mir0n  audit producer IXRod -> AuditBusBridge; the saveAcct / deleteAcct post() calls drop the
 *                   trailing MSG_TYPE_AUDIT arg
 * 06/18/2026 mir0n  audit module left common: AuditBusBridge moved to pro.mir0n.esquire.audit
 * 06/22/2026 mir0n  broadcastPublisher retyped EsqEntityBroadcastPublisher -> EntityBusAdapter; RodEvent import
 *                   messaging.xrod.RodEvent -> messaging.RodEvent (package move)
 * 06/23/2026 mir0n  EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)
 * 07/02/2026 mir0n  saveAcct / deleteAcct read requestId via requireRequestId() -- X-Request-ID mandatory on writes
 * 07/08/2026 mir0n  @EsqTraced on esquireCommand / esquireCommandSave / esquireCommandDelete
 *                   (esq.svc.acct.read / save / delete)
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- deleteAcct() counts esq.biz.acct.close.total (tag purge = test-house|none),
 *                   only once the delete has SUCCEEDED past the three guards, so a refused delete never inflates
 *                   it; the purge tag names the Test-House branch that forces those guards open, so a real closure
 *                   is never confused with a fixture teardown. The branch condition is lifted to a local flag
 * 08/11/2026 mir0n  v1.2.12 -- the account row raises its change number before every update, the entity
 *                   broadcast carries it on create, save and delete, and esquireCommandDelete returns the
 *                   number the delete raised
 */

package pro.mir0n.esquire.pacMan.service.impl;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.*;

import pro.mir0n.esquire.backend.o11y.EsqTraced;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.backend.service.EntityFieldUtils;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.pacMan.messaging.EntityBusAdapter;
import pro.mir0n.esquire.pacMan.service.IPacManService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusConstants;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacManService  implements IPacManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + PacManService.class.getName());

    private final EsqAcctRepository entityRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager em;
    private final EntityBusAdapter broadcastPublisher;
    private final EsqAcctTransactionRepository acctTrxRepo;
    private final AuditBusBridge audit;   // audit: account UPDATE / DELETE

    // Test House subtree path prefix. Accounts whose ep_path starts with this
    // prefix sit inside the seeded Test House (org_pk=14) and are recognized
    // as test data: deleteAcct purges their transactions, clears fundedDate,
    // and forces status="C" so the production delete validator passes. The
    // prefix is data-shape gated, not config gated -- nothing else in any
    // environment roots under "1.14.", so the branch is inert for real
    // entities. Tied to the db.seed Test House (esq_entity_path ep_pk=14
    // ep_path='1.14.'); if the seed Test House moves, this prefix moves
    // with it.
    private static final String TEST_HOUSE_PATH_PREFIX = "1.14.";


    @Override
    @EsqTraced(name = "esq.svc.acct.read", label = "read account")
    public EsqEntity esquireCommand(int kind, String id, String cmd) {
        String rootPath = RequestContextUtils.getRootPath();
        devLog.debug("srvc: esquireCommand: kind:{}, id:{}, cmd:{}, rootPath:{}",  kind, id, cmd, rootPath);

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
    @EsqTraced(name = "esq.svc.acct.save", label = "save account")
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.requireRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
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
            publishEntityEvent(ret, k, BusConstants.EVENT_UPDATE, requestId, correlationId, fields);
        }
        devLog.debug("srvc: esquireCommandSave(2): entity:{}", ret);
        return ret;
    }

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

    // Broadcast UPDATE only when fields that affect the account's public identity or status change.
    // name / desc / status (acc_status) are the current scope.
    private boolean isBroadcastableUpdate(Map<String, Object> fields) {
        return fields != null && (fields.containsKey(EsqConstants.TEXT_NAME) || fields.containsKey(EsqConstants.TEXT_DESC)
                               || fields.containsKey(EsqConstants.TEXT_STATUS));
    }

    // Runs synchronously on the request thread — publish failure is absorbed (log.warn),
    // so broker unavailability cannot fail the HTTP response.
    // If broker latency becomes observable in production, promote to @Async with an MDC
    // task decorator to preserve correlationId/requestId in the async thread.
    private void publishEntityEvent(EsqEntity entity, int entityKind, String eventType,
                                    String requestId, String correlationId, Map<String, Object> fields) {
        if (entity == null) return;
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        text.put(EsqConstants.TEXT_ID,        entity.getId());
        text.put(EsqConstants.TEXT_KIND,      entityKind);
        text.put(EsqConstants.TEXT_PARENT_ID, entity.getParentId());
        if (fields.containsKey(EsqConstants.TEXT_NAME))   text.put(EsqConstants.TEXT_NAME,   fields.get(EsqConstants.TEXT_NAME));
        if (fields.containsKey(EsqConstants.TEXT_DESC))   text.put(EsqConstants.TEXT_DESC,   fields.get(EsqConstants.TEXT_DESC));
        if (fields.containsKey(EsqConstants.TEXT_STATUS)) text.put(EsqConstants.TEXT_STATUS, fields.get(EsqConstants.TEXT_STATUS));
        if (fields.containsKey(EsqConstants.TEXT_PATH))   text.put(EsqConstants.TEXT_PATH,   fields.get(EsqConstants.TEXT_PATH));
        try {
            // the number rides on the entity the service just built from the row it wrote
            broadcastPublisher.publish(entityKind, entity.getId(), eventType,
                    requestId, correlationId, text, entity.getChangeNo());
        } catch (Exception e) {
            log.error("publishEntityEvent: broadcast failed for kind={}, id={}: {}", entityKind, entity.getId(), e.getMessage());
            devLog.error("publishEntityEvent: broadcast failed for kind={}, id={}, requestId={}, correlationId={}: {}", entityKind, entity.getId(), requestId, correlationId, e.getMessage(), e);
        }
    }

    @Override
    @EsqTraced(name = "esq.svc.acct.delete", label = "delete account")
    public Long esquireCommandDelete(int kind, String id, String cmd, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.requireRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        devLog.debug("srvc: esquireCommandDelete: kind:{}, id:{}, cmd:{}, rootPath:{}", kind, id, cmd, rootPath);

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

        Long changeNo = transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            return deleteAcct(k, id, rootPath);
        });

        publishDeleteEvent(id, k, BusConstants.EVENT_DELETE, requestId, correlationId, changeNo);
        devLog.debug("srvc: esquireCommandDelete(2): kind:{}, id:{}", k, id);
        return changeNo;
    }

    /** Deletes the account and returns the delete's change number (bumped once, on the row). */
    private Long deleteAcct(int kind, String id, String rootPath) {
        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(id, kind, rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("deleteAcct", "id", id);
        }
        // Test House subtree: accounts whose ep_path starts with "1.14." are
        // test data. Purge transactions, clear fundedDate, force status "C"
        // in memory so the three production delete guards (no funded, status
        // closed, no transactions) all pass. Outside the Test House subtree
        // the branch is skipped and the validator runs unchanged.
        String acctPath = entityRepository.acctPath(id);
        boolean testHousePurge = acctPath != null && acctPath.startsWith(TEST_HOUSE_PATH_PREFIX);
        if (testHousePurge) {
            acctTrxRepo.deleteAcctTransactionsByAccPk(Long.parseLong(id));
            acct.setFundedDate(null);
            acct.setStatus("C");
        }
        ValidatorFactory.getInstance().validateDelete(acct);
        entityRepository.deleteAcct(id);
        entityRepository.deleteEntityPath(id);
        // esq.biz.acct.close.total (O1/T8 phase B): an account actually closed. Counted only once the delete has
        // SUCCEEDED (past the three guards), so a refused delete never inflates it. The purge tag says whether
        // this went through the Test-House branch -- the demo-data path that forces the guards open -- so a real
        // closure is never confused with a test-fixture teardown on the panel.
        EsqBizMeters.count("esq.biz.acct.close.total", "purge", testHousePurge ? "test-house" : "none");
        // audit: account DELETE (id + kind). ONE bump on the row object; the returned number (for the
        // broadcast) and the audit event (the source) then read the same value, and it matches what the
        // trigger path writes.
        Long ret = acct.bumpChangeNo();
        audit.post(RodEvent.Op.DELETE, kind, id, null, acct);
        return ret;
    }

    private void saveAcct(int kind, String id, Map<String, Object> fields, String rootPath,
                          String uid, String correlationId, String requestId,
                          EsqEntityJpa[] updated) {
        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(id, kind, rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("saveAcct", "id", id);
        }
        if (EntityFieldUtils.applyFields(acct, fields)) {
            entityRepository.updateAcct(id, acct.getDesc(), acct.getCcy(), acct.getStatus(), acct.getNegativeAllowed(),
                    acct.bumpChangeNo(), uid, correlationId, requestId);
        }
        //note: if a DB trigger or default value modifies the row, saveAcct won't reflect it.
        updated[0] = acct;
        // audit: account UPDATE (ccy / status / desc / neg-allowed). The acct holds the applied state.
        audit.post(RodEvent.Op.UPDATE, kind, acct.getId(), null, acct);
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

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: org command/save service (split from EnyManService)
 *                   esquireCommand(), esquireCommandSave(), saveOrg() moved from EnyManService
 * 03/06/2026 mir0n  ORG_WRITABLE removed; applyFields() dict-driven via ValidatorFactory
 *                   custom field validation via dictionary readwrite flag
 * 03/08/2026 mir0n  unused imports removed; applyFields/validate calls pass personal=false
 * 03/09/2026 mir0n  esquireCommandSave(): roles param added
 * 03/10/2026 mir0n  import: RequestContextUtils updated to backend.service package
 * 03/10/2026 mir0n  fillКindFieldLayer() call updated to fillKindFieldLayer() — Cyrillic К → ASCII K
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 03/26/2026 mir0n  createOrg(), deleteOrg(), esquireCommandNew(), esquireCommandDelete() added
 * 03/28/2026 mir0n  createOrg(): injectDefaults before applyFields; custom field loop restricted to request fields
 * 03/28/2026 mir0n  createOrg(): insertOrgPath before insertOrg; deleteOrg(): deleteEntityPath after deleteOrg (INSERT uses par_default)
 * 03/31/2026 mir0n  esquireCommandMove() + moveOrg(): subtree path update, descendant guard,
 *                   skip-if-same-parent; insertOrgPath: kind param added (ep_et_pk)
 * 04/01/2026 mir0n  move: collects updated records
 * 04/07/2026 mir0n  all kind params Integer → int (including private createOrg)
 * 04/09/2026 mir0n  applyFields() and enforceDefaults() delegated to EntityFieldUtils
 * 04/16/2026 mir0n  ret declarations moved to top; moveOrg(): null-guard replaces early return
 * 06/01/2026 mir0n  id minting call retargeted: EsqUtils.generateEntityId() -> EntityIdGenerator.generateEntityId()
 *                   (id minter moved from common to enyMan in v1.2.6).
 * 06/04/2026 mir0n  rootPath / uid read via RequestContextUtils instead of method params (dropped from
 *                   the IEnyManService public signatures)
 * 06/05/2026 mir0n  XYRod injected; x-Rod audit posts at the org write sites (create / save / delete / move) +
 *                   per-param org_par events via listOrgPar (enabled-gated); create/save resolve the
 *                   dictionary via completedDictionary (custom-param save fix)
 * 06/15/2026 mir0n  audit dep XYRod -> IXRod (import common.xrod -> messaging.xrod); every org/org_par post()
 *                   passes an explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT).
 * 06/17/2026 mir0n  audit dep IXRod -> AuditBusBridge; the org / org_par post() calls drop the trailing
 *                   MSG_TYPE_AUDIT arg; the isEnabled() guard reads audit
 * 06/18/2026 mir0n  audit module left common: AuditBusBridge moved to pro.mir0n.esquire.audit
 * 06/22/2026 mir0n  RodEvent import retargeted messaging.xrod.RodEvent -> messaging.RodEvent (package move).
 * 06/27/2026 mir0n  test-only createOrg window widener: sleepCreateWindow() (createDelayMs from
 *                   ENYMAN_TEST_CREATE_DELAY_MS) holds the create transaction open between the parent-path read and
 *                   the child insert, so a concurrent cross-instance move can rewrite the parent path in the gap
 *                   (the deterministic race-8b repro lever; 0 = off)
 * 08/11/2026 mir0n  v1.2.12 -- the org row's change number is raised before every update and passed to the
 *                   statement; delete bumps once and returns the number, which the delete event and the
 *                   audit record share
 * 08/26/2026 mir0n  saveOrg / deleteOrg / moveOrg take the kind; moveOrg reads the moved paths AFTER the parent
 *                   write, so the broadcast carries the raised entity number rather than the previous one
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.enyMan.service.EntityIdGenerator;
import pro.mir0n.esquire.backend.jpa.EsqCustomEntityFieldJpa;
import pro.mir0n.esquire.backend.service.EntityFieldUtils;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import pro.mir0n.esquire.backend.jpa.entity.EsqParRow;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.audit.AuditBusBridge;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class OrgService  extends AEnyManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + OrgService.class.getName());

    private EsqEntityDictionaryRepository entityDictionaryRepository;
    private EsqOrgRepository orgRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;
    private AuditBusBridge audit;
    // Test-only window widener (ENYMAN_TEST_CREATE_DELAY_MS env var, default 0 = off). When > 0, createOrg
    // sleeps between reading the parent path and inserting the child, so a concurrent move on another
    // instance can rewrite the parent path in that gap -- the deterministic race-8b reproduction lever.
    private final long createDelayMs = testCreateDelayMs();

    public OrgService(EsqEntityDictionaryRepository entityDictionaryRepository,
                      EsqOrgRepository orgRepository,
                      TransactionTemplate transactionTemplate,
                      EntityManager em,
                      AuditBusBridge audit) {
        super(entityDictionaryRepository);
        this.entityDictionaryRepository = entityDictionaryRepository;
        this.orgRepository = orgRepository;
        this.transactionTemplate = transactionTemplate;
        this.em = em;
        this.audit = audit;
    }

    // Test-only: hold the create transaction open between the parent-path read and the child insert,
    // so a concurrent cross-instance move can rewrite the parent path in the gap (race-8b lever).
    private void sleepCreateWindow() {
        if (createDelayMs > 0L) {
            try {
                Thread.sleep(createDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }


    @Override
    public EsqEntity esquireCommand(int kind, String id, String cmd) {
        EsqEntity ret = null;
        String rootPath = RequestContextUtils.getRootPath();
        devLog.debug("srvc: esquireCommand(org): kind:{}, id:{}, cmd:{}, rootPath:{}",  kind, id, cmd, rootPath);
        EsqEntityJpa jpa = orgRepository.detailOrg(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }
        List<EsqNameValueJpa> custom = orgRepository.customOrg(id);
        ret = EsqEntityFactory.getInstance().createEntity(jpa, custom, null);
        devLog.debug("srvc: esquireCommand(org): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields,
                                        List<String> roles) {
        EsqEntity ret = null;
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
        devLog.debug("srvc: esquireCommandSave(org): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);

        EsqEntityJpa[] updated  = {null};
        List<EsqNameValueJpa>[] custom   = new List[]{null};

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveOrg(kind, id, fields, rootPath, uid, correlationId, requestId, updated, custom);
            EsqOrgJpa savedOrg = (EsqOrgJpa) updated[0];
            audit.post(RodEvent.Op.UPDATE, savedOrg.getKind(), savedOrg.getId(), null, savedOrg);
            return null;
        }); // <- transaction commits here

        ret = EsqEntityFactory.getInstance().createEntity(updated[0], custom[0], null);
        devLog.debug("srvc: esquireCommandSave(org): entity:{}", ret);
        return ret;
    }

    @Override
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields,
                                       List<String> roles) {
        EsqEntity ret = null;
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
        devLog.debug("srvc: esquireCommandNew(org): kind:{}, parentId:{}, cmd:{}, rootPath:{}, uid:{}", kind, parentId, cmd, rootPath, uid);

        EsqEntityJpa[] created = {null};

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            createOrg(kind, parentId, fields, rootPath, uid, correlationId, requestId, created);
            EsqOrgJpa org = (EsqOrgJpa) created[0];
            audit.post(RodEvent.Op.CREATE, org.getKind(), org.getId(), null, org);
            // full-fidelity param audit: every org-param (defaults + explicit) is born with the org.
            // Guarded so the re-SELECT is skipped entirely when audit is disabled.
            if (audit.isEnabled()) {
                for (EsqParRow p : orgRepository.listOrgPar(org.getId())) {
                    audit.post(RodEvent.Op.CREATE, EsqConstants.KIND_ORG_PAR, org.getId(), p.getName(), p);
                }
            }
            return null;
        });

        ret = EsqEntityFactory.getInstance().createEntity(created[0], null, null);
        devLog.debug("srvc: esquireCommandNew(org): entity:{}", ret);
        return ret;
    }

    @Override
    public Long esquireCommandDelete(int kind, String id, String cmd, List<String> roles) {
        String rootPath = RequestContextUtils.getRootPath();
        devLog.debug("srvc: esquireCommandDelete(org): kind:{}, id:{}, cmd:{}, rootPath:{}", kind, id, cmd, rootPath);
        return transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            EsqOrgJpa deleted = deleteOrg(kind, id, rootPath);
            // ONE bump for this delete, on the row object itself. Everything that reports the delete then reads
            // the same value off the same object -- the returned number for the broadcast, the source for the
            // audit event. The delete record gets the number AFTER the last live state, exactly what the
            // database trigger writes (OLD.<x>_change_no + 1), so both audit paths agree.
            Long cn = deleted.bumpChangeNo();
            audit.post(RodEvent.Op.DELETE, kind, id, null, deleted);
            return cn;
        });
    }

    @Override
    public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
        devLog.debug("srvc: esquireCommandMove(org): kind:{}, id:{}, distId:{}, rootPath:{}, uid:{}", kind, id, distId, rootPath, uid);
        List<EsqMoveRecord> records = transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            return moveOrg(kind, id, distId, rootPath, uid, correlationId, requestId);
        });
        return records != null ? records : List.of();
    }

    private List<EsqMoveRecord> moveOrg(int kind, String id, String distId, String rootPath, String uid, String correlationId, String requestId) {
        List<EsqMoveRecord> rows = null;
        orgRepository.lockEntityPathRoot();
        EsqOrgJpa org = orgRepository.detailOrgForUpdate(id, rootPath);
        if (org == null) {
            throw new ResourceNotFoundException("moveOrg", "id", id);
        }
        EnyManService.assertKindMatches("moveOrg", kind, org.getKind(), id);
        if (distId.equals(org.getParentId())) {
            rows = List.of();
        } else {
            String currentPath = orgRepository.orgPath(id, rootPath);
            String destPath    = orgRepository.orgPath(distId, rootPath);
            if (destPath == null) {
                throw new ResourceNotFoundException("moveOrg", "dist_id", distId);
            }
            if (destPath.startsWith(currentPath)) {
                throw new PermissionDeniedException("org", "cannot move org into its own subtree");
            }
            String newEntityPath = destPath + id + ".";
            orgRepository.moveOrgPaths(currentPath, newEntityPath);
            orgRepository.moveOrgParent(id, distId, org.bumpChangeNo(), uid, correlationId, requestId);
            //xxx: read AFTER the parent write -- the row carries the number that write just raised
            rows = orgRepository.listMovedPaths(newEntityPath);
            // x-Rod audit: move is one parent-ref UPDATE (org_org_pk); path rewrites are not audited.
            org.setParentId(distId);
            audit.post(RodEvent.Op.UPDATE, org.getKind(), org.getId(), null, org);
        }
        return rows;
    }

    private void createOrg(int kind, String parentId, Map<String, Object> fields,
                            String rootPath, String uid, String correlationId, String requestId,
                            EsqEntityJpa[] created) {
        String parentPath = orgRepository.orgPath(parentId, rootPath);
        if (parentPath == null) {
            throw new ResourceNotFoundException("createOrg", "parentId", parentId);
        }
        sleepCreateWindow();
        long newId = EntityIdGenerator.generateEntityId();
        String idStr = String.valueOf(newId);
        String path = parentPath + newId + ".";
        fields.put("path", path);

        // Validate top-level fields via dictionary (mirrors saveOrg)
        EsqOrgJpa org = new EsqOrgJpa();
        org.setKind(kind);
        EsqEntityDictionary dictOrg = EsqEntityDictionaryStorage.getInstance().get(kind);
        EsqEntityLayer orgLayer = (dictOrg != null) ? dictOrg.findLayer(1) : null;
        if (orgLayer != null) orgLayer.injectDefaults(fields);
        EntityFieldUtils.applyFields(org, fields, false, 0, null);
        if (orgLayer != null) EntityFieldUtils.enforceDefaults(orgLayer, org);

        orgRepository.insertOrgPath(newId, kind, path);
        orgRepository.insertOrg(newId, kind, org.getName(), org.getDesc(), org.getFullName(), parentId, uid, correlationId, requestId);
        orgRepository.insertCustomOrg(newId, kind, uid, correlationId, requestId);
        // The INSERT left the row at 1 (column default) and no create-time UPDATE follows on the org row
        // itself. The object still has to carry it -- the CREATE audit event is built from this object.
        org.setChangeNo(1L);

        List<EsqCustomEntityFieldJpa> customFields = entityDictionaryRepository.findCustom(kind);
        if (customFields != null && !customFields.isEmpty()) {
            EsqEntityDictionary dict = completedDictionary(kind);
            EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
            for (EsqCustomEntityFieldJpa cf : customFields) {
                String fieldName = cf.getName();
                if (cf.getReadwrite() != null && (cf.getReadwrite() & 2) == 2 && fields.containsKey(fieldName)) {
                    Object rawVal = fields.get(fieldName);
                    if (rawVal == null) continue;
                    kfl = (dict != null) ? dict.fillKindFieldLayer(fieldName, kfl) : null;
                    String val = (String) ValidatorFactory.getInstance().validate(org, kfl, false, rawVal);
                    orgRepository.updateCustomOrg(idStr, fieldName, val, 2L, uid, correlationId, requestId);
                }
            }
        }

        org.setId(idStr);
        org.setPath(path);
        org.setParentId(parentId);
        created[0] = org;
    }

    /** Deletes the org and returns the row as it was read for the delete -- the caller posts the audit event
     *  from it, which is the only place its change number is still available. */
    private EsqOrgJpa deleteOrg(int kind, String id, String rootPath) {
        EsqOrgJpa org = orgRepository.detailOrgForUpdate(id, rootPath);
        if (org == null) {
            throw new ResourceNotFoundException("deleteOrg", "id", id);
        }
        EnyManService.assertKindMatches("deleteOrg", kind, org.getKind(), id);
        ValidatorFactory.getInstance().validateDelete(org);
        orgRepository.deleteOrg(id);
        orgRepository.deleteEntityPath(id);
        return org;
    }

    private void saveOrg(int kind, String id, Map<String, Object> fields, String rootPath,
                         String uid, String correlationId, String requestId,
                         EsqEntityJpa[] updated, List<EsqNameValueJpa>[] custom) {
        EsqOrgJpa org = orgRepository.detailOrgForUpdate(id, rootPath);
        if (org == null) {
            throw new ResourceNotFoundException("saveOrg", "id", id);
        }
        EnyManService.assertKindMatches("saveOrg", kind, org.getKind(), id);
        List<EsqNameValueJpa> cstm = orgRepository.customOrg(id);
        if (EntityFieldUtils.applyFields(org, fields, false, 0, null)) {
            orgRepository.updateOrg(id, org.getName(), org.getDesc(), org.getFullName(),
                    org.bumpChangeNo(), uid, correlationId, requestId);
        }

        Set<String> changedPars = new HashSet<>();
        if (cstm != null) {
            EsqEntityDictionary dict = completedDictionary(org.getKind());
            EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
            for (EsqNameValueJpa nv : cstm) {
                String nm = nv.getName();
                if (fields.containsKey(nm)) {
                    String val = (String)fields.get(nm);
                    kfl = (dict != null) ? dict.fillKindFieldLayer(nm,kfl) : null;
                    EsqEntityField field = (kfl != null) ? kfl.getField() : null;
                    if (field != null && (field.getReadwrite()  & 2) == 2) {
                        val = (String) ValidatorFactory.getInstance().validate(org, kfl, false, val);
                        nv.setValue(val);
                        orgRepository.updateCustomOrg(id, nm, val, nv.bumpChangeNo(), uid, correlationId, requestId);
                        changedPars.add(nm);
                    }
                }
            }
        }

        //note: if a DB trigger or default value modifies the row, saveOrg won't reflect it.
        updated[0] = org; //orgRepository.detailOrg(id, rootPath);
        custom[0]  = cstm;

        // param audit: post one ORG_PAR UPDATE per actually-changed param (re-SELECT for the
        // committed value + the param's et_pk).
        if (audit.isEnabled() && !changedPars.isEmpty()) {
            for (EsqParRow p : orgRepository.listOrgPar(id)) {
                if (changedPars.contains(p.getName())) {
                    audit.post(RodEvent.Op.UPDATE, EsqConstants.KIND_ORG_PAR, id, p.getName(), p);
                }
            }
        }
    }

}


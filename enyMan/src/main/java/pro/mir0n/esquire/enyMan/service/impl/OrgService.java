/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
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
 * 03/28/2026 mir0n  createOrg(): injectDefaults before applyFields; custom field loop restricted to request fields (INSERT uses par_default)
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.*;
import java.util.LinkedHashMap;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.backend.jpa.EsqCustomEntityFieldJpa;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class OrgService  extends AEnyManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + OrgService.class.getName());

    private EsqEntityDictionaryRepository entityDictionaryRepository;
    private EsqOrgRepository orgRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;

    public OrgService(EsqEntityDictionaryRepository entityDictionaryRepository,
                      EsqOrgRepository orgRepository,
                      TransactionTemplate transactionTemplate,
                      EntityManager em) {
        super(entityDictionaryRepository);
        this.entityDictionaryRepository = entityDictionaryRepository;
        this.orgRepository = orgRepository;
        this.transactionTemplate = transactionTemplate;
        this.em = em;
    }


    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        //String correlationId = RequestContextUtils.getCorrelationId();
        //String requestId = RequestContextUtils.getRequestId();
        devLog.debug("srvc: esquireCommand(org): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);
        EsqEntityJpa jpa = orgRepository.detailOrg(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }
        List<EsqNameValueJpa> custom = orgRepository.customOrg(id);
        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, custom, null);
        devLog.debug("srvc: esquireCommand(org): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
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
            saveOrg(id, fields, rootPath, uid, correlationId, requestId, updated, custom);
            return null;
        }); // ← transaction commits here

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(updated[0], custom[0], null);
        devLog.debug("srvc: esquireCommandSave(org): entity:{}", ret);
        return ret;
    }

    @Override
    public EsqEntity esquireCommandNew(Integer kind, String parentId, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        devLog.debug("srvc: esquireCommandNew(org): kind:{}, parentId:{}, cmd:{}, rootPath:{}, uid:{}", kind, parentId, cmd, rootPath, uid);

        EsqEntityJpa[] created = {null};

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            createOrg(kind, parentId, fields, rootPath, uid, correlationId, requestId, created);
            return null;
        });

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(created[0], null, null);
        devLog.debug("srvc: esquireCommandNew(org): entity:{}", ret);
        return ret;
    }

    @Override
    public void esquireCommandDelete(Integer kind, String id, String cmd, String rootPath, String uid, List<String> roles) {
        devLog.debug("srvc: esquireCommandDelete(org): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);
        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            deleteOrg(id, rootPath);
            return null;
        });
    }

    private void createOrg(Integer kind, String parentId, Map<String, Object> fields,
                            String rootPath, String uid, String correlationId, String requestId,
                            EsqEntityJpa[] created) {
        String parentPath = orgRepository.orgPath(parentId, rootPath);
        if (parentPath == null) {
            throw new ResourceNotFoundException("createOrg", "parentId", parentId);
        }
        long newId = EsqUtils.generateEntityId();
        String idStr = String.valueOf(newId);
        String path = parentPath + newId + ".";
        fields.put("path", path);

        // Validate top-level fields via dictionary (mirrors saveOrg)
        EsqOrgJpa org = new EsqOrgJpa();
        org.setKind(kind);
        EsqEntityDictionary dictOrg = EsqEntityDictionaryStorage.getInstance().get(kind);
        EsqEntityLayer orgLayer = (dictOrg != null) ? dictOrg.findLayer(1) : null;
        if (orgLayer != null) orgLayer.injectDefaults(fields);
        applyFields(org, fields, false, 0, null);

        orgRepository.insertOrg(newId, kind, org.getName(), org.getDesc(), org.getFullName(), path, parentId, uid, correlationId, requestId);
        orgRepository.insertCustomOrg(newId, kind, uid, correlationId, requestId);

        List<EsqCustomEntityFieldJpa> customFields = entityDictionaryRepository.findCustom(kind);
        if (customFields != null && !customFields.isEmpty()) {
            EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(kind);
            EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
            for (EsqCustomEntityFieldJpa cf : customFields) {
                String fieldName = cf.getName();
                if (cf.getReadwrite() != null && (cf.getReadwrite() & 2) == 2 && fields.containsKey(fieldName)) {
                    Object rawVal = fields.get(fieldName);
                    if (rawVal == null) continue;
                    kfl = (dict != null) ? dict.fillKindFieldLayer(fieldName, kfl) : null;
                    String val = (String) ValidatorFactory.getInstance().validate(org, kfl, false, rawVal);
                    orgRepository.updateCustomOrg(idStr, fieldName, val, uid, correlationId, requestId);
                }
            }
        }

        org.setId(idStr);
        org.setPath(path);
        org.setParentId(parentId);
        created[0] = org;
    }

    private void deleteOrg(String id, String rootPath) {
        EsqOrgJpa org = orgRepository.detailOrgForUpdate(id, rootPath);
        if (org == null) {
            throw new ResourceNotFoundException("deleteOrg", "id", id);
        }
        orgRepository.deleteOrg(id);
    }

    private void saveOrg(String id, Map<String, Object> fields, String rootPath,
                         String uid, String correlationId, String requestId,
                         EsqEntityJpa[] updated, List<EsqNameValueJpa>[] custom) {
        EsqOrgJpa org = orgRepository.detailOrgForUpdate(id, rootPath);
        if (org == null) {
            throw new ResourceNotFoundException("saveOrg", "id", id);
        }
        List<EsqNameValueJpa> cstm = orgRepository.customOrg(id);
        if (applyFields(org, fields, false,0,null)) {
            orgRepository.updateOrg(id, org.getName(), org.getDesc(), org.getFullName(), uid, correlationId, requestId);
        }

        if (cstm != null) {
            EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(org.getKind());
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
                        orgRepository.updateCustomOrg(id, nm, val, uid, correlationId, requestId);
                    }
                }
            }
        }

        //note: if a DB trigger or default value modifies the row, saveOrg won't reflect it.
        updated[0] = org; //orgRepository.detailOrg(id, rootPath);
        custom[0]  = cstm;
    }
}


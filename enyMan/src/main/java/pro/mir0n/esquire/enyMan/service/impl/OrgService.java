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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class OrgService  extends AEnyManService {

    private EsqOrgRepository orgRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;

    public OrgService(EsqEntityDictionaryRepository entityDictionaryRepository,
                      EsqOrgRepository orgRepository,
                      TransactionTemplate transactionTemplate,
                      EntityManager em) {
        super(entityDictionaryRepository);
        this.orgRepository = orgRepository;
        this.transactionTemplate = transactionTemplate;
        this.em = em;
    }


    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        //String correlationId = RequestContextUtils.getCorrelationId();
        //String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommand(org): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);
        EsqEntityJpa jpa = orgRepository.detailOrg(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }
        List<EsqNameValueJpa> custom = orgRepository.customOrg(id);
        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, custom, null);
        log.debug("srvc: esquireCommand(org): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommandSave(org): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);

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
        log.debug("srvc: esquireCommandSave(org): entity:{}", ret);
        return ret;
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
                    kfl = (dict != null) ? dict.fillКindFieldLayer(nm,kfl) : null;
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


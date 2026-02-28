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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqConstants;

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

    //TODO: use dictionary eventually for fields validation and readonly fields
    private static final Set<String> ORG_WRITABLE = Set.of("name","desc","fullName");
    private void saveOrg(String id, Map<String, Object> fields, String rootPath,
                         String uid, String correlationId, String requestId,
                         EsqEntityJpa[] updated, List<EsqNameValueJpa>[] custom) {
        EsqOrgJpa org = orgRepository.detailOrgForUpdate(id, rootPath);
        if (org == null) {
            throw new ResourceNotFoundException("saveOrg", "id", id);
        }
        List<EsqNameValueJpa> cstm = orgRepository.customOrg(id);
        if (applyFields(org, fields, ORG_WRITABLE)) {
            orgRepository.updateOrg(id, org.getName(), org.getDesc(), org.getFullName(), uid, correlationId, requestId);
        }
        if (cstm != null) {
            for (EsqNameValueJpa nv : cstm) {
                String nm = nv.getName();
                if (fields.containsKey(nm)) {
                    String val = (String) fields.get(nm);
                    if (val != null && val.isBlank()) {
                        val = null;
                    }
                    nv.setValue(val);
                    orgRepository.updateCustomOrg(id, nm, val, uid, correlationId, requestId);
                }
            }
        }
        //note: if a DB trigger or default value modifies the row, saveOrg won't reflect it.
        updated[0] = org; //orgRepository.detailOrg(id, rootPath);
        custom[0]  = cstm;
    }
}


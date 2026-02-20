/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  EntityManager + TransactionTemplate injected
 *                   FlushModeType.COMMIT prevents Hibernate auto-flush before native queries
 *                   esquireKeySave() with saveAccess() helper
 *                   ACCESS_WRITABLE = {email, loginId, pwdChangeForced, tfaMethod}
 */

package pro.mir0n.esquire.keySmith.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.access.*;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import pro.mir0n.esquire.backend.jpa.access.*;
import pro.mir0n.esquire.keySmith.jpa.EsqAccessProfileRepository;
import pro.mir0n.esquire.keySmith.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.keySmith.service.IKeySmithService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class KeySmithService implements IKeySmithService {

    private EsqAccessProfileRepository accessProfileRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;

    @Override
    public EsqAccessProfile esquireKey(String id, String rootPath, String uid) {

        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();

        log.debug("srvc: esquireKey: id:{}, rootPath:{}, uid:{}",  id, rootPath, uid);

        String upk = id == null ? uid : id;

        EsqAccessProfileJpa jpa = accessProfileRepository.access(upk, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireKey", "id", upk);
        }
        List<EsqRoleJpa> roles = accessProfileRepository.roles(upk);
        List<EsqPermissionJpa> permissions = accessProfileRepository.permissions(upk);

        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, roles, permissions);
        log.debug("srvc: esquireKey(2): accessProfile:{}",  ret);
        return  ret;
    }

    @Override
    public EsqAccessProfile esquireKeySave(String id, Map<String, Object> fields, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireKeySave: id:{}, rootPath:{}, uid:{}", id, rootPath, uid);

        String upk = id == null ? uid : id;

        EsqAccessProfileJpa[] updated = {null};
        List<EsqRoleJpa>[] roles = new List[]{null};
        List<EsqPermissionJpa>[] permissions = new List[]{null};

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveAccess(upk, fields, rootPath, uid, correlationId, requestId, updated, roles, permissions);
            return null;
        }); // ← transaction commits here

        EsqAccessProfile ret = new EsqAccessProfile().fill(updated[0], roles[0], permissions[0]);
        log.debug("srvc: esquireKeySave(2): accessProfile:{}", ret);
        return ret;
    }

    private void saveAccess(String id, Map<String, Object> fields, String rootPath,
                            String uid, String correlationId, String requestId,
                            EsqAccessProfileJpa[] updated,
                            List<EsqRoleJpa>[] roles, List<EsqPermissionJpa>[] permissions) {
        EsqAccessProfileJpa jpa = accessProfileRepository.accessForUpdate(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("saveAccess", "id", id);
        }
        if (applyFields(jpa, fields, ACCESS_WRITABLE)) {
            accessProfileRepository.updateAccess(id, jpa.getEmail(), jpa.getLoginId(), jpa.getPwdChangeForced(), jpa.getTfaMethod(), uid, correlationId, requestId);
        }
        //note: if a DB trigger or default value modifies the row, saveAccess won't reflect it.
        updated[0]     = jpa;
        roles[0]       = accessProfileRepository.roles(id);
        permissions[0] = accessProfileRepository.permissions(id);
    }

    private static final Set<String> ACCESS_WRITABLE = Set.of("email", "loginId", "pwdChangeForced", "tfaMethod");

    private boolean applyFields(Object jpa, Map<String, Object> fields, Set<String> writables) {
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        boolean changed = false;
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            if (writables.contains(pd.getName()) && fields.containsKey(pd.getName())) {
                changed = true;
                Object newValue = fields.get(pd.getName());
                if (newValue instanceof String && ((String) newValue).isBlank()) {
                    newValue = null;
                }
                wrapper.setPropertyValue(pd.getName(), newValue);
            }
        }
        return changed;
    }

}

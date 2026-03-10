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
 * 03/03/2026 mir0n  saveAccess(): roles list change detection added
 *                   deleting roles/ adding roles inserted
 * 03/06/2026 mir0n  ACCESS_WRITABLE removed; applyFields() dict-driven via ValidatorFactory
 *                   roles field validated via BizValidatorFactory
 * 03/08/2026 mir0n  personal = upk.equals(uid); passed to saveAccess() and applyFields()
 * 03/09/2026 mir0n  roles var renamed to rolesAssigned; roles param added to saveAccess()
 *                   isAdminCmdPermitted(AUTH) permission check; PermissionDeniedException thrown
 * 03/10/2026 mir0n  import: RequestContextUtils updated to backend.service package
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
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.access.*;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.jpa.access.*;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.keySmith.jpa.EsqAccessProfileRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
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
        List<EsqRoleJpa> rolesAll = accessProfileRepository.rolesAll(upk);
        List<EsqPermissionJpa> permissions = accessProfileRepository.permissions(upk);

        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, roles, rolesAll, permissions);
        log.debug("srvc: esquireKey(2): accessProfile:{}",  ret);
        return  ret;
    }

    @Override
    public EsqAccessProfile esquireKeySave(String id, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireKeySave: id:{}, rootPath:{}, uid:{}", id, rootPath, uid);

        String upk = id == null ? uid : id;
        boolean personal = upk.equals(uid);

        EsqAccessProfileJpa[] updated = {null};
        List<EsqRoleJpa>[] rolesAssigned = new List[]{null};
        List<EsqRoleJpa>[] rolesAll = new List[]{null};

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveAccess(upk, fields, rootPath, uid, correlationId, requestId, personal, updated, rolesAssigned, rolesAll, roles);
            return null;
        }); // ← transaction commits here

        List<EsqPermissionJpa> permissions = accessProfileRepository.permissions(upk);
        EsqAccessProfile ret = new EsqAccessProfile().fill(updated[0], rolesAssigned[0], rolesAll[0], permissions);
        log.debug("srvc: esquireKeySave(2): accessProfile:{}", ret);
        return ret;
    }

    private void saveAccess(String id, Map<String, Object> fields, String rootPath,
            String uid, String correlationId, String requestId,
            boolean personal,
            EsqAccessProfileJpa[] updated,
            List<EsqRoleJpa>[] rolesAssigned,
            List<EsqRoleJpa>[] rolesAll,
            List<String> roles) {
        EsqAccessProfileJpa jpa = accessProfileRepository.accessForUpdate(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("saveAccess", "id", id);
        }
        Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
        boolean permitted = false;
        if (id != null && id.equals(uid)) {
            permitted = true;
        } else if (permissions != null) {
            permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                permissions.get(jpa.getKind()),
                EsqRolesStorage.AdminCmd.AUTH
            );
        }
        if (!permitted) {
            throw new PermissionDeniedException("Access Profile", "modify");
        }
        if (applyFields(jpa, personal, fields)) {
            accessProfileRepository.updateAccess(id, jpa.getEmail(), jpa.getLoginId(), jpa.getPwdChangeForced(), jpa.getTfaMethod(), uid, correlationId, requestId);
        }
        List<EsqRoleJpa> originRoles = accessProfileRepository.roles(id);
        Set<String> originIds = new HashSet<>();
        for (EsqRoleJpa r : originRoles) {
            originIds.add(r.getId());
        }

        if (fields.containsKey(IKeySmithService.FIELD_ROLES)) {
            EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(EsqConstants.KIND_ACCESS_PROFILE);
            EsqEntityKindFieldLayer kfl = dict.fillКindFieldLayer(IKeySmithService.FIELD_ROLES, null);
            List<?> value = (List<?>) fields.get(IKeySmithService.FIELD_ROLES);
            log.debug("keySmith:saveAccess: roles:{}", value);
            ValidatorFactory.getInstance().validate(jpa, kfl, personal, value);

            List<EsqRoleJpa> givenRoles = new ArrayList<>();
            Set<String> givenIds = new HashSet<>();
            for (Object r : value) {
                if (r instanceof Map) {
                    EsqRoleJpa jpaRole = new EsqRoleJpa();
                    Object roleId = ((Map<?, ?>) r).get("id");
                    Object kind = ((Map<?, ?>) r).get("kind");
                    Object name = ((Map<?, ?>) r).get("name");
                    jpaRole.setId(String.valueOf(roleId));
                    jpaRole.setKind(Integer.parseInt(String.valueOf(kind)));
                    jpaRole.setName(String.valueOf(name));
                    givenRoles.add(jpaRole);
                    givenIds.add(jpaRole.getId());
                }
            }
            for (String rid : originIds) {
                if (!givenIds.contains(rid)) {
                    accessProfileRepository.deleteUserRole(id, rid);
                }
            }
            for (String rid : givenIds) {
                if (!originIds.contains(rid)) {
                    accessProfileRepository.insertUserRole(id, rid);
                }
            }
            rolesAssigned[0] = givenRoles;
        } else {
            rolesAssigned[0] = originRoles;
        }
        //note: if a DB trigger or default value modifies the row, saveAccess won't reflect it.
        updated[0]     = jpa;
        rolesAll[0]    = accessProfileRepository.rolesAll(id);
    }

    private boolean applyFields(EsqAccessProfileJpa jpa, boolean personal, Map<String, Object> fields) {
        if (jpa == null || fields == null) {
            return false;
        }
        EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(EsqConstants.KIND_ACCESS_PROFILE);
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        boolean changed = false;
        EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            String name = pd.getName();
            if (fields.containsKey(name)) {
//log.debug("keySmith:applyFields: name:{}", name);
                Object value = fields.get(name);
//log.debug("keySmith:applyFields: value:{}", value);
                kfl = dict.fillКindFieldLayer(name, kfl) ;
//log.debug("keySmith:applyFields: kfl:{} {} {} ", kfl.getEntityKind(), kfl.getLayer(), kfl.getField());
                EsqEntityField field = kfl.getField();
                if (field != null) {
                    if (field.getReadwrite() != null && (field.getReadwrite() & 2) == 2) {
                        value = ValidatorFactory.getInstance().validate(jpa, kfl, personal, value);
//log.debug("keySmith:validated: value:{}", value);
                        wrapper.setPropertyValue(name, value);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

}


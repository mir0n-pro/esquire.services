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
 * 03/10/2026 mir0n  fillКindFieldLayer() calls updated to fillKindFieldLayer() — Cyrillic К → ASCII K
 *                   @Primary added; rolesAll and permissions loaded from EsqRolesStorage (no JPA call)
 *                   saveAccess(): rolesAll[] param removed; rolesAll set from Storage inside
 *                   esquireKey/esquireKeySave: fillPermissionsForRole() loop over assigned roles
 * 03/16/2026 mir0n  IKeycloakIdentityService injected
 *                   esquireKey(): confirmPendingFlags() on login handshake (id=null)
 *                     pwdChangeForced Y→N; tfaMethod g/n→G/N on confirm
 *                   esquireKeySave(): oldLoginId[], oldConnectFlg[] captured; syncToKeycloak() added
 *                   saveAccess(): connectFlg change detection; TOTP reset to N on connect N→Y
 *                   syncToKeycloak(): three-branch — delete(Y→N) / create(N→Y) / update(else)
 *                   applyFields(): tfaMethod state machine — G/N only; pending via lowercase g/n
 * 03/20/2026 mir0n  KC sync decoupled: IKeycloakIdentityService replaced with KcSyncPublisher
 *                   syncToKeycloak() replaced with kcSyncPublisher.publish() — fire-and-forget via JMS
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 04/16/2026 mir0n  ret declarations moved to top in esquireKeyDetail() and esquireKeySave()
 */

package pro.mir0n.esquire.keySmith.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
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
import pro.mir0n.esquire.keySmith.messaging.KcSyncPublisher;
import pro.mir0n.esquire.keySmith.service.IKeySmithService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Primary  //xxx: letting this IKeySmithService implementation be the primary one.
@Service
@AllArgsConstructor
public class KeySmithService implements IKeySmithService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KeySmithService.class.getName());

    private EsqAccessProfileRepository accessProfileRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;
    private KcSyncPublisher kcSyncPublisher;

    @Override
    public EsqAccessProfile esquireKey(String id, String rootPath, String uid) {
        EsqAccessProfile ret = null;
        //String correlationId = RequestContextUtils.getCorrelationId();
        //String requestId = RequestContextUtils.getRequestId();

        devLog.debug("KeySmithService: esquireKey: id:{}, rootPath:{}, uid:{}",  id, rootPath, uid);

        String upk = id == null ? uid : id;

        EsqAccessProfileJpa jpa = accessProfileRepository.access(upk, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireKey", "id", upk);
        }
        if (id == null) {
            String newPwdForced = "Y".equals(jpa.getPwdChangeForced()) ? "N" : null;
            String tfa = jpa.getTfaMethod();
            String newTfa = ("g".equals(tfa) || "n".equals(tfa)) ? tfa.toUpperCase() : null;
            if (newPwdForced != null || newTfa != null) {
                accessProfileRepository.confirmPendingFlags(upk, newPwdForced, newTfa);
                if (newPwdForced != null) jpa.setPwdChangeForced(newPwdForced);
                if (newTfa != null) jpa.setTfaMethod(newTfa);
            }
        }
        List<EsqRoleJpa> roles = accessProfileRepository.roles(upk);
        List<EsqRole> rolesAll = EsqRolesStorage.getInstance().roles();
        List<EsqPermission> permissions = null;
        for (EsqRoleJpa r : roles) {
            permissions = EsqRolesStorage.getInstance().fillPermissionsForRole(r.getName(), permissions);
        }

        ret = new EsqAccessProfile().fill(jpa, roles, rolesAll, permissions);
        devLog.debug("KeySmithService: esquireKey(2): accessProfile:{}",  ret);
        return  ret;
    }

    @Override
    public EsqAccessProfile esquireKeySave(String id, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        EsqAccessProfile ret = null;
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        devLog.debug("KeySmithService: esquireKeySave: id:{}, rootPath:{}, uid:{}", id, rootPath, uid);

        String upk = id == null ? uid : id;
        boolean personal = upk.equals(uid);

        EsqAccessProfileJpa[] updated = {null};
        List<EsqRoleJpa>[] rolesAssigned = new List[]{null};
        String[] oldLoginId = {null};
        String[] oldConnectFlg = {null};
        //xxx: we cannot validate permission yet: we need to get a kind of the user where changes applying
        //     we need to read that first: we do it by first thing within the transction.
        //     so let's move the permission validation inside
        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveAccess(upk, fields, rootPath, uid, correlationId, requestId, personal, updated, rolesAssigned, roles, oldLoginId, oldConnectFlg);
            return null;
        }); // ← transaction commits here

        kcSyncPublisher.publish(oldLoginId[0], oldConnectFlg[0], updated[0], rolesAssigned[0], correlationId, requestId);

        List<EsqRole> rolesAll = EsqRolesStorage.getInstance().roles();
        List<EsqPermission> permissions = null;
        for (EsqRoleJpa r : rolesAssigned[0]) {
            permissions = EsqRolesStorage.getInstance().fillPermissionsForRole(r.getName(), permissions);
        }
        ret = new EsqAccessProfile().fill( updated[0], rolesAssigned[0], rolesAll, permissions);
        devLog.debug("KeySmithService: esquireKeySave(2): accessProfile:{}", ret);
        return ret;
    }

    private void saveAccess(String id, Map<String, Object> fields, String rootPath,
            String uid, String correlationId, String requestId,
            boolean personal,
            EsqAccessProfileJpa[] updated,
            List<EsqRoleJpa>[] rolesAssigned,
            List<String> roles,
            String[] oldLoginId,
            String[] oldConnectFlg) {
        EsqAccessProfileJpa jpa = accessProfileRepository.accessForUpdate(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("saveAccess", "id", id);
        }
        oldLoginId[0]   = jpa.getLoginId();
        oldConnectFlg[0] = jpa.getConnectFlg();
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
        boolean changed = applyFields(jpa, personal, fields);
        if ("N".equals(oldConnectFlg[0]) && "Y".equals(jpa.getConnectFlg()) && !"N".equals(jpa.getTfaMethod())) {
            jpa.setTfaMethod("N");
            changed = true;
        }
        if (changed) {
            accessProfileRepository.updateAccess(id, jpa.getEmail(), jpa.getLoginId(), jpa.getPwdChangeForced(), jpa.getTfaMethod(), jpa.getConnectFlg(), uid, correlationId, requestId);
        }
        List<EsqRoleJpa> originRoles = accessProfileRepository.roles(id);
        Set<String> originIds = new HashSet<>();
        for (EsqRoleJpa r : originRoles) {
            originIds.add(r.getId());
        }

        if (fields.containsKey(IKeySmithService.FIELD_ROLES)) {
            EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(EsqConstants.KIND_ACCESS_PROFILE);
            EsqEntityKindFieldLayer kfl = dict.fillKindFieldLayer(IKeySmithService.FIELD_ROLES, null);
            List<?> value = (List<?>) fields.get(IKeySmithService.FIELD_ROLES);
            devLog.debug("keySmith:saveAccess: roles:{}", value);
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
//devLog.debug("keySmith:applyFields: name:{}", name);
                Object value = fields.get(name);
//devLog.debug("keySmith:applyFields: value:{}", value);
                kfl = dict.fillKindFieldLayer(name, kfl) ;
//devLog.debug("keySmith:applyFields: kfl:{} {} {} ", kfl.getEntityKind(), kfl.getLayer(), kfl.getField());
                EsqEntityField field = kfl.getField();
                if (field != null) {
                    if (field.getReadwrite() != null && (field.getReadwrite() & 2) == 2) {
                        value = ValidatorFactory.getInstance().validate(jpa, kfl, personal, value);
//devLog.debug("keySmith:validated: value:{}", value);
                        boolean apply = true;
                        if ("tfaMethod".equals(name)) {
                            String v = value != null ? String.valueOf(value).toUpperCase() : "";
                            if (!"N".equals(v) && !"G".equals(v)) {
                                apply = false;
                            } else {
                                String currentUpper = jpa.getTfaMethod() != null ? jpa.getTfaMethod().toUpperCase() : "N";
                                if (v.equals(currentUpper)) {
                                    apply = false;
                                } else {
                                    value = v.toLowerCase();  // G->g, N->n (pending)
                                }
                            }
                        }
                        if (apply) {
                            wrapper.setPropertyValue(name, value);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

}


/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
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
 * 06/02/2026 mir0n  esquireKeySave(): KEYSMITH_TEST_CONNECT_HOLD_MS test hook (race-8c repro) --
 *                   optional Thread.sleep between the committed path read and the activation URQ
 *                   publish; default 0 = disabled, never set in production
 * 06/04/2026 mir0n  esquireKey / esquireKeySave read rootPath / uid via RequestContextUtils instead of
 *                   params; dropped from the IKeySmithService signatures (passed to saveAccess)
 * 06/05/2026 mir0n  XYRod injected; auth UPDATE posts an x-Rod esq_auth_log audit event (managed non-secret
 *                   fields only; security question / answer excluded)
 * 06/15/2026 mir0n  audit field retyped XYRod -> IXRod (messaging.xrod); the auth UPDATE post() now passes an
 *                   explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT) as the trailing argument.
 * 06/17/2026 mir0n  audit field IXRod -> AuditBusBridge; the auth UPDATE post() drops the trailing MSG_TYPE_AUDIT arg
 * 06/18/2026 mir0n  audit module left common: AuditBusBridge moved to pro.mir0n.esquire.audit
 * 06/22/2026 mir0n  KcSyncPublisher field/import -> KcBusAdapter (the merged kc-CLIENT adapter); RodEvent import
 *                   repointed messaging.xrod.RodEvent -> messaging.RodEvent
 * 07/02/2026 mir0n  esquireKeySave reads requestId via requireRequestId() -- X-Request-ID mandatory on writes
 * 07/08/2026 mir0n  @EsqTraced on esquireKey / esquireKeySave (esq.svc.key.read / esq.svc.key.save)
 * 08/11/2026 mir0n  v1.2.12 -- saveAccessProfile raises the auth row's change number and passes it to
 *                   updateAccess; the audit copy is stamped with the raised value
 * 08/12/2026 mir0n  v1.2.13 -- field KcBusAdapter -> IIdentityGateway; identityCommand() picks C/U/D from the connect flag
 *                   and identityEvent() builds the AuthSyncRequest + RodEvent posted to it
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
import java.util.UUID;

import pro.mir0n.esquire.backend.o11y.EsqTraced;
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
import pro.mir0n.esquire.backend.identity.IIdentityGateway;
import pro.mir0n.esquire.backend.identity.AuthSyncRequest;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.keySmith.jpa.EsqAccessProfileRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
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
    private IIdentityGateway identityGateway;
    private AuditBusBridge audit;

    @Override
    @EsqTraced(name = "esq.svc.key.read", label = "read access profile")
    public EsqAccessProfile esquireKey(String id) {
        EsqAccessProfile ret = null;
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();

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
    @EsqTraced(name = "esq.svc.key.save", label = "save access profile")
    public EsqAccessProfile esquireKeySave(String id, Map<String, Object> fields, List<String> roles) {
        EsqAccessProfile ret = null;
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.requireRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
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

        // TEST HOOK (race-8c reproduction): hold between capturing the entity path (read in the
        // committed transaction above, now frozen in updated[0].getPath()) and publishing the
        // activation URQ. A concurrent enyMan move can update ep_path during this window; keySmith
        // then sends the now-STALE captured path, while the move's EVENT_UPDATE_PATH is skipped by
        // kcMaster (the KC user does not exist until this URQ lands). Default 0 = disabled; set
        // KEYSMITH_TEST_CONNECT_HOLD_MS only in repro tests, never in production.
        long testHoldMs = 0L;
        try { testHoldMs = Long.parseLong(System.getenv().getOrDefault("KEYSMITH_TEST_CONNECT_HOLD_MS", "0")); }
        catch (NumberFormatException ignore) { /* keep 0 */ }
        if (testHoldMs > 0L) {
            devLog.debug("KeySmithService: esquireKeySave: TEST hold {}ms before URQ publish (race-8c repro); path={}",
                    testHoldMs, updated[0] != null ? updated[0].getPath() : null);
            try { Thread.sleep(testHoldMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }

        String command = identityCommand(oldConnectFlg[0], updated[0]);
        identityGateway.postRequest(identityEvent(command, oldLoginId[0], updated[0], rolesAssigned[0],
                correlationId, requestId));

        List<EsqRole> rolesAll = EsqRolesStorage.getInstance().roles();
        List<EsqPermission> permissions = null;
        for (EsqRoleJpa r : rolesAssigned[0]) {
            permissions = EsqRolesStorage.getInstance().fillPermissionsForRole(r.getName(), permissions);
        }
        ret = new EsqAccessProfile().fill( updated[0], rolesAssigned[0], rolesAll, permissions);
        devLog.debug("KeySmithService: esquireKeySave(2): accessProfile:{}", ret);
        return ret;
    }

    /**
     * What the identity provider is being asked to do, read from the connect flag either side of the save:
     * the flag turning on means the identity starts existing, turning off means it stops, and anything else
     * is a change to an identity that already exists.
     */
    private String identityCommand(String oldConnectFlg, EsqAccessProfileJpa jpa) {
        String ret;
        if ("Y".equals(oldConnectFlg) && "N".equals(jpa.getConnectFlg())) {
            ret = BusConstants.EVENT_DELETE;
        } else if ("N".equals(oldConnectFlg) && "Y".equals(jpa.getConnectFlg())) {
            ret = BusConstants.EVENT_CREATE;
        } else {
            ret = BusConstants.EVENT_UPDATE;
        }
        return ret;
    }

    /**
     * The saved access profile as one identity command. Each command fills only the fields it is about: a
     * delete needs the login id to remove, a create carries the whole profile including the path the new
     * identity starts at, and an update carries what may have changed. On a create the provider knows nothing
     * yet, so the login id is the saved one; otherwise it is the login id the provider still knows, and a
     * rename travels as the new one beside it.
     */
    private RodEvent identityEvent(String command, String oldLoginId, EsqAccessProfileJpa jpa,
                                   List<EsqRoleJpa> roles, String correlationId, String requestId) {
        String entityId = String.valueOf(jpa.getId());
        AuthSyncRequest req = new AuthSyncRequest();
        req.setId(entityId);
        req.setKind(EsqConstants.KIND_ACCESS_PROFILE);

        if (BusConstants.EVENT_DELETE.equals(command)) {
            req.setLoginId(oldLoginId);
        } else {
            boolean created = BusConstants.EVENT_CREATE.equals(command);
            req.setLoginId(created ? jpa.getLoginId() : oldLoginId);
            if (!created && jpa.getLoginId() != null && !jpa.getLoginId().equals(oldLoginId)) {
                req.setNewLoginId(jpa.getLoginId());
            }
            req.setEmail(jpa.getEmail());
            req.setPwdChangeForced(jpa.getPwdChangeForced());
            req.setTfaMethod(jpa.getTfaMethod());
            req.setConnectFlg(jpa.getConnectFlg());
            if (created) {
                req.setPath(jpa.getPath());
            }
            List<String> roleNames = new ArrayList<>();
            if (roles != null) {
                for (EsqRoleJpa r : roles) {
                    roleNames.add(r.getName());
                }
            }
            req.setRoles(roleNames);
        }

        // guarantee a non-null tracking id (the former testReqId; it rides as the requestId on the wire).
        String reqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
        // null change number: a KeyCloak request leg reports none.
        return new RodEvent(RodEvent.opFromCode(command), EsqConstants.KIND_ACCESS_PROFILE, entityId, null,
                null, System.currentTimeMillis(), correlationId, reqId, null, null,
                BusConstants.MSG_TYPE_REQUEST, req.toMap());
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
            accessProfileRepository.updateAccess(id, jpa.getEmail(), jpa.getLoginId(), jpa.getPwdChangeForced(),
                    jpa.getTfaMethod(), jpa.getConnectFlg(), jpa.bumpChangeNo(), uid, correlationId, requestId);
            // audit: auth UPDATE -> esq_auth_log (entityId = au_usr_pk, kind = KIND_ACCESS_PROFILE). Carries
            // only the managed, non-secret fields; security question / answer are never logged.
            // This is a COPY built for the audit event, not the row that was read -- so the change number has
            // to be carried over by hand. The UPDATE above already raised it on jpa.
            EsqAuthJpa auth = new EsqAuthJpa();
            auth.setLoginId(jpa.getLoginId());
            auth.setEmail(jpa.getEmail());
            auth.setConnectFlg(jpa.getConnectFlg());
            auth.setTfaMethod(jpa.getTfaMethod());
            auth.setForceChangeFlg(jpa.getPwdChangeForced());
            auth.setChangeNo(jpa.getChangeNo());
            audit.post(RodEvent.Op.UPDATE, EsqConstants.KIND_ACCESS_PROFILE, id, null, auth);
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


/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: usr command/save service (split from EnyManService)
 *                   esquireCommand(), esquireCommandSave(), saveUsr() moved from EnyManService
 *                   person/address/bizaddr subentity read and update support added
 * 03/01/2026 mir0n  prsn.getDob() uncommented — DOB field now active in updatePerson()
 * 03/03/2026 mir0n  updatePerson/updateAddress/updateAddress2: use user id instead of sub-entity id
 * 03/06/2026 mir0n  USR_WRITABLE reduced to {name}; dict-driven subLayer for person/address
 *                   ValidatorFactory used for custom field validation
 * 03/08/2026 mir0n  personal = id.equals(uid); self-update context passed to all applyFields/validate calls
 * 03/09/2026 mir0n  esquireCommandSave(): roles param added
 * 03/10/2026 mir0n  import: RequestContextUtils updated to backend.service package
 * 03/10/2026 mir0n  fillКindFieldLayer() call updated to fillKindFieldLayer() — Cyrillic К → ASCII K
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 03/26/2026 mir0n  createUsr() with EmailExistsException on duplicate email;
 *                   deleteUsr() + deleteAuth() (auth deleted before usr, FK constraint);
 *                   esquireCommandNew(), esquireCommandDelete() added
 * 03/28/2026 mir0n  deleteUsr(): connectFlg="Y" → DeleteRestrictedException before delete;
 *                   sequence: deletePersonAddresses → deletePersonBankInfo → deleteUsr
 * 03/28/2026 mir0n  createUsr(): injectDefaults before each applyFields (person/usr/address/address2);
 *                   removed hardcoded deleted="N" default; custom field loop restricted to request fields
 * 03/28/2026 mir0n  createUsr(): insertUsrPath before insertUsr; deleteUsr(): deleteEntityPath after deleteUsr
 * 03/31/2026 mir0n  esquireCommandMove() + moveUsr(): mass path update for user+accounts (equality),
 *                   skip-if-same-parent; insertUsrPath: kind param added (ep_et_pk)
 * 04/01/2026 mir0n  move: collects updated records
 * 04/06/2026 mir0n  moveUsr(): admin/regular branch split — admin uses pk-based moveAdminPath (no ACCT cascade);
 *                   regular uses equality moveUsrPaths (covers user row + all ACCT rows)
 *                   createUsr(): admin ep_path = parent org path only (no own PK appended)
 * 04/07/2026 mir0n  all kind params Integer → int; moveUsr/createUsr: get kind directly without normalization
 * 04/09/2026 mir0n  applyFields() and enforceDefaults() delegated to EntityFieldUtils
 * 04/16/2026 mir0n  ret declarations moved to top; moveUsr(): null-guard replaces early return
 * 06/01/2026 mir0n  id minting call retargeted: EsqUtils.generateEntityId() -> EntityIdGenerator.generateEntityId()
 *                   (id minter moved from common to enyMan in v1.2.6).
 * 06/04/2026 mir0n  rootPath / uid read via RequestContextUtils instead of method params (dropped from
 *                   the IEnyManService public signatures)
 * 06/05/2026 mir0n  XYRod injected; x-Rod audit posts across the user footprint (user / person / address /
 *                   usr_par create / update / delete + move parent-ref); delete enumerates child pks before
 *                   the cascade; per-param events via listUsrPar (enabled-gated); create/save resolve the
 *                   dictionary via completedDictionary (custom-param save fix)
 * 06/12/2026 mir0n  createUsr(): usr deleted defaults to 'N' when null (NOT NULL system field, no dictionary default)
 * 06/15/2026 mir0n  audit dep XYRod -> IXRod (import common.xrod -> messaging.xrod); every user / person /
 *                   address / usr_par post() passes an explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT).
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.EsqCustomEntityFieldJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAddressJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqPersonJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.service.EntityFieldUtils;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.DeleteRestrictedException;
import pro.mir0n.esquire.backend.error.EmailExistsException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import pro.mir0n.esquire.backend.jpa.entity.EsqParRow;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.enyMan.service.EntityIdGenerator;

@Slf4j
public class UsrService  extends AEnyManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + UsrService.class.getName());

    private final EsqEntityDictionaryRepository entityDictionaryRepository;
    private final EsqUsrRepository usrRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager em;
    private final IXRod xyRod;

    public UsrService(EsqEntityDictionaryRepository entityDictionaryRepository,
                      EsqUsrRepository usrRepository,
                      TransactionTemplate transactionTemplate,
                      EntityManager em,
                      IXRod xyRod) {
        super(entityDictionaryRepository);
        this.entityDictionaryRepository = entityDictionaryRepository;
        this.usrRepository = usrRepository;
        this.transactionTemplate = transactionTemplate;
        this.em = em;
        this.xyRod = xyRod;
    }


    @Override
    public List<EsqEntityLayer> esquireDictionary(int kind) {
        //xxx: not in use
        return null;
    }

    @Override
    public EsqEntity esquireCommand(int kind, String id, String cmd) {
        EsqEntity ret = null;
        String rootPath = RequestContextUtils.getRootPath();
        devLog.debug("srvc: esquireCommand(usr): kind:{}, id:{}, cmd:{}, rootPath:{}",  kind, id, cmd, rootPath);

        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        List<EsqEntityJpa> children = null;
        // xxx: path is safe
        List<String> path = EsqTreeNodeMapper.pathArray(rootPath);
        EsqEntityJpa jpa = usrRepository.detailUsr(id, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }

        List<EsqNameValueJpa> custom = usrRepository.customUsr(id);
        EsqEntityJpa person = usrRepository.person(id, EsqConstants.KIND_PERSON_PRIMARY);
        EsqEntityJpa address = null;
        EsqEntityJpa address2 = null;
        if (eek.isAddress()) {
            address = usrRepository.address(id, EsqConstants.KIND_PERSON_PRIMARY);
            address2 = usrRepository.address2(id, EsqConstants.KIND_PERSON_PRIMARY);
            //xxx: JPA thing:
            //     to make repository returns both address records, records must have unique id,
            // following JPA does not allow
            //if (address != null) {
            //    address.setId(id);
            //}
            //if (address2 != null) {
            //    address2.setId(id);
            //}
        }
        if (eek.isChildrenDetailed()) {
            children = (List<EsqEntityJpa>) (List<?>) usrRepository.userAccts(id, rootPath);
        }
        ret = EsqEntityFactory.getInstance().createUser(jpa, custom, children, person, address, address2 );
        devLog.debug("srvc: esquireCommand(usr): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, List<String> roles) {
        EsqEntity ret = null;
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
        devLog.debug("srvc: esquireCommandSave(usr): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);

        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        //xxx: tricks with pointers
        EsqEntityJpa[] updated  = {null};
        List<EsqNameValueJpa>[] custom   = new List[]{null};
        List<EsqEntityJpa>[] children = eek.isChildrenDetailed() ? new List[]{null} : null;
        EsqEntityJpa[] person = {null};
        EsqEntityJpa[] address = eek.isAddress() ? new EsqEntityJpa[]{null} : null;
        EsqEntityJpa[] address2 = eek.isAddress() ? new EsqEntityJpa[]{null} : null;

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveUsr(id, fields, rootPath, uid, correlationId, requestId, updated, custom, children, person, address, address2);
            return null;
        }); // ← transaction commits here

        ret = EsqEntityFactory.getInstance().createUser(updated[0], custom[0], children == null ? null : children[0],
                    person == null ? null : person[0], address == null ? null : address[0], address2 == null ? null : address2[0]);
        devLog.debug("srvc: esquireCommandSave(usr): entity:{}", ret);
        return ret;
    }

    @Override
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, List<String> roles) {
        EsqEntity ret = null;
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
        devLog.debug("srvc: esquireCommandNew(usr): kind:{}, parentId:{}, cmd:{}, rootPath:{}, uid:{}", kind, parentId, cmd, rootPath, uid);

        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        EsqEntityJpa[] created = {null};
        List<EsqNameValueJpa>[] custom = new List[]{null};
        EsqEntityJpa[] person = {null};
        EsqEntityJpa[] address = eek.isAddress() ? new EsqEntityJpa[]{null} : null;
        EsqEntityJpa[] address2 = eek.isAddress() ? new EsqEntityJpa[]{null} : null;

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            createUsr(kind, parentId, fields, rootPath, uid, correlationId, requestId, created, custom, person, address, address2);
            return null;
        });

        ret = EsqEntityFactory.getInstance().createUser(created[0], custom[0], null, person[0],
                address == null ? null : address[0], address2 == null ? null : address2[0]);
        devLog.debug("srvc: esquireCommandNew(usr): entity:{}", ret);
        return ret;
    }

    @Override
    public void esquireCommandDelete(int kind, String id, String cmd, List<String> roles) {
        String rootPath = RequestContextUtils.getRootPath();
        devLog.debug("srvc: esquireCommandDelete(usr): kind:{}, id:{}, cmd:{}, rootPath:{}", kind, id, cmd, rootPath);
        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            deleteUsr(id, rootPath);
            // x-Rod audit: one DELETE event for the user (id + kind); cascaded person/address/params
            // are implied by the owner delete (no per-child delete events).
            xyRod.post(RodEvent.Op.DELETE, kind, id, null, EsqMsgConstants.MSG_TYPE_AUDIT);
            return null;
        });
    }

    @Override
    public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
        devLog.debug("srvc: esquireCommandMove(usr): kind:{}, id:{}, distId:{}, rootPath:{}, uid:{}", kind, id, distId, rootPath, uid);
        List<EsqMoveRecord> records = transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            return moveUsr(id, distId, rootPath, uid, correlationId, requestId);
        });
        return records != null ? records : List.of();
    }

    private List<EsqMoveRecord> moveUsr(String id, String distId, String rootPath, String uid, String correlationId, String requestId) {
        List<EsqMoveRecord> rows = null;
        usrRepository.lockEntityPathRoot();
        EsqUsrJpa usr = usrRepository.detailUsrForUpdate(id, rootPath);
        if (usr == null) {
            throw new ResourceNotFoundException("moveUsr", "id", id);
        }
        if (distId.equals(usr.getParentId())) {
            rows = List.of();
        } else {
            EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(usr.getKind());
            String destPath    = usrRepository.usrPath(distId, rootPath);
            if (eek.isPathParentOnly()) {
                // Admin users own no entities — ep_path has only one row (no ACCT cascade).
                // Multiple admins under the same org share the same ep_path value, so update and query by pk.
                usrRepository.moveAdminPath(id, destPath);
                rows = usrRepository.listAdminMovedPath(id);
            } else {
                // Regular user: ep_path = dest org path + user PK.
                // Equality update covers the user row and all their ACCT rows (same ep_path value).
                String currentPath = usrRepository.usrPath(id, rootPath);
                String newPath     = destPath + id + ".";
                usrRepository.moveUsrPaths(currentPath, newPath);
                rows = usrRepository.listMovedPaths(newPath);
            }
            usrRepository.moveUsrParent(id, distId, uid, correlationId, requestId);
            // x-Rod audit: move is one parent-ref UPDATE (usr_org_pk); path rewrites are not audited.
            usr.setParentId(distId);
            xyRod.post(RodEvent.Op.UPDATE, usr.getKind(), usr.getId(), null, usr, EsqMsgConstants.MSG_TYPE_AUDIT);
        }
        return rows;
    }

    private void createUsr(int kind, String parentId, Map<String, Object> fields,
                            String rootPath, String uid, String correlationId, String requestId,
                            EsqEntityJpa[] created, List<EsqNameValueJpa>[] custom,
                            EsqEntityJpa[] person, EsqEntityJpa[] address, EsqEntityJpa[] address2) {
        // Treat empty string as null
        if (parentId != null && parentId.isEmpty()) {
            parentId = null;
        }
        String parentPath = usrRepository.usrPath(parentId, rootPath);
        if (parentPath == null) {
            throw new ResourceNotFoundException("createUsr", "parentId", parentId);
        }
        long newId = EntityIdGenerator.generateEntityId();
        String idStr = String.valueOf(newId);
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        String path = eek.isPathParentOnly() ? parentPath : parentPath + newId + ".";
        fields.put("path", path);

        EsqEntityDictionary dictUser = completedDictionary(kind);
        EsqEntityKindFieldLayer kfl  = new EsqEntityKindFieldLayer();

        // Validate person subentity — derive name and email
        EsqPersonJpa prsn = new EsqPersonJpa();
        prsn.setKind(EsqConstants.KIND_PERSON_PRIMARY);
        Map<String, Object> mprsn = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_PERSON);
        kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_PERSON, kfl);
        EsqEntityLayer prsnLayer = dictUser.findLayer(kfl.getLayer());
        if (prsnLayer != null) prsnLayer.injectDefaults(mprsn);
        EntityFieldUtils.applyFields(prsn, mprsn, false, kfl.getLayer(), null);
        String name    = prsn.getName();
        String email   = prsn.getEmail();
        String loginId = email;

        // Email uniqueness check — must be first write guard in the transaction
        if (usrRepository.countByEmail(email) > 0) {
            throw new EmailExistsException(email);
        }

        fields.put("name", name);

        // Validate user-level fields (desc) — mirrors saveUsr applyFields(usr, fields, ...)
        EsqUsrJpa usr = new EsqUsrJpa();
        usr.setId(idStr);
        usr.setKind(kind);
        EsqEntityLayer usrLayer = dictUser.findLayer(1);
        if (usrLayer != null) usrLayer.injectDefaults(fields);
        EntityFieldUtils.applyFields(usr, fields, false, 0, USR_WRITABLE);
        if (usrLayer != null) EntityFieldUtils.enforceDefaults(usrLayer, usr);

        // usr_deleted_flg is a NOT NULL system field (not a dictionary param, so enforceDefaults
        // cannot supply it); a new user is never created already-deleted -> guarantee 'N'.
        if (usr.getDeleted() == null) usr.setDeleted("N");

        // Ensure registration and deleted flags are in fields for broadcast
        if (usr.getRegistration() != null) fields.put("registration", usr.getRegistration());
        fields.put("deleted", usr.getDeleted());

        // Insert main rows
        usrRepository.insertUsrPath(newId, kind, path);
        usrRepository.insertUsr(newId, kind, usr.getName(), usr.getDesc(), parentId, usr.getRegistration(), usr.getDeleted(), uid, correlationId, requestId);
        usrRepository.insertAuth(newId, loginId, email, uid, correlationId, requestId);
        usrRepository.insertCustomUsr(newId, kind, uid, correlationId, requestId);

        // Insert skeleton address and person rows (PKs from DB sequence)
        Long adPk    = null;
        Long bizAdPk = null;
        if (address != null) {
            adPk = usrRepository.nextAddrPk();
            usrRepository.insertAddress(adPk, uid, correlationId, requestId);
        }
        if (address2 != null) {
            bizAdPk = usrRepository.nextAddrPk();
            usrRepository.insertAddress(bizAdPk, uid, correlationId, requestId);
        }
        usrRepository.insertPerson(newId, EsqConstants.KIND_PERSON_PRIMARY, adPk, bizAdPk, uid, correlationId, requestId);

        // Persist validated person fields
        usrRepository.updatePerson(idStr, prsn.getKind(), prsn.getFirstName(), prsn.getMiddleName(),
                prsn.getLastName(), prsn.getTitle(), prsn.getDob(), prsn.getBirthPlace(),
                prsn.getSex(), prsn.getTaxId(), prsn.getCitizenship(), prsn.getMarStatus(),
                prsn.getPersonIdType(), prsn.getPersonIdNumber(), prsn.getEmail(),
                prsn.getPhone(), prsn.getPhone2(), uid, correlationId, requestId);

        if (address != null) {
            // Validate and persist postal address fields
            EsqAddressJpa addr = new EsqAddressJpa();
            addr.setKind(EsqConstants.KIND_ADDRESS_POSTAL);
            Map<String, Object> maddr = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_ADDRESS);
            kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_ADDRESS, kfl);
            EsqEntityLayer addrLayer = dictUser.findLayer(kfl.getLayer());
            if (addrLayer != null) addrLayer.injectDefaults(maddr);
            if (EntityFieldUtils.applyFields(addr, maddr, false, kfl.getLayer(), null)) {
                usrRepository.updateAddress(idStr, EsqConstants.KIND_PERSON_PRIMARY, addr.getDesc(),
                        addr.getAddr(), addr.getAddr2(), addr.getCity(), addr.getCompany(),
                        addr.getCountry(), addr.getDepartment(), addr.getFax(),
                        addr.getPostalCode(), addr.getProvince(), addr.getTitle(), addr.getUrl(),
                        uid, correlationId, requestId);
            }
            address[0] = addr;
        }

        if (address2 != null) {
            // Validate and persist business address fields
            EsqAddressJpa addr2 = new EsqAddressJpa();
            addr2.setKind(EsqConstants.KIND_ADDRESS_BIZ);
            Map<String, Object> maddr2 = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_ADDRESS2);
            kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_ADDRESS2, kfl);
            EsqEntityLayer addr2Layer = dictUser.findLayer(kfl.getLayer());
            if (addr2Layer != null) addr2Layer.injectDefaults(maddr2);
            if (EntityFieldUtils.applyFields(addr2, maddr2, false, kfl.getLayer(), null)) {
                usrRepository.updateAddress2(idStr, EsqConstants.KIND_PERSON_PRIMARY, addr2.getDesc(),
                        addr2.getAddr(), addr2.getAddr2(), addr2.getCity(), addr2.getCompany(),
                        addr2.getCountry(), addr2.getDepartment(), addr2.getFax(),
                        addr2.getPostalCode(), addr2.getProvince(), addr2.getTitle(), addr2.getUrl(),
                        uid, correlationId, requestId);
            }
            address2[0] = addr2;
        }

        // Custom field validation loop
        List<EsqCustomEntityFieldJpa> customFields = entityDictionaryRepository.findCustom(kind);
        if (customFields != null && !customFields.isEmpty()) {
            for (EsqCustomEntityFieldJpa cf : customFields) {
                String fieldName = cf.getName();
                if (cf.getReadwrite() != null && (cf.getReadwrite() & 2) == 2 && fields.containsKey(fieldName)) {
                    Object rawVal = fields.get(fieldName);
                    if (rawVal == null) continue;
                    kfl = dictUser.fillKindFieldLayer(fieldName, kfl);
                    String val = (String) ValidatorFactory.getInstance().validate(usr, kfl, false, rawVal);
                    usrRepository.updateCustomUsr(idStr, fieldName, val, uid, correlationId, requestId);
                }
            }
        }

        usr.setName(name);
        usr.setLoginId(loginId);
        usr.setEmail(email);
        usr.setPath(path);
        usr.setParentId(parentId);
        created[0] = usr;
        // Populate custom fields for response
        custom[0] = usrRepository.customUsr(idStr);
        // Populate person
        person[0] = prsn;

        // x-Rod audit: full per-row CREATE events for the whole user footprint (entityId = usr_pk).
        xyRod.post(RodEvent.Op.CREATE, usr.getKind(), idStr, null, usr, EsqMsgConstants.MSG_TYPE_AUDIT);
        xyRod.post(RodEvent.Op.CREATE, prsn.getKind(), idStr, null, prsn, EsqMsgConstants.MSG_TYPE_AUDIT);
        if (address != null && adPk != null && address[0] != null) {
            EsqAddressJpa a = (EsqAddressJpa) address[0];
            xyRod.post(RodEvent.Op.CREATE, a.getKind(), idStr, String.valueOf(adPk), a, EsqMsgConstants.MSG_TYPE_AUDIT);
        }
        if (address2 != null && bizAdPk != null && address2[0] != null) {
            EsqAddressJpa a2 = (EsqAddressJpa) address2[0];
            xyRod.post(RodEvent.Op.CREATE, a2.getKind(), idStr, String.valueOf(bizAdPk), a2, EsqMsgConstants.MSG_TYPE_AUDIT);
        }
        if (xyRod.isEnabled()) {
            for (EsqParRow p : usrRepository.listUsrPar(idStr)) {
                xyRod.post(RodEvent.Op.CREATE, EsqConstants.KIND_USR_PAR, idStr, p.getName(), p, EsqMsgConstants.MSG_TYPE_AUDIT);
            }
        }
    }

    private void deleteUsr(String id, String rootPath) {
        EsqUsrJpa usr = usrRepository.detailUsrForUpdate(id, rootPath);
        if (usr == null) {
            throw new ResourceNotFoundException("deleteUsr", "id", id);
        }
        if ("Y".equals(usr.getConnectFlg())) {
            throw new DeleteRestrictedException("user", "active auth connection — disable login before deleting");
        }
        ValidatorFactory.getInstance().validateDelete(usr);
        // x-Rod audit: capture the child address pks BEFORE the cascade (the DB removes them silently,
        // so they must be enumerated here). Only when enabled -- no extra reads otherwise.
        EsqAddressJpa addr = null;
        EsqAddressJpa addr2 = null;
        if (xyRod.isEnabled()) {
            addr  = usrRepository.address(id, EsqConstants.KIND_PERSON_PRIMARY);
            addr2 = usrRepository.address2(id, EsqConstants.KIND_PERSON_PRIMARY);
        }
        usrRepository.deletePersonAddresses(id);
        usrRepository.deletePersonBankInfo(id);
        usrRepository.deleteUsr(id);
        usrRepository.deleteEntityPath(id);
        // One DELETE event per cascaded child row (id + kind only). person id = usr_pk (known, no query);
        // usr_par params cascade with the owner and get NO per-row event (owner DELETE implies them gone).
        if (xyRod.isEnabled()) {
            xyRod.post(RodEvent.Op.DELETE, EsqConstants.KIND_PERSON_PRIMARY, id, null, EsqMsgConstants.MSG_TYPE_AUDIT);
            if (addr != null) {
                xyRod.post(RodEvent.Op.DELETE, addr.getKind(), id, addr.getId(), EsqMsgConstants.MSG_TYPE_AUDIT);
            }
            if (addr2 != null) {
                xyRod.post(RodEvent.Op.DELETE, addr2.getKind(), id, addr2.getId(), EsqMsgConstants.MSG_TYPE_AUDIT);
            }
        }
    }

    private static final Set<String> USR_WRITABLE = Set.of("name"); //, xxx overwrite readonly field 'name'
    private void saveUsr(String id,
                         Map<String, Object> fields,
                         String rootPath,
                         String uid, String correlationId,
                         String requestId,
                         EsqEntityJpa[] updated, List<EsqNameValueJpa>[] custom,
                         List<EsqEntityJpa>[] children,
                         EsqEntityJpa[] person,
                         EsqEntityJpa[] address,
                         EsqEntityJpa[] address2
    ) {
        EsqUsrJpa usr = usrRepository.detailUsrForUpdate(id, rootPath);
        if (usr == null) {
            throw new ResourceNotFoundException("saveUsr", "id", id);
        }
        List<EsqNameValueJpa> cstm = usrRepository.customUsr(id);
        EsqPersonJpa prsn = usrRepository.person(id, EsqConstants.KIND_PERSON_PRIMARY);
        Map<String, Object> mprsn = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_PERSON);
        EsqEntityDictionary dictUser = completedDictionary(usr.getKind());
        EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
        boolean personal = id.equals(uid);
//devLog.debug("looking for {} {}",EsqConstants.SUBENTITY_PERSON, dictUser.getKind());
        kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_PERSON,kfl);
//devLog.debug("person layer {}",kfl.getLayer());
        if (EntityFieldUtils.applyFields(prsn, mprsn, personal, kfl.getLayer(), null)) {

            // Derive user name from person (First [Middle] Last) and inject into fields.
            // This ensures EnyManService.isBroadcastableUpdate() detects the name change
            // even when the caller did not provide "name" directly in the request.
            fields.put("name", prsn.getName());

            usrRepository.updatePerson(id,
                    prsn.getKind(),
                    prsn.getFirstName(),
                    prsn.getMiddleName(),
                    prsn.getLastName(),
                    prsn.getTitle(),
                    prsn.getDob(),
                    prsn.getBirthPlace(),
                    prsn.getSex(),
                    prsn.getTaxId(),
                    prsn.getCitizenship(),
                    prsn.getMarStatus(),
                    prsn.getPersonIdType(),
                    prsn.getPersonIdNumber(),
                    prsn.getEmail(),
                    prsn.getPhone(),
                    prsn.getPhone2(),
                    uid, correlationId, requestId
            );
            xyRod.post(RodEvent.Op.UPDATE, prsn.getKind(), id, null, prsn, EsqMsgConstants.MSG_TYPE_AUDIT);
        }

        if (EntityFieldUtils.applyFields(usr, fields, personal, 0, USR_WRITABLE)) {
            usrRepository.updateUsr(id, usr.getName(), usr.getRegistration(), usr.getDeleted(), usr.getDesc(), uid, correlationId, requestId);
            xyRod.post(RodEvent.Op.UPDATE, usr.getKind(), usr.getId(), null, usr, EsqMsgConstants.MSG_TYPE_AUDIT);
        }
        Set<String> changedPars = new HashSet<>();
        if (cstm != null) {
            for (EsqNameValueJpa nv : cstm) {
                String nm = nv.getName();
                if (fields.containsKey(nm)) {
                    String val = (String)fields.get(nm);
                    kfl = dictUser.fillKindFieldLayer(nm,kfl);
                    EsqEntityField field = kfl.getField();
                    if (field != null && (field.getReadwrite()  & 2) == 2) {
                        val = (String)ValidatorFactory.getInstance().validate(usr, kfl, personal, val);
                        nv.setValue(val);
                        usrRepository.updateCustomUsr(id, nm, val, uid, correlationId, requestId);
                        changedPars.add(nm);
                    }
                }
            }
        }
        if (xyRod.isEnabled() && !changedPars.isEmpty()) {
            for (EsqParRow p : usrRepository.listUsrPar(id)) {
                if (changedPars.contains(p.getName())) {
                    xyRod.post(RodEvent.Op.UPDATE, EsqConstants.KIND_USR_PAR, id, p.getName(), p, EsqMsgConstants.MSG_TYPE_AUDIT);
                }
            }
        }

        if (address != null && address2 != null) {
            EsqAddressJpa addr = usrRepository.address(id, EsqConstants.KIND_PERSON_PRIMARY);
            if (addr != null) {
                //addr.setId(id);
                Map<String, Object> maddr = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_ADDRESS);
//devLog.debug("looking for {} {}",EsqConstants.SUBENTITY_ADDRESS, dictUser.getKind());
                kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_ADDRESS, kfl);
//devLog.debug("address layer {} {}", EsqConstants.SUBENTITY_ADDRESS, kfl.getLayer());
                if (EntityFieldUtils.applyFields(addr, maddr, personal, kfl.getLayer(), null)) {
                    usrRepository.updateAddress(id, EsqConstants.KIND_PERSON_PRIMARY, addr.getDesc(),
                            addr.getAddr(), addr.getAddr2(), addr.getCity(), addr.getCompany(),
                            addr.getCountry(), addr.getDepartment(), addr.getFax(),
                            addr.getPostalCode(), addr.getProvince(), addr.getTitle(), addr.getUrl(),
                            uid, correlationId, requestId);
                    xyRod.post(RodEvent.Op.UPDATE, addr.getKind(), id, addr.getId(), addr, EsqMsgConstants.MSG_TYPE_AUDIT);
                }
            }

            EsqAddressJpa addr2 = usrRepository.address2(id, EsqConstants.KIND_PERSON_PRIMARY);
            if (addr2 != null) {
                //addr2.setId(id);
                Map<String, Object> maddr2 = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_ADDRESS2);
//devLog.debug("looking for {} {}",EsqConstants.SUBENTITY_ADDRESS2, dictUser.getKind());
                kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_ADDRESS2, kfl);
//devLog.debug("address2 layer {} {}", EsqConstants.SUBENTITY_ADDRESS2, kfl.getLayer());
                if (EntityFieldUtils.applyFields(addr2, maddr2, personal, kfl.getLayer(), null)) {
                    usrRepository.updateAddress2(id, EsqConstants.KIND_PERSON_PRIMARY, addr2.getDesc(),
                            addr2.getAddr(), addr2.getAddr2(), addr2.getCity(), addr2.getCompany(),
                            addr2.getCountry(), addr2.getDepartment(), addr2.getFax(),
                            addr2.getPostalCode(), addr2.getProvince(), addr2.getTitle(), addr2.getUrl(),
                            uid, correlationId, requestId);
                    xyRod.post(RodEvent.Op.UPDATE, addr2.getKind(), id, addr2.getId(), addr2, EsqMsgConstants.MSG_TYPE_AUDIT);
                }
            }
            address[0] = addr;
            address2[0] = addr2;
        }
        if (children != null) {
            children[0] = (List<EsqEntityJpa>)(List<?>)usrRepository.userAccts(id, rootPath);
        }
        //note: if a DB trigger or default value modifies the row, saveUsr won't reflect it.
        updated[0] = usr;
        custom[0] = cstm;
        person[0] = prsn;
    }

}

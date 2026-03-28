/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.EsqCustomEntityFieldJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAddressJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqPersonJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.DeleteRestrictedException;
import pro.mir0n.esquire.backend.error.EmailExistsException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;

@Slf4j
public class UsrService  extends AEnyManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + UsrService.class.getName());

    private final EsqEntityDictionaryRepository entityDictionaryRepository;
    private final EsqUsrRepository usrRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager em;

    public UsrService(EsqEntityDictionaryRepository entityDictionaryRepository,
                      EsqUsrRepository usrRepository,
                      TransactionTemplate transactionTemplate,
                      EntityManager em) {
        super(entityDictionaryRepository);
        this.entityDictionaryRepository = entityDictionaryRepository;
        this.usrRepository = usrRepository;
        this.transactionTemplate = transactionTemplate;
        this.em = em;
    }


    @Override
    public List<EsqEntityLayer> esquireDictionary(Integer kind) {
        //xxx: not in use
        return null;
    }

    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        devLog.debug("srvc: esquireCommand(usr): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);

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
        EsqEntity ret = EsqEntityFactory.getInstance().createUser(jpa, custom, children, person, address, address2 );
        devLog.debug("srvc: esquireCommand(usr): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
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

        EsqEntity ret = EsqEntityFactory.getInstance().createUser(updated[0], custom[0], children == null ? null : children[0],
                    person == null ? null : person[0], address == null ? null : address[0], address2 == null ? null : address2[0]);
        devLog.debug("srvc: esquireCommandSave(usr): entity:{}", ret);
        return ret;
    }

    @Override
    public EsqEntity esquireCommandNew(Integer kind, String parentId, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
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

        EsqEntity ret = EsqEntityFactory.getInstance().createUser(created[0], custom[0], null, person[0],
                address == null ? null : address[0], address2 == null ? null : address2[0]);
        devLog.debug("srvc: esquireCommandNew(usr): entity:{}", ret);
        return ret;
    }

    @Override
    public void esquireCommandDelete(Integer kind, String id, String cmd, String rootPath, String uid, List<String> roles) {
        devLog.debug("srvc: esquireCommandDelete(usr): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);
        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            deleteUsr(id, rootPath);
            return null;
        });
    }

    private void createUsr(Integer kind, String parentId, Map<String, Object> fields,
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
        long newId = EsqUtils.generateEntityId();
        String idStr = String.valueOf(newId);
        String path = parentPath + newId +".";
        fields.put("path", path);

        EsqEntityDictionary dictUser = EsqEntityDictionaryStorage.getInstance().get(kind);
        EsqEntityKindFieldLayer kfl  = new EsqEntityKindFieldLayer();

        // Validate person subentity — derive name and email
        EsqPersonJpa prsn = new EsqPersonJpa();
        prsn.setKind(EsqConstants.KIND_PERSON_PRIMARY);
        Map<String, Object> mprsn = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_PERSON);
        kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_PERSON, kfl);
        EsqEntityLayer prsnLayer = dictUser.findLayer(kfl.getLayer());
        if (prsnLayer != null) prsnLayer.injectDefaults(mprsn);
        applyFields(prsn, mprsn, false, kfl.getLayer(), null);
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
        applyFields(usr, fields, false, 0, USR_WRITABLE);

        // Ensure registration and deleted flags are in fields for broadcast
        if (usr.getRegistration() != null) fields.put("registration", usr.getRegistration());
        if (usr.getDeleted() != null)      fields.put("deleted", usr.getDeleted());

        // Insert main rows
        usrRepository.insertUsr(newId, kind, usr.getName(), usr.getDesc(), path, parentId, usr.getRegistration(), usr.getDeleted(), uid, correlationId, requestId);
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
            if (applyFields(addr, maddr, false, kfl.getLayer(), null)) {
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
            if (applyFields(addr2, maddr2, false, kfl.getLayer(), null)) {
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
    }

    private void deleteUsr(String id, String rootPath) {
        EsqUsrJpa usr = usrRepository.detailUsrForUpdate(id, rootPath);
        if (usr == null) {
            throw new ResourceNotFoundException("deleteUsr", "id", id);
        }
        if ("Y".equals(usr.getConnectFlg())) {
            throw new DeleteRestrictedException("user", "active auth connection — disable login before deleting");
        }
        usrRepository.deletePersonAddresses(id);
        usrRepository.deletePersonBankInfo(id);
        usrRepository.deleteUsr(id);
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
        EsqEntityDictionary dictUser = EsqEntityDictionaryStorage.getInstance().get(usr.getKind());
        EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
        boolean personal = id.equals(uid);
//devLog.debug("looking for {} {}",EsqConstants.SUBENTITY_PERSON, dictUser.getKind());
        kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_PERSON,kfl);
//devLog.debug("person layer {}",kfl.getLayer());
        if (applyFields(prsn, mprsn, personal, kfl.getLayer(), null)) {

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
        }

        if (applyFields(usr, fields, personal, 0, USR_WRITABLE)) {
            usrRepository.updateUsr(id, usr.getName(), usr.getRegistration(), usr.getDeleted(), usr.getDesc(), uid, correlationId, requestId);
        }
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
                    }
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
                if (applyFields(addr, maddr, personal, kfl.getLayer(), null)) {
                    usrRepository.updateAddress(id, EsqConstants.KIND_PERSON_PRIMARY, addr.getDesc(),
                            addr.getAddr(), addr.getAddr2(), addr.getCity(), addr.getCompany(),
                            addr.getCountry(), addr.getDepartment(), addr.getFax(),
                            addr.getPostalCode(), addr.getProvince(), addr.getTitle(), addr.getUrl(),
                            uid, correlationId, requestId);
                }
            }

            EsqAddressJpa addr2 = usrRepository.address2(id, EsqConstants.KIND_PERSON_PRIMARY);
            if (addr2 != null) {
                //addr2.setId(id);
                Map<String, Object> maddr2 = (Map<String, Object>) fields.get(EsqConstants.SUBENTITY_ADDRESS2);
//devLog.debug("looking for {} {}",EsqConstants.SUBENTITY_ADDRESS2, dictUser.getKind());
                kfl = dictUser.fillKindFieldLayer(EsqConstants.SUBENTITY_ADDRESS2, kfl);
//devLog.debug("address2 layer {} {}", EsqConstants.SUBENTITY_ADDRESS2, kfl.getLayer());
                if (applyFields(addr2, maddr2, personal, kfl.getLayer(), null)) {
                    usrRepository.updateAddress2(id, EsqConstants.KIND_PERSON_PRIMARY, addr2.getDesc(),
                            addr2.getAddr(), addr2.getAddr2(), addr2.getCity(), addr2.getCompany(),
                            addr2.getCountry(), addr2.getDepartment(), addr2.getFax(),
                            addr2.getPostalCode(), addr2.getProvince(), addr2.getTitle(), addr2.getUrl(),
                            uid, correlationId, requestId);
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

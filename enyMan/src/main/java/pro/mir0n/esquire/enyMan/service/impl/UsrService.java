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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.repository.query.Param;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAddressJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqPersonJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
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
public class UsrService  extends AEnyManService {

    private final EsqUsrRepository usrRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager em;

    public UsrService(EsqEntityDictionaryRepository entityDictionaryRepository,
                      EsqUsrRepository usrRepository,
                      TransactionTemplate transactionTemplate,
                      EntityManager em) {
        super(entityDictionaryRepository);
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
        log.debug("srvc: esquireCommand(usr): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);

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
        log.debug("srvc: esquireCommand(usr): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommandSave(usr): kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);

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
        log.debug("srvc: esquireCommandSave(usr): entity:{}", ret);
        return ret;
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
//log.debug("looking for {} {}",EsqConstants.SUBENTITY_PERSON, dictUser.getKind());
        kfl = dictUser.fillКindFieldLayer(EsqConstants.SUBENTITY_PERSON,kfl);
//log.debug("person layer {}",kfl.getLayer());
        if (applyFields(prsn, mprsn, personal, kfl.getLayer(), null)) {

            fields.put("name", prsn.getName());  //xxx set a user's name based on first, middle, last names

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
                    kfl = dictUser.fillКindFieldLayer(nm,kfl);
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
//log.debug("looking for {} {}",EsqConstants.SUBENTITY_ADDRESS, dictUser.getKind());
                kfl = dictUser.fillКindFieldLayer(EsqConstants.SUBENTITY_ADDRESS, kfl);
//log.debug("address layer {} {}", EsqConstants.SUBENTITY_ADDRESS, kfl.getLayer());
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
//log.debug("looking for {} {}",EsqConstants.SUBENTITY_ADDRESS2, dictUser.getKind());
                kfl = dictUser.fillКindFieldLayer(EsqConstants.SUBENTITY_ADDRESS2, kfl);
//log.debug("address2 layer {} {}", EsqConstants.SUBENTITY_ADDRESS2, kfl.getLayer());
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

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 * 01/12/2026 mir0n added "profile" command
 * 01/12/2026 mir0n BizTreeConstants moved to common package
 *                  Error handling with rfc9457 compliance
 *                  Debug logs added
 * 01/23/2026 mir0n use common library
 *                  no more EsqTreeNode methods  
 *                  use entityRepository.acctsAsNodes()
 * 01/24/2026 mir0n  ResourceNotFoundException moved to common lib
 *                   detailAcct() removed (moved to pacMan)
 * 02/12/2026 mir0n  EsqObjectKind instead if EsqEntityKind
 *                   removed "profile" command
 * 02/13/2026 mir0n userAccts() instead of acctsAsNodes
 * 02/19/2026 mir0n EntityManager + TransactionTemplate injected
 *                  FlushModeType.COMMIT prevents Hibernate auto-flush before native queries
 *                  esquireCommandSave() with saveOrg() / saveUsr() helpers
 *                  EsqEntityRepository/EsqCustomFieldRepository replaced by
 *                  EsqOrgRepository / EsqUsrRepository
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
@Service
@AllArgsConstructor
public class EnyManService  implements IEnyManService {

    private EsqEntityDictionaryRepository entityDictionaryRepository;
    private EsqOrgRepository orgRepository;
    private EsqUsrRepository usrRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;


    @Override
    public List<EsqEntityLayer> esquireDictionary(Integer kind) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireDictionary: kind:{}",  kind);

        List<EsqEntityLayer> ret = null;
        EsqEntityDictionary dict  = EsqEntityDictionaryStorage.getInstance().get(kind);
        if  (dict != null) {
            if(!dict.isCompleted()) {
                List<EsqCustomEntityFieldJpa> custom = entityDictionaryRepository.findCustom(kind);
                if   (custom != null && !custom.isEmpty()) {
                    EsqEntityDictionaryMapper.mapTo(custom, dict);
                }
                dict.setCompleted(true);
            }
            ret = dict.getLayers();
        }
        if (ret == null) {
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
        log.debug("srvc: esquireDictionary(2): ret:{}",  ret);
        return ret;
    }

    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommand: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);

        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
        EsqEntityJpa jpa = null;
        List<EsqNameValueJpa> custom = null;
        List<EsqEntityJpa> children = null;
        // xxx: path is safe
        List<String> path = EsqTreeNodeMapper.pathArray(rootPath);
        if (eek.isOrg()) {
            jpa = orgRepository.detailOrg(id, rootPath);
            custom = orgRepository.customOrg(id);
        } else if (eek.isUsr()) {
            jpa = usrRepository.detailUsr(id, rootPath);
            custom = usrRepository.customUsr(id);
        }
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }

        if (eek.isChildrenDetailed() && eek.isUsr()) {
            //xxx: returning level will be incorrect, but that is ok: it does not matter here
            children = (List<EsqEntityJpa>)(List<?>)usrRepository.userAccts(id, rootPath);
        }

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, custom, children);
        log.debug("srvc: esquireCommand(2): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommandSave: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);

        int k = (int)Math.floor((double) kind/2) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
        if (!(eek.isOrg() || eek.isUsr())) {
            throw new ResourceNotFoundException("esquireCommandSave", "kind", kind.toString());
        }

        //xxx: tricks with pointers
        EsqEntityJpa[] updated  = {null};
        List<EsqNameValueJpa>[] custom   = new List[]{null};
        List<EsqEntityJpa>[] children = eek.isChildrenDetailed() ? new List[]{null} : null;

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            if (eek.isOrg()) {
                saveOrg(id, fields, rootPath, uid, correlationId, requestId, updated, custom);
            } else { //if (eek.isUsr()) {
                saveUsr(id, fields, rootPath, uid, correlationId, requestId, updated, custom, children);
            }
            return null;
        }); // ← transaction commits here

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(updated[0], custom[0], children == null ? null : children[0]);
        log.debug("srvc: esquireCommandSave(2): entity:{}", ret);
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


    private void saveUsr(String id, Map<String, Object> fields, String rootPath,
                         String uid, String correlationId, String requestId,
                         EsqEntityJpa[] updated, List<EsqNameValueJpa>[] custom, List<EsqEntityJpa>[] children) {
        EsqUsrJpa usr = usrRepository.detailUsrForUpdate(id, rootPath);
        if (usr == null) {
            throw new ResourceNotFoundException("saveUsr", "id", id);
        }
        List<EsqNameValueJpa> cstm  = usrRepository.customUsr(id);
        if (applyFields(usr, fields, USR_WRITABLE)) {
            usrRepository.updateUsr(id, usr.getName(), usr.getRegistration(), usr.getDeleted(), usr.getDesc(), uid, correlationId, requestId);
        }
        if (cstm != null) {
            for(EsqNameValueJpa nv : cstm) {
                String nm = nv.getName();
                if (fields.containsKey(nm)) {
                    String val = (String) fields.get(nm);
                    if (val != null && val.isBlank()) {
                        val = null;
                    }
                    nv.setValue(val);
                    usrRepository.updateCustomUsr(id, nm, val, uid, correlationId, requestId);
                }
            }
        }

        if (children != null) {
            children[0] = (List<EsqEntityJpa>)(List<?>)usrRepository.userAccts(id, rootPath);
        }
        //note: if a DB trigger or default value modifies the row, saveUsr won't reflect it.
        updated[0]  = usr; //usrRepository.detailUsr(id, rootPath);
        custom[0]   = cstm;
    }

    //TODO: use dictionary eventually for fields validation and readonly fields
    //TODO: move to EsqEntityJpa or some common utils class
    //TODO: add primary contact structure/sub-bean
    //TODO: find an user name to update based on first, middle, last names
    private static final Set<String> ORG_WRITABLE = Set.of("name","desc","fullName");
    private static final Set<String> USR_WRITABLE = Set.of("desc","registration","deleted");

    private boolean applyFields(EsqEntityJpa jpa, Map<String, Object> fields, Set<String> writables) {
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        boolean changed = false;
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            if ((writables == null || writables.contains(pd.getName()))
            && fields.containsKey(pd.getName())) {
                changed = true;
                Object newValue = fields.get(pd.getName());
                if (newValue instanceof String && ((String)newValue).isBlank()) {   // isEmpty()?
                    newValue = null;
                }
                //xxx: this updates the given jpa
                wrapper.setPropertyValue(pd.getName(), newValue);
            }
        }
        return changed;
    }
}

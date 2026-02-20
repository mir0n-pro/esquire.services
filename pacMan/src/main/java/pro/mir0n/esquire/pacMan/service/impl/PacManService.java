/*
 *  Esquire frameworks (tm)
 *  PacMan service
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
 * 02/12/2026 mir0n EsqObjectKind instead if EsqEntityKind
 *                  removed "profile" command
 * 02/13/2026 mir0n removed unused variables
 * 02/19/2026 mir0n EntityManager + TransactionTemplate injected
 *                  FlushModeType.COMMIT prevents Hibernate auto-flush before native queries
 *                  esquireCommandSave() with saveAcct() helper
 *                  ACCT_WRITABLE = {desc, status}
 */

package pro.mir0n.esquire.pacMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.pacMan.service.IPacManService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@Service
@AllArgsConstructor
public class PacManService  implements IPacManService {

    private EsqAcctRepository entityRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;


    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommand: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);

        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);

        EsqEntityJpa jpa = null;
        // xxx: path is safe
        if (eek.isAcct()) {
            jpa = entityRepository.detailAcct(id, rootPath);
        }
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, null, null);
        log.debug("srvc: esquireCommand(2): entity:{}",  ret);
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommandSave: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);
//ave failed: esquireCommandSave not found with the given input data kind : '53'
        int k = ((int)Math.floor((double) kind/2)) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
        if (!eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommandSave", "kind", kind.toString());
        }

        EsqEntityJpa[] updated = {null};

        transactionTemplate.execute(status -> {
            // xxx: COMMIT flush mode prevents Hibernate from auto-flushing managed entities
            //      before native query execution. We use native queries exclusively for writes,
            //      so JPA dirty-tracking must never interfere. clearAutomatically=true on
            //      @Modifying queries clears the context after each native update, so nothing
            //      remains to flush at commit.
            em.setFlushMode(FlushModeType.COMMIT);
            saveAcct(id, fields, rootPath, uid, correlationId, requestId, updated);
            return null;
        }); // ← transaction commits here

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(updated[0], null, null);
        log.debug("srvc: esquireCommandSave(2): entity:{}", ret);
        return ret;
    }

    private void saveAcct(String id, Map<String, Object> fields, String rootPath,
                          String uid, String correlationId, String requestId,
                          EsqEntityJpa[] updated) {
        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(id, rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("saveAcct", "id", id);
        }
        if (applyFields(acct, fields, ACCT_WRITABLE)) {
            entityRepository.updateAcct(id, acct.getDesc(), acct.getStatus(), uid, correlationId, requestId);
        }
        //note: if a DB trigger or default value modifies the row, saveAcct won't reflect it.
        updated[0] = acct;
    }

    private static final Set<String> ACCT_WRITABLE = Set.of("status","desc");

    private boolean applyFields(EsqEntityJpa jpa, Map<String, Object> fields, Set<String> writables) {
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

    private int rootLevel(List<String> path, String uid) {
        int ret = 0;
        if (path.size() > 1) {
            ret = path.size() -1;
            if (path.get(ret).equals(uid)) {
                ret++; // xxx: non-admin user, root is the current user
            }
        }
        //log.debug("rootLevel: path={}, uid={}, ret={}", path, uid, ret);
        return ret;
    }

}

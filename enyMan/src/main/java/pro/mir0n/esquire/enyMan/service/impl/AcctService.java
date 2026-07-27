/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: account CREATE service on enyMan side
 * 06/04/2026 mir0n  esquireCommandNew reads uid via RequestContextUtils; rootPath / uid params dropped
 *                   from the IEnyManService overrides
 * 06/05/2026 mir0n  XYRod injected; account CREATE posts an x-Rod audit event (enyMan owns CREATE;
 *                   pacMan owns UPDATE / DELETE / balance)
 * 06/15/2026 mir0n  audit dep XYRod -> IXRod (import common.xrod -> messaging.xrod); CREATE post() passes an
 *                   explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT).
 * 06/17/2026 mir0n  audit dep IXRod -> AuditBusBridge; the CREATE post() drops the trailing MSG_TYPE_AUDIT arg
 * 06/18/2026 mir0n  audit module left common: AuditBusBridge moved to pro.mir0n.esquire.audit
 * 06/22/2026 mir0n  RodEvent import retargeted messaging.xrod.RodEvent -> messaging.RodEvent (package move).
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 * 07/23/2026 mir0n  v1.2.11 -- createAcct reads rootPath (RequestContextUtils) and passes it to
 *                   acctPath(parentId, rootPath) -- the parent lookup is now tenant-scoped, like org/usr create
 */

package pro.mir0n.esquire.enyMan.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.service.EntityFieldUtils;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.enyMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.enyMan.service.EntityIdGenerator;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class AcctService extends AEnyManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + AcctService.class.getName());

    private final EsqAcctRepository acctRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager em;
    private final AuditBusBridge audit;

    public AcctService(EsqEntityDictionaryRepository entityDictionaryRepository,
                       EsqAcctRepository acctRepository,
                       TransactionTemplate transactionTemplate,
                       EntityManager em,
                       AuditBusBridge audit) {
        super(entityDictionaryRepository);
        this.acctRepository = acctRepository;
        this.transactionTemplate = transactionTemplate;
        this.em = em;
        this.audit = audit;
    }

    // Account READ/UPDATE/DELETE stay in pacMan; AcctService owns CREATE only.
    @Override
    public EsqEntity esquireCommand(int kind, String id, String cmd) {
        throw new UnsupportedOperationException("esquireCommand(acct) is owned by pacMan");
    }

    @Override
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, List<String> roles) {
        throw new UnsupportedOperationException("esquireCommandSave(acct) is owned by pacMan");
    }

    @Override
    public void esquireCommandDelete(int kind, String id, String cmd, List<String> roles) {
        throw new UnsupportedOperationException("esquireCommandDelete(acct) is owned by pacMan");
    }

    @Override
    public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, List<String> roles) {
        throw new UnsupportedOperationException("esquireCommandMove(acct) not supported");
    }

    @Override
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, List<String> roles) {
        EsqEntity ret = null;
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String uid = RequestContextUtils.getUid();
        String rootPath = RequestContextUtils.getRootPath();
        devLog.debug("srvc: esquireCommandNew(acct): kind:{}, parentId:{}, cmd:{}, rootPath:{}, uid:{}", kind, parentId, cmd, rootPath, uid);

        EsqEntityJpa[] created = {null};

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            createAcct(kind, parentId, fields, rootPath, uid, correlationId, requestId, created);
            return null;
        });

        ret = EsqEntityFactory.getInstance().createEntity(created[0], null, null);
        devLog.debug("srvc: esquireCommandNew(acct)(2): entity:{}", ret);
        return ret;
    }

    // Account is a leaf: ep_path of the new account equals the parent's path
    // (no own-pk segment appended). Mirrors EsqObjectKind.isPathParentOnly() for acct.
    private void createAcct(int kind, String parentId, Map<String, Object> fields,
                            String rootPath, String uid, String correlationId, String requestId,
                            EsqEntityJpa[] created) {
        String parentPath = acctRepository.acctPath(parentId, rootPath);
        if (parentPath == null) {
            throw new ResourceNotFoundException("createAcct", "parentId", parentId);
        }
        long   newId  = EntityIdGenerator.generateEntityId();
        String path   = parentPath;
        String prefix = pro.mir0n.esquire.backend.storage.EsqObjectKindStorage.getInstance().get(kind).getName().substring(0, 1).toUpperCase();
        String name   = prefix + newId;

        fields.put(EsqConstants.TEXT_NAME, name);
        fields.put(EsqConstants.TEXT_PATH, path);

        EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(kind);
        if (dict != null) {
            for (EsqEntityLayer layer : dict.getLayers()) {
                layer.injectDefaults(fields);
            }
        }

        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setKind(kind);
        EntityFieldUtils.applyFields(acct, fields);
        if (dict != null) {
            for (EsqEntityLayer layer : dict.getLayers()) {
                EntityFieldUtils.enforceDefaults(layer, acct);
            }
        }

        acct.setId(String.valueOf(newId));
        acct.setName(name);
        acct.setPath(path);
        acct.setParentId(parentId);

        acctRepository.insertAcctPath(newId, kind, path);
        acctRepository.insertAcct(newId, kind, name, acct.getDesc(), acct.getCcy(), acct.getStatus(), acct.getNegativeAllowed(), parentId, uid, correlationId, requestId);

        created[0] = acct;

        // x-Rod audit: account CREATE (enyMan owns CREATE; UPDATE/DELETE/balance are pacMan's domain).
        audit.post(RodEvent.Op.CREATE, acct.getKind(), acct.getId(), null, acct);
    }
}

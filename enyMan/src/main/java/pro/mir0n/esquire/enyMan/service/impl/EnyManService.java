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
 * 02/28/2026 mir0n  extends AEnyManService; delegates to OrgService/UsrService
 *                   saveOrg/saveUsr/applyFields moved to OrgService/UsrService
 * 03/09/2026 mir0n  roles param added; isAdminCmdPermitted(UPDATE) permission check
 *                   self-update bypass for USR (id.equals(uid)); PermissionDeniedException thrown
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
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqPersonJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
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
@Service
public class EnyManService  extends AEnyManService {

    private final IEnyManService orgService;
    private final IEnyManService usrService;

    public EnyManService(EsqEntityDictionaryRepository entityDictionaryRepository,
                         EsqOrgRepository orgRepository,
                         EsqUsrRepository usrRepository,
                         TransactionTemplate transactionTemplate,
                         EntityManager em) {
        super(entityDictionaryRepository);
        this.orgService = new OrgService(entityDictionaryRepository, orgRepository, transactionTemplate, em);
        this.usrService = new UsrService(entityDictionaryRepository, usrRepository, transactionTemplate, em);
    }

    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
        EsqEntity ret = null;
        if (eek.isOrg()) {
            ret = orgService.esquireCommand(k, id, cmd, rootPath, uid);
        } else if (eek.isUsr()) {
            ret = usrService.esquireCommand(k, id, cmd, rootPath, uid);
        } else {
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
        return  ret;
    }

    @Override
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);
        Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
        boolean permitted = false;
        if (permissions != null) {
            permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                permissions.get(k),
                EsqRolesStorage.AdminCmd.UPDATE
            );
        }
        EsqEntity ret = null;
        if (eek.isOrg()) {
            if (permitted) {
                ret = orgService.esquireCommandSave(k, id, cmd, fields, rootPath, uid, roles);
            }
        } else if (eek.isUsr()) {
            if (id != null && id.equals(uid)) {
                permitted = true;
            }
            if (permitted) {
                ret = usrService.esquireCommandSave(k, id, cmd, fields, rootPath, uid, roles);
            }
        } else {
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
        if (ret == null && !permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "modify");
        }
        return  ret;
    }
}

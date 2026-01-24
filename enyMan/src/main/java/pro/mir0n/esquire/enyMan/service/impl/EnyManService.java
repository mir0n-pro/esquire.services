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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.query.Param;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.enyMan.jpa.EsqCustomFieldRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityRepository;
import pro.mir0n.esquire.enyMan.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@Service
@AllArgsConstructor
public class EnyManService  implements IEnyManService {

    private EsqEntityDictionaryRepository entityDictionaryRepository;
    private EsqEntityRepository entityRepository;
    private EsqCustomFieldRepository customEntityRepository;


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

        EsqEntityFactory.EsqEntityKind eek;
        String upk = id;
        if (EsqConstants.CMD_PROFILE.equals(cmd)) {
            eek = EsqEntityFactory.EsqEntityKind.ADMIN;
            upk = uid;
        } else {
            int k = (int)Math.floor( (double) kind/2 ) * 2;
            eek = EsqEntityFactory.EsqEntityKind.getKind(k);
        }
        EsqEntityJpa jpa = null;
        List<EsqNameValueJpa> custom = null;
        List<EsqTreeNodeJpa> children = null;
        // xxx: path is safe
        List<String> path = EsqTreeNodeMapper.pathArray(rootPath);
        if (eek.isOrg()) {
            jpa = entityRepository.detailOrg(id, rootPath);
            custom = customEntityRepository.customOrg(id);
        } else if (eek.isUsr()) {
            jpa = entityRepository.detailUsr(upk, rootPath);
            custom = customEntityRepository.customUsr(upk);
        }
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }

        if (eek.isChildrenDetailed() && eek.isUsr()) {
            //xxx: returning level will be incorrect, but that is ok: it does not matter here
            children = entityRepository.acctsAsNodes(id, rootPath);
        }

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, custom, children);
        log.debug("srvc: esquireCommand(2): entity:{}",  ret);
        return  ret;
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

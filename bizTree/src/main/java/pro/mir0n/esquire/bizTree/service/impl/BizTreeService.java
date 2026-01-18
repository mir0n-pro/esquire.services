/*
 *  Esquire frameworks (tm)
 *  BizTree service
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
 */

package pro.mir0n.esquire.bizTree.service.impl;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.bizTree.dto.*;
import pro.mir0n.esquire.bizTree.jpa.*;
import pro.mir0n.esquire.bizTree.service.RequestContextUtils;
import pro.mir0n.esquire.bizTree.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.bizTree.exception.ResourceNotFoundException;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@Service
@AllArgsConstructor
public class BizTreeService  implements IBizTreeService {

    private EsqTreeNodeRepository treeNodeRepository;
    private EsqEntityDictionaryRepository entityDictionaryRepository;
    private EsqEntityRepository entityRepository;
    private EsqCustomFieldRepository customEntityRepository;

    @Override
    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid) {
        //xxx: ignore skip, take for now
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();

        List<EsqTreeNodeJpa> nodes;
        // xxx: path is safe
        List<String> path = EsqTreeNodeMapper.pathArray(rootPath);
        //xxx: for non-admins, it needs to make root-level  "path.size() -2"
        int rootLevel = rootLevel(path, uid);
        log.debug("srvc: esquire: id:{}, rootPath:{}, uid:{}, level:{} cid:{} rid:{}", id, rootPath,uid, rootLevel, correlationId, requestId );

        if (id != null && !id.isEmpty()) {
            nodes = treeNodeRepository.findNodes(id, rootLevel, rootPath);
        } else {
            //log.debug("srvc: esquire(0): id:{}, rootPath:{}, uid:{}, level:{}", path.get(path.size() -1), rootPath,uid, rootLevel);
            nodes = treeNodeRepository.findRoot(path.get(path.size() -1),rootLevel, rootPath);
            //log.debug("srvc: esquire(1): nodes:{}", nodes);
        }
        if (nodes == null) {// || nodes.isEmpty()) {
            throw new ResourceNotFoundException("esquire", "id", id==null?"''":id);
        }
        log.debug("srvc: esquire(2): nodes:{}", nodes);
        return EsqTreeNodeMapper.mapTo(nodes, new ArrayList<>());
    }

    @Override
    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        EsqTreeNodeJpa node = null;
        // xxx: path is safe
        List<String> path = EsqTreeNodeMapper.pathArray(rootPath);
        int rootLevel = rootLevel(path, uid);
        log.debug("srvc: esquireEntityNode: kind:{}, id:{}, name:{}, rootPath:{}, uid{}, rootLevel:{}", kind, id, name, rootPath, uid, rootLevel);

        if (id != null && !id.isEmpty()) {
            node = treeNodeRepository.findByEntityId(id, rootLevel, rootPath);
        } else if (name != null && kind != null) {
            node = treeNodeRepository.findByNameKind(name, kind, rootLevel, rootPath);
        }
        if (node == null) {
            throw new ResourceNotFoundException("esquireEntityNode", "id,name,kind", id + "," + name + "," + kind);
        }
        log.debug("srvc: esquireEntityNode(2): node:{}", node);
        return EsqTreeNodeMapper.mapTo(node, new EsqTreeNode());
    }

    @Override
    public List<String> esquirePath(String id, String rootPath) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquirePath: id:{}, rootPath:{}",  id, rootPath);

        String ret = treeNodeRepository.findPath(id);
        List<String> rpath = EsqTreeNodeMapper.pathArray(rootPath);

        List<String> path =  EsqTreeNodeMapper.pathArray(ret);
        if (rpath.size() <= 1) {
            //return path;
        }else if (rpath.size() >= path.size()) {
            path = new ArrayList<>();
        } else {
            path = path.subList(rpath.size() -1, path.size());
        }
        log.debug("srvc: esquirePath(2): path:{}", id, path);
        return path;
    }

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

        EsqEntityFactory.EsqEntityKind eek = EsqEntityFactory.EsqEntityKind.ADMIN;
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
        } else if (eek.isAcct()) {
            jpa = entityRepository.detailAcct(id, rootPath);
        }
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }
        if (eek.isChildrenDetailed()) {
            children = treeNodeRepository.findNodes(id, rootLevel(path, uid), rootPath);
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

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
 * 01/23/2026 mir0n use common library
 *                  only EsqTreeNode requests
 * 01/24/2026 mir0n  ResourceNotFoundException.java moved to common lib
 * 03/10/2026 mir0n  import: RequestContextUtils updated to backend.service package
 */

package pro.mir0n.esquire.bizTree.service.impl;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.bizTree.jpa.EsqTreeNodeRepository;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

//import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@Service
@AllArgsConstructor
public class BizTreeService  implements IBizTreeService {

    private EsqTreeNodeRepository treeNodeRepository;

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

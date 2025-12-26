/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.service.impl;

import java.util.*;

import pro.mir0n.esquire.bizTree.dto.*;
import pro.mir0n.esquire.bizTree.jpa.*;
import pro.mir0n.esquire.bizTree.dto.*;
import pro.mir0n.esquire.bizTree.jpa.*;
import pro.mir0n.esquire.bizTree.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.bizTree.exception.ResourceNotFoundException;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class BizTreeService  implements IBizTreeService {

    private EsqTreeNodeRepository treeNodeRepository;
    private EsqEntityDictionaryRepository entityDictionaryRepository;
    private EsqEntityRepository entityRepository;
    private EsqCustomFieldRepository customEntityRepository;

    @Override
    public List<EsqTreeNode> esquire(String id, Integer skip, Integer take) {
        //xxx: ignore skip, take for now
        List<EsqTreeNodeJpa> nodes;
        if (id != null && !id.isEmpty()) {
            nodes = treeNodeRepository.findNodes(id);
        } else {
            nodes = treeNodeRepository.findRoot();
        }
        if (nodes == null) {// || nodes.isEmpty()) {
            throw new ResourceNotFoundException("esquire", "id", id==null?"''":id);
        }
        return EsqTreeNodeMapper.mapTo(nodes, new ArrayList<>());
    }

    @Override
    public EsqTreeNode esquireEntityNode(Integer kind, String id, String name) {
        EsqTreeNodeJpa node = null;
        if (id != null && !id.isEmpty()) {
            node = treeNodeRepository.findByEntityId(id);
        } else if (name != null && kind != null) {
            node = treeNodeRepository.findByNameKind(name, kind);
        }
        if (node == null) {
            throw new ResourceNotFoundException("esquireEntityNode", "id,name,kind", id + "," + name + "," + kind);
        }
        return EsqTreeNodeMapper.mapTo(node, new EsqTreeNode());
    }

    @Override
    public List<String> esquirePath(String id) {
        String ret = treeNodeRepository.findPath(id);
        return EsqTreeNodeMapper.pathArray(ret);
    }

    @Override
    public List<EsqEntityLayer> esquireDictionary(Integer kind) {
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
        return ret;
    }

    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd) {
        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqEntityFactory.EsqEntityKind eek = EsqEntityFactory.EsqEntityKind.getKind(k);
        EsqEntityJpa jpa = null;
        List<EsqNameValueJpa> custom = null;
        List<EsqTreeNodeJpa> children = null;

        if (eek.isOrg()) {
            jpa = entityRepository.detailOrg(id);
            custom = customEntityRepository.customOrg(id);
        } else if (eek.isUsr()) {
            jpa = entityRepository.detailUsr(id);
            custom = customEntityRepository.customUsr(id);
        } else if (eek.isAcct()) {
            jpa = entityRepository.detailAcct(id);
        }
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," +  id );
        }
        if (eek.isChildrenDetailed()) {
            children = treeNodeRepository.findNodes(id);;
        }
        return  EsqEntityFactory.getInstance().createEntity(jpa, custom, children);
    }

}

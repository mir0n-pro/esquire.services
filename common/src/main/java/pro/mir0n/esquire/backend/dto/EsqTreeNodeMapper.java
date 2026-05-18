/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n let's have '.' as path separator as generic
 * 02/13/2026 mir0n removed treeFlags
 * 05/14/2026 mir0n  entityPath populated from biztree tree_path with virtual-folder segments stripped
 *                   (stripVirtualSegments); produces the biztree-side path for CompareTrees diff
 */

package pro.mir0n.esquire.backend.dto;

import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EsqTreeNodeMapper {
    private EsqTreeNodeMapper() {
    }
    public static EsqTreeNode mapTo(EsqTreeNodeJpa node, EsqTreeNode nodeDto) {
        nodeDto.setId(node.getId());
        nodeDto.setParentId(node.getParentId());
        nodeDto.setLinkId(node.getLinkId());
        nodeDto.setName(node.getName());
        nodeDto.setKind(node.getKind());
        nodeDto.setEntityId(node.getEntityId());
        nodeDto.setStatusCode(node.getStatusCode());
        nodeDto.setLevel(node.getLevel());
        nodeDto.setDesc(node.getDesc());
        nodeDto.setPath(pathArray(node.getPath()));
        // entityPath on the biztree side is derived from tree_path by stripping
        // virtual-folder segments (those containing "~"). This avoids storing a
        // duplicate of tree_entity_path in the cache row mapping. The result
        // matches esq_entity_path.ep_path for kinds that follow the default
        // path semantics (orgs, regular USRs); for kinds with
        // isPathParentOnly=true (admins, accounts) it diverges and callers
        // should rely on the enyMan-side entityPath for those.
        nodeDto.setEntityPath(stripVirtualSegments(node.getPath()));
        return nodeDto;
    }

    /**
     * Removes virtual-folder segments (those containing "~") from a
     * dot-separated tree_path string. "1.5.5~8.12." -> "1.5.12.".
     * Used to derive entityPath from biztree's tree_path.
     */
    public static String stripVirtualSegments(String treePath) {
        if (treePath == null || treePath.isEmpty()) return treePath;
        StringBuilder ret = new StringBuilder();
        for (String s : treePath.split("[.]")) {
            if (s.isEmpty() || s.indexOf('~') >= 0) continue;
            ret.append(s).append('.');
        }
        return ret.toString();
    }


    public static List<EsqTreeNode> mapTo(List<EsqTreeNodeJpa> nodes, List<EsqTreeNode> nodesDto) {
        for (EsqTreeNodeJpa node : nodes) {
            nodesDto.add(mapTo(node, new EsqTreeNode()));
        }
        return nodesDto;
    }

    public static List<String> pathArray(String path) {
        List<String> ret = new ArrayList<>();
        if (path != null && !path.isEmpty()) {
            String[] pathArr = path.split("[.]");
            Collections.addAll(ret, pathArr);
        }
        return ret;

    }


}

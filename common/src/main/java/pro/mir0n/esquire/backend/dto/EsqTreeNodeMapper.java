/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n let's have '.' as path separator as generic
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
        nodeDto.setTreeFlags(node.getTreeFlags());
        nodeDto.setLevel(node.getLevel());
        nodeDto.setDesc(node.getDesc());
        nodeDto.setPath(pathArray(node.getPath()));
        return nodeDto;
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

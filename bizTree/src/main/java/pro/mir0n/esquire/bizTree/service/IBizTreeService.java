/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 * 01/23/2026 mir0n use common library
 *                  only EsqTreeNode requests
 * 05/14/2026 mir0n  esquireSubtree(id, rootPath, uid) added for /esq-tree
 */

package pro.mir0n.esquire.bizTree.service;

import java.util.List;

import pro.mir0n.esquire.backend.dto.EsqTreeNode;
//import pro.mir0n.esquire.backend.dto.EsqEntityLayer;

public interface IBizTreeService {

    List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid);
    List<String> esquirePath(String id, String rootPath);
    EsqTreeNode esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid);
    List<EsqTreeNode> esquireSubtree(String id, String rootPath, String uid);

    }

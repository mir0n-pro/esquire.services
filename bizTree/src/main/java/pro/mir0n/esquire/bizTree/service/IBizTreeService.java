/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 */

package pro.mir0n.esquire.bizTree.service;

import java.util.List;

import pro.mir0n.esquire.bizTree.dto.EsqEntity;
import pro.mir0n.esquire.bizTree.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.dto.EsqEntityLayer;

public interface IBizTreeService {

    List<EsqTreeNode> esquire(String id, Integer skip, Integer take, String rootPath, String uid);
    List<EsqEntityLayer> esquireDictionary(Integer kind);
    EsqTreeNode esquireEntityNode(Integer kind, String id, String name, String rootPath, String uid);
    List<String> esquirePath(String id, String rootPath);
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid );


    }

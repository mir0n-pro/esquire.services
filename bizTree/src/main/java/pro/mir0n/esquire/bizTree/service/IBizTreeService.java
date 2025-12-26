/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.service;

import java.util.List;

import pro.mir0n.esquire.bizTree.dto.EsqEntity;
import pro.mir0n.esquire.bizTree.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.dto.EsqEntityLayer;

public interface IBizTreeService {

    List<EsqTreeNode> esquire(String id, Integer skip, Integer take);
    List<EsqEntityLayer> esquireDictionary(Integer kind);
    EsqTreeNode esquireEntityNode(Integer kind, String id, String name);
    List<String> esquirePath(String id);
    public EsqEntity esquireCommand(Integer kind, String id, String cmd);


    }

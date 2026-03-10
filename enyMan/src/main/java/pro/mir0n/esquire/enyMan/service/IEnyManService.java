/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 * 01/23/2026 mir0n use common library
 *                  no more EsqTreeNode methods
 * 02/19/2026 mir0n added esquireCommandSave()
 * 03/09/2026 mir0n  esquireCommandSave(): roles param added
 */

package pro.mir0n.esquire.enyMan.service;

import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;

public interface IEnyManService {

    List<EsqEntityLayer> esquireDictionary(Integer kind);
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid );
    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles );

}

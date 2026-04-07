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
 * 03/26/2026 mir0n  esquireCommandNew(), esquireCommandDelete() added
 * 03/31/2026 mir0n  esquireCommandMove() added
 * 04/01/2026 mir0n  esquireCommandMove(): returns List<EsqMoveRecord> (id,kind,path per moved entity)
 * 04/07/2026 mir0n  all kind params Integer → int (primitive)
 */

package pro.mir0n.esquire.enyMan.service;

import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;

public interface IEnyManService {

    List<EsqEntityLayer> esquireDictionary(int kind);
    public EsqEntity esquireCommand(int kind, String id, String cmd, String rootPath, String uid );
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles );
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles);
    public void esquireCommandDelete(int kind, String id, String cmd, String rootPath, String uid, List<String> roles);
    public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, String rootPath, String uid, List<String> roles);

}

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
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
 * 05/14/2026 mir0n  esquireCommandTree(kind, id, rootPath, uid) added for /esq-cmd-tree
 * 06/02/2026 mir0n  esquireCommandMove(): javadoc note -- EnyManService impl now returns null
 *                   (async-ack: submits to move queue); per-kind impls still return records for the worker
 * 06/04/2026 mir0n  rootPath + uid params removed from esquireCommand / Save / New / Delete / Move / Tree --
 *                   read from the unified request context (RequestContextUtils)
 * 08/11/2026 mir0n  v1.2.12 -- esquireCommandDelete returns the delete's change number for the caller's
 *                   broadcast
 */

package pro.mir0n.esquire.enyMan.service;

import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;

public interface IEnyManService {

    // uid / rootPath are no longer method params: they belong to the unified per-request context
    // (EsqRequestContext) and are read inside each impl via RequestContextUtils.getUid() /
    // getRootPath() -- the same uniform way crl_id / req_id are already obtained.
    List<EsqEntityLayer> esquireDictionary(int kind);
    public EsqEntity esquireCommand(int kind, String id, String cmd );
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, List<String> roles );
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, List<String> roles);
    /** Deletes the entity and RETURNS the delete's change number -- the number the row's own counter took
     *  when it went (see {@code EsqEntityJpa.bumpChangeNo}). The caller needs it for the broadcast; a caller
     *  that does not may ignore it. Null when the implementation has no row behind the delete. */
    public Long esquireCommandDelete(int kind, String id, String cmd, List<String> roles);
    // v1.2.6 Goal 3: returns List<EsqMoveRecord> at the OrgService/UsrService level (the per-kind
    // workers still produce them for the worker thread to publish), but the top-level
    // EnyManService implementation returns null because /esq-move is now async-ack: handler
    // submits to the move queue and returns 202 Accepted without the records.
    public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, List<String> roles);

    // Tree query is a cross-cutting concern handled at the top-level orchestrator
    // (EnyManService), not by the per-kind services (OrgService / UsrService).
    default List<EsqTreeNode> esquireCommandTree(int kind, String id) {
        throw new UnsupportedOperationException("esquireCommandTree not implemented");
    }

}

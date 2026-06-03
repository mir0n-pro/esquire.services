/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: ep_path composition rule extracted from the per-kind createXxx
 *                   methods (OrgService / UsrService / AcctService). Single source of truth so
 *                   MoveQueueWorker can re-compute the expected path the same way the
 *                   create-time code did. The rule is: when isPathParentOnly() (acct + admin usr)
 *                   the path equals the parent path; otherwise the path is parent + own id + ".".
 */

package pro.mir0n.esquire.enyMan.queue;

import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

public final class PathRule {

    private PathRule() {}

    /**
     * Compute the ep_path an entity of {@code kind} should have given its
     * parent's path and its own id. Mirrors the inline composition in
     * OrgService.createOrg / UsrService.createUsr / AcctService.createAcct.
     */
    public static String expectedFor(int kind, String parentPath, String ownId) {
        String ret;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        if (eek.isPathParentOnly()) {
            ret = parentPath;
        } else {
            ret = parentPath + ownId + ".";
        }
        return ret;
    }
}

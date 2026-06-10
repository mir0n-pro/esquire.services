/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the ONE definition of the audit kind -> AuditLogSql-key map, shared by every
 *                   producer (in-process registry) and the standalone xxRod consumer (which must handle all
 *                   kinds). Entity kinds (org / user / account) come from the esq-object-kinds dictionary by
 *                   their semantic flags; sub-entity / parameter / auth kinds use the named EsqConstants.
 */
package pro.mir0n.esquire.common.audit;

import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.HashMap;
import java.util.Map;

public final class AuditKinds {

    private AuditKinds() {
    }

    /** The complete audit kind -> sql-key map (requires the kind dictionary to be loaded). */
    public static Map<Integer, String> all(EsqObjectKindStorage storage) {
        Map<Integer, String> ret = new HashMap<>();
        for (EsqObjectKind k : storage.getAll()) {
            if (k.isAcct()) {
                ret.put(k.getId(), AuditLogSql.ACCOUNT);
            } else if (k.isUsr()) {
                ret.put(k.getId(), AuditLogSql.USER);
            } else if (k.isOrg()) {
                ret.put(k.getId(), AuditLogSql.ORG);
            }
        }
        ret.put(EsqConstants.KIND_ORG_PAR, AuditLogSql.ORG_PAR);
        ret.put(EsqConstants.KIND_USR_PAR, AuditLogSql.USR_PAR);
        ret.put(EsqConstants.KIND_PERSON_PRIMARY,   AuditLogSql.PERSON);
        ret.put(EsqConstants.KIND_PERSON_SECONDARY, AuditLogSql.PERSON);
        ret.put(EsqConstants.KIND_PERSON_JOINT,     AuditLogSql.PERSON);
        ret.put(EsqConstants.KIND_ADDRESS_POSTAL, AuditLogSql.ADDRESS);
        ret.put(EsqConstants.KIND_ADDRESS_BIZ,    AuditLogSql.ADDRESS);
        ret.put(EsqConstants.KIND_ACCESS_PROFILE, AuditLogSql.AUTH);
        return ret;
    }
}

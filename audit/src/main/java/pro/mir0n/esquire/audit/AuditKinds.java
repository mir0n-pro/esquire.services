/*
 *  Esquire frameworks (tm)
 *  esquire-audit
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the ONE definition of the audit kind -> SQL-statement-key map, shared by every
 *                   producer (in-process keep) and the auKeep consumer (which handles all kinds). Entity kinds
 *                   (org / user / account) come from the esq-object-kinds dictionary by their semantic flags;
 *                   sub-entity / parameter / auth kinds use the named EsqConstants.
 * 06/18/2026 mir0n  self-contained: the statement-key names (the audit *_log data) live here now, not in the
 *                   (now generic) SQL store. This + the *_log SQL resources are the audit director's data.
 */
package pro.mir0n.esquire.audit;

import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.HashMap;
import java.util.Map;

/** The audit kind -> SQL-statement-key map + the statement keys themselves (matching META-INF/audit/*.xml). */
public final class AuditKinds {

    // The audit *_log statement keys -- one per *_log table; match <statement key="..."> in META-INF/audit/*.xml.
    public static final String ORG     = "org";
    public static final String ORG_PAR = "orgPar";
    public static final String USER    = "user";
    public static final String PERSON  = "person";
    public static final String ADDRESS = "address";
    public static final String USR_PAR = "usrPar";
    public static final String ACCOUNT = "account";
    public static final String AUTH    = "auth";

    private AuditKinds() {
    }

    /** The complete audit kind -> statement-key map (requires the kind dictionary to be loaded). */
    public static Map<Integer, String> all(EsqObjectKindStorage storage) {
        Map<Integer, String> ret = new HashMap<>();
        for (EsqObjectKind k : storage.getAll()) {
            if (k.isAcct()) {
                ret.put(k.getId(), ACCOUNT);
            } else if (k.isUsr()) {
                ret.put(k.getId(), USER);
            } else if (k.isOrg()) {
                ret.put(k.getId(), ORG);
            }
        }
        ret.put(EsqConstants.KIND_ORG_PAR, ORG_PAR);
        ret.put(EsqConstants.KIND_USR_PAR, USR_PAR);
        ret.put(EsqConstants.KIND_PERSON_PRIMARY,   PERSON);
        ret.put(EsqConstants.KIND_PERSON_SECONDARY, PERSON);
        ret.put(EsqConstants.KIND_PERSON_JOINT,     PERSON);
        ret.put(EsqConstants.KIND_ADDRESS_POSTAL, ADDRESS);
        ret.put(EsqConstants.KIND_ADDRESS_BIZ,    ADDRESS);
        ret.put(EsqConstants.KIND_ACCESS_PROFILE, AUTH);
        return ret;
    }
}

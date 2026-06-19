/*
 *  Esquire frameworks (tm)
 *  esquire-audit
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the AUDIT director.
 * 06/18/2026 mir0n  reduced to a pure declaration (the generic keep engine now owns the datasource, pool, SQL
 *                   store, the RodEvent->DB writer, the registry and the relay): the audit director says only its
 *                   SQL group ("audit") + the kinds it handles. The single audit-specific implementation of
 *                   IKeepDirector, used by BOTH an in-process producer keep (b) and the auKeep consumer (c).
 */
package pro.mir0n.esquire.audit;

import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.dataKeep.director.IKeepDirector;

import java.util.Map;

/** The audit keep director: declares the audit SQL group + the kinds audit handles. Nothing else -- the generic
 *  keep does the rest. */
public final class AuditKeepDirector implements IKeepDirector {

    /** The audit SQL resource group: META-INF/audit/{postgres,oracle}.xml. */
    public static final String SQL_GROUP = "audit";

    @Override
    public String sqlGroup() {
        return SQL_GROUP;
    }

    @Override
    public Map<Integer, String> kinds() {
        return AuditKinds.all(EsqObjectKindStorage.getInstance());
    }
}

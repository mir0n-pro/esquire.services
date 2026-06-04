/*
 *  Esquire frameworks (tm)
 *  KeySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  added esquireKeySave()
 * 03/06/2026 mir0n  FIELD_ROLES constant added
 * 03/09/2026 mir0n  esquireKeySave(): roles param added
 * 06/04/2026 mir0n  esquireKey / esquireKeySave: rootPath + uid params removed (read from request context)
 */

package pro.mir0n.esquire.keySmith.service;

import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;

public interface IKeySmithService {
    public final static String FIELD_ROLES = "roles";
    // uid / rootPath come from the unified per-request context (RequestContextUtils), not params.
    public EsqAccessProfile esquireKey(String id);
    public EsqAccessProfile esquireKeySave(String id, Map<String, Object> fields, List<String> roles);

}

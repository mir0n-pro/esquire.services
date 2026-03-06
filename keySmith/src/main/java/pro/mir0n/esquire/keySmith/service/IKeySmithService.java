/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  added esquireKeySave()
 * 03/06/2026 mir0n  FIELD_ROLES constant added
 */

package pro.mir0n.esquire.keySmith.service;

import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;

public interface IKeySmithService {
    public final static String FIELD_ROLES = "roles";
    public EsqAccessProfile esquireKey(String id, String rootPath, String uid );
    public EsqAccessProfile esquireKeySave(String id, Map<String, Object> fields, String rootPath, String uid );

}

/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.keySmith.service;

import java.util.List;

import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;

public interface IKeySmithService {

    public EsqAccessProfile esquireKey(String id, String rootPath, String uid );

}

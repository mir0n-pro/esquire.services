/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.keySmith.service.impl;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.access.*;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import pro.mir0n.esquire.backend.jpa.access.*;
import pro.mir0n.esquire.keySmith.jpa.EsqAccessProfileRepository;
import pro.mir0n.esquire.keySmith.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.keySmith.service.IKeySmithService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class KeySmithService implements IKeySmithService {

    private EsqAccessProfileRepository accessProfileRepository;

    @Override
    public EsqAccessProfile esquireKey(String id, String rootPath, String uid) {

        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();

        log.debug("srvc: esquireKey: id:{}, rootPath:{}, uid:{}",  id, rootPath, uid);

        String upk = id == null ? uid : id;

        EsqAccessProfileJpa jpa = accessProfileRepository.access(upk, rootPath);
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireKey", "id", upk);
        }
        List<EsqRoleJpa> roles = accessProfileRepository.roles(upk);
        List<EsqPermissionJpa> permissions = accessProfileRepository.permissions(upk);

        EsqAccessProfile ret = new EsqAccessProfile().fill(jpa, roles, permissions);
        log.debug("srvc: esquireKey(2): accessProfile:{}",  ret);
        return  ret;
    }

}

/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 * 01/12/2026 mir0n added "profile" command
 * 01/12/2026 mir0n BizTreeConstants moved to common package
 *                  Error handling with rfc9457 compliance
 *                  Debug logs added
 * 01/23/2026 mir0n use common library
 *                  no more EsqTreeNode methods  
 *                  use entityRepository.acctsAsNodes()
 * 02/12/2026 mir0n EsqObjectKind instead if EsqEntityKind
 *                  removed "profile" command
 * 02/13/2026 mir0n removed unused variables
 */

package pro.mir0n.esquire.pacMan.service.impl;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.query.Param;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.service.RequestContextUtils;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.pacMan.service.IPacManService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@Service
@AllArgsConstructor
public class PacManService  implements IPacManService {

    private EsqAcctRepository entityRepository;


    @Override
    public EsqEntity esquireCommand(Integer kind, String id, String cmd, String rootPath, String uid) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireCommand: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}",  kind, id, cmd, rootPath, uid);

        int k = (int)Math.floor( (double) kind/2 ) * 2;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(k);

        EsqEntityJpa jpa = null;
        // xxx: path is safe
        if (eek.isAcct()) {
            jpa = entityRepository.detailAcct(id, rootPath);
        }
        if (jpa == null) {
            throw new ResourceNotFoundException("esquireEntity", "kind, id", kind + "," + id);
        }

        EsqEntity ret = EsqEntityFactory.getInstance().createEntity(jpa, null, null);
        log.debug("srvc: esquireCommand(2): entity:{}",  ret);
        return  ret;
    }

    private int rootLevel(List<String> path, String uid) {
        int ret = 0;
        if (path.size() > 1) {
            ret = path.size() -1;
            if (path.get(ret).equals(uid)) {
                ret++; // xxx: non-admin user, root is the current user
            }
        }
        //log.debug("rootLevel: path={}, uid={}, ret={}", path, uid, ret);
        return ret;
    }

}

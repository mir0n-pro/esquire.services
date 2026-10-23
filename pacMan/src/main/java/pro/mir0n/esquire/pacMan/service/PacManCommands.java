/*
 *  Esquire frameworks (tm)
 *  pacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: the pacMan commands for every front: esquireCommandSave / esquireCommandDelete /
 *                   esquireCommandAcct / esquireCommand over IPacManService and AcctTransactionService, with the
 *                   develop log lines; the caller is read from the request context
 */

package pro.mir0n.esquire.pacMan.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.acct.service.AcctTransactionService;

import java.util.Map;

/**
 * The pacMan commands, the same for every front: REST (PacManController) and the biz bus (paacMan) call these.
 * The caller -- uid, root path, roles -- is read from the request context, which each front sets.
 *
 * @author mir0n
 */
@Slf4j
@Component
public class PacManCommands {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + PacManCommands.class.getName());

    private final IPacManService iPacManService;
    private final AcctTransactionService acctTransactionService;

    public PacManCommands(IPacManService iPacManService, AcctTransactionService acctTransactionService) {
        this.iPacManService = iPacManService;
        this.acctTransactionService = acctTransactionService;
    }

    public EsqEntity esquireCommandSave(Integer kind, String id, String cmd, Map<String, Object> fields) {
        EsqEntity ret = iPacManService.esquireCommandSave(kind, id, cmd, fields);
        devLog.debug("esquireCommandSave: kind:{}, id:{}, cmd:{}, result:{}", kind, id, cmd, ret);
        return ret;
    }

    public void esquireCommandDelete(Integer kind, String id, String cmd) {
        iPacManService.esquireCommandDelete(kind, id, cmd);
        devLog.debug("esquireCommandDelete: kind:{}, id:{}, cmd:{}", kind, id, cmd);
    }

    public AcctTransactionSingle esquireCommandAcct(Integer kind, String id, String cmd, Map<String, Object> fields) {
        AcctTransactionSingle ret = acctTransactionService.esquireCommandAcct(kind, id, cmd, fields);
        devLog.debug("esquireCommandAcct: kind:{}, id:{}, cmd:{}, result:{}", kind, id, cmd, ret);
        return ret;
    }

    public EsqEntity esquireCommand(Integer kind, String id, String cmd) {
        EsqEntity ret = iPacManService.esquireCommand(kind, id, cmd);
        devLog.debug("esquireCommand: kind:{}, id:{}, cmd:{}, result:{}", kind, id, cmd, ret);
        return ret;
    }
}

/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 04/09/2026 mir0n  created: account transaction command; POST /esq-acct deposit/credit with amount/status/balance validation
 * 04/12/2026 mir0n  KIND_ACCTTR: 980 -> 1000 (aligns with esq-entity-dictionaries.xml kind)
 *                   skipValidation: explicit boolean parameter (no longer derived from fields map)
 *                   field validation: EntityFieldUtils.applyFields(KIND_ACCTTR, fields) — dictionary-driven with listvalues check
 * 04/13/2026 mir0n  refactored: thin router; processing split to AcctTransactionProcessorSingle / AcctTransactionProcessorTransfer
 *                   pre-validates fields/typeId/UNKNOWN/amount; routes by AcctOperation.Code.transfer flag
 * 06/04/2026 mir0n  esquireCommandAcct: rootPath + uid params removed; read via RequestContextUtils and
 *                   passed to the single / transfer processors (processor signatures unchanged)
 */

package pro.mir0n.esquire.pacMan.acct.service;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.pacMan.acct.AcctOperation;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AcctTransactionService {

    private final AcctTransactionProcessorSingle processorSingle;
    private final AcctTransactionProcessorTransfer processorTransfer;

    public AcctTransactionService(
            EsqAcctRepository entityRepository,
            EsqAcctTransactionRepository transactionRepository,
            TransactionTemplate transactionTemplate,
            EntityManager em) {
        this.processorSingle   = new AcctTransactionProcessorSingle(entityRepository, transactionRepository, transactionTemplate, em);
        this.processorTransfer = new AcctTransactionProcessorTransfer(entityRepository, transactionRepository, transactionTemplate, em);
    }

    public AcctTransactionSingle esquireCommandAcct(int kind, String id, String cmd, Map<String, Object> fields, List<String> roles) {
        String rootPath = RequestContextUtils.getRootPath();
        String uid = RequestContextUtils.getUid();
        if (fields == null) {
            throw new IllegalArgumentException("acctTransaction: missing fields map");
        }
        Object rawTypeId = fields.get(AcctTransactionSingle.FIELD_TYPE_ID);
        if (rawTypeId == null) {
            throw new IllegalArgumentException("acctTransaction: missing field: " + AcctTransactionSingle.FIELD_TYPE_ID);
        }
        int typeId = rawTypeId instanceof Number ? ((Number) rawTypeId).intValue() : Integer.parseInt(rawTypeId.toString());
        AcctOperation.Code op = AcctOperation.Code.valueOf(typeId);
        if (op == AcctOperation.Code.UNKNOWN) {
            throw new IllegalArgumentException("acctTransaction: unknown operation typeId: " + typeId);
        }
        Object rawAmount = fields.get(AcctTransactionSingle.FIELD_AMOUNT);
        if (rawAmount == null) {
            throw new IllegalArgumentException("acctTransaction: missing field: " + AcctTransactionSingle.FIELD_AMOUNT);
        }

        AcctTransactionSingle ret = null;
        if (op.transfer) {
            ret = processorTransfer.esquireCommandAcct(kind, id, op, fields, rootPath, uid, roles);
        } else {
            ret = processorSingle.esquireCommandAcct(kind, id, op, fields, rootPath, uid, roles);
        }
        return ret;
    }
}

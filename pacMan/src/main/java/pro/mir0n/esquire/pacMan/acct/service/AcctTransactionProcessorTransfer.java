/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/13/2026 mir0n  created: two-leg transfer processor (draft); debit source leg, credit target leg with -amount and skipValidation=true
 */

package pro.mir0n.esquire.pacMan.acct.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.service.EntityFieldUtils;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.pacMan.acct.AcctOperation;
import pro.mir0n.esquire.pacMan.acct.IAcctTransactionProcessor;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.service.IPacManService;

import java.util.List;
import java.util.Map;

@Slf4j
public class AcctTransactionProcessorTransfer extends AcctTransactionProcessorSingle {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + AcctTransactionProcessorTransfer.class.getName());

    public AcctTransactionProcessorTransfer(EsqAcctRepository entityRepository, EsqAcctTransactionRepository transactionRepository, TransactionTemplate transactionTemplate, EntityManager em) {
        super(entityRepository, transactionRepository, transactionTemplate, em);
    }

    public AcctTransactionSingle esquireCommandAcct(int kind, String id, AcctOperation.Code oper, Map<String, Object> fields, boolean skipValidation, String rootPath, String uid, List<String> roles) {
        Object rawId2 = fields.get(AcctTransactionSingle.FIELD_ID2);
        Object rawKind2 = fields.get(AcctTransactionSingle.FIELD_KIND2);
        if (rawId2 == null || rawKind2 == null) {
            throw new IllegalArgumentException("acctTransaction: missing fields: " + AcctTransactionSingle.FIELD_ID2 + ", " + AcctTransactionSingle.FIELD_KIND2 );
        }
        int kind2 = rawKind2 instanceof Number ? ((Number) rawKind2).intValue() : Integer.parseInt(rawKind2.toString());
        String id2 = rawKind2 instanceof String ? ((String) rawId2) : rawId2.toString();
        EsqObjectKind eek = validatePermissions(kind, roles);
        EsqObjectKind eek2 = validatePermissions(kind2, roles);
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();

        AcctTransactionSingle ret  = _esquireCommandAcct(eek,  id,  oper, fields,  skipValidation, rootPath, uid, correlationId, requestId);

        Object rawAmount = fields.get(AcctTransactionSingle.FIELD_AMOUNT);
        double amount = rawAmount instanceof Number ? ((Number) rawAmount).doubleValue() : Double.parseDouble(rawAmount.toString());
        fields.put(AcctTransactionSingle.FIELD_AMOUNT, -amount);
        AcctTransactionSingle ret2 = _esquireCommandAcct(eek2, id2, oper, fields, true,  rootPath, uid, correlationId, requestId);
        return ret;
    }
}

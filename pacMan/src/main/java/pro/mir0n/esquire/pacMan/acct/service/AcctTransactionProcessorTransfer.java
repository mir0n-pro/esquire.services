/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 04/13/2026 mir0n  created: two-leg transfer processor (draft); debit source leg, credit target leg with -amount and skipValidation=true
 * 04/14/2026 mir0n  instanceof check bug fixed (was testing rawKind2, now rawId2);
 *                   same-account guard added (InvalidValueException); paper account restriction added
 * 04/20/2026 mir0n  FIELD_RATE required (must be > 0); credit amount = abs(debit) * rate;
 *                   shared pkTx links both legs; sourceCcy forwarded to credit leg
 * 06/05/2026 mir0n  XYRod ctor param added + forwarded to super (both transfer legs audit the balance change)
 */

package pro.mir0n.esquire.pacMan.acct.service;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.pacMan.acct.AcctOperation;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;

import java.util.List;
import java.util.Map;

@Slf4j
public class AcctTransactionProcessorTransfer extends AcctTransactionProcessorSingle {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + AcctTransactionProcessorTransfer.class.getName());

    public AcctTransactionProcessorTransfer(EsqAcctRepository entityRepository, EsqAcctTransactionRepository transactionRepository, TransactionTemplate transactionTemplate, EntityManager em, pro.mir0n.esquire.common.xrod.XYRod xyRod) {
        super(entityRepository, transactionRepository, transactionTemplate, em, xyRod);
    }

    public AcctTransactionSingle esquireCommandAcct(int kind, String id, AcctOperation.Code oper, Map<String, Object> fields, boolean skipValidation, String rootPath, String uid, List<String> roles) {
        Object rawId2 = fields.get(AcctTransactionSingle.FIELD_ID2);
        Object rawKind2 = fields.get(AcctTransactionSingle.FIELD_KIND2);
        if (rawId2 == null || rawKind2 == null) {
            throw new IllegalArgumentException("acctTransaction: missing fields: " + AcctTransactionSingle.FIELD_ID2 + ", " + AcctTransactionSingle.FIELD_KIND2 );
        }
        int kind2 = rawKind2 instanceof Number ? ((Number) rawKind2).intValue() : Integer.parseInt(rawKind2.toString());
        String id2 = rawId2 instanceof String ? (String) rawId2 : rawId2.toString();
        if (id.equals(id2)) {
            throw new InvalidValueException("Transfer source and target must be different accounts", AcctTransactionSingle.FIELD_ID2, "id2", "1");
        }
        if (kind == AcctOperation.ACCT_KIND_PAPER || kind2 == AcctOperation.ACCT_KIND_PAPER) {
            throw new InvalidValueException("Paper accounts cannot be transferred", "kind", "kind", "1");
        }
        Object rawRate = fields.get(AcctTransactionSingle.FIELD_RATE);
        if (rawRate == null) {
            throw new InvalidValueException("Conversion rate is required", AcctTransactionSingle.FIELD_RATE, "rate", "1");
        }
        double rate = rawRate instanceof Number ? ((Number) rawRate).doubleValue() : Double.parseDouble(rawRate.toString());
        if (rate <= 0.0) {
            throw new InvalidValueException("Conversion rate must be positive", AcctTransactionSingle.FIELD_RATE, "rate", "1");
        }

        EsqObjectKind eek = validatePermissions(kind, roles);
        EsqObjectKind eek2 = validatePermissions(kind2, roles);
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        String pkTx = generateTransId();

        Object rawAmount = fields.get(AcctTransactionSingle.FIELD_AMOUNT);
        double amount = rawAmount instanceof Number ? ((Number) rawAmount).doubleValue() : Double.parseDouble(rawAmount.toString());

        AcctTransactionSingle ret = _esquireCommandAcct(eek, id, oper, fields, skipValidation, rootPath, uid, correlationId, requestId, rate, null, null, pkTx, id2);

        String sourceCcy = ret.getCcy();
        double creditAmount = Math.abs(amount) * rate;
        fields.put(AcctTransactionSingle.FIELD_AMOUNT, creditAmount);
        _esquireCommandAcct(eek2, id2, oper, fields, true, rootPath, uid, correlationId, requestId, rate, Math.abs(amount), sourceCcy, pkTx, id);
        return ret;
    }
}

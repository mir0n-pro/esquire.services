/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/13/2026 mir0n  created: single-leg acct transaction processor; permission check, amount/status/balance validation, EntityFieldUtils field validation, insert + balance update
 * 04/14/2026 mir0n  detailAcctForUpdate call: kind param dropped
 * 04/15/2026 mir0n  transaction PK: EsqUtils.generateEntityId() replaced by transactionRepository.nextId() (ESQ_ATR_SEQ)
 * 04/20/2026 mir0n  conversion rate support: convRate/amtIncoming/ccyIncoming/pkTx/counterpartId params threaded through;
 *                   generateTransId() replaces nextId(); ccy populated in result; refCode4 auto-note on transfer legs
 * 06/05/2026 mir0n  XYRod injected; balance change posts an x-Rod account UPDATE audit event (new balance +
 *                   funded_dt mirror on first funding)
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
import pro.mir0n.esquire.pacMan.acct.AcctOperation;
import pro.mir0n.esquire.pacMan.acct.IAcctTransactionProcessor;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.service.IPacManService;

import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class AcctTransactionProcessorSingle implements IAcctTransactionProcessor {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + AcctTransactionProcessorSingle.class.getName());


    private EsqAcctRepository entityRepository;
    private EsqAcctTransactionRepository transactionRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;
    private pro.mir0n.esquire.common.xrod.XYRod xyRod;   // audit: balance change -> account UPDATE

    /** skipValidation: For test use only — allows bypassing status/balance/field validation. */
    public AcctTransactionSingle esquireCommandAcct(int kind, String id, AcctOperation.Code oper, Map<String, Object> fields, boolean skipValidation, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        EsqObjectKind eek = validatePermissions(kind, roles);
        return _esquireCommandAcct(eek, id, oper, fields, skipValidation, rootPath, uid, correlationId, requestId, null, null, null, null, null);
    }

    protected EsqObjectKind validatePermissions(int kind, List<String> roles) {
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        int k = eek.getId();
        if (!eek.isAcct()) {
            throw new ResourceNotFoundException("esquireCommandAcct", "kind", String.valueOf(kind));
        }

        Map<Integer, EsqPermission> permissions = EsqRolesStorage.getInstance().findAdminPermissions(roles);
        boolean permitted = false;
        if (permissions != null) {
            permitted = EsqRolesStorage.getInstance().isAdminCmdPermitted(
                    permissions.get(k),
                    EsqRolesStorage.AdminCmd.ACCT
            );
        }
        if (!permitted) {
            throw new PermissionDeniedException(eek.getTitle(), "acct");
        }
        return eek;
    }

    protected AcctTransactionSingle _esquireCommandAcct(EsqObjectKind eek,
            String id, AcctOperation.Code oper,
            Map<String, Object> fields,
            boolean skipValidation,
            String rootPath,
            String uid,
            String correlationId,
            String requestId,
            Double convRate,
            Double amtIncoming,
            String ccyIncoming,
            String pkTx,
            String counterpartId) {

        devLog.debug("srvc: esquireCommandAcct: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", eek.getId(), id, oper, rootPath, uid);
        AcctTransactionSingle result[] = {null}; // xxx: trick to handle lambda syntax

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            result[0] = postAcctTransaction(eek, id, fields, oper, skipValidation, rootPath, uid, correlationId, requestId, convRate, amtIncoming, ccyIncoming, pkTx, counterpartId);
            return null;
        });
        devLog.debug("srvc: esquireCommandAcct(2): result:{}", result);
        return result[0];
    }

    private AcctTransactionSingle postAcctTransaction(EsqObjectKind eek,
                                                      String acctId,
                                                      Map<String, Object> fields,
                                                      AcctOperation.Code oper,
                                                      boolean skipValidation,
                                                      String rootPath, String uid,
                                                      String correlationId, String requestId,
                                                      Double convRate, Double amtIncoming, String ccyIncoming,
                                                      String pkTx, String counterpartId) {
        Object rawAmount = fields.get(AcctTransactionSingle.FIELD_AMOUNT);
        double amount = rawAmount instanceof Number ? ((Number) rawAmount).doubleValue() : Double.parseDouble(rawAmount.toString());
        if (!skipValidation) {
            switch (oper.effect) {
                case AcctOperation.AmountEffect.NEGATIVE:
                    if (amount >= 0) {
                        throw new InvalidValueException("Amount must be negative", AcctTransactionSingle.FIELD_AMOUNT, "Amount", "1");
                    }
                    break;
                case AcctOperation.AmountEffect.POSITIVE:
                    if (amount <= 0) {
                        throw new InvalidValueException("Amount must be positive", AcctTransactionSingle.FIELD_AMOUNT, "Amount", "1");
                    }
                    break;
                default:
                    if (amount == 0.0) {
                        throw new InvalidValueException("Amount must not be zero", AcctTransactionSingle.FIELD_AMOUNT, "Amount", "1");
                    }
                    break;
            }
        }

        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(acctId, eek.getId(), rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("postAcctTransaction", "acct Id", acctId);
        }

        if (!skipValidation && !EsqMsgConstants.FLAG_OPEN.equals(acct.getStatus())) {
            throw new InvalidValueException("Account is not open", IPacManService.FIELD_STATUS, "Status", "1");
        }
        if (!skipValidation && "N".equals(acct.getNegativeAllowed())) {
            if (acct.getBalance() + amount < 0) {
                throw new InvalidValueException("Insufficient balance",AcctTransactionSingle.FIELD_AMOUNT, "Amount", "1");
            }
        }

        double prevBalance = acct.getBalance() != null ? acct.getBalance() : 0.0;
        double newBalance  = prevBalance + amount;

        Map<String, Object> validated = fields;
        if (!skipValidation) {
            validated = EntityFieldUtils.applyFields(oper.kind, fields);
        }

        String trPk = generateTransId();

        AcctTransactionSingle ret = new AcctTransactionSingle();
        ret.fill(validated);
        ret.setId(trPk);
        ret.setKind(eek.getId());
        ret.setTypeId(oper.id);
        ret.setAmount(amount);
        ret.setCcy(acct.getCcy());
        ret.setConvRate(convRate);
        ret.setAmtIncoming(amtIncoming);
        if (counterpartId != null) {
            String note = amtIncoming != null
                ? String.format("%s %.2f %s (%.2f %s) from Account %s",
                    oper.name, Math.abs(amount), acct.getCcy(), amtIncoming, ccyIncoming, counterpartId)
                : String.format("%s %.2f %s to Account %s",
                    oper.name, Math.abs(amount), acct.getCcy(), counterpartId);
            ret.setRefCode4(note);
        }
        ret.setCcyIncoming(ccyIncoming);

        transactionRepository.insertAcctTransaction(
                trPk, pkTx, Long.parseLong(acctId), oper.id,
                amount, prevBalance,
                ret.getDesc(), ret.getRefCode(), ret.getRefCode2(), ret.getRefCode3(), ret.getRefCode4(),
                ret.getMemo(), correlationId, requestId, uid,
                amtIncoming, ccyIncoming, convRate);

        entityRepository.updateAcctBalance(acctId, newBalance, uid, correlationId, requestId);
        // audit: balance change -> account UPDATE. Reflect the new balance on the loaded acct, and mirror the
        // updateAcctBalance COALESCE(acc_funded_dt, NOW()) so the first funding shows funded_dt in the log too.
        acct.setBalance(newBalance);
        if (acct.getFundedDate() == null || acct.getFundedDate().isEmpty()) {
            acct.setFundedDate(java.time.LocalDate.now().toString());
        }
        xyRod.post(pro.mir0n.esquire.common.xrod.RodEvent.Op.UPDATE, acct.getKind(), acctId, null, acct);
        return ret;
    }
}

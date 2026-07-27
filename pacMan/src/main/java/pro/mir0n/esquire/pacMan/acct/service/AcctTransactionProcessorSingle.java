/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 04/13/2026 mir0n  created: single-leg acct transaction processor; permission check, amount/status/balance validation, EntityFieldUtils field validation, insert + balance update
 * 04/14/2026 mir0n  detailAcctForUpdate call: kind param dropped
 * 04/15/2026 mir0n  transaction PK: EsqUtils.generateEntityId() replaced by transactionRepository.nextId() (ESQ_ATR_SEQ)
 * 04/20/2026 mir0n  conversion rate support: convRate/amtIncoming/ccyIncoming/pkTx/counterpartId params threaded through;
 *                   generateTransId() replaces nextId(); ccy populated in result; refCode4 auto-note on transfer legs
 * 06/05/2026 mir0n  XYRod injected; balance change posts an x-Rod account UPDATE audit event (new balance +
 *                   funded_dt mirror on first funding)
 * 06/15/2026 mir0n  audit producer is now messaging.xrod.IXRod (was common.xrod.XYRod); the balance-change
 *                   post() carries an explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT)
 * 06/17/2026 mir0n  audit producer IXRod -> AuditBusBridge; the balance-change post() drops the trailing MSG_TYPE_AUDIT arg
 * 06/18/2026 mir0n  audit module left common: AuditBusBridge moved to pro.mir0n.esquire.audit
 * 06/22/2026 mir0n  RodEvent import: messaging.xrod.RodEvent -> messaging.RodEvent (package move)
 * 06/23/2026 mir0n  EsqMsgConstants app constants -> common.EsqConstants (references repointed)
 * 07/08/2026 mir0n  esquireCommandAcct() body wrapped in EsqTraceMark.around("esq.svc.acct.tx", "account
 *                   transaction", ...) -- this processor is constructed with new() by AcctTransactionService,
 *                   so Spring never proxies it and @EsqTraced would not be advised
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- esquireCommandAcct() counts esq.biz.acct.tx.total and times
 *                   esq.biz.acct.tx.duration (tags type = AcctOperation.Code, outcome = ok|denied|error);
 *                   _esquireCommandAcct() counts esq.biz.acct.fx.apply.total when convRate is non-null (a
 *                   conversion rate is present only on the cross-currency leg, so it IS the FX application).
 *                   operTag() added: the type tag is NULL-SAFE because these read from a finally, and a raw
 *                   oper.name() there throws an NPE that REPLACES the real exception on its way out
 * 07/23/2026 mir0n  v1.2.11 -- round3(): the amount and the new balance are rounded to 3 decimals (the NUMERIC(16,3)
 *                   scale, half away from zero) before any check or store, so double FP dust never reaches the ledger
 */

package pro.mir0n.esquire.pacMan.acct.service;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import pro.mir0n.esquire.backend.o11y.EsqTraceMark;
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
import pro.mir0n.esquire.common.EsqConstants;
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
    private pro.mir0n.esquire.audit.AuditBusBridge audit;   // audit: balance change -> account UPDATE

    /** skipValidation: For test use only — allows bypassing status/balance/field validation. */
    public AcctTransactionSingle esquireCommandAcct(int kind, String id, AcctOperation.Code oper, Map<String, Object> fields, boolean skipValidation, String rootPath, String uid, List<String> roles) {
        AcctTransactionSingle ret = null;
        // esq.biz.acct.tx.total / .duration (O1/T8 phase B) -- the money path, counted and timed by OPERATION.
        // EsqBizMeters is static for the same reason EsqTraceMark is (see the mark below): this processor is
        // new()-ed, never proxied, so nothing can be injected into it. Both tags bounded: type is the
        // AcctOperation.Code enum, outcome is ok | denied | error.
        String outcome = "error";
        long startedAt = System.nanoTime();
        try {
            // Programmatic mark, not @EsqTraced: this processor is constructed with new() by
            // AcctTransactionService, so Spring never proxies it and the annotation would not be advised.
            ret = EsqTraceMark.around("esq.svc.acct.tx", "account transaction", () -> {
                String correlationId = RequestContextUtils.getCorrelationId();
                String requestId = RequestContextUtils.getRequestId();
                EsqObjectKind eek = validatePermissions(kind, roles);
                return _esquireCommandAcct(eek, id, oper, fields, skipValidation, rootPath, uid, correlationId, requestId, null, null, null, null, null);
            });
            outcome = "ok";
        } catch (PermissionDeniedException e) {
            outcome = "denied";
            throw e;
        } finally {
            EsqBizMeters.count("esq.biz.acct.tx.total", "type", operTag(oper), "outcome", outcome);
            EsqBizMeters.time("esq.biz.acct.tx.duration", System.nanoTime() - startedAt, "type", operTag(oper));
        }
        return ret;
    }

    /**
     * The tx-type tag, NULL-SAFE.
     *
     * <p>oper can legitimately be null (a malformed request reaches the processor before validation refuses it),
     * and these meters are read from a finally block. A raw oper.name() there throws an NPE that REPLACES the
     * real exception on its way out -- turning a clean PermissionDenied into an NPE and hiding what actually
     * happened. Instrumentation must never alter control flow; a meter that can throw is worse than no meter.
     */
    protected static String operTag(AcctOperation.Code oper) {
        return (oper != null) ? oper.name() : "unknown";
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
        // esq.biz.acct.fx.apply.total (O1/T8 phase B): a conversion rate is present only on the cross-currency
        // leg of a transfer, so a non-null convRate IS the FX application -- no new plumbing, just the condition
        // that already decides it. Counted here because both the single and the transfer path come through.
        if (convRate != null) {
            EsqBizMeters.count("esq.biz.acct.fx.apply.total", "type", operTag(oper));
        }
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
        amount = round3(amount);   // 3dp (NUMERIC(16,3)) -- clip double FP dust before any check or store
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

        if (!skipValidation && !EsqConstants.FLAG_OPEN.equals(acct.getStatus())) {
            throw new InvalidValueException("Account is not open", IPacManService.FIELD_STATUS, "Status", "1");
        }
        if (!skipValidation && "N".equals(acct.getNegativeAllowed())) {
            if (acct.getBalance() + amount < 0) {
                throw new InvalidValueException("Insufficient balance",AcctTransactionSingle.FIELD_AMOUNT, "Amount", "1");
            }
        }

        double prevBalance = acct.getBalance() != null ? acct.getBalance() : 0.0;
        double newBalance  = round3(prevBalance + amount);   // 3dp -- clip the addition's FP dust before store

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
        audit.post(pro.mir0n.esquire.messaging.RodEvent.Op.UPDATE, acct.getKind(), acctId, null, acct);
        return ret;
    }

    /** Round a money value to 3 decimals (the NUMERIC(16,3) scale), half away from zero, so the double
     *  arithmetic's binary-floating-point dust never reaches the ledger amount or the stored balance. Rounds the
     *  MAGNITUDE and reapplies the sign, so round3(-x) == -round3(x): a transfer's debit (signed) and credit (abs)
     *  legs stay balanced even for an amount sitting exactly on a 3rd-decimal tie. */
    private static double round3(double amt) {
        double a = amt < 0 ? -amt : amt;
        long l = (long) Math.floor(a * 1000 + 0.5);
        double ret = (double) l / 1000;
        return amt < 0 ? -ret : ret;
    }
}

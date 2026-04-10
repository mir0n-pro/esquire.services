/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/09/2026 mir0n  created: account transaction command; POST /esq-acct deposit/credit with amount/status/balance validation
 */

package pro.mir0n.esquire.pacMan.acct.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.access.EsqPermission;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.backend.storage.EsqRolesStorage;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.service.RequestContextUtils;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.EsqUtils;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSimple;
import pro.mir0n.esquire.pacMan.acct.jpa.EsqAcctTransactionRepository;
import pro.mir0n.esquire.pacMan.jpa.EsqAcctRepository;
import pro.mir0n.esquire.pacMan.service.IPacManService;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class AcctTransactionService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + AcctTransactionService.class.getName());

    private static final int    KIND_ACCTTR          = 980;
    private static final String FIELD_AMOUNT         = "amount";
    private static final String FIELD_TYPE_ID        = "typeId";
    private static final String FIELD_SKIP_VALIDATION = "skipValidation";

    private EsqAcctRepository entityRepository;
    private EsqAcctTransactionRepository transactionRepository;
    private TransactionTemplate transactionTemplate;
    private EntityManager em;

    public AcctTransactionSimple esquireCommandAcct(int kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        String correlationId = RequestContextUtils.getCorrelationId();
        String requestId = RequestContextUtils.getRequestId();
        devLog.debug("srvc: esquireCommandAcct: kind:{}, id:{}, cmd:{}, rootPath:{}, uid:{}", kind, id, cmd, rootPath, uid);

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

        AcctTransactionSimple[] result = {null};

        transactionTemplate.execute(status -> {
            em.setFlushMode(FlushModeType.COMMIT);
            result[0] = postAcctTransaction(k, id, fields, rootPath, uid, correlationId, requestId);
            return null;
        });

        devLog.debug("srvc: esquireCommandAcct(2): result:{}", result[0]);
        return result[0];
    }

    private AcctTransactionSimple postAcctTransaction(int acctKind, String id, Map<String, Object> fields,
                                                       String rootPath, String uid,
                                                       String correlationId, String requestId) {
        EsqAcctJpa acct = entityRepository.detailAcctForUpdate(id, rootPath);
        if (acct == null) {
            throw new ResourceNotFoundException("postAcctTransaction", "id", id);
        }

        Object rawAmount = fields.get(FIELD_AMOUNT);
        if (rawAmount == null) {
            return null; // skip: no amount provided
        }
        double amount = rawAmount instanceof Number ? ((Number) rawAmount).doubleValue() : Double.parseDouble(rawAmount.toString());
        if (amount == 0.0) {
            return null; // skip zero transaction
        }
        if (amount < 0) {
            throw new InvalidValueException("Amount must be positive", FIELD_AMOUNT, "Amount", "1");
        }

        boolean skipValidation = Boolean.TRUE.equals(fields.get(FIELD_SKIP_VALIDATION));
        if (!skipValidation && !EsqMsgConstants.FLAG_OPEN.equals(acct.getStatus())) {
            throw new InvalidValueException("Account is not open", IPacManService.FIELD_STATUS, "Status", "1");
        }
        if (!skipValidation && "N".equals(acct.getNegativeAllowed())) {
            if (acct.getBalance() + amount < 0) {
                throw new InvalidValueException("Insufficient balance", FIELD_AMOUNT, "Amount", "1");
            }
        }

        double prevBalance = acct.getBalance() != null ? acct.getBalance() : 0.0;
        double newBalance  = prevBalance + amount;

        Object rawTypeId = fields.get(FIELD_TYPE_ID);
        int    typeId    = rawTypeId != null ? (rawTypeId instanceof Number ? ((Number) rawTypeId).intValue() : Integer.parseInt(rawTypeId.toString())) : KIND_ACCTTR;
        String desc      = (String) fields.get("desc");
        String refCode   = (String) fields.get("refCode");
        String refCode2  = (String) fields.get("refCode2");
        String refCode3  = (String) fields.get("refCode3");
        String refCode4  = (String) fields.get("refCode4");
        String memo      = (String) fields.get("memo");

        long trPk = EsqUtils.generateEntityId();

        transactionRepository.insertAcctTransaction(
                trPk, Long.parseLong(id), typeId,
                amount, prevBalance,
                desc, refCode, refCode2, refCode3, refCode4,
                memo, correlationId, requestId, uid);

        entityRepository.updateAcctBalance(id, newBalance, uid, correlationId, requestId);

        AcctTransactionSimple ret = new AcctTransactionSimple();
        ret.setId(String.valueOf(trPk));
        ret.setKind(KIND_ACCTTR);
        ret.setTypeId(typeId);
        ret.setAmount(amount);
        ret.setDesc(desc);
        ret.setRefCode(refCode);
        ret.setRefCode2(refCode2);
        ret.setRefCode3(refCode3);
        ret.setRefCode4(refCode4);
        ret.setMemo(memo);
        return ret;
    }
}

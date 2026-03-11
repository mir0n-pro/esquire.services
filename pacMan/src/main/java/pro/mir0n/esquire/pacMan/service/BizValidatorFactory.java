/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n created: biz validator — cannot close account with positive balance
 * 03/08/2026 mir0n  validate(): boolean personal param added (interface alignment, no behavior change)
 */

package pro.mir0n.esquire.pacMan.service;
import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.validator.IValidator;

import java.util.Map;

@Slf4j
public class BizValidatorFactory {

    private static final Map<Integer, IValidator> bizValidators = Map.of(
        IPacManService.KIND_CL_ACCT, new StatusBizValidator(),
        IPacManService.KIND_MR_ACCT, new StatusBizValidator(),
        IPacManService.KIND_P_ACCT, new StatusBizValidator()
    );

    public static final Map<Integer, IValidator> getBizValidators() {
        return bizValidators;
    }

    private static class StatusBizValidator implements IValidator {

        @Override
        public Object validate(EsqEntityJpa origin, EsqEntityKindFieldLayer kfl, boolean personal, Object value) {
//log.debug("keySmith:BizValidator:validate: value:{} balance:{}",  value, ((EsqAcctJpa)origin).getBalance());
            Object ret = value;
            EsqEntityField field = kfl.getField();
            if (field != null) {
//log.debug("keySmith:BizValidator:validate: {} value:{} balance:{}", field.getName(), value, ((EsqAcctJpa)origin).getBalance());
                if (field.getName().equals(IPacManService.FIELD_STATUS)
                && "C".equals(value)
                && ((EsqAcctJpa)origin).getBalance() > 0) {
                    throw new InvalidValueException("Cannot close account while it has balance", field.getName(),
                        field.getLabel(), String.valueOf(kfl.getLayer() -1));
                }
            }
            return ret;
        }
    }

}
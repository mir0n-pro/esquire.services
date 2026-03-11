/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n created: biz validator — max 1 admin role per user
 * 03/08/2026 mir0n  personal guard added: throws if personal=true (cannot change own permissions)
 * 03/10/2026 mir0n  unused import removed; getBizValidators() final modifier removed; comment corrected
 */

package pro.mir0n.esquire.keySmith.service;
import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.validator.IValidator;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.List;
import java.util.Map;

@Slf4j
public class BizValidatorFactory {

    private static final Map<Integer, IValidator> bizValidators = Map.of(
        EsqConstants.KIND_ACCESS_PROFILE, new RolesBizValidator()
    );

    public static Map<Integer, IValidator> getBizValidators() {
        return bizValidators;
    }

    private static class RolesBizValidator implements IValidator {

        @Override
        public Object validate(EsqEntityJpa origin, EsqEntityKindFieldLayer kfl, boolean personal, Object value) {
            Object ret = value;
log.debug("keySmith:BizValidator:validate: value:{}", value);
            EsqEntityField field = kfl.getField();
            if (field != null
            && field.getName().equals(IKeySmithService.FIELD_ROLES)) {
                if (personal) {   // we do not allow changing your own permissions
                    throw new InvalidValueException("You cannot change your own permissions", field.getName(),
                            kfl.getLabel(), String.valueOf(kfl.getLayer() -1));
                }
                try {
                    log.debug("keySmith:BizValidator:validate: {} value:{}", field.getName(), value);
                    List<?> lst = (List<?>) value;
                    int adminRoles = 0;
                    for (Object r : lst) {
                        if (r instanceof Map) {
                            int kind = ((Map<?, ?>) r).get("kind") != null ? Integer.parseInt(String.valueOf(((Map<?, ?>) r).get("kind"))) : 0;
                            if (kind == EsqConstants.KIND_ADMIN_ROLE) {
                                adminRoles++;
                            }
                        }
                    }
                    if (adminRoles > 1) {
                        throw new InvalidValueException("Cannot have more than one administrative role", field.getName(),
                                kfl.getLabel(), String.valueOf(kfl.getLayer() -1));
                    }
                } catch (NumberFormatException e) {
                     throw new InvalidValueException(e.getMessage(), field.getName(),
                             kfl.getLabel(), String.valueOf(kfl.getLayer() -1));

                }
            }
            return ret;
        }
    }

}
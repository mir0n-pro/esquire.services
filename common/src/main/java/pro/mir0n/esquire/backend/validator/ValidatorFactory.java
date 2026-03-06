/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n created: factory initializing generic + biz validators; validate() dispatch
 */

package pro.mir0n.esquire.backend.validator
;
import lombok.extern.slf4j.Slf4j;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

@Slf4j
public class ValidatorFactory implements IValidator {
    private static final ValidatorFactory itself = new ValidatorFactory();

    IValidator validator = new GenericValidator();
    Map<Integer, IValidator> bizValidators = null;
    //IValidator router = new RoutingValidator();
    private ValidatorFactory  () {};

    public static ValidatorFactory getInstance() {
        return itself;
    }

    public void init(Map<Integer, IValidator> bizValidators) {
        this.bizValidators = bizValidators;
    }

    @Override
    public Object validate(EsqEntityJpa origin, EsqEntityKindFieldLayer kfl, Object value) {
        Object ret = validator.validate(origin, kfl, value);
        if (bizValidators != null) {
            IValidator biz = bizValidators.get(kfl.getEntityKind());
//log.debug("ValidatorFactory:validate: kind:{} biz:{}", kfl.getEntityKind(), biz);
            if (biz != null) {
                ret = biz.validate(origin, kfl, ret );
            }
        }
        return ret;
    }

}
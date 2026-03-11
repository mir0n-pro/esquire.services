/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n created: validator interface
 * 03/08/2026 mir0n  validate() signature: boolean personal param added
 */

package pro.mir0n.esquire.backend.validator;

import org.springframework.beans.BeanWrapper;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

public interface IValidator {

    public Object validate(EsqEntityJpa origin, EsqEntityKindFieldLayer kfl, boolean personal, Object value );

}
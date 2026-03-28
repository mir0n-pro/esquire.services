/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/27/2026 mir0n  created: thrown when an entity cannot be deleted due to its current state
 *                   user: au_connect_flg = 'Y' (active auth); account: acc_status != 'C' (not closed)
 */

package pro.mir0n.esquire.backend.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class DeleteRestrictedException extends GenericRuntimeException {

    public DeleteRestrictedException(String entityName, String reason) {
        super("Cannot delete " + entityName + ": " + reason);
    }
}

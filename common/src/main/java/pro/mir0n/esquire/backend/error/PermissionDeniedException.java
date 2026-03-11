/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  created: HTTP 403 FORBIDDEN; extends GenericRuntimeException;
 *                   format: "You have no permission to %s %s" (command, resourceName)
 */

package pro.mir0n.esquire.backend.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class PermissionDeniedException extends GenericRuntimeException {

    public PermissionDeniedException(String resourceName, String command) {
        super(String.format("You have no permission to %s %s", command, resourceName));
    }

}

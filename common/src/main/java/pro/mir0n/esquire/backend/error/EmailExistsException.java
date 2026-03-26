/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/24/2026 mir0n  created: thrown when au_email already exists in esq_auth on user creation
 */

package pro.mir0n.esquire.backend.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class EmailExistsException extends GenericRuntimeException {

    public EmailExistsException(String email) {
        super("Email already registered: " + email);
    }
}

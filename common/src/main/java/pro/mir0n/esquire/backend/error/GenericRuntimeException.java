/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  created: base RuntimeException for all Esquire runtime exceptions
 */

package pro.mir0n.esquire.backend.error;

public class GenericRuntimeException extends RuntimeException {
    public GenericRuntimeException(String message) {
        super(message);
    }
}

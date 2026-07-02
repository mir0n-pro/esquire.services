/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/02/2026 mir0n  created: 400 Bad Request when a write command arrives without X-Request-ID
 */

package pro.mir0n.esquire.backend.error;

// Thrown when a writeable command (entity save / new / delete / move, account transaction,
// access-profile save) arrives without the client-supplied X-Request-ID header. The header is
// required so every write carries a client-controlled identity; this is a presence check only,
// not a uniqueness / dedup check. Extends GenericRuntimeException, so the global handler maps it
// to 400 Bad Request.
public class MissingRequestIdException extends GenericRuntimeException {
    public MissingRequestIdException() {
        super("X-Request-ID header is required for write operations");
    }
}

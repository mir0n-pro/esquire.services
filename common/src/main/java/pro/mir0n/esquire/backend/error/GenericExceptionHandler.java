/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  created: static exception handler utility; centralizes handleMethodArgumentNotValid,
 *                   handleGenericRuntimeException, handlePermissionDeniedException, handleException
 *                   used by all service GlobalExceptionHandlers as thin delegates
 * 03/10/2026 mir0n  handleMethodArgumentNotValid, handleException moved back to GlobalExceptionHandler (common)
 *                   only GenericRuntimeException(s) are here
 * 03/21/2026 mir0n  devLog added; log.warn on exception->dual error (log.error+devLog.error); unused imports removed
 * 03/26/2026 mir0n  handleEmailExists(): EmailExistsException -> 409 CONFLICT RFC 9457 response
 * 03/28/2026 mir0n  DeleteRestrictedException added to 409 handler alongside EmailExistsException
 * 08/15/2026 mir0n  v1.2.13 -- ResourceNotFoundException gets its own branch: 404 NOT_FOUND, title "Not Found"
 *                   (was the 400 BAD_REQUEST branch it shared with InvalidValueException)
 * 08/26/2026 mir0n  CommandNotAcceptedException answers 503 Service Unavailable
 * 10/19/2026 mir0n  v2.0.1 -- statusOf(Throwable) / titleOf(Throwable): the one exception -> status and title
 *                   mapping, for the REST response and the bus answer; handlePermissionDeniedException removed (the
 *                   403 comes from the same mapping); an exception that is not a GenericRuntimeException maps to
 *                   500
 */

package pro.mir0n.esquire.backend.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;

@Slf4j
public class GenericExceptionHandler{

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + GenericExceptionHandler.class.getName());

    public static ResponseEntity<ProblemDetail> handleGenericRuntimeException(GenericRuntimeException ex, HttpServletRequest request) {
        log.error(ex.getClass().getSimpleName() + ": {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        devLog.error(ex.getClass().getSimpleName() + ": {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        HttpStatus status = statusOf(ex);
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(request, status, titleOf(ex), null, ex);
        return ResponseEntity.status(status).body(problem);
    }

    /** The status an exception is answered with -- the one mapping for a REST response and a bus answer. */
    public static HttpStatus statusOf(Throwable ex) {
        HttpStatus ret;
        if (ex instanceof PermissionDeniedException) {
            ret = HttpStatus.FORBIDDEN;
        } else if (ex instanceof EmailExistsException || ex instanceof DeleteRestrictedException) {
            ret = HttpStatus.CONFLICT;
        } else if (ex instanceof CommandNotAcceptedException) {
            ret = HttpStatus.SERVICE_UNAVAILABLE;
        } else if (ex instanceof ResourceNotFoundException) {
            // The status the exception has always declared with @ResponseStatus(NOT_FOUND). An @ExceptionHandler
            // is resolved ahead of @ResponseStatus, so that annotation never reached the wire and the asked-for
            // thing being absent was answered as if the request were malformed.
            ret = HttpStatus.NOT_FOUND;
        } else if (ex instanceof GenericRuntimeException) {
            //InvalidValueException
            ret = HttpStatus.BAD_REQUEST;
        } else {
            ret = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ret;
    }

    /** The problem title that goes with {@link #statusOf}. */
    public static String titleOf(Throwable ex) {
        String ret;
        if (ex instanceof PermissionDeniedException) {
            ret = "Permission Denied";
        } else if (ex instanceof EmailExistsException || ex instanceof DeleteRestrictedException) {
            ret = "Conflict";
        } else if (ex instanceof CommandNotAcceptedException) {
            ret = "Service Unavailable";
        } else if (ex instanceof ResourceNotFoundException) {
            ret = "Not Found";
        } else if (ex instanceof GenericRuntimeException) {
            ret = "Validation Error";
        } else {
            ret = "An unexpected error occurred";
        }
        return ret;
    }

    protected static ResponseEntity<ProblemDetail> handlePermissionDeniedException(PermissionDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request,
                HttpStatus.FORBIDDEN,
                "Permission Denied",
                null,
                ex
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }
}

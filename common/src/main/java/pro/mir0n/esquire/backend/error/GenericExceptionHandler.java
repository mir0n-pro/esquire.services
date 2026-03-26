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
 * 03/21/2026 mir0n  devLog added; log.warn on exception→dual error (log.error+devLog.error); unused imports removed
 * 03/26/2026 mir0n  handleEmailExists(): EmailExistsException → 409 CONFLICT RFC 9457 response
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
        ResponseEntity<ProblemDetail> response;
        if (ex instanceof PermissionDeniedException) {
            response = handlePermissionDeniedException((PermissionDeniedException) ex, request);
        } else if (ex instanceof EmailExistsException) {
            ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                    request,
                    HttpStatus.CONFLICT,
                    "Conflict",
                    null,
                    ex
            );
            response = ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        } else {
            //InvalidValueException, ResourceNotFoundException
            ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                    request,
                    HttpStatus.BAD_REQUEST,
                    "Validation Error",
                    null,
                    ex
            );
            response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        }
        return response;
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

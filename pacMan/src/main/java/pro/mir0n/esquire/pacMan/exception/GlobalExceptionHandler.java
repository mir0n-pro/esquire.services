/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 *  01/14/2026 mir0n  BizTreeExceptionHandler.java renamed with GlobalExceptionHandler
 *                    Error handling with rfc9457 compliance
 * 01/21/2026 mir0n  ProblemDetailMill moved to backend common package
 * 03/09/2026 mir0n  @Slf4j + logging added; PermissionDeniedException (HTTP 403) and
 *                   InvalidValueException (HTTP 400) handlers added
 *                   ResourceNotFoundException: NOT_FOUND → BAD_REQUEST
 *                   refactored: thin delegate — all handlers forward to GenericExceptionHandler
 */

package pro.mir0n.esquire.pacMan.exception;
//properties
//# For Spring MVC (Servlet-based)
//spring.mvc.problemdetails.enabled=true
//# For Spring WebFlux (Reactive)
//spring.webflux.problemdetails.enabled=true


import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pro.mir0n.esquire.backend.error.GenericExceptionHandler;
import pro.mir0n.esquire.backend.error.GenericRuntimeException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return GenericExceptionHandler.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    @ExceptionHandler(GenericRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleGenericRuntimeException(GenericRuntimeException ex, HttpServletRequest request) {
        return GenericExceptionHandler.handleGenericRuntimeException(ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
        return GenericExceptionHandler.handleException(ex, request);
    }

}


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
 */

package pro.mir0n.esquire.backend.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import pro.mir0n.esquire.common.EsqConstants;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GenericExceptionHandler{

    public static ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
            (HttpServletRequest) request.resolveReference("request"),
            HttpStatus.BAD_REQUEST,
            "Invalid Request Content",
            "Validation failed",
            ex
        );
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty(EsqConstants.PD_ERRORS, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    public static ResponseEntity<ProblemDetail> handleGenericRuntimeException(GenericRuntimeException ex, HttpServletRequest request) {
        log.warn(ex.getClass().getSimpleName() + ": {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        ResponseEntity<ProblemDetail> response;
        if (ex instanceof PermissionDeniedException) {
            response = handlePermissionDeniedException((PermissionDeniedException) ex, request);
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

    public static ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
            request,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
            null,
            ex
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

}

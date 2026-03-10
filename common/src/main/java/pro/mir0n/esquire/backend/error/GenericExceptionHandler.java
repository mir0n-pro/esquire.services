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
}

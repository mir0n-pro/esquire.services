/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 *  01/14/2026 mir0n  BizTreeExceptionHandler.java renamed with GlobalExceptionHandler
 *                    Error handling with rfc9457 compliance
 * 01/21/2026 mir0n  ProblemDetailMill moved to backend common package
 * 01/24/2026 mir0n  ResourceNotFoundException.java moved to common lib
 * 03/09/2026 mir0n  @Slf4j added; NOT_FOUND → BAD_REQUEST; logging added to all handlers
 */

package pro.mir0n.esquire.bizTree.exception;
//properties
//# For Spring MVC (Servlet-based)
//spring.mvc.problemdetails.enabled=true
//# For Spring WebFlux (Reactive)
//spring.webflux.problemdetails.enabled=true


import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

import pro.mir0n.esquire.backend.error.ProblemDetailMill;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
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


    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(ResourceNotFoundException ex,  HttpServletRequest request) {
        log.warn("Resource not found: {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
            request,
            HttpStatus.BAD_REQUEST,
            "Data not found",
            null,
            ex
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail>  handleException(Exception ex,  HttpServletRequest request) {
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


/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import pro.mir0n.esquire.common.EsqConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import pro.mir0n.esquire.common.EsqUtils;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ProblemDetailMill {
    private ProblemDetailMill () {};

    public static ProblemDetail createProblemDetail(HttpServletRequest request, HttpStatus status, String title, String msg, Exception ex ) {
        // Create standard problem detail
        String details = msg;
        Throwable rootCause = ex == null? null : ExceptionUtils.getRootCause(ex);
        if (msg == null && ex != null) {
            details = (rootCause != null) ? rootCause.getMessage() : ex.getMessage();
            if (details == null) {
                details =  (rootCause != null) ? rootCause.getClass().getSimpleName() : ex.getClass().getSimpleName();
            }
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, details);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        if (rootCause != null) {
            problem.setProperty(EsqConstants.PD_STACK_TRACE, ExceptionUtils.getStackTrace(rootCause));
        } else if (ex != null) {
            problem.setProperty(EsqConstants.PD_STACK_TRACE, ExceptionUtils.getStackTrace(ex));
        }
        problem.setProperty(EsqConstants.PD_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));
        String correlationId = getCorrelationId(request);
        if (correlationId == null) {
            problem.setProperty(EsqConstants.PD_TRACE_ID, EsqUtils.generateCorrelationId());
        } else {
            problem.setProperty(EsqConstants.PD_TRACE_ID,correlationId);
            problem.setProperty(EsqConstants.PD_CORRELATION_ID,correlationId);
        }
        String requestId = getRequestId(request);
        if (requestId != null) {
            problem.setProperty(EsqConstants.PD_REQUEST_ID,requestId);
        }
        if (problem.getType() == null || problem.getType().toString().equals("about:blank")) {
            problem.setType(URI.create("https://mir0n.pro/errors"));
        }
        return problem;
    }

    public static String getCorrelationId(HttpServletRequest request) {
        if (request.getHeader(EsqConstants.ESQ_CORRELATION_ID) != null) {
            return request.getHeader(EsqConstants.ESQ_CORRELATION_ID);
        } else if (request.getHeader(EsqConstants.X_CORRELATION_ID) != null) {
            return request.getHeader(EsqConstants.X_CORRELATION_ID);
        } else {
            return null;
        }
    }
    public static String getRequestId(HttpServletRequest request) {
        return request.getHeader(EsqConstants.X_REQUEST_ID);
    }


}
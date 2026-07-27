/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/18/2026 mir0n let stack trace optional
 * 03/06/2026 mir0n InvalidValueException.errors included in problem detail response
 * 04/16/2026 mir0n  extractCorrelationId(): ret pattern replaces early returns
 * 07/08/2026 mir0n  createProblemDetail(): the incoming correlation id runs through
 *                   EsqUtils.settleCorrelationId(); the settled value is set on BOTH the traceId and the
 *                   correlationId problem-detail properties (was: traceId only when no correlation id came in)
 */

package pro.mir0n.esquire.backend.error;

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
        boolean shouldCapture = ex != null && "true".equals(request.getHeader(EsqConstants.ESQ_CAPTURE_METRICS));
        if (shouldCapture) {
            problem.setProperty(EsqConstants.PD_STACK_TRACE, ExceptionUtils.getStackTrace(rootCause != null ?  rootCause : ex));
        }

        problem.setProperty(EsqConstants.PD_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));
        String incomingCorrelationId = getCorrelationId(request);
        String requestId = getRequestId(request);
        String traceId = EsqUtils.settleCorrelationId(incomingCorrelationId);
        problem.setProperty(EsqConstants.PD_TRACE_ID, traceId);
        problem.setProperty(EsqConstants.PD_CORRELATION_ID, traceId);
        if (requestId != null) {
            problem.setProperty(EsqConstants.PD_REQUEST_ID, requestId);
        }
        if (problem.getType() == null || problem.getType().toString().equals("about:blank")) {
            problem.setType(URI.create("https://mir0n.pro/errors"));
        }
        if (ex instanceof InvalidValueException) {
            problem.setProperty("errors",((InvalidValueException) ex).errors);
        }

        return problem;
    }

    public static String getCorrelationId(HttpServletRequest request) {
        String ret = null;
        if (request.getHeader(EsqConstants.ESQ_CORRELATION_ID) != null) {
            ret = request.getHeader(EsqConstants.ESQ_CORRELATION_ID);
        } else if (request.getHeader(EsqConstants.X_CORRELATION_ID) != null) {
            ret = request.getHeader(EsqConstants.X_CORRELATION_ID);
        }
        return ret;
    }
    public static String getRequestId(HttpServletRequest request) {
        return request.getHeader(EsqConstants.X_REQUEST_ID);
    }


}
/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
  * 01/18/2026 mir0n  let stack trace optional
*/

package pro.mir0n.esquire.gateway.error;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import pro.mir0n.esquire.common.EsqConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import pro.mir0n.esquire.common.EsqUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ProblemDetailMill {
    private ProblemDetailMill () {};

    public static ProblemDetail createProblemDetail(ServerRequest request, HttpStatus status, String title, String msg, boolean shouldCapture, Throwable ex ) {
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
        problem.setInstance(URI.create(request.path()));
        if (shouldCapture) {
            Throwable target = (rootCause != null) ? rootCause : ex;
            if (target != null) {
                problem.setProperty(EsqConstants.PD_STACK_TRACE, ExceptionUtils.getStackTrace(target));
            }
        }

        problem.setProperty(EsqConstants.PD_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));
        HttpHeaders headers = request.headers().asHttpHeaders();

        String correlationId = getCorrelationId(headers);
        if (correlationId == null) {
            problem.setProperty(EsqConstants.PD_TRACE_ID, EsqUtils.generateCorrelationId());
        } else {
            problem.setProperty(EsqConstants.PD_TRACE_ID,correlationId);
            problem.setProperty(EsqConstants.PD_CORRELATION_ID,correlationId);
        }
        String requestId = getRequestId(headers);
        if (requestId != null) {
            problem.setProperty(EsqConstants.PD_REQUEST_ID,requestId);
        }
        if (problem.getType() == null || problem.getType().toString().equals("about:blank")) {
            problem.setType(URI.create("https://mir0n.pro/errors"));
        }
        return problem;
    }

    public static String getCorrelationId(HttpHeaders requestHeaders) {
        String ret = null;
        if (requestHeaders.get(EsqConstants.ESQ_CORRELATION_ID) != null) {
            ret = requestHeaders.getFirst(EsqConstants.ESQ_CORRELATION_ID);
        } else if (requestHeaders.get(EsqConstants.X_CORRELATION_ID) != null) {
            ret = requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID);
        }
        return ret;
    }

    public static String getRequestId(HttpHeaders requestHeaders) {
        String ret = null;
        if (requestHeaders.get(EsqConstants.X_REQUEST_ID) != null) {
            ret = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
        }
        return ret;
    }



}
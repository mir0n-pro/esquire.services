/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.pacMan.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import pro.mir0n.esquire.common.EsqConstants;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Collection;

/*
    If your EnyManDervice ever starts using @Async methods or CompletableFuture,
    the MDC context (Correlation ID, Request ID) will not automatically follow the new thread.

    Fix: If you use async, you'll need to configure a TaskDecorator to copy the MDC from
    the parent thread to the child thread. For now, just keep in mind that MDC is thread-bound.
*/


@Slf4j
@Component
@Order(1) // Ensure this runs early in the filter chain
@RequiredArgsConstructor
public class MdcFilter extends OncePerRequestFilter {

    private final RequestPerformance performance; //lets lombok work

    //public MdcFilter(RequestPerformance performance) {
    //    this.performance = performance;
    //}

    @Override
    protected void doFilterInternal(HttpServletRequest givenRequest, HttpServletResponse givenResponse, FilterChain filterChain)
            throws ServletException, IOException {

        log.info("INCOMING: {} {}",
            givenRequest.getMethod(),
            givenRequest.getRequestURI()
        );

        log.debug("MDC , headers: {}", headerNames(givenRequest.getHeaderNames()));

        long startTime = System.currentTimeMillis();
        performance.setMetricsCaptured("true".equalsIgnoreCase( givenRequest.getHeader(EsqConstants.ESQ_CAPTURE_METRICS)));
        ContentCachingResponseWrapper wrappedResponse = null;
        HttpServletResponse response = givenResponse;
        if (performance.isMetricsCaptured()) {
            // note: There is a trick with wrappedResponse
            //       it is adding some overhead,
            //       so make it optional
            wrappedResponse = new ContentCachingResponseWrapper(response);
            response = wrappedResponse;
        }


        try{
            String correlationId = givenRequest.getHeader(EsqConstants.ESQ_CORRELATION_ID);
            if (correlationId == null) {
                correlationId = givenRequest.getHeader(EsqConstants.X_CORRELATION_ID);
            }
            String requestId = givenRequest.getHeader(EsqConstants.X_REQUEST_ID);
            if (correlationId != null) {
                MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);
            }
            if (requestId != null) {
                MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            }
            log.debug("MDC populated with correlationId: {}, requestId: {}", correlationId, requestId);
            filterChain.doFilter(givenRequest, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            if (performance.isMetricsCaptured()
            && wrappedResponse != null) {
                try {
                    if (!response.isCommitted()) {
                        response.addHeader(EsqConstants.ESQ_SERVICE_TIME, duration + "ms");
                        response.addHeader(EsqConstants.ESQ_BACKEND_TIME, performance.getTotalJpaTime() + "ms");
                    } else {
                        log.error("Could not add headers due response is commited already");
                    }

                } catch (Exception e) {
                    // Silently ignore or log - we don't want this to prevent MDC cleanup
                    log.error("Could not add headers", e);
                }
                wrappedResponse.copyBodyToResponse();
            }

            log.info("OUTGOING: {} {}, Status: {},  Service: {}ms, Backend: {}ms",
                givenRequest.getMethod(),
                givenRequest.getRequestURI(),
                response.getStatus(),
                duration,
                performance.isMetricsCaptured()? performance.getTotalJpaTime(): "(n/a)"
            );

            // ALWAYS clear the MDC to prevent cross-request contamination in thread pools
            MDC.clear();
        }
    }

    private String headerNames(Enumeration<String> names) {
        String s = "";
        while (names.hasMoreElements()) {
            s = s + ";" + names.nextElement();
        }
        return s;
    }
    private String headerNames (Collection < String > names) {
        String s = "";
        for (String a: names) {
            s = s + ";" + a;
        }
        return s;
    }
}

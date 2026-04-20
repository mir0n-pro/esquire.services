/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/10/2026 mir0n  created: generalized from per-service implementations; MDC population, metrics headers
 * 03/21/2026 mir0n  three-tier logging: devLog added; log.debug→devLog.debug; dual error pattern
 *                   actuator short-circuit: /actuator/** bypasses MDC setup and logging entirely
 * 04/16/2026 mir0n  jpaTime local variable inlined in log call
 * 04/20/2026 mir0n  String.valueOf() applied to getTotalJpaTime() in log statement
 */

package pro.mir0n.esquire.backend.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
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
    If your Esquire service ever starts using @Async methods or CompletableFuture,
    the MDC context (Correlation ID, Request ID) will not automatically follow the new thread.

    Fix: If you use async, you'll need to configure a TaskDecorator to copy the MDC from
    the parent thread to the child thread. For now, just keep in mind that MDC is thread-bound.
*/


@Slf4j
@Component
@Order(1) // Ensure this runs early in the filter chain
@RequiredArgsConstructor
public class MdcFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + MdcFilter.class.getName());

    private final RequestPerformance performance; //lets lombok work

    @Override
    protected void doFilterInternal(HttpServletRequest givenRequest, HttpServletResponse givenResponse, FilterChain filterChain)
            throws ServletException, IOException {

        if (givenRequest.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(givenRequest, givenResponse);
            return;
        }

        log.info("INCOMING: {} {}",
            givenRequest.getMethod(),
            givenRequest.getRequestURI()
        );

        devLog.debug("MDC , headers: {}", headerNames(givenRequest.getHeaderNames()));

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
            devLog.debug("MDC populated with correlationId: {}, requestId: {}", correlationId, requestId);
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
                    devLog.error("Could not add headers due response is commited already");
                    }

                } catch (Exception e) {
                    // Silently ignore or log - we don't want this to prevent MDC cleanup
                    log.error("Could not add headers: {}", e.getMessage());
                    devLog.error("Could not add headers: {}", e.getMessage(), e);
                }
                wrappedResponse.copyBodyToResponse();
            }

            String jpaTime = performance.isMetricsCaptured() ? String.valueOf(performance.getTotalJpaTime()) : "(n/a)";
            log.info("OUTGOING: {} {}, Status: {},  Service: {}ms, Backend: {}ms",
                givenRequest.getMethod(),
                givenRequest.getRequestURI(),
                response.getStatus(),
                duration,
                jpaTime
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

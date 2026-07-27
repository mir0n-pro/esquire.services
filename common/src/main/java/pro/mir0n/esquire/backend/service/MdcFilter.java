/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/10/2026 mir0n  created: generalized from per-service implementations; MDC population, metrics headers
 * 03/21/2026 mir0n  three-tier logging: devLog added; log.debug→devLog.debug; dual error pattern
 *                   actuator short-circuit: /actuator/** bypasses MDC setup and logging entirely
 * 04/16/2026 mir0n  jpaTime local variable inlined in log call
 * 04/20/2026 mir0n  String.valueOf() applied to getTotalJpaTime() in log statement
 * 05/14/2026 mir0n  metrics header names migrated to Esq-Srv-Outer-Time / Esq-Srv-Inner-Time
 *                   (observability four-layer protocol; was Esq-Service-Time / Esq-Backend-Time)
 * 07/11/2026 mir0n  v1.2.11 O1/T5-B -- the same two numbers behind the timing headers are now also recorded as
 *                   Micrometer timers, so the service-side latency bands exist without a client asking for the
 *                   headers: esq.srv.outer (the whole servlet wall time) and esq.srv.inner (the JPA/DB time the
 *                   PerformanceAspect accumulated for this request = the DB band), tagged by the matched route
 *                   pattern (HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, so the tag stays bounded). Explicit
 *                   ctor (RequestPerformance, ObjectProvider<MeterRegistry>): the registry is absent when
 *                   observability is off, and the timers are then simply not recorded. The X-Capture-Metrics
 *                   response headers are untouched -- the timers are a second, independent consumer of the numbers
 * 07/23/2026 mir0n  v1.2.11 -- the INCOMING log line is emitted AFTER MDC is populated, so it carries the
 *                   correlationId / requestId fields like every other line in the request
 */

package pro.mir0n.esquire.backend.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import pro.mir0n.esquire.common.EsqConstants;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/*
    If your Esquire service ever starts using @Async methods or CompletableFuture,
    the MDC context (Correlation ID, Request ID) will not automatically follow the new thread.

    Fix: If you use async, you'll need to configure a TaskDecorator to copy the MDC from
    the parent thread to the child thread. For now, just keep in mind that MDC is thread-bound.
*/


@Slf4j
@Component
@Order(1) // Ensure this runs early in the filter chain
public class MdcFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + MdcFilter.class.getName());

    private final RequestPerformance performance;
    // Non-null only when observability is on (the MeterRegistry bean exists). Resolved once at construction.
    private final MeterRegistry registry;

    public MdcFilter(RequestPerformance performance, ObjectProvider<MeterRegistry> registryProvider) {
        this.performance = performance;
        this.registry = registryProvider.getIfAvailable();
    }

    // The matched handler pattern (e.g. /api/{...}) -- a bounded tag; populated only after doFilter, so read in the
    // finally block, never at entry.
    private static String routeTag(HttpServletRequest req) {
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : "unknown";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest givenRequest, HttpServletResponse givenResponse, FilterChain filterChain)
            throws ServletException, IOException {

        if (givenRequest.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(givenRequest, givenResponse);
            return;
        }

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
            // Log arrival AFTER MDC is populated so the INCOMING line carries the correlationId / requestId
            // structured fields -- like every other line in the request, and like the gateway's own INCOMING
            // (RequestTraceFilter wraps its INCOMING in MDC.put/remove for the same reason).
            log.info("INCOMING: {} {}",
                givenRequest.getMethod(),
                givenRequest.getRequestURI()
            );
            filterChain.doFilter(givenRequest, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            // Steady-state latency-band meters (O1/T5-B): the service-outer wall time and the service-inner (JPA/DB)
            // time, both recorded whenever observability is on -- PerformanceAspect accumulates the JPA time under
            // the same condition, so the DB band is real here, not a capture-only number. Tagged by the matched
            // route pattern (bounded), and separate from the response-header instrument below, which stays gated on
            // the X-Capture-Metrics load-test trigger exactly as before.
            if (registry != null) {
                String route = routeTag(givenRequest);
                registry.timer("esq.srv.outer", "route", route).record(duration, TimeUnit.MILLISECONDS);
                registry.timer("esq.srv.inner", "route", route).record(performance.getTotalJpaTime(), TimeUnit.MILLISECONDS);
            }
            if (performance.isMetricsCaptured()
            && wrappedResponse != null) {
                try {
                    if (!response.isCommitted()) {
                        response.addHeader(EsqConstants.ESQ_SRV_OUTER_TIME, duration + "ms");
                        response.addHeader(EsqConstants.ESQ_SRV_INNER_TIME, performance.getTotalJpaTime() + "ms");
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

/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/18/2026 mir0n  Optional capture metrics
 *                   Request/Collabration IDs
 *                   INFO logs generalized
 * 03/21/2026 mir0n  three-tier logging: devLog added; raw response headers dump moved to devLog.debug;
 *                   unused imports removed
 * 05/14/2026 mir0n  v1.2.4: stays a GlobalFilter at the default order (0) so its .then() fires
 *                   BEFORE NettyWriteResponseFilter (-1) commits the response body -- headers
 *                   are still mutable here. The OUTER start timestamp is captured earlier, by
 *                   RequestTraceFilter (now a WebFilter at HIGHEST_PRECEDENCE that runs before
 *                   Spring Security); this filter just consumes that attribute to compute
 *                   X-Response-Time. Service-tier headers renamed: Esq-Srv-Outer-Time and
 *                   Esq-Srv-Inner-Time (was Esq-Service-Time / Esq-Backend-Time).
 * 07/11/2026 mir0n  v1.2.11 O1/T5-B -- the OUTER window is now also recorded as the esq.gw.outer Micrometer timer
 *                   (the edge band of the 4-layer latency breakdown), tagged by the matched gateway route id
 *                   (ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, so the tag stays bounded). Explicit ctor taking
 *                   ObjectProvider<MeterRegistry>: absent when observability is off, and the timer is then not
 *                   recorded. Recording is INDEPENDENT of the X-Capture-Metrics header instrument -- the header
 *                   is still written only when the caller asks, the timer always
 * 08/26/2026 mir0n  the meter registry is taken only when the metrics switch is on; the trace ids reach MDC
 */
package pro.mir0n.esquire.gateway.filters;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

// OUTSIDE InnerTimerFilter (1), so this post-phase runs after the timer has written Esq-Gw-Inner-Time: the
// OUTGOING line reports the real gateway-inner window, and the strip below is the last word on what leaves.
@Slf4j
@Order(0)
@Component
public class ResponseTraceFilter implements GlobalFilter {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + ResponseTraceFilter.class.getName());

    // Non-null only when observability is on (the MeterRegistry bean exists). Resolved once at construction.
    private final MeterRegistry registry;

    public ResponseTraceFilter(ObjectProvider<MeterRegistry> registryProvider,
                                   @Value("${esquire.observability.metrics.enabled:false}") boolean metricsOn) {
        // The SWITCH decides, not the presence of a bean. A MeterRegistry is NOT absent when the umbrella is
        // off: with the Prometheus export backed off, Boot's SimpleMetricsExportAutoConfiguration supplies a
        // SimpleMeterRegistry, so reading the bean answered "observability is on" in every posture.
        this.registry = metricsOn ? registryProvider.getIfAvailable() : null;
    }

    // The matched route id -- a bounded tag (never the raw URI, which carries entity ids).
    private static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unknown";
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            HttpHeaders responseHeaders = exchange.getResponse().getHeaders();

            String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
            if (requestId != null
            && !responseHeaders.containsKey(EsqConstants.X_REQUEST_ID)) {
                responseHeaders.add(EsqConstants.X_REQUEST_ID, requestId);
            }

            Long duration = null;
            Boolean captureServiceMetrics = exchange.getAttribute(EsqConstants.ESQ_CAPTURE_METRICS);
            Long startTime = exchange.getAttribute(EsqConstants.ESQ_START_TIME);
            if (startTime != null) {
                duration = System.currentTimeMillis() - startTime;
            }

            // Steady-state latency-band meter (O1/T5-B): the gateway-outer total, recorded whenever observability
            // is on, tagged by route -- independent of the X-Capture-Metrics header instrument below.
            if (registry != null && duration != null) {
                registry.timer("esq.gw.outer", "route", routeId(exchange)).record(duration, TimeUnit.MILLISECONDS);
            }

            if (duration != null
            && requestHeaders.containsKey(EsqConstants.X_CAPTURE_METRICS)) {
                responseHeaders.add(EsqConstants.X_RESPONSE_TIME, duration + "ms");
                if (!Boolean.TRUE.equals(captureServiceMetrics)) {
                    // master switch off -- strip service-tier + gateway-inner metric headers
                    responseHeaders.remove(EsqConstants.ESQ_GW_INNER_TIME);
                    responseHeaders.remove(EsqConstants.ESQ_SRV_OUTER_TIME);
                    responseHeaders.remove(EsqConstants.ESQ_SRV_INNER_TIME);
                }
            } else {
                // trigger absent -- strip every metric header, even ones forwarded
                // from downstream services (they should not leak without the trigger)
                responseHeaders.remove(EsqConstants.X_RESPONSE_TIME);
                responseHeaders.remove(EsqConstants.ESQ_GW_INNER_TIME);
                responseHeaders.remove(EsqConstants.ESQ_SRV_OUTER_TIME);
                responseHeaders.remove(EsqConstants.ESQ_SRV_INNER_TIME);
            }

            // The SETTLED id, the one RequestTraceFilter put on the exchange and sent downstream -- not the one
            // the client happened to send. A caller that sends none still gets a correlation id, and reading the
            // inbound header instead left this line, and the header going back, empty for exactly those callers.
            String correlationId = exchange.getAttribute(EsqConstants.ESQ_CORRELATION_ID);
            if (correlationId == null) {
                correlationId = requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID);
            }
            if (correlationId != null
            && !responseHeaders.containsKey(EsqConstants.X_CORRELATION_ID)) {
                responseHeaders.add(EsqConstants.X_CORRELATION_ID, correlationId);
            }

            // Both ids as log FIELDS, not only as message text: the logs-to-trace link and the "one request end
            // to end" trail key on the FIELD. INCOMING is wrapped the same way; without this the gateway's
            // closing line is missing from the trail that every service tier is in.
            MDC.put(EsqConstants.PD_CORRELATION_ID, correlationId);
            if (requestId != null) {
                MDC.put(EsqConstants.PD_REQUEST_ID, requestId);
            }
            try {
                logOutgoing(exchange, responseHeaders, correlationId, requestId,
                        Boolean.TRUE.equals(captureServiceMetrics), duration);
            } finally {
                MDC.remove(EsqConstants.PD_CORRELATION_ID);
                MDC.remove(EsqConstants.PD_REQUEST_ID);
            }
        }));
    }

    /** The gateway's closing line. Carries the metric bands only when the master switch put them there. */
    private static void logOutgoing(ServerWebExchange exchange, HttpHeaders responseHeaders,
                                    String correlationId, String requestId,
                                    boolean captureServiceMetrics, Long duration) {
        Integer status = exchange.getResponse().getStatusCode() == null
                ? null : exchange.getResponse().getStatusCode().value();
        if (captureServiceMetrics) {
            log.info("OUTGOING: correlationId={}, requestId={}, {} {}, status={}, srvInnerTime={}, srvOuterTime={}, gwInnerTime={}, gwOuterTime={}ms",
                    correlationId,
                    requestId,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI(),
                    status,
                    responseHeaders.getFirst(EsqConstants.ESQ_SRV_INNER_TIME),
                    responseHeaders.getFirst(EsqConstants.ESQ_SRV_OUTER_TIME),
                    responseHeaders.getFirst(EsqConstants.ESQ_GW_INNER_TIME),
                    duration);
        } else {
            log.info("OUTGOING: correlationId={}, requestId={}, {} {}, status={}, gwOuterTime={}ms",
                    correlationId,
                    requestId,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI(),
                    status,
                    duration);
        }
    }
}

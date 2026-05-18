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
 */
package pro.mir0n.esquire.gateway.filters;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;

@Slf4j
@Order(0)
@Component
public class ResponseTraceFilter implements GlobalFilter {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + ResponseTraceFilter.class.getName());

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

            String correlationId = requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID);
            if (correlationId != null
            && !responseHeaders.containsKey(EsqConstants.X_CORRELATION_ID)) {
                responseHeaders.add(EsqConstants.X_CORRELATION_ID, correlationId);
            }

            if (Boolean.TRUE.equals(captureServiceMetrics)) {
                log.info("OUTGOING: correlationId={}, requestId={}, {} {}, status={}, srvInnerTime={}, srvOuterTime={}, gwInnerTime={}, gwOuterTime={}ms",
                    correlationId,
                    requestId,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI(),
                    exchange.getResponse().getStatusCode() == null ? null : exchange.getResponse().getStatusCode().value(),
                    responseHeaders.getFirst(EsqConstants.ESQ_SRV_INNER_TIME),
                    responseHeaders.getFirst(EsqConstants.ESQ_SRV_OUTER_TIME),
                    responseHeaders.getFirst(EsqConstants.ESQ_GW_INNER_TIME),
                    duration
                );
            } else {
                log.info("OUTGOING: correlationId={}, requestId={}, {} {}, status={}, gwOuterTime={}ms",
                    correlationId,
                    requestId,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI(),
                    exchange.getResponse().getStatusCode() == null ? null : exchange.getResponse().getStatusCode().value(),
                    duration
                );
            }
        }));
    }
}

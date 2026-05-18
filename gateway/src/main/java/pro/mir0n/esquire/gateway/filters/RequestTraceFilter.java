/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n  log expanded
 * 01/18/2026 mir0n  Optional capture metrics
 *                   Request/Collabration IDs
 *                   INFO logs generalized
 * 05/14/2026 mir0n  v1.2.4: convert from Spring Cloud Gateway GlobalFilter @Order(1)
 *                   to a WebFlux WebFilter at HIGHEST_PRECEDENCE so the START
 *                   timestamp is captured BEFORE Spring Security's filter chain
 *                   runs -- auth-layer time (Pattern 3 broker call, Pattern 4
 *                   token-exchange call) is now part of the gateway OUTER timer.
 */
package pro.mir0n.esquire.gateway.filters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestTraceFilter implements WebFilter {

    @Value("${esquire.gateway.service-metrics.enabled:true}")
    private boolean serviceMetricsEnabled;

    @Override
    public Mono<Void> filter(ServerWebExchange origExchange, WebFilterChain chain) {
        ServerWebExchange exchange = origExchange;
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

        String correlationId = null;
        boolean updateCorrelationId = false;
        String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
        if (requestHeaders.get(EsqConstants.ESQ_CORRELATION_ID) != null) {
            correlationId = requestHeaders.getFirst(EsqConstants.ESQ_CORRELATION_ID);
        } else {
            correlationId = obtainCorrelationId(requestHeaders);
            updateCorrelationId = true;
        }

        String finalCorrelationId = correlationId;
        boolean finalUpdateCorrelationId = updateCorrelationId;
        if (finalUpdateCorrelationId || serviceMetricsEnabled) {
            exchange = exchange.mutate()
                    .request(r -> {
                        if (finalUpdateCorrelationId) {
                            r.header(EsqConstants.ESQ_CORRELATION_ID, finalCorrelationId);
                        }
                        if (serviceMetricsEnabled) {
                            r.header(EsqConstants.ESQ_CAPTURE_METRICS, "true");
                        }
                    })
                    .build();
        }

        // The OUTER start timestamp -- captured before Spring Security runs,
        // so the gateway's X-Response-Time covers auth + routing + downstream.
        exchange.getAttributes().put(EsqConstants.ESQ_CORRELATION_ID, correlationId);
        exchange.getAttributes().put(EsqConstants.ESQ_START_TIME, System.currentTimeMillis());
        exchange.getAttributes().put(EsqConstants.ESQ_CAPTURE_METRICS, serviceMetricsEnabled);

        log.info("INCOMING: correlationId={}, requestId={}, {} {}",
            correlationId,
            requestId,
            exchange.getRequest().getMethod(),
            exchange.getRequest().getURI()
        );
        return chain.filter(exchange);
    }

    public String obtainCorrelationId(HttpHeaders requestHeaders) {
        String ret = null;
        if (requestHeaders.get(EsqConstants.ESQ_CORRELATION_ID) != null) {
            ret = requestHeaders.getFirst(EsqConstants.ESQ_CORRELATION_ID);
        } else if (requestHeaders.get(EsqConstants.X_CORRELATION_ID) != null) {
            ret = requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID);
        } else {
            ret = EsqUtils.generateCorrelationId();
        }
        return ret;
    }

}

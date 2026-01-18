/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n  log expanded
 * 01/18/2026 mir0n  Optional capture metrics
 *                   Request/Collabration IDs
 *                   INFO logs generalized
 */
package pro.mir0n.esquire.gateway.filters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Order(1)
@Component
public class RequestTraceFilter implements GlobalFilter {

    @Value("${esquire.gateway.service-metrics.enabled:true}")
    private boolean serviceMetricsEnabled;

    @Override
    public Mono<Void> filter(ServerWebExchange origExchange, GatewayFilterChain chain) {
        ServerWebExchange exchange = origExchange;
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

        String correlationId = null;
        boolean updateCorrelationId = false;
        String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
        if (requestHeaders.get(EsqConstants.ESQ_CORRELATION_ID) != null) {
            correlationId = requestHeaders.getFirst(EsqConstants.ESQ_CORRELATION_ID);
            log.trace("esq-correlation-id found in RequestTraceFilter : {}", correlationId);
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
            log.trace("esq-correlation-id generated in RequestTraceFilter : {} uri:{}:{}"
                    , correlationId, exchange.getRequest().getMethod(), exchange.getRequest().getURI());
        }


        // THE MISSING LINK: Save to attributes so the Error Handler can find it!
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

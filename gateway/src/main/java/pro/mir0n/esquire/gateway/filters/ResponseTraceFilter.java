/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/18/2026 mir0n  Optional capture metrics
 *                   Request/Collabration IDs
 *                   INFO logs generalized
 */
package pro.mir0n.esquire.gateway.filters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Configuration
public class ResponseTraceFilter {

    @Bean
    public GlobalFilter postGlobalFilter() {
        return (exchange, chain) -> {
            // Record the start time when the request enters this filter
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
                HttpHeaders responseHeaders = exchange.getResponse().getHeaders();

log.info("OUTGOING: {}", responseHeaders);

                String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
                if (requestId != null
                && !responseHeaders.containsKey(EsqConstants.X_REQUEST_ID)) {
                    responseHeaders.add(EsqConstants.X_REQUEST_ID, requestId);
                }
                Long duration = null;
                Boolean captureServiceMetrics = exchange.getAttribute(EsqConstants.ESQ_CAPTURE_METRICS);
                Long startTime = exchange.getAttribute(EsqConstants.ESQ_START_TIME);
                //log.info("startTime {}", startTime);
                if (startTime != null) {
                    duration = System.currentTimeMillis() - startTime;
                }
                //log.info("duration {}", duration);
                if (duration != null
                && exchange.getRequest().getHeaders().containsKey(EsqConstants.X_CAPTURE_METRICS)) {
                    exchange.getResponse().getHeaders().add(EsqConstants.X_RESPONSE_TIME, duration + "ms");
                    if (!captureServiceMetrics) {
                        //just in case: remove figures from the response headers since not permitted
                        responseHeaders.remove(EsqConstants.ESQ_SERVICE_TIME);
                        responseHeaders.remove(EsqConstants.ESQ_BACKEND_TIME);
                    }
                } else {
                    // a service returns its metrics by its own condition, we need to skip adding it to the response headers
                    responseHeaders.remove(EsqConstants.X_RESPONSE_TIME);
                    responseHeaders.remove(EsqConstants.ESQ_SERVICE_TIME);
                    responseHeaders.remove(EsqConstants.ESQ_BACKEND_TIME);
                }


                String correlationId = requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID);
                if (correlationId != null
                && !responseHeaders.containsKey(EsqConstants.X_CORRELATION_ID) ) {
                    responseHeaders.add(EsqConstants.X_CORRELATION_ID, correlationId);
                }
                if (captureServiceMetrics) {
                    log.info("OUTGOING: correlationId={}, requestId={}, {} {}, status={}, backendTime={}, serviceTime={}, gatewayTime={}ms",
                        correlationId,
                        requestId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI(),
                        exchange.getResponse().getStatusCode().value(),
                        responseHeaders.getFirst(EsqConstants.ESQ_BACKEND_TIME),
                        responseHeaders.getFirst(EsqConstants.ESQ_SERVICE_TIME),
                        duration
                    );
                } else {
                    log.info("OUTGOING: correlationId={}, requestId={}, {} {}, status={}, gatewayTime={}ms",
                        correlationId,
                        requestId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI(),
                        exchange.getResponse().getStatusCode().value(),
                        duration
                    );
                }
            }));
        };
    }
}
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
 * 07/08/2026 mir0n  v1.2.11 -- obtainCorrelationId() returns a settled W3C-shaped id
 *                   (EsqUtils.settleCorrelationId over Esq-/X-Correlation-ID; X-Request-ID
 *                   is no longer a seed); Esq-Correlation-ID is now stamped on EVERY
 *                   downstream request, not only when it was absent. settleTraceparent()
 *                   added: keeps an incoming traceparent whose trace id equals the settled
 *                   correlation id, else mints a root one from it; the traceparent header
 *                   is stamped downstream so span traceId == correlationId.
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

        String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
        // The gateway ALWAYS yields a W3C-shaped Esq-Correlation-ID (settle: keep-if-valid / convert /
        // generate). It is stamped downstream as the canonical trace id; the client's X-Correlation-ID
        // and X-Request-ID are left untouched as their own references.
        String correlationId = obtainCorrelationId(requestHeaders);
        // Seed the W3C traceparent from that same id so OTel spans inherit traceId == correlationId.
        String traceparent = settleTraceparent(requestHeaders.getFirst(EsqConstants.TRACEPARENT), correlationId);

        String finalCorrelationId = correlationId;
        String finalTraceparent = traceparent;
        exchange = exchange.mutate()
                .request(r -> {
                    r.header(EsqConstants.ESQ_CORRELATION_ID, finalCorrelationId);
                    r.header(EsqConstants.TRACEPARENT, finalTraceparent);
                    if (serviceMetricsEnabled) {
                        r.header(EsqConstants.ESQ_CAPTURE_METRICS, "true");
                    }
                })
                .build();

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

    // Settle the edge correlation id: an incoming Esq-/X-Correlation-ID is kept when already W3C-shaped
    // and converted otherwise; failing that a fresh id is generated. The X-Request-ID is NOT a seed --
    // the correlation id is its own identity (it is the trace id). Always returns a W3C-shaped id.
    public String obtainCorrelationId(HttpHeaders requestHeaders) {
        String incoming = EsqUtils.firstNonBlank(
                requestHeaders.getFirst(EsqConstants.ESQ_CORRELATION_ID),
                requestHeaders.getFirst(EsqConstants.X_CORRELATION_ID)
        );
        return EsqUtils.settleCorrelationId(incoming);
    }

    // The downstream traceparent carries the settled correlationId as its trace id. An incoming
    // traceparent is kept only when it already agrees with that trace id (so an upstream span stays
    // the parent); otherwise a fresh root traceparent is minted from the correlationId.
    public String settleTraceparent(String incomingTraceparent, String correlationId) {
        String ret;
        if (EsqUtils.isValidTraceparent(incomingTraceparent)
                && correlationId.equals(EsqUtils.traceIdFromTraceparent(incomingTraceparent))) {
            ret = incomingTraceparent;
        } else {
            ret = EsqUtils.buildTraceparent(correlationId);
        }
        return ret;
    }

}

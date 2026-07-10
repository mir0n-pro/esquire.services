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
 * 07/09/2026 mir0n  v1.2.11 -- constructor takes ObjectProvider<Tracer>; settleTraceparent() and the downstream
 *                   traceparent stamp removed (the CorrelationPropagator injects it); currentTraceId(exchange)
 *                   reads the trace id off the server request observation; a one-shot WARN when tracing is on
 *                   and a proxied request has no current span
 */
package pro.mir0n.esquire.gateway.filters;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqUtils;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestTraceFilter implements WebFilter {

    @Value("${esquire.gateway.service-metrics.enabled:true}")
    private boolean serviceMetricsEnabled;

    // The tracing facade -- present only when tracing is enabled. Used to read the current span's trace id,
    // which the CorrelationPropagator has forced to the settled correlation id, so the logged correlation id
    // is identical to the trace id. Public API only -- no reach into observation-context internals.
    private final ObjectProvider<Tracer> tracerProvider;

    // One-shot loud signal for the "can't happen" case: tracing enabled + proxied path, yet no current span,
    // which would silently re-split traces. Latched so it warns once per JVM, not per request.
    private final AtomicBoolean traceIdMissWarned = new AtomicBoolean(false);

    public RequestTraceFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange origExchange, WebFilterChain chain) {
        ServerWebExchange exchange = origExchange;
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

        String requestId = requestHeaders.getFirst(EsqConstants.X_REQUEST_ID);
        // The gateway ALWAYS yields a W3C-shaped Esq-Correlation-ID (settle: keep-if-valid / convert /
        // generate) and stamps it downstream as the canonical id; the client's X-Correlation-ID and
        // X-Request-ID are left untouched as their own references. When tracing is ON the correlation id is
        // READ BACK from the server span's trace id -- the CorrelationPropagator has already settled it (from
        // these same headers) and forced it as the span trace id ahead of this filter, so reading it here keeps
        // the logged correlation id identical to the trace id even in the generate case (no second, divergent
        // generate). When tracing is OFF there is no span, so we settle the headers here. The downstream
        // W3C traceparent is NOT stamped here -- with tracing on the tracer injects it from the server span
        // (that is the whole point of the propagator); with tracing off there are no spans to link.
        String correlationId = currentTraceId(exchange);
        if (correlationId == null) {
            warnIfTraceIdUnexpectedlyMissing(exchange);
            correlationId = obtainCorrelationId(requestHeaders);
        }

        String finalCorrelationId = correlationId;
        exchange = exchange.mutate()
                .request(r -> {
                    r.header(EsqConstants.ESQ_CORRELATION_ID, finalCorrelationId);
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

    // The trace id of the server span for THIS request. With tracing enabled the CorrelationPropagator forced
    // it to the settled correlation id (from the same headers) ahead of this filter, so reading it back here
    // keeps the correlation id identical to the trace id even in the generate case. Read from the observation
    // context the reactive HttpWebHandlerAdapter parks on the exchange -- NOT via Tracer.currentSpan(), which
    // returns null in a WebFilter (the span scope is not on the thread when the filter assembles the chain;
    // verified live). Both types used here are public API (Spring + Micrometer-Tracing), just low level. Null
    // when tracing is off (no tracing handler ran) or the path opened no span; the caller then settles headers.
    private static String currentTraceId(ServerWebExchange exchange) {
        String ret = null;
        Object context = exchange.getAttribute(
                ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE);
        if (context instanceof ServerRequestObservationContext observationContext) {
            TracingObservationHandler.TracingContext tracingContext =
                    observationContext.get(TracingObservationHandler.TracingContext.class);
            if (tracingContext != null && tracingContext.getSpan() != null) {
                ret = tracingContext.getSpan().context().traceId();
            }
        }
        return ret;
    }

    // Case 3 alarm (see the correlation-id comment above): tracing is enabled and this is a proxied,
    // non-actuator path, yet no current span was found -- which should never happen. It means the correlation
    // id is being settled from headers and can diverge from the trace id, re-splitting traces. Warn ONCE.
    private void warnIfTraceIdUnexpectedlyMissing(ServerWebExchange exchange) {
        if (tracerProvider.getIfAvailable() != null) {
            String path = exchange.getRequest().getURI().getPath();
            if (!path.startsWith("/actuator") && traceIdMissWarned.compareAndSet(false, true)) {
                log.warn("Trace id unavailable while tracing is enabled for proxied path '{}' -- correlation id "
                        + "will be settled from headers and may diverge from the span trace id; check "
                        + "Micrometer/Spring Tracing compatibility (RequestTraceFilter.currentTraceId).", path);
            }
        }
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

}

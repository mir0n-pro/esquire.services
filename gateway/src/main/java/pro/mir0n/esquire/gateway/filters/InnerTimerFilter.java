/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: GlobalFilter @Order(0) measuring the downstream-call-only window;
 *                   captures Esq-Gw-Inner-Start-Time at the beginning of the proxied call, computes
 *                   Esq-Gw-Inner-Time at .then(); paired with RequestTraceFilter for the outer total
 * 07/11/2026 mir0n  v1.2.11 O1/T5-B -- the downstream-call window is now also recorded as the esq.gw.inner
 *                   Micrometer timer, tagged by the matched gateway route id (ServerWebExchangeUtils
 *                   .GATEWAY_ROUTE_ATTR, so the tag stays bounded). Explicit ctor taking
 *                   ObjectProvider<MeterRegistry>: absent when observability is off, and the timer is then not
 *                   recorded. Recording is INDEPENDENT of the X-Capture-Metrics header instrument -- the header
 *                   is still written only when the caller asks, the timer always. esq.gw.outer minus esq.gw.inner
 *                   is the gateway's own overhead band
 */
package pro.mir0n.esquire.gateway.filters;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
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

/**
 * Gateway INNER timer: measures the request window from "just before the
 * downstream service call is dispatched" to "the downstream response is
 * received". Runs as a Spring Cloud Gateway {@link GlobalFilter} -- which
 * executes INSIDE the gateway's WebHandler, AFTER Spring Security's filter
 * chain. So this timer naturally excludes auth + Spring Security work.
 *
 * The pairing with the OUTER timer (RequestTraceFilter / ResponseTraceFilter,
 * which are WebFilters at HIGHEST_PRECEDENCE) gives a clean attribution:
 *
 *   X-Response-Time   - Esq-Gw-Inner-Time  =  gateway "self" overhead
 *                                              (auth + routing decisions
 *                                              + response assembly)
 *   Esq-Gw-Inner-Time - Esq-Srv-Outer-Time =  in-cluster network + serialization
 *   Esq-Srv-Outer-Time - Esq-Srv-Inner-Time = service application logic
 *   Esq-Srv-Inner-Time                      = umbrella of all service-inner cost
 *                                              (today: JPA queries)
 */
@Order(0)
@Component
public class InnerTimerFilter implements GlobalFilter {

    // Non-null only when observability is on (the MeterRegistry bean exists) -- the timer records then, independent
    // of the X-Capture-Metrics header instrument. Resolved once at construction.
    private final MeterRegistry registry;

    public InnerTimerFilter(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - start;
            // Steady-state latency-band meter (O1/T5-B): the gateway-inner window, recorded whenever observability
            // is on, tagged by the matched route (bounded) -- separate from the load-test header below.
            if (registry != null) {
                registry.timer("esq.gw.inner", "route", routeId(exchange)).record(duration, TimeUnit.MILLISECONDS);
            }
            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
            if (requestHeaders.containsKey(EsqConstants.X_CAPTURE_METRICS)
            && !responseHeaders.containsKey(EsqConstants.ESQ_GW_INNER_TIME)) {
                responseHeaders.add(EsqConstants.ESQ_GW_INNER_TIME, duration + "ms");
            }
        }));
    }

    // The matched route id -- a bounded tag (never the raw URI, which carries entity ids).
    private static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unknown";
    }
}

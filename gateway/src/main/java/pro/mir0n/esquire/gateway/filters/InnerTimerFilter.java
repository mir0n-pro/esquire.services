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
 */
package pro.mir0n.esquire.gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;

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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - start;
            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
            if (requestHeaders.containsKey(EsqConstants.X_CAPTURE_METRICS)
            && !responseHeaders.containsKey(EsqConstants.ESQ_GW_INNER_TIME)) {
                responseHeaders.add(EsqConstants.ESQ_GW_INNER_TIME, duration + "ms");
            }
        }));
    }
}

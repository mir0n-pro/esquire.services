/*
 *  Esquire frameworks (tm)
 *  gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: writes the answer body BizBusRoutingFilter kept on the exchange, in the post-phase at
 *                   the order of NettyWriteResponseFilter
 */

package pro.mir0n.esquire.gateway.bus;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes the body of a biz bus answer that {@link BizBusRoutingFilter} kept on the exchange -- in the
 * post-phase, at the order of the HTTP write, so every gateway post-filter has added its headers first.
 */
@Component
public class BizBusWriteResponseFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.defer(() -> write(exchange)));
    }

    private static Mono<Void> write(ServerWebExchange exchange) {
        Mono<Void> ret = Mono.empty();
        byte[] bytes = exchange.getAttribute(BizBusRoutingFilter.ANSWER_BODY_ATTR);
        if (bytes != null) {
            ServerHttpResponse response = exchange.getResponse();
            ret = response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        }
        return ret;
    }
}

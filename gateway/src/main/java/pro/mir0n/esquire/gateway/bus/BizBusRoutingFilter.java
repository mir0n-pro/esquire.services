/*
 *  Esquire frameworks (tm)
 *  gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/19/2026 mir0n  created: a GlobalFilter for the routes whose uri is bus://biz-bus: builds the command from the
 *                   request (the operation from the path, kind / id / cmd from the query, the fields from the body,
 *                   uid / rootPath / roles from the token), sends it with BizBusClient and sets the status and
 *                   headers from the answer; a body that is not a JSON object, or no numeric kind, answers 400
 */

package pro.mir0n.esquire.gateway.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.gateway.security.JwtClaimPeek;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.RodEvent;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * Routes a request whose route uri is {@code bus://biz-bus} over the biz bus instead of HTTP: builds the command
 * from the request, posts it, and turns the answer into the HTTP response: the status and headers here, the
 * body later in {@link BizBusWriteResponseFilter}. It stands where the HTTP call stands, so the route filters
 * (CircuitBreaker, Retry) and the gateway timers wrap it unchanged.
 */
@Slf4j
@Component
public class BizBusRoutingFilter implements GlobalFilter, Ordered {

    public static final String SCHEME = "bus";
    private static final String FIELDS_ATTR = BizBusRoutingFilter.class.getName() + ".fields";
    public static final String ANSWER_BODY_ATTR = BizBusRoutingFilter.class.getName() + ".answerBody";
    private static final String BEARER = "Bearer ";

    private final BizBusClient client;
    private final ObjectMapper objectMapper;

    public BizBusRoutingFilter(BizBusClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    //xxx: just before NettyRoutingFilter (LOWEST_PRECEDENCE), which takes only http/https
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Mono<Void> ret;
        URI url = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (url == null || !SCHEME.equals(url.getScheme()) || ServerWebExchangeUtils.isAlreadyRouted(exchange)) {
            ret = chain.filter(exchange);
        } else if (!EsqConstants.BUS_KEY_BIZ.equals(url.getHost())) {
            ret = Mono.error(new IllegalStateException("route uri names an unknown bus: " + url));
        } else {
            ServerWebExchangeUtils.setAlreadyRouted(exchange);
            ret = fieldsOf(exchange).flatMap(fields -> call(exchange, url, fields));
        }
        return ret;
    }

    private Mono<Void> call(ServerWebExchange exchange, URI url, Map<String, Object> fields) {
        RodEvent command = commandOf(exchange, url, fields);
        return client.send(command, spanOf(exchange)).flatMap(answer -> write(exchange, answer));
    }

    //xxx: the body is read once and kept on the exchange -- a Retry re-runs this filter, and a request body
    // cannot be read twice
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> fieldsOf(ServerWebExchange exchange) {
        Mono<Map<String, Object>> ret;
        Map<String, Object> cached = exchange.getAttribute(FIELDS_ATTR);
        if (cached != null) {
            ret = Mono.just(cached);
        } else {
            ret = DataBufferUtils.join(exchange.getRequest().getBody())
                    .map(buffer -> parse(buffer))
                    .defaultIfEmpty(Map.of())
                    .doOnNext(fields -> exchange.getAttributes().put(FIELDS_ATTR, fields));
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(DataBuffer buffer) {
        Map<String, Object> ret;
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            ret = bytes.length == 0 ? Map.of() : objectMapper.readValue(bytes, Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the request body is not a JSON object", e);
        } finally {
            DataBufferUtils.release(buffer);
        }
        return ret;
    }

    // The command: the operation from the path, kind/id/cmd from the query, the caller's claims from the token
    // the route sends on (after the token relay), the body as it is. The correlation id, settled by
    // RequestTraceFilter for every request, keys the answer.
    private RodEvent commandOf(ServerWebExchange exchange, URI url, Map<String, Object> fields) {
        MultiValueMap<String, String> query = exchange.getRequest().getQueryParams();
        String path = url.getPath();
        String operation = path.substring(path.lastIndexOf('/') + 1);
        Map<String, Object> claims = JwtClaimPeek.peekClaims(tokenOf(exchange.getRequest().getHeaders()));

        Map<String, Object> body = new HashMap<>();
        body.put(EsqConstants.BIZ_KEY_OPERATION, operation);
        String cmd = query.getFirst("cmd");
        if (cmd != null) {
            body.put(EsqConstants.BIZ_KEY_CMD, cmd);
        }
        body.put(EsqConstants.BIZ_KEY_FIELDS, fields);
        body.put(EsqConstants.BIZ_KEY_ROOT_PATH, claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH));
        body.put(EsqConstants.BIZ_KEY_ROLES, rolesOf(claims));
        body.put(EsqConstants.BIZ_KEY_CAPTURE,
                Boolean.TRUE.equals(exchange.getAttribute(EsqConstants.ESQ_CAPTURE_METRICS)));

        Object uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID);
        return new RodEvent(null, kindOf(query.getFirst("kind")), query.getFirst("id"), null, null,
                System.currentTimeMillis(), exchange.getAttribute(EsqConstants.ESQ_CORRELATION_ID),
                exchange.getRequest().getHeaders().getFirst(EsqConstants.X_REQUEST_ID),
                uid != null ? uid.toString() : null, null, BusConstants.MSG_TYPE_REQUEST, body);
    }

    // The request's server span, from the observation context the reactive HttpWebHandlerAdapter parks on the
    // exchange (as RequestTraceFilter reads it); null when tracing is off.
    private static Span spanOf(ServerWebExchange exchange) {
        Span ret = null;
        Object context = exchange.getAttribute(ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE);
        if (context instanceof ServerRequestObservationContext observationContext) {
            TracingObservationHandler.TracingContext tracingContext =
                    observationContext.get(TracingObservationHandler.TracingContext.class);
            if (tracingContext != null) {
                ret = tracingContext.getSpan();
            }
        }
        return ret;
    }

    private static String tokenOf(HttpHeaders headers) {
        String ret = null;
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith(BEARER)) {
            ret = auth.substring(BEARER.length());
        }
        return ret;
    }

    private static int kindOf(String kind) {
        int ret;
        try {
            ret = Integer.parseInt(kind);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is required and must be a number", e);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesOf(Map<String, Object> claims) {
        List<String> ret = new ArrayList<>();
        Object realmAccess = claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS);
        if (realmAccess instanceof Map) {
            Object roles = ((Map<String, Object>) realmAccess).get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES);
            if (roles instanceof List) {
                for (Object role : (List<Object>) roles) {
                    ret.add(String.valueOf(role));
                }
            }
        }
        return ret;
    }

    // The answer as the HTTP response: the status, the two service times (as MdcFilter sets them), and the result
    // or the problem as the body.
    //xxx: the body is only KEPT here; BizBusWriteResponseFilter writes it after the post-filters added their
    // headers -- written here, the response is committed and their header writes fail
    private Mono<Void> write(ServerWebExchange exchange, RodEvent answer) {
        Map<String, Object> body = answer.body() != null ? answer.body() : Map.of();
        ServerHttpResponse response = exchange.getResponse();
        Object status = body.get(EsqConstants.BIZ_KEY_STATUS);
        response.setStatusCode(status instanceof Number n
                ? HttpStatusCode.valueOf(n.intValue()) : HttpStatus.BAD_GATEWAY);

        Object outerMs = body.get(EsqConstants.BIZ_KEY_OUTER_MS);
        if (outerMs != null) {
            response.getHeaders().add(EsqConstants.ESQ_SRV_OUTER_TIME, outerMs + "ms");
            response.getHeaders().add(EsqConstants.ESQ_SRV_INNER_TIME, body.get(EsqConstants.BIZ_KEY_INNER_MS) + "ms");
        }

        Object problem = body.get(EsqConstants.BIZ_KEY_PROBLEM);
        Object payload = problem != null ? problem : body.get(EsqConstants.BIZ_KEY_RESULT);
        if (payload != null) {
            response.getHeaders().setContentType(problem != null
                    ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON);
            try {
                exchange.getAttributes().put(ANSWER_BODY_ATTR, objectMapper.writeValueAsBytes(payload));
            } catch (Exception e) {
                throw new IllegalStateException("biz bus: the answer cannot be written", e);
            }
        }
        return Mono.empty();
    }
}

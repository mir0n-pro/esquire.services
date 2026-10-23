package pro.mir0n.esquire.gateway.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.RodEvent;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;

class BizBusRoutingFilterTest {

    private BizBusClient client;
    private GatewayFilterChain chain;
    private BizBusRoutingFilter filter;

    @BeforeEach
    void setUp() {
        client = mock(BizBusClient.class);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        filter = new BizBusRoutingFilter(client, new ObjectMapper());
    }

    private static String token() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("joe")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ID, "u1")
                .claim(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "1.2.")
                .claim(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                        Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("teller")))
                .build();
        return new PlainJWT(claims).serialize();
    }

    private static MockServerWebExchange exchange(String routeUri) {
        MockServerWebExchange ret = MockServerWebExchange.from(MockServerHttpRequest
                .post("/esq-acct?kind=52&id=a1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .header(EsqConstants.X_REQUEST_ID, "r1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"typeId\":1000,\"amount\":10}"));
        ret.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,
                URI.create(routeUri + "/esq-acct?kind=52&id=a1"));
        ret.getAttributes().put(EsqConstants.ESQ_CORRELATION_ID, "c1");
        ret.getAttributes().put(EsqConstants.ESQ_CAPTURE_METRICS, true);
        return ret;
    }

    private static RodEvent answer(Map<String, Object> body) {
        return new RodEvent(null, 52, "a1", null, null, 0L, "c1", "r1", null, "gateway.0",
                BusConstants.MSG_TYPE_RESPONSE, body);
    }

    @Test
    @DisplayName("an http route is left to the HTTP routing filter")
    void httpRoutePassesThrough() {
        MockServerWebExchange exchange = exchange("http://pacman:3004");
        filter.filter(exchange, chain).block(Duration.ofSeconds(2));
        verify(chain).filter(exchange);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a bus route sends the command with the claims; the body is written after the post-filters' headers")
    @SuppressWarnings("unchecked")
    void busRouteSendsAndWrites() {
        Map<String, Object> body = new HashMap<>();
        body.put(EsqConstants.BIZ_KEY_STATUS, 200);
        body.put(EsqConstants.BIZ_KEY_RESULT, Map.of("balance", 10));
        body.put(EsqConstants.BIZ_KEY_OUTER_MS, 12);
        body.put(EsqConstants.BIZ_KEY_INNER_MS, 3);
        ArgumentCaptor<RodEvent> sent = ArgumentCaptor.forClass(RodEvent.class);
        when(client.send(sent.capture(), isNull())).thenReturn(Mono.just(answer(body)));
        MockServerWebExchange exchange = exchange("bus://biz-bus");

        filter.filter(exchange, chain).block(Duration.ofSeconds(2));

        RodEvent c = sent.getValue();
        assertEquals(52, c.kind());
        assertEquals("a1", c.entityId());
        assertEquals("u1", c.uid());
        assertEquals("c1", c.correlationId());
        assertEquals("r1", c.requestId());
        assertEquals(BusConstants.MSG_TYPE_REQUEST, c.msgType());
        assertEquals(EsqConstants.BIZ_OP_ACCT, c.body().get(EsqConstants.BIZ_KEY_OPERATION));
        assertEquals("1.2.", c.body().get(EsqConstants.BIZ_KEY_ROOT_PATH));
        assertEquals(List.of("teller"), c.body().get(EsqConstants.BIZ_KEY_ROLES));
        assertEquals(Boolean.TRUE, c.body().get(EsqConstants.BIZ_KEY_CAPTURE));
        assertEquals(10, ((Map<String, Object>) c.body().get(EsqConstants.BIZ_KEY_FIELDS)).get("amount"));
        assertFalse(c.body().containsKey(EsqConstants.BIZ_KEY_CMD));
        verify(chain, never()).filter(any());

        assertEquals(200, exchange.getResponse().getStatusCode().value());
        assertEquals("12ms", exchange.getResponse().getHeaders().getFirst(EsqConstants.ESQ_SRV_OUTER_TIME));
        assertEquals("3ms", exchange.getResponse().getHeaders().getFirst(EsqConstants.ESQ_SRV_INNER_TIME));
        assertFalse(exchange.getResponse().isCommitted());
        exchange.getResponse().getHeaders().add(EsqConstants.ESQ_GW_INNER_TIME, "20ms");

        new BizBusWriteResponseFilter().filter(exchange, chain).block(Duration.ofSeconds(2));

        assertEquals("{\"balance\":10}", exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(2)));
        assertEquals("20ms", exchange.getResponse().getHeaders().getFirst(EsqConstants.ESQ_GW_INNER_TIME));
    }

    @Test
    @DisplayName("a request without X-Request-ID is keyed by the gateway's correlation id")
    void keyedByCorrelationId() {
        ArgumentCaptor<RodEvent> sent = ArgumentCaptor.forClass(RodEvent.class);
        when(client.send(sent.capture(), isNull())).thenReturn(Mono.just(answer(Map.of(EsqConstants.BIZ_KEY_STATUS, 200))));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/esq-cmd?kind=52&id=a1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()));
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,
                URI.create("bus://biz-bus/esq-cmd?kind=52&id=a1"));
        exchange.getAttributes().put(EsqConstants.ESQ_CORRELATION_ID, "c9");

        filter.filter(exchange, chain).block(Duration.ofSeconds(2));

        assertEquals("c9", sent.getValue().correlationId());
        assertNull(sent.getValue().requestId());
    }

    @Test
    @DisplayName("a reject answer becomes its status with the problem as an application/problem+json body")
    void rejectWritesProblem() {
        Map<String, Object> body = new HashMap<>();
        body.put(EsqConstants.BIZ_KEY_STATUS, 403);
        body.put(EsqConstants.BIZ_KEY_PROBLEM, Map.of("title", "Permission Denied"));
        when(client.send(any(), isNull())).thenReturn(Mono.just(answer(body)));
        MockServerWebExchange exchange = exchange("bus://biz-bus");

        filter.filter(exchange, chain).block(Duration.ofSeconds(2));

        assertEquals(403, exchange.getResponse().getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, exchange.getResponse().getHeaders().getContentType());
        assertNull(exchange.getResponse().getHeaders().getFirst(EsqConstants.ESQ_SRV_OUTER_TIME));
    }
}

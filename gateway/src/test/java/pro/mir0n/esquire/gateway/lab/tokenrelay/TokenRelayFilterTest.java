/*
 *  Esquire frameworks (tm)
 *  Gateway service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: how the filter dispatches over the variants and what reaches the chain
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter over the variants: first one with something to say wins, and what the chain sees afterwards.
 * <p>
 * This is the part a new pattern inherits without writing it -- a variant decides, the filter carries the
 * decision out. What it must never do is let a refused caller through, or hand the chain the caller's own
 * credential instead of the brokered one.
 */
class TokenRelayFilterTest {

    /** Records whether the chain ran, and with which request. */
    private static final class RecordingChain implements WebFilterChain {

        private boolean called;
        private String seenAuthorization;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            called = true;
            seenAuthorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            return Mono.empty();
        }
    }

    /** A variant that always answers the same thing -- the filter's input, made explicit. */
    private static final class FixedVariant implements ITokenRelayVariant {

        private final VariantAction action;
        private boolean examined;

        private FixedVariant(VariantAction action) {
            this.action = action;
        }

        @Override
        public VariantAction examine(ServerWebExchange exchange) {
            examined = true;
            return action;
        }
    }

    private static ServerWebExchange anExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/esq-kinds")
                        .header(HttpHeaders.AUTHORIZATION, "Basic Y2xpZW50OnNlY3JldA==").build());
    }

    private static TokenRelayCache cacheReturning(String jwt) {
        ITokenRelayClient client = request -> Mono.just(
                new ExpiringJwt(jwt, Instant.now().plus(5, ChronoUnit.MINUTES)));
        return new TokenRelayCache(client);
    }

    private static TokenRelayCache cacheThatFails() {
        ITokenRelayClient client = request -> Mono.error(new IllegalStateException("KeyCloak is not answering"));
        return new TokenRelayCache(client);
    }

    private static VariantAction.Relay relayAction() {
        return new VariantAction.Relay("key-1", new KcTokenRequest(new LinkedMultiValueMap<>(), "c", "s"));
    }

    @Test
    @DisplayName("every variant passes -> the chain runs on the request as it arrived")
    void allPassLeavesTheRequestAlone() {
        RecordingChain chain = new RecordingChain();
        List<ITokenRelayVariant> variants = new ArrayList<>();
        variants.add(new FixedVariant(new VariantAction.Pass()));
        TokenRelayFilter filter = new TokenRelayFilter(variants, cacheReturning("brokered"), -100);

        filter.filter(anExchange(), chain).block();

        assertThat(chain.called).isTrue();
        assertThat(chain.seenAuthorization).isEqualTo("Basic Y2xpZW50OnNlY3JldA==");
    }

    @Test
    @DisplayName("no variants at all -> the filter is a pass-through")
    void noVariantsIsAPassThrough() {
        RecordingChain chain = new RecordingChain();
        TokenRelayFilter filter = new TokenRelayFilter(null, cacheReturning("brokered"), -100);

        filter.filter(anExchange(), chain).block();

        assertThat(chain.called).isTrue();
    }

    @Test
    @DisplayName("a refusal -> 401, and the chain never runs")
    void rejectStopsTheRequest() {
        RecordingChain chain = new RecordingChain();
        List<ITokenRelayVariant> variants = new ArrayList<>();
        variants.add(new FixedVariant(new VariantAction.Reject("not allowlisted")));
        TokenRelayFilter filter = new TokenRelayFilter(variants, cacheReturning("brokered"), -100);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/esq-kinds").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("a relay -> the chain sees the brokered Bearer, not the caller's credential")
    void relaySwapsTheCredential() {
        RecordingChain chain = new RecordingChain();
        List<ITokenRelayVariant> variants = new ArrayList<>();
        variants.add(new FixedVariant(relayAction()));
        TokenRelayFilter filter = new TokenRelayFilter(variants, cacheReturning("brokered-jwt"), -100);

        filter.filter(anExchange(), chain).block();

        assertThat(chain.called).isTrue();
        assertThat(chain.seenAuthorization).isEqualTo("Bearer brokered-jwt");
    }

    @Test
    @DisplayName("the token cannot be got -> 401, and the chain never runs")
    void acquireFailureIsRefused() {
        RecordingChain chain = new RecordingChain();
        List<ITokenRelayVariant> variants = new ArrayList<>();
        variants.add(new FixedVariant(relayAction()));
        TokenRelayFilter filter = new TokenRelayFilter(variants, cacheThatFails(), -100);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/esq-kinds").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("the first variant with something to say wins -- the next is never asked")
    void firstDecisionWins() {
        RecordingChain chain = new RecordingChain();
        FixedVariant first  = new FixedVariant(relayAction());
        FixedVariant second = new FixedVariant(new VariantAction.Reject("would have refused"));
        List<ITokenRelayVariant> variants = new ArrayList<>();
        variants.add(first);
        variants.add(second);
        TokenRelayFilter filter = new TokenRelayFilter(variants, cacheReturning("brokered-jwt"), -100);

        filter.filter(anExchange(), chain).block();

        assertThat(first.examined).isTrue();
        assertThat(second.examined).isFalse();
        assertThat(chain.seenAuthorization).isEqualTo("Bearer brokered-jwt");
    }

    @Test
    @DisplayName("the filter keeps the order it was given -- it runs before the security chain")
    void orderIsWhatItWasGiven() {
        TokenRelayFilter filter = new TokenRelayFilter(new ArrayList<>(), cacheReturning("x"), -100);
        assertThat(filter.getOrder()).isEqualTo(-100);
    }
}

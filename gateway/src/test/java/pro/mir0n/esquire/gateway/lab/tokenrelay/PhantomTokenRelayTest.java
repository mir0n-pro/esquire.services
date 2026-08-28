/*
 *  Esquire frameworks (tm)
 *  Gateway service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: what the Phantom variant decides -- stripped Bearer in, token-exchange out
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Phantom Token Relay: the client presents a claim-stripped Bearer and the gate exchanges it (RFC 8693). */
class PhantomTokenRelayTest {

    private static final String CLIENT          = "esq-hauberk-M";
    private static final String EXCHANGE_ID     = "esq-gw-exchange";
    private static final String EXCHANGE_SECRET = "exchange-secret";
    private static final String GRANT_EXCHANGE  = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE      = "urn:ietf:params:oauth:token-type:access_token";

    private final PhantomTokenRelay variant =
            new PhantomTokenRelay(Set.of(CLIENT), EXCHANGE_ID, EXCHANGE_SECRET);

    private static ServerWebExchange exchangeWith(String authorization) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/esq-kinds").header(HttpHeaders.AUTHORIZATION, authorization).build());
    }

    @Test
    @DisplayName("an allowlisted Bearer -> exchange, keyed by jti, authenticated as the exchange client")
    void allowlistedBearerExchanges() {
        String token = RelayTestTokens.jwt(CLIENT, "jti-abc");

        VariantAction action = variant.examine(exchangeWith("Bearer " + token));

        assertThat(action).isInstanceOf(VariantAction.Relay.class);
        VariantAction.Relay relay = (VariantAction.Relay) action;
        assertThat(relay.cacheKey()).isEqualTo("jti-abc");
        assertThat(relay.kcRequest().basicAuthClientId()).isEqualTo(EXCHANGE_ID);
        assertThat(relay.kcRequest().basicAuthSecret()).isEqualTo(EXCHANGE_SECRET);
        assertThat(relay.kcRequest().formParams().getFirst("grant_type")).isEqualTo(GRANT_EXCHANGE);
        assertThat(relay.kcRequest().formParams().getFirst("subject_token")).isEqualTo(token);
        assertThat(relay.kcRequest().formParams().getFirst("subject_token_type")).isEqualTo(TOKEN_TYPE);
    }

    @Test
    @DisplayName("the cache key is the caller's jti, so two callers never share an exchanged token")
    void cacheKeyIsPerToken() {
        VariantAction first  = variant.examine(exchangeWith(RelayTestTokens.bearer(CLIENT, "jti-1")));
        VariantAction second = variant.examine(exchangeWith(RelayTestTokens.bearer(CLIENT, "jti-2")));

        assertThat(((VariantAction.Relay) first).cacheKey())
                .isNotEqualTo(((VariantAction.Relay) second).cacheKey());
    }

    @Test
    @DisplayName("an allowlisted Bearer with no jti -> refused, there is no key to cache it under")
    void bearerWithoutJtiIsRefused() {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.bearer(CLIENT, null)));

        assertThat(action).isInstanceOf(VariantAction.Reject.class);
        assertThat(((VariantAction.Reject) action).reason()).contains("jti");
    }

    @Test
    @DisplayName("HTTP Basic is not this variant's business")
    void basicIsPassed() {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.basic(CLIENT, "whatever")));
        assertThat(action).isInstanceOf(VariantAction.Pass.class);
    }
}

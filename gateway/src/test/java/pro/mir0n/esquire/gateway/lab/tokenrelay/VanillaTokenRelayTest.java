/*
 *  Esquire frameworks (tm)
 *  Gateway service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: what the Vanilla variant decides -- Basic in, client_credentials out
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

/** Vanilla Token Relay: the client presents HTTP Basic and the gate runs client_credentials for it. */
class VanillaTokenRelayTest {

    private static final String CLIENT = "esq-hauberk-S";
    private static final String SECRET = "s3cret";

    private final VanillaTokenRelay variant = new VanillaTokenRelay(Set.of(CLIENT));

    private static ServerWebExchange exchangeWith(String authorization) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/esq-kinds").header(HttpHeaders.AUTHORIZATION, authorization).build());
    }

    @Test
    @DisplayName("Basic for an allowlisted client -> relay, keyed by client_id, as that client")
    void allowlistedBasicRelays() {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.basic(CLIENT, SECRET)));

        assertThat(action).isInstanceOf(VariantAction.Relay.class);
        VariantAction.Relay relay = (VariantAction.Relay) action;
        assertThat(relay.cacheKey()).isEqualTo(CLIENT);
        assertThat(relay.kcRequest().basicAuthClientId()).isEqualTo(CLIENT);
        assertThat(relay.kcRequest().basicAuthSecret()).isEqualTo(SECRET);
        assertThat(relay.kcRequest().formParams().getFirst("grant_type")).isEqualTo("client_credentials");
    }

    @Test
    @DisplayName("the cache key is the client, so two calls by one client share one token")
    void cacheKeyIsStablePerClient() {
        VariantAction first  = variant.examine(exchangeWith(RelayTestTokens.basic(CLIENT, SECRET)));
        VariantAction second = variant.examine(exchangeWith(RelayTestTokens.basic(CLIENT, SECRET)));

        assertThat(((VariantAction.Relay) first).cacheKey())
                .isEqualTo(((VariantAction.Relay) second).cacheKey());
    }

    @Test
    @DisplayName("Basic for a client nobody allowlisted -> refused")
    void unknownBasicIsRefused() {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.basic("esq-stranger", SECRET)));
        assertThat(action).isInstanceOf(VariantAction.Reject.class);
    }

    @Test
    @DisplayName("a Basic header that is not decodable -> refused")
    void malformedBasicIsRefused() {
        VariantAction action = variant.examine(exchangeWith("Basic ****not-base64****"));
        assertThat(action).isInstanceOf(VariantAction.Reject.class);
    }

    @Test
    @DisplayName("a vanilla client arriving with a Bearer -> refused, it must use Basic")
    void vanillaClientMayNotBringItsOwnBearer() {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.bearer(CLIENT, "jti-1")));

        assertThat(action).isInstanceOf(VariantAction.Reject.class);
        assertThat(((VariantAction.Reject) action).reason()).contains(CLIENT);
    }

    @Test
    @DisplayName("someone else's Bearer is not this variant's business")
    void otherBearerIsPassed() {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.bearer("esq-angular", "jti-2")));
        assertThat(action).isInstanceOf(VariantAction.Pass.class);
    }
}

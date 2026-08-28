/*
 *  Esquire frameworks (tm)
 *  Gateway service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: the rules EVERY token-relay variant obeys, run over every variant there is
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What every Token Relay variant must do, whatever pattern it implements.
 * <p>
 * The gate's relay is a seam, not a pair of features: a variant is an {@link ITokenRelayVariant}, and the
 * filter knows only Pass / Reject / Relay. A new pattern -- another identity service, another grant -- arrives
 * as another variant, and these are the rules it has to keep. Adding it to {@link #variants()} is what runs
 * them for it.
 * <p>
 * The rules are the ones that would be a hole if broken, not the ones that describe a particular grant:
 * an anonymous request is nobody's business, an unrecognised scheme is left alone, and a caller the
 * deployment did not allowlist is never relayed on.
 */
class TokenRelayVariantContractTest {

    private static final String ALLOWED   = "esq-allowed-client";
    private static final String STRANGER  = "esq-some-other-client";
    private static final String SECRET    = "a-secret";
    private static final String EXCHANGE  = "esq-gw-exchange";

    static List<ITokenRelayVariant> variants() {
        List<ITokenRelayVariant> ret = new ArrayList<>();
        ret.add(new VanillaTokenRelay(Set.of(ALLOWED)));
        ret.add(new PhantomTokenRelay(Set.of(ALLOWED), EXCHANGE, SECRET));
        return ret;
    }

    private static ServerWebExchange exchangeWith(String authorization) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/esq-kinds");
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @ParameterizedTest(name = "{0} passes an anonymous request")
    @MethodSource("variants")
    void anonymousRequestIsPassed(ITokenRelayVariant variant) {
        VariantAction action = variant.examine(exchangeWith(null));
        assertThat(action).isInstanceOf(VariantAction.Pass.class);
    }

    @ParameterizedTest(name = "{0} passes a scheme it does not handle")
    @MethodSource("variants")
    void unknownSchemeIsPassed(ITokenRelayVariant variant) {
        VariantAction action = variant.examine(exchangeWith("Negotiate YIIFjgYGKwYB"));
        assertThat(action).isInstanceOf(VariantAction.Pass.class);
    }

    @ParameterizedTest(name = "{0} never relays a client outside its allowlist -- basic")
    @MethodSource("variants")
    void basicCredentialsOfAStrangerAreNeverRelayed(ITokenRelayVariant variant) {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.basic(STRANGER, SECRET)));
        assertThat(action).isNotInstanceOf(VariantAction.Relay.class);
    }

    @ParameterizedTest(name = "{0} never relays a client outside its allowlist -- bearer")
    @MethodSource("variants")
    void bearerOfAStrangerIsNeverRelayed(ITokenRelayVariant variant) {
        VariantAction action = variant.examine(exchangeWith(RelayTestTokens.bearer(STRANGER, "jti-1")));
        assertThat(action).isNotInstanceOf(VariantAction.Relay.class);
    }

    @ParameterizedTest(name = "{0} never relays a bearer that is not a JWT at all")
    @MethodSource("variants")
    void unparseableBearerIsNeverRelayed(ITokenRelayVariant variant) {
        VariantAction action = variant.examine(exchangeWith("Bearer not-a-jwt"));
        assertThat(action).isNotInstanceOf(VariantAction.Relay.class);
    }
}

/*
 *  Esquire frameworks (tm)
 *  Gateway service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: the gate refuses to start when it was told to relay and cannot
 */
package pro.mir0n.esquire.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.identity.KcConnectionSettings;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The start-up guard on the Token Relay wiring.
 * <p>
 * An allowlist is a deployment saying those clients get relayed. The gate must not come up half-able: either
 * it can broker a token for them, or it says so and stops. An EMPTY allowlist is the off switch, not a
 * mistake -- the cloud gate runs that way.
 */
class TokenRelayWiringGuardTest {

    private static final Set<String> NOBODY  = Set.of();
    private static final Set<String> VANILLA = Set.of("esq-hauberk-S");
    private static final Set<String> PHANTOM = Set.of("esq-hauberk-M");

    private static KcConnectionSettings connection(String baseUrl, String realm, String clientId) {
        return new KcConnectionSettings(baseUrl, realm, clientId, "a-secret", 5000, 10000);
    }

    private static KcConnectionSettings whole() {
        return connection("http://keycloak:8080/kc-auth", "esquire", "esq-gw-exchange");
    }

    @Test
    @DisplayName("nobody allowlisted and nothing configured -> the relay is simply off")
    void offIsNotAMisconfiguration() {
        KcConnectionSettings nothing = connection("", "", "");
        assertThatCode(() -> SecurityConfig.assertRelayWiring(NOBODY, NOBODY, nothing))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nobody allowlisted while the connection IS set -> still off, still fine (the cloud gate)")
    void connectionWithoutAllowlistsIsFine() {
        assertThatCode(() -> SecurityConfig.assertRelayWiring(NOBODY, NOBODY, whole()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("armed and reachable -> starts")
    void fullyWiredStarts() {
        assertThatCode(() -> SecurityConfig.assertRelayWiring(VANILLA, PHANTOM, whole()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("vanilla allowlisted with no endpoint -> refuses to start")
    void vanillaWithoutEndpointFailsFast() {
        KcConnectionSettings noEndpoint = connection("", "esquire", "esq-gw-exchange");
        assertThatThrownBy(() -> SecurityConfig.assertRelayWiring(VANILLA, NOBODY, noEndpoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base-url");
    }

    @Test
    @DisplayName("phantom allowlisted with no realm -> refuses to start")
    void phantomWithoutRealmFailsFast() {
        KcConnectionSettings noRealm = connection("http://keycloak:8080/kc-auth", "", "esq-gw-exchange");
        assertThatThrownBy(() -> SecurityConfig.assertRelayWiring(NOBODY, PHANTOM, noRealm))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("realm");
    }

    @Test
    @DisplayName("phantom allowlisted with no exchange client -> refuses to start")
    void phantomWithoutExchangeClientFailsFast() {
        KcConnectionSettings noClient = connection("http://keycloak:8080/kc-auth", "esquire", "");
        assertThatThrownBy(() -> SecurityConfig.assertRelayWiring(NOBODY, PHANTOM, noClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-id");
    }

    @Test
    @DisplayName("vanilla alone needs no exchange client -- that is phantom's credential")
    void vanillaDoesNotNeedTheExchangeClient() {
        KcConnectionSettings noClient = connection("http://keycloak:8080/kc-auth", "esquire", "");
        assertThatCode(() -> SecurityConfig.assertRelayWiring(VANILLA, NOBODY, noClient))
                .doesNotThrowAnyException();
    }
}

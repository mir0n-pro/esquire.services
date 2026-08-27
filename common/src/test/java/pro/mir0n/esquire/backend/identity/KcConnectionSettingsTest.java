/*
 *  Esquire frameworks (tm)
 *  Common module -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: the one reader of a KeyCloak connection -- the values it hands both clients
 */
package pro.mir0n.esquire.backend.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the two KeyCloak clients are handed.
 * <p>
 * Neither client's deadline can be right if the number reaching its builder is wrong, and this is where that
 * number is decided -- once, for kcMaster's admin client under {@code keycloak.admin} and for the gate's relay
 * client under {@code keycloak.exchange}. That the builders then HONOUR what they are given is the libraries'
 * contract, measured by hand against a black-holed address rather than pinned here: a connect-deadline test
 * needs a network that swallows a SYN, which is not a thing a build can rely on.
 */
class KcConnectionSettingsTest {

    private static final String PREFIX = "keycloak.admin";

    private static MockEnvironment envWith(String baseUrl, String realm) {
        MockEnvironment ret = new MockEnvironment();
        ret.setProperty(PREFIX + ".base-url", baseUrl);
        ret.setProperty(PREFIX + ".realm", realm);
        return ret;
    }

    @Test
    @DisplayName("every key present -> every key read")
    void readsTheWholeBlock() {
        MockEnvironment env = envWith("http://keycloak:8080/kc-auth", "esquire");
        env.setProperty(PREFIX + ".client-id", "esq-kcMaster");
        env.setProperty(PREFIX + ".client-secret", "s3cret");
        env.setProperty(PREFIX + ".connect-timeout-ms", "1500");
        env.setProperty(PREFIX + ".read-timeout-ms", "7000");

        KcConnectionSettings settings = KcConnectionSettings.from(env, PREFIX);

        assertThat(settings.getBaseUrl()).isEqualTo("http://keycloak:8080/kc-auth");
        assertThat(settings.getRealm()).isEqualTo("esquire");
        assertThat(settings.getClientId()).isEqualTo("esq-kcMaster");
        assertThat(settings.getClientSecret()).isEqualTo("s3cret");
        assertThat(settings.getConnectTimeoutMs()).isEqualTo(1500);
        assertThat(settings.getReadTimeoutMs()).isEqualTo(7000);
    }

    @Test
    @DisplayName("no deadlines given -> the defaults, not zero")
    void deadlinesHaveDefaults() {
        KcConnectionSettings settings =
                KcConnectionSettings.from(envWith("http://keycloak:8080", "esquire"), PREFIX);

        assertThat(settings.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(settings.getReadTimeoutMs()).isEqualTo(10000);
    }

    @Test
    @DisplayName("nothing given at all -> still a deadline, and off")
    void emptyBlockIsOffButStillBounded() {
        KcConnectionSettings settings = KcConnectionSettings.from(new MockEnvironment(), PREFIX);

        assertThat(settings.isConfigured()).isFalse();
        assertThat(settings.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(settings.getReadTimeoutMs()).isEqualTo(10000);
    }

    @Test
    @DisplayName("the token endpoint is base-url + realm, never configured whole")
    void buildsTheTokenEndpoint() {
        KcConnectionSettings settings =
                KcConnectionSettings.from(envWith("http://keycloak:8080/kc-auth", "esquire"), PREFIX);

        assertThat(settings.tokenUri())
                .isEqualTo("http://keycloak:8080/kc-auth/realms/esquire/protocol/openid-connect/token");
    }

    @Test
    @DisplayName("a trailing slash on the base url does not double up in the endpoint")
    void trailingSlashIsTrimmed() {
        KcConnectionSettings settings =
                KcConnectionSettings.from(envWith("https://esquire.mir0n.pro/kc-auth//", "esquire"), PREFIX);

        assertThat(settings.getBaseUrl()).isEqualTo("https://esquire.mir0n.pro/kc-auth");
        assertThat(settings.tokenUri())
                .isEqualTo("https://esquire.mir0n.pro/kc-auth/realms/esquire/protocol/openid-connect/token");
    }

    @Test
    @DisplayName("a connection needs both an endpoint and a realm to be one")
    void isConfiguredNeedsBoth() {
        assertThat(KcConnectionSettings.from(envWith("http://keycloak:8080", "esquire"), PREFIX)
                .isConfigured()).isTrue();
        assertThat(KcConnectionSettings.from(envWith("", "esquire"), PREFIX)
                .isConfigured()).isFalse();
        assertThat(KcConnectionSettings.from(envWith("http://keycloak:8080", ""), PREFIX)
                .isConfigured()).isFalse();
        assertThat(KcConnectionSettings.from(envWith("   ", "esquire"), PREFIX)
                .isConfigured()).isFalse();
    }

    @Test
    @DisplayName("a missing secret is an empty one -- the client builder is never handed a null")
    void secretIsNeverNull() {
        KcConnectionSettings settings =
                KcConnectionSettings.from(envWith("http://keycloak:8080", "esquire"), PREFIX);

        assertThat(settings.getClientSecret()).isEmpty();
        assertThat(new KcConnectionSettings("http://x", "r", "c", null, 1, 2).getClientSecret()).isEmpty();
    }

    @Test
    @DisplayName("the prefix picks the connection -- two blocks, one reader")
    void thePrefixChoosesTheConnection() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("keycloak.admin.base-url", "http://keycloak:8080/kc-auth");
        env.setProperty("keycloak.admin.realm", "esquire");
        env.setProperty("keycloak.admin.client-id", "esq-kcMaster");
        env.setProperty("keycloak.exchange.base-url", "https://esquire.mir0n.pro/kc-auth");
        env.setProperty("keycloak.exchange.realm", "esquire");
        env.setProperty("keycloak.exchange.client-id", "esq-gw-exchange");

        KcConnectionSettings admin    = KcConnectionSettings.from(env, "keycloak.admin");
        KcConnectionSettings exchange = KcConnectionSettings.from(env, "keycloak.exchange");

        assertThat(admin.getClientId()).isEqualTo("esq-kcMaster");
        assertThat(exchange.getClientId()).isEqualTo("esq-gw-exchange");
        assertThat(admin.getBaseUrl()).isNotEqualTo(exchange.getBaseUrl());
    }
}

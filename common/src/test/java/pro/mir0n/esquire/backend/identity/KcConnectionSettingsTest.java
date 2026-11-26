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
 * What kcMaster's KeyCloak admin client is handed.
 * <p>
 * The client's deadline cannot be right if the number reaching its builder is wrong, and this is where that
 * number is decided -- once, under {@code keycloak.admin}. That the builder then HONOURS what it is given is the
 * library's contract, measured by hand against a black-holed address rather than pinned here: a connect-deadline
 * test needs a network that swallows a SYN, which is not a thing a build can rely on.
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
}

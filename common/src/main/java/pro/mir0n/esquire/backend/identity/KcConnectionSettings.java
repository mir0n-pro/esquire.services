/*
 *  Esquire frameworks (tm)
 *  Common module
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: the one shape a KeyCloak connection is configured in -- endpoint, realm,
 *                   credentials and the two timeouts -- read from a property prefix so every service
 *                   that talks to KeyCloak is configured the same way
 */
package pro.mir0n.esquire.backend.identity;

import org.springframework.core.env.Environment;

/**
 * What a service needs to reach KeyCloak, in the one shape every service uses.
 */
public class KcConnectionSettings {

    private static final String TOKEN_PATH = "/realms/%s/protocol/openid-connect/token";

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS    = 10000;

    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final int    connectTimeoutMs;
    private final int    readTimeoutMs;

    public KcConnectionSettings(String baseUrl, String realm, String clientId, String clientSecret,
                                int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl          = trimTrailingSlash(baseUrl);
        this.realm            = realm;
        this.clientId         = clientId;
        this.clientSecret     = clientSecret == null ? "" : clientSecret;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs    = readTimeoutMs;
    }

    /**
     * Reads one connection out of the environment. The prefix names WHICH connection -- {@code keycloak.admin}
     * or {@code keycloak.exchange} -- and the six keys under it are the same either way.
     */
    public static KcConnectionSettings from(Environment env, String prefix) {
        String baseUrl      = env.getProperty(prefix + ".base-url", "");
        String realm        = env.getProperty(prefix + ".realm", "");
        String clientId     = env.getProperty(prefix + ".client-id", "");
        String clientSecret = env.getProperty(prefix + ".client-secret", "");
        int connectMs = env.getProperty(prefix + ".connect-timeout-ms", Integer.class, DEFAULT_CONNECT_TIMEOUT_MS);
        int readMs    = env.getProperty(prefix + ".read-timeout-ms",    Integer.class, DEFAULT_READ_TIMEOUT_MS);

        KcConnectionSettings ret =
                new KcConnectionSettings(baseUrl, realm, clientId, clientSecret, connectMs, readMs);
        return ret;
    }

    /** True when there is an endpoint and a realm to talk to; a blank base url is how a connection stays off. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && realm != null && !realm.isBlank();
    }

    /** The realm's token endpoint, built from the base url the same way the admin client builds its own. */
    public String tokenUri() {
        return baseUrl + String.format(TOKEN_PATH, realm);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getRealm() {
        return realm;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    private static String trimTrailingSlash(String url) {
        String ret = url;
        if (ret != null) {
            while (ret.endsWith("/")) {
                ret = ret.substring(0, ret.length() - 1);
            }
        }
        return ret;
    }
}

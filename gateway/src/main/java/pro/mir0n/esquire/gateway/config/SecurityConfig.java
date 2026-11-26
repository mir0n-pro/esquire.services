/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n  make sure TREE role is in place
 * 01/18/2026 mir0n  added exposedHeaders to CORS
 * 02/12/2026 mir0n  let "/esq-kinds" pass thru without validation
 * 05/14/2026 mir0n  v1.2.4: jwtDecoder bean (NimbusReactiveJwtDecoder, optionally wrapped
 *                   by JweAwareJwtDecoder when esq.jwe.private-key-path is set);
 *                   CredentialBoundAuthenticationFilter wired into the security chain
 *                   ahead of AUTHENTICATION (Pattern 3 -- HTTP Basic at client, gateway
 *                   brokers JWT via client_credentials);
 *                   PhantomTokenAuthenticationFilter wired after Credential-Bound, also
 *                   ahead of AUTHENTICATION (Pattern 4 -- stripped JWT at client, gateway
 *                   exchanges via RFC 8693 token-exchange);
 *                   both filters built inline by springSecurityFilterChain (not @Bean) so
 *                   Spring WebFlux does not auto-register them globally;
 *                   esq.gateway.credential-bound.{token-uri,clients} and
 *                   esq.gateway.phantom-token.{token-uri,clients,exchange-client-id,exchange-client-secret}
 *                   @Value injections added;
 *                   CORS exposed-headers extended with the four observability headers
 *                   (X-Response-Time, Esq-Gw-Inner-Time, Esq-Srv-Outer-Time, Esq-Srv-Inner-Time)
 * 07/17/2026 mir0n  note at the switch: the JWKS fetch to KeyCloak is left un-instrumented on purpose (I42/L3
 *                   accepted) -- it has no meter and its time falls in the gw.outer-minus-gw.inner window; the
 *                   cost lands on one request per key rotation (ReactiveRemoteJWKSource caches).
 * 07/23/2026 mir0n  v1.2.11 -- the "/esq*" authorization is a SINGLE hasRole("TREE") rule (implies authenticated
 *                   AND the TREE realm role); comment on why there must be exactly one /esq* rule (first-match-wins)
 * 08/26/2026 mir0n  the JWT decoder carries the default validators through DelegatingOAuth2TokenValidator, and the
 *                   JWE-aware decoder and token-relay wiring leave this class
 * 09/05/2026 mir0n  v1.2.15 -- exceptionHandling() names EsqAccessDeniedHandler as the access-denied handler
 * 11/21/2026 mir0n  v2.0.2 -- pure OAuth (lab): the esq.gateway.pure-oauth.* settings; the PureOAuthClaimsCache
 *                   bean (a WebClient under the routes' connect timeout and claims-timeout-ms; started only when
 *                   a client is listed, and claims-uri required then) and the PureOAuthEvictionListener bean;
 *                   the JWT authentication converter is PureOAuthAuthenticationConverter, PureOAuthHeaderFilter
 *                   runs LAST, and EsqClaimsValidator gets the pure client list
 * 11/22/2026 mir0n  v2.0.3 -- pure OAuth only: the JWE-aware decoder, the token relay (TokenRelayFilter, the
 *                   keycloak.exchange connection, assertRelayWiring) and the claims-mode converter
 *                   (KeycloakRoleConverter) out; the esq.gateway.oauth.* settings (cache-idle-ms, claims-uri,
 *                   claims-timeout-ms) in place of esq.gateway.pure-oauth.*, with no client list; the
 *                   claimsCache bean always started, claims-uri required (assertOAuthWiring); the
 *                   evictionListener bean; AuthenticationConverter for every token; EsqClaimsValidator with no
 *                   client list; log prefix OAUTH
 * 11/25/2026 mir0n  v2.0.3 -- .oauth2Client() out; 
 */
package pro.mir0n.esquire.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.reactive.function.client.WebClient;
import pro.mir0n.esquire.gateway.security.EsqAccessDeniedHandler;
import pro.mir0n.esquire.gateway.security.EsqClaimsValidator;
import pro.mir0n.esquire.gateway.oauth.AuthenticationConverter;
import pro.mir0n.esquire.gateway.oauth.ClaimsCache;
import pro.mir0n.esquire.gateway.oauth.EvictionListener;
import pro.mir0n.esquire.gateway.oauth.HeaderFilter;
import pro.mir0n.esquire.gateway.oauth.WebClientUserClaimsClient;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger log    = LoggerFactory.getLogger(SecurityConfig.class);
    private static final Logger devLog = LoggerFactory.getLogger("develop." + SecurityConfig.class.getName());

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${esq.gateway.oauth.cache-idle-ms:60000}")
    private long claimsCacheIdleMs;

    @Value("${esq.gateway.oauth.claims-uri:}")
    private String claimsUri;

    @Value("${esq.gateway.oauth.claims-timeout-ms:5000}")
    private long claimsTimeoutMs;

    //xxx: keySmith is a backend like the routed services -- the claims call connects under the routes' timeout
    @Value("${spring.cloud.gateway.httpclient.connect-timeout:2000}")
    private int backendConnectTimeoutMs;

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        OAuth2TokenValidator<Jwt> tokenValidator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new EsqClaimsValidator());
        NimbusReactiveJwtDecoder ret = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        ret.setJwtValidator(tokenValidator);
        return ret;
    }

    static void assertOAuthWiring(String claimsUri) {
        if (claimsUri == null || claimsUri.isBlank()) {
            log.error("OAUTH | esq.gateway.oauth.claims-uri is not set -- there is nowhere to ask for the claims");
            throw new IllegalStateException("OAuth: esq.gateway.oauth.claims-uri is not set");
        }
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ClaimsCache claimsCache() {
        assertOAuthWiring(claimsUri);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, backendConnectTimeoutMs)
                .responseTimeout(Duration.ofMillis(claimsTimeoutMs));
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        ClaimsCache ret = new ClaimsCache(
                new WebClientUserClaimsClient(webClient, claimsUri), claimsCacheIdleMs);
        log.info("OAUTH | claimsUri={} cacheIdleMs={} connectTimeoutMs={} claimsTimeoutMs={}",
                claimsUri, claimsCacheIdleMs, backendConnectTimeoutMs, claimsTimeoutMs);
        return ret;
    }

    @Bean(initMethod = "start")
    public EvictionListener evictionListener(ClaimsCache claimsCache) {
        return new EvictionListener(claimsCache);
    }

     //we're going to have custom SecurityWebFilterChain so spring.cloud.gateway.globalcors would not work
    // will use custom spring.security.cors structure
     @ConfigurationProperties(prefix = "spring.security.cors")
     public record CorsProperties(
             List<String> allowedOrigins,
             List<String> allowedMethods,
             List<String> allowedHeaders,
             List<String> exposedHeaders,
             boolean allowCredentials,
             Long maxAge
     ) {}

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(props.allowedMethods()); // Ensure OPTIONS is included
        config.setAllowedHeaders(props.allowedHeaders());
        config.setExposedHeaders(props.exposedHeaders());

        config.setAllowCredentials(props.allowCredentials());
        config.setMaxAge(props.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity,
                                                            CorsProperties props,
                                                            ClaimsCache claimsCache) {
        AuthenticationConverter authenticationConverter = new AuthenticationConverter(
                claimsCache);
/*
       serverHttpSecurity.authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.GET).permitAll()
                .pathMatchers("/eazybank/accounts/**").hasRole("ACCOUNTS")
                .pathMatchers("/eazybank/cards/**").hasRole("CARDS")
                .pathMatchers("/eazybank/loans/**").hasRole("LOANS"))
                .oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec
                        .jwt(jwtSpec -> jwtSpec.jwtAuthenticationConverter(grantedAuthoritiesExtractor())));
*/
/*
        serverHttpSecurity.authorizeExchange(exchanges ->
                 exchanges.pathMatchers("/login").permitAll()
                 .pathMatchers("/esq").authenticated() // Protect your endpoint
                 .anyExchange().permitAll())
                 .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
//                .oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec
//                .jwt(jwtSpec -> jwtSpec.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))
                .csrf(csrfSpec -> csrfSpec.disable())
                .cors(cors -> cors.disable());
 */
        serverHttpSecurity
                .csrf(csrfSpec -> csrfSpec.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(props)))
                //xxx: LAST, after authorization -- the claims headers need the authenticated caller
                .addFilterAt(new HeaderFilter(), SecurityWebFiltersOrder.LAST)
                .authorizeExchange(exchanges -> exchanges
                    .pathMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                    .pathMatchers("/esq-kinds").permitAll()
                    .pathMatchers("/esq*").hasRole("TREE")  // authenticated + the TREE role
                    .anyExchange().permitAll()
                )
                .exceptionHandling(spec -> spec.accessDeniedHandler(new EsqAccessDeniedHandler()))
                //xxx: the roles come from keySmith, as ROLE_<role>; the default converter emits SCOPE_* only and
                // ROLE_TREE would never be present
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtSpec ->
                        jwtSpec.jwtAuthenticationConverter(authenticationConverter)))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        return serverHttpSecurity.build();
    }

}

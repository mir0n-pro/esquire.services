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
 */
package pro.mir0n.esquire.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.reactive.function.client.WebClient;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.gateway.security.JweAwareJwtDecoder;
import pro.mir0n.esquire.gateway.security.tokenrelay.ITokenRelayClient;
import pro.mir0n.esquire.gateway.security.tokenrelay.ITokenRelayVariant;
import pro.mir0n.esquire.gateway.security.tokenrelay.PhantomTokenRelay;
import pro.mir0n.esquire.gateway.security.tokenrelay.TokenRelayCache;
import pro.mir0n.esquire.gateway.security.tokenrelay.TokenRelayFilter;
import pro.mir0n.esquire.gateway.security.tokenrelay.VanillaTokenRelay;
import pro.mir0n.esquire.gateway.security.tokenrelay.WebClientTokenRelayClient;
import reactor.core.publisher.Mono;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + SecurityConfig.class.getName());

    @Value("${esq.jwe.private-key-path:}")
    private String jwePrivateKeyPath;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${esq.gateway.token-relay.token-uri:}")
    private String tokenRelayTokenUri;

    @Value("${esq.gateway.token-relay.vanilla.clients:}")
    private List<String> vanillaClients;

    @Value("${esq.gateway.token-relay.phantom.clients:}")
    private List<String> phantomClients;

    @Value("${esq.gateway.token-relay.phantom.exchange-client-id:}")
    private String phantomExchangeClientId;

    @Value("${esq.gateway.token-relay.phantom.exchange-client-secret:}")
    private String phantomExchangeClientSecret;

    /**
     * JWT decoder bean. Two layers (innermost first):
     *   1. Base JWS decoder: NimbusReactiveJwtDecoder against KC's JWKS.
     *   2. JWE wrapper (optional): JweAwareJwtDecoder when
     *      esq.jwe.private-key-path is set and the key file exists.
     *      Handles 5-part JWE tokens; 3-part JWS pass through untouched.
     *
     * Token Relay variants (Vanilla, Phantom) are handled at a different
     * seam: the TokenRelayFilter (added below) rewrites the inbound
     * Authorization to Bearer with the relayed/exchanged JWT BEFORE this
     * decoder runs, so by the time the decoder sees the request it is
     * indistinguishable from plain JWT traffic.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // I42/L3 (ACCEPTED, 2026-07-16): the JWKS fetch to KC is left UN-instrumented on purpose -- no span, no
        // timer. The question I42 asks is whether every step of a REST collaboration can have its DURATION
        // accounted for, and this step's cannot: it has no meter, and its time falls inside the gateway's own
        // gw.outer-minus-gw.inner window. That is accepted because of FREQUENCY, not because it is measured.
        // ReactiveRemoteJWKSource caches the JWK set (AtomicReference<Mono<JWKSet>>) and re-fetches ONLY when a
        // kid is missing from the cache -- i.e. at first use and at key rotation. So the cost lands on ONE request
        // per pod lifetime / per rotation, not on the hot path. Contrast L2, the Token Relay /token call, which
        // EVERY cache-missing request pays and which therefore does carry its own drawn meter
        // (esq.biz.gw.tokenrelay.duration by outcome).
        // If this ever needs instrumenting, the seam already exists -- withJwkSetUri(uri).webClient(wc) accepts a
        // WebClient, so handing it an observation-instrumented one (the autoconfigured WebClient.Builder bean,
        // NOT the static WebClient.builder()) gives it a CLIENT span + http.client.requests timer with no new
        // machinery. Deliberately not done: it would buy visibility into an event that hits one request per
        // rotation. The same note sits on the JWE path -- see JweAwareJwtDecoder's ctor.
        ReactiveJwtDecoder ret = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        if (jwePrivateKeyPath != null && !jwePrivateKeyPath.isBlank()) {
            try (FileInputStream fis = new FileInputStream(jwePrivateKeyPath)) {
                RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(fis);
                devLog.debug("jwtDecoder: JWE-aware decoder configured -- key loaded from [{}]", jwePrivateKeyPath);
                ret = new JweAwareJwtDecoder(privateKey, jwkSetUri);
            } catch (FileNotFoundException ex) {
                devLog.debug("jwtDecoder: key file absent at [{}] -- JWE disabled, using plain JWS decoder", jwePrivateKeyPath);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to load JWE private key: " + ex.getMessage(), ex);
            }
        }
        return ret;
    }

    /**
     * Build the unified Token Relay filter. NOT a @Bean -- if it were, Spring
     * WebFlux would auto-register it globally and the filter would run twice
     * per request (once in the global chain, once in the security chain),
     * causing the post-rewrite Bearer to re-trigger the filter and fail the
     * azp check. Constructed inline by springSecurityFilterChain so it only
     * runs in the security filter chain.
     *
     * The filter iterates configured variants (Vanilla first, Phantom second)
     * and branches on whichever returns a non-Pass action. Both variants
     * share one TokenRelayCache and one WebClientTokenRelayClient. Adding a
     * future variant is one new {@link ITokenRelayVariant} implementation
     * plus one line in this method.
     */
    private TokenRelayFilter buildTokenRelayFilter() {
        Set<String> vanillaAllowlist = parseAllowlist(vanillaClients);
        Set<String> phantomAllowlist = parseAllowlist(phantomClients);

        List<ITokenRelayVariant> variants = new ArrayList<>();
        // Vanilla MUST run before Phantom: Vanilla rejects Bearer-with-azp-in-vanilla
        // allowlist; if Phantom ran first that same Bearer would Pass (azp not in
        // phantom allowlist) and Vanilla would still reject -- correct either way --
        // but ordering Vanilla first keeps the reject decision visible at the top of
        // the variant chain.
        if (!vanillaAllowlist.isEmpty()) {
            variants.add(new VanillaTokenRelay(vanillaAllowlist));
            devLog.debug("buildTokenRelayFilter: Vanilla variant enabled, clients={}", vanillaAllowlist);
        }
        if (!phantomAllowlist.isEmpty()
                && phantomExchangeClientId != null && !phantomExchangeClientId.isBlank()) {
            variants.add(new PhantomTokenRelay(phantomAllowlist, phantomExchangeClientId,
                    phantomExchangeClientSecret == null ? "" : phantomExchangeClientSecret));
            devLog.debug("buildTokenRelayFilter: Phantom variant enabled, clients={}, exchange-client=[{}]",
                    phantomAllowlist, phantomExchangeClientId);
        }

        // Both variants funnel through one shared KC client at one shared token-uri.
        ITokenRelayClient kcClient;
        if (tokenRelayTokenUri != null && !tokenRelayTokenUri.isBlank() && !variants.isEmpty()) {
            kcClient = new WebClientTokenRelayClient(WebClient.builder().build(), tokenRelayTokenUri);
            devLog.debug("buildTokenRelayFilter: KC client enabled, token-uri=[{}]", tokenRelayTokenUri);
        } else {
            kcClient = req -> Mono.error(new IllegalStateException(
                    "Token Relay not configured -- token-uri unset or no variants enabled"));
            devLog.debug("buildTokenRelayFilter: KC client disabled (token-uri-set={}, variants={})",
                    tokenRelayTokenUri != null && !tokenRelayTokenUri.isBlank(), variants.size());
        }

        TokenRelayCache cache = new TokenRelayCache(kcClient);
        return new TokenRelayFilter(variants, cache, -100);
    }

    private Set<String> parseAllowlist(List<String> raw) {
        Set<String> ret = new HashSet<>();
        if (raw != null) {
            for (String s : raw) {
                if (s != null && !s.isBlank()) {
                    ret.add(s.trim());
                }
            }
        }
        return ret;
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
                                                            CorsProperties props) {
        TokenRelayFilter tokenRelayFilter = buildTokenRelayFilter();
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
                .addFilterBefore(tokenRelayFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                    .pathMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                    // hasRole("TREE") implies authenticated (an anonymous request carries no ROLE_TREE), so this
                    // single rule enforces BOTH: a valid realm JWT AND the TREE realm role. authorizeExchange is
                    // first-match-wins -- an earlier ".authenticated()" on the same "/esq*" pattern would shadow
                    // this and skip the role check, so there must be exactly ONE "/esq*" rule and it must be the
                    // role one. It works only because the JWT converter is wired to KeycloakRoleConverter (see
                    // oauth2ResourceServer below), which maps realm_access.roles -> ROLE_<role>; Spring's default
                    // converter emits SCOPE_* only, which is why "hasRole did not work" before and it had been
                    // left as bare authenticated().
                    .pathMatchers("/esq-kinds").permitAll() // public dictionary endpoint
                    .pathMatchers("/esq*").hasRole("TREE")  // authenticated + TREE realm role
                    .anyExchange().permitAll()
                )
                // Wire KeycloakRoleConverter (realm_access.roles -> ROLE_<role>) so hasRole("TREE") above can
                // actually match; the default converter emits SCOPE_* only and ROLE_TREE would never be present.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtSpec ->
                        jwtSpec.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))
                .oauth2Client(Customizer.withDefaults())
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        return serverHttpSecurity.build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter =
                new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter
                (new KeycloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

}

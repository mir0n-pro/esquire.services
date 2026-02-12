/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n  make sure TREE role is in place
 * 01/18/2026 mir0n  added exposedHeaders to CORS 
 * 02/12/2026 mir0n  let "/esq-kinds" pass thru without validation
 */
package pro.mir0n.esquire.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import pro.mir0n.esquire.common.EsqConstants;
import reactor.core.publisher.Mono;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

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
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity, CorsProperties props) {
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
                .authorizeExchange(exchanges -> exchanges
                    .pathMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                    //XXX: hasRole already implies the user must be authenticated.
                    // for some reason it does not work well
                    // we keep Double-checks authentication and TREE role for esq* paths for a while
                    .pathMatchers("/esq-kinds").permitAll() // Protect your endpoint
                    .pathMatchers("/esq*").authenticated() // Protect your endpoint
                    .pathMatchers("/esq*").hasRole("TREE")
                    .anyExchange().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
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

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/10/2026 mir0n  created: generalized from per-service implementations; stateless JWT filter chain
 * 04/02/2026 mir0n  k8s issues: addded corsAllowAll()
 * 08/26/2026 mir0n  an authentication entry point answers a ProblemDetail JSON instead of an empty 401; the
 *                   filter is JwtClaimsExtractionFilter, and /esq-kinds joins the permitAll list
 * 08/27/2026 mir0n  v1.2.13 -- unauthenticated() writes through ProblemDetailWriter; the class keeps no
 *                   ObjectMapper of its own
 */

package pro.mir0n.esquire.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import pro.mir0n.esquire.backend.error.ProblemDetailMill;
import pro.mir0n.esquire.backend.error.ProblemDetailWriter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtClaimsExtractionFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsAllowAll()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/swagger-ui/**", "/actuator/**", "/v3/api-docs/**",
                                 "/esq-kinds")
                    .permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint(SecurityConfiguration::unauthenticated))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void unauthenticated(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException ex) throws IOException {
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request, HttpStatus.UNAUTHORIZED, "Unauthorized", "No identity was presented", null);
        ProblemDetailWriter.write(response, problem);
    }

    private CorsConfigurationSource corsAllowAll() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

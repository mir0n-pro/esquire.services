/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/21/2024 mir0n  ProblemDetailMill moved to backend common package
 */

package pro.mir0n.esquire.enyMan.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

import pro.mir0n.esquire.backend.error.ProblemDetailMill;
import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
//log.debug("shouldNotFilter: request {} : {}", request.getMethod(), path);
        return path.startsWith("/api/public/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;


//log.debug("request {} : {}", request.getMethod(),request.getServletPath());
//log.debug("authHeader: {}", authHeader);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            Claims claims = jwtService.extractAllClaims(jwt);
            username = claims.getSubject();

            // REJECT if username is missing or some required custom claim is missing
            if (username == null
            || claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID) == null
            || claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH) == null
// todo: add validation of rootpath length > 1 & and has "."
            ) {
                sendErrorResponse(request, response, "Missing required claims ");
                log.debug("Missing required claims {}", claims);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        claims, // Pass claims as principal
                        null,
                        new ArrayList<>()
                );
               authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
               SecurityContextHolder.getContext().setAuthentication(authToken);
           }
        } catch (Exception e) {
            log.error("error on claims",e);
            sendErrorResponse(request, response, "Invalid or expired token");
            return; // STOP the chain here
        }
        filterChain.doFilter(request, response);

    }

    private void sendErrorResponse(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        ProblemDetail problem = ProblemDetailMill.createProblemDetail(
                request,
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed",
                message,
                null
        );
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Use Jackson to write the ProblemDetail object as JSON
        new ObjectMapper().writeValue(response.getWriter(), problem);
    }

}

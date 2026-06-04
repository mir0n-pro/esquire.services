/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/09/2026 mir0n  realm_access.roles existence validated; request rejected (401) if missing/empty
 * 03/10/2026 mir0n  moved to common; shared by all services via scanBasePackages
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug; unused imports removed
 * 03/31/2026 mir0n  JavaTimeModule registered on ObjectMapper: fixes OffsetDateTime serialization
 *                   in ProblemDetail error responses
 * 06/04/2026 mir0n  on a valid token builds EsqRequestContext (crl/req from headers, uid/rootPath from
 *                   claims), sets EsqContextHolder + MDC uid; both cleared in a finally
 */

package pro.mir0n.esquire.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.backend.error.ProblemDetailMill;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.common.EsqConstants;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + JwtAuthenticationFilter.class.getName());

    private final JwtService jwtService;
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
//devLog.debug("shouldNotFilter: request {} : {}", request.getMethod(), path);
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
        String uid = null;
        String rootPath = null;


//devLog.debug("request {} : {}", request.getMethod(),request.getServletPath());
//devLog.debug("authHeader: {}", authHeader);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            Claims claims = jwtService.extractAllClaims(jwt);
            username = claims.getSubject();

            // REJECT if username is missing or some required custom claim is missing
            Map realmAccess = claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class);
            List roles = realmAccess != null ? (List) realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES) : null;
            if (username == null
            || claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID) == null
            || claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH) == null
// todo: add validation of rootpath length > 1 & and has "."
            || roles == null || roles.isEmpty()
            ) {
                sendErrorResponse(request, response, "Missing required claims ");
                devLog.debug("Missing required claims {}", claims);
                return;
            }

            uid      = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);
            rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);

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
            log.error("JwtAuthenticationFilter: error on claims: {}", e.getMessage());
            devLog.error("JwtAuthenticationFilter: error on claims: {}", e.getMessage(), e);
            sendErrorResponse(request, response, "Invalid or expired token");
            return; // STOP the chain here
        }

        // Establish the unified per-request context for the duration of the request: crl/req from
        // headers, uid/rootPath from the authenticated claims. Bound to this thread; cleared in
        // finally so a pooled thread never leaks one caller's identity into the next request.
        String correlationId = request.getHeader(EsqConstants.ESQ_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = request.getHeader(EsqConstants.X_CORRELATION_ID);
        }
        String requestId = request.getHeader(EsqConstants.X_REQUEST_ID);
        EsqContextHolder.set(new EsqRequestContext(correlationId, requestId, uid, rootPath));
        if (uid != null) {
            MDC.put(EsqConstants.PD_UID, uid);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(EsqConstants.PD_UID);
            EsqContextHolder.clear();
        }

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
        new ObjectMapper().registerModule(new JavaTimeModule()).writeValue(response.getWriter(), problem);
    }

}

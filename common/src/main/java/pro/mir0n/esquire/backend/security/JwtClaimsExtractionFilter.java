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
 * 03/21/2026 mir0n  devLog added; log.debug->devLog.debug; unused imports removed
 * 03/31/2026 mir0n  JavaTimeModule registered on ObjectMapper: fixes OffsetDateTime serialization
 *                   in ProblemDetail error responses
 * 06/04/2026 mir0n  on a valid token builds EsqRequestContext (crl/req from headers, uid/rootPath from
 *                   claims), sets EsqContextHolder + MDC uid; both cleared in a finally
 * 08/27/2026 mir0n  v1.2.13 -- sendErrorResponse() writes through ProblemDetailWriter; the per-call
 *                   ObjectMapper is gone
 * 09/05/2026 mir0n  v1.2.15 -- shouldNotFilter() adds /esq-kinds, the path both security configs
 *                   already permitAll
 * 10/19/2026 mir0n  v2.0.1 -- the realm roles go into the EsqRequestContext; the claim check is
 *                   EsqRequestScope.claimsComplete(), and the bind / unbind of the context and the MDC uid are
 *                   EsqRequestScope.bind() / unbind()
 * 11/17/2026 mir0n  v2.0.2 -- pure OAuth (lab): with no esq_uid in the token, uid / rootPath / roles come from
 *                   the X-Esq-UID / X-Esq-Root-Path / X-Esq-Roles headers the gateway set; claimsComplete()
 *                   checks the values taken
 * 11/23/2026 mir0n  v2.0.3 -- uid / rootPath / roles from the X-Esq-UID / X-Esq-Root-Path / X-Esq-Roles headers
 *                   only; the token's esq_uid / esq_rootpath / realm_access claims are not read; the roles
 *                   header parse is EsqRequestScope.rolesOf()
 */

package pro.mir0n.esquire.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pro.mir0n.esquire.backend.error.ProblemDetailMill;
import pro.mir0n.esquire.backend.error.ProblemDetailWriter;
import pro.mir0n.esquire.backend.service.EsqRequestScope;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.common.EsqConstants;

/**
 * Reads the caller's identity out of the bearer token and puts it where the service layer can use it: the
 * SecurityContext (roles), EsqContextHolder (uid / rootPath / correlationId / requestId) and MDC.
 *
 * <p>It does NOT authenticate. The token was verified at the gate -- signature, expiry and the login id
 * ({@code EsqClaimsValidator} on the gateway's decoder) -- and Esquire verifies once, at the boundary. The token
 * carries no Esquire claims: the gateway looks them up and sends them as the X-Esq-* headers. The claim check
 * below is a SHAPE check: it refuses a request missing something the service needs, which is a different
 * question from whether the token is genuine. Re-verifying here would be the duplicate the boundary rule exists
 * to remove.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtClaimsExtractionFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + JwtClaimsExtractionFilter.class.getName());

    private final JwtService jwtService;
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
//devLog.debug("shouldNotFilter: request {} : {}", request.getMethod(), path);
        return path.startsWith("/api/public/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.equals("/esq-kinds");
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
        List<String> roles = null;


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

            //xxx: the gateway strips every inbound copy of these headers, so a header here was set by the gateway
            uid      = request.getHeader(EsqConstants.X_ESQ_UID);
            rootPath = request.getHeader(EsqConstants.X_ESQ_ROOT_PATH);
            roles    = EsqRequestScope.rolesOf(request.getHeader(EsqConstants.X_ESQ_ROLES));
// todo: add validation of rootpath length > 1 & and has "."
            if (username == null
            || !EsqRequestScope.claimsComplete(uid, rootPath, roles)
            ) {
                sendErrorResponse(request, response, "Missing required claims ");
                // WHICH claim is absent, not the values -- this line reaches the log store like any other.
                devLog.debug("Missing required claims: username={}, {}={}, {}={}, {}={}",
                        username != null,
                        EsqConstants.X_ESQ_UID, uid != null,
                        EsqConstants.X_ESQ_ROOT_PATH, rootPath != null,
                        EsqConstants.X_ESQ_ROLES, !roles.isEmpty());
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
            log.error("JwtClaimsExtractionFilter: error on claims: {}", e.getMessage());
            devLog.error("JwtClaimsExtractionFilter: error on claims: {}", e.getMessage(), e);
            sendErrorResponse(request, response, "Invalid or expired token");
            return; // STOP the chain here
        }

        // Establish the unified per-request context for the duration of the request: crl/req from
        // headers, uid/rootPath/roles from the authenticated claims. Bound to this thread; cleared in
        // finally so a pooled thread never leaks one caller's identity into the next request.
        String correlationId = request.getHeader(EsqConstants.ESQ_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = request.getHeader(EsqConstants.X_CORRELATION_ID);
        }
        String requestId = request.getHeader(EsqConstants.X_REQUEST_ID);
        EsqRequestScope.bind(new EsqRequestContext(correlationId, requestId, uid, rootPath, roles));
        try {
            filterChain.doFilter(request, response);
        } finally {
            EsqRequestScope.unbind();
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
        ProblemDetailWriter.write(response, problem);
    }

}

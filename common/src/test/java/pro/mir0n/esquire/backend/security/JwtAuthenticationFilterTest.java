package pro.mir0n.esquire.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.common.EsqConstants;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    // Build a REAL jjwt Claims (same builder JwtService uses) -- avoids mocking the Claims
    // interface, whose default / generic getters trip Mockito's when()/doReturn().
    private Claims claims(boolean withEntityId) {
        var b = Jwts.claims()
                .subject("alice")
                .add(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "1.2.")
                .add(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                        Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, List.of("admin")));
        if (withEntityId) {
            b.add(EsqConstants.JWT_CLAIM_ENTITY_ID, "5");
        }
        return b.build();
    }

    @Test
    void validToken_capturesContextDuringChain_setsPrincipal_clearsAfter() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(request.getHeader(EsqConstants.ESQ_CORRELATION_ID)).thenReturn("corr-1");
        when(request.getHeader(EsqConstants.X_REQUEST_ID)).thenReturn("req-1");
        when(jwtService.extractAllClaims("tok")).thenReturn(claims(true));

        // Capture what downstream sees on the request thread while the chain runs.
        EsqRequestContext[] seen = { null };
        String[] seenMdcUid = { null };
        FilterChain chain = (req, res) -> {
            seen[0] = EsqContextHolder.get();
            seenMdcUid[0] = MDC.get(EsqConstants.PD_UID);
        };

        filter.doFilterInternal(request, response, chain);

        // The unified context was visible to downstream during the chain.
        assertThat(seen[0]).isNotNull();
        assertThat(seen[0].uid()).isEqualTo("5");
        assertThat(seen[0].rootPath()).isEqualTo("1.2.");
        assertThat(seen[0].correlationId()).isEqualTo("corr-1");
        assertThat(seen[0].requestId()).isEqualTo("req-1");
        assertThat(seenMdcUid[0]).isEqualTo("5");

        // The authenticated principal is the Claims object.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(Claims.class);

        // Leak guard: holder + MDC uid cleared in the finally.
        assertThat(EsqContextHolder.get()).isNull();
        assertThat(MDC.get(EsqConstants.PD_UID)).isNull();
    }

    @Test
    void noAuthHeader_continuesChain_noContext_noAuthentication() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean[] chainCalled = { false };
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilterInternal(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
        assertThat(EsqContextHolder.get()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingEntityIdClaim_returns401_chainNotCalled_noContext() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(request.getRequestURI()).thenReturn("/esq-cmd");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        when(jwtService.extractAllClaims("tok")).thenReturn(claims(false));   // no esq_uid claim

        boolean[] chainCalled = { false };
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilterInternal(request, response, chain);

        assertThat(chainCalled[0]).isFalse();
        assertThat(EsqContextHolder.get()).isNull();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}

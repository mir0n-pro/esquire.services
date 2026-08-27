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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtClaimsExtractionFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtClaimsExtractionFilter filter = new JwtClaimsExtractionFilter(jwtService);

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    // Build a REAL jjwt Claims (same builder JwtService uses) -- avoids mocking the Claims
    // interface, whose default / generic getters trip Mockito's when()/doReturn().
    private Claims claims(boolean withEntityId) {
        return claims(withEntityId, true, List.of("admin"));
    }

    // Build claims controlling each REQUIRED custom claim independently -- esq_uid, esq_rootpath, and the
    // realm-access roles (a null roles list = realm_access absent). Lets each rejection branch be exercised.
    private Claims claims(boolean withEntityId, boolean withRootPath, List<String> roles) {
        var b = Jwts.claims().subject("alice");
        if (withEntityId) {
            b.add(EsqConstants.JWT_CLAIM_ENTITY_ID, "5");
        }
        if (withRootPath) {
            b.add(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, "1.2.");
        }
        if (roles != null) {
            b.add(EsqConstants.JWT_CLAIM_REALM_ACCESS,
                    Map.of(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES, roles));
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
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capture(body));

        when(jwtService.extractAllClaims("tok")).thenReturn(claims(false));   // no esq_uid claim

        boolean[] chainCalled = { false };
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilterInternal(request, response, chain);

        assertThat(chainCalled[0]).isFalse();
        assertThat(EsqContextHolder.get()).isNull();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertProblemJson(body);
    }

    // The required-claims gate also rejects a token missing esq_rootpath or without realm-access roles -- the
    // custom, security-relevant part of auth. Drive each branch and assert 401 + the chain is not entered.
    private void assert401For(Claims badClaims) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(request.getRequestURI()).thenReturn("/esq-cmd");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capture(body));
        when(jwtService.extractAllClaims("tok")).thenReturn(badClaims);

        boolean[] chainCalled = { false };
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilterInternal(request, response, chain);

        assertThat(chainCalled[0]).isFalse();
        assertThat(EsqContextHolder.get()).isNull();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertProblemJson(body);
    }

    @Test
    void missingRootPathClaim_returns401_chainNotCalled() throws Exception {
        assert401For(claims(true, false, List.of("admin")));   // esq_uid present, esq_rootpath absent
    }

    @Test
    void nullRoles_returns401_chainNotCalled() throws Exception {
        assert401For(claims(true, true, null));                // realm_access absent -> roles null
    }

    @Test
    void emptyRoles_returns401_chainNotCalled() throws Exception {
        assert401For(claims(true, true, List.of()));           // realm_access present but roles empty
    }

    private static ServletOutputStream capture(ByteArrayOutputStream sink) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) {
                sink.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };
    }

    private static void assertProblemJson(ByteArrayOutputStream body) throws Exception {
        JsonNode node = new ObjectMapper().readTree(body.toByteArray());
        assertThat(node.path("status").asInt()).isEqualTo(401);
        assertThat(node.path("properties").path(EsqConstants.PD_TIMESTAMP).asText()).isNotEmpty();
    }
}

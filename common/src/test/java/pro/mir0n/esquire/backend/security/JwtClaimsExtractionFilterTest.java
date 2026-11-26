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

    // A REAL jjwt Claims (same builder JwtService uses) -- avoids mocking the Claims interface, whose default /
    // generic getters trip Mockito's when()/doReturn().
    private static Claims claims() {
        return Jwts.claims().subject("alice").add("preferred_username", "alice").build();
    }

    private static HttpServletRequest request(String uid, String rootPath, String roles) {
        HttpServletRequest ret = mock(HttpServletRequest.class);
        when(ret.getHeader("Authorization")).thenReturn("Bearer tok");
        when(ret.getHeader(EsqConstants.X_ESQ_UID)).thenReturn(uid);
        when(ret.getHeader(EsqConstants.X_ESQ_ROOT_PATH)).thenReturn(rootPath);
        when(ret.getHeader(EsqConstants.X_ESQ_ROLES)).thenReturn(roles);
        when(ret.getRequestURI()).thenReturn("/esq-cmd");
        return ret;
    }

    @Test
    void validToken_capturesContextDuringChain_setsPrincipal_clearsAfter() throws Exception {
        HttpServletRequest request = request("5", "1.2.", "admin, teller");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(EsqConstants.ESQ_CORRELATION_ID)).thenReturn("corr-1");
        when(request.getHeader(EsqConstants.X_REQUEST_ID)).thenReturn("req-1");
        when(jwtService.extractAllClaims("tok")).thenReturn(claims());

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
        assertThat(seen[0].roles()).containsExactly("admin", "teller");
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

    private void assert401For(HttpServletRequest request, Claims tokenClaims) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capture(body));
        when(jwtService.extractAllClaims("tok")).thenReturn(tokenClaims);

        boolean[] chainCalled = { false };
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilterInternal(request, response, chain);

        assertThat(chainCalled[0]).isFalse();
        assertThat(EsqContextHolder.get()).isNull();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertProblemJson(body);
    }

    @Test
    void missingUidHeader_returns401_chainNotCalled() throws Exception {
        assert401For(request(null, "1.2.", "admin"), claims());
    }

    @Test
    void missingRootPathHeader_returns401_chainNotCalled() throws Exception {
        assert401For(request("5", null, "admin"), claims());
    }

    @Test
    void missingRolesHeader_returns401_chainNotCalled() throws Exception {
        assert401For(request("5", "1.2.", null), claims());
    }

    @Test
    void blankRolesHeader_returns401_chainNotCalled() throws Exception {
        assert401For(request("5", "1.2.", " "), claims());
    }

    @Test
    void claimsInTheTokenAreNotRead_returns401() throws Exception {
        Claims claimsToken = Jwts.claims().subject("alice")
                .add("esq_uid", "5").add("esq_rootpath", "1.2.")
                .add("realm_access", Map.of("roles", List.of("admin"))).build();
        assert401For(request(null, null, null), claimsToken);
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

/*
 *  Esquire frameworks (tm)
 *  Gateway, tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */

package pro.mir0n.esquire.gateway.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import pro.mir0n.esquire.backend.dto.access.EsqUserClaims;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.RodEvent;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthTest {

    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final EsqUserClaims ADMIN =
            new EsqUserClaims("mainadmin", "5", "1.", List.of("SUPERVIZOR", "TREE"), 1L);

    private static class CountingClient implements IUserClaimsClient {
        private final AtomicInteger calls = new AtomicInteger();
        private final Mono<EsqUserClaims> answer;
        private volatile String askedFor;

        CountingClient(Mono<EsqUserClaims> answer) {
            this.answer = answer;
        }

        @Override
        public Mono<EsqUserClaims> fetch(String loginId) {
            calls.incrementAndGet();
            askedFor = loginId;
            return answer;
        }
    }

    private static Jwt token(String azp, String loginId) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-subject")
                .claim("azp", azp).claim("preferred_username", loginId).build();
    }

    private static List<String> authorities(AbstractAuthenticationToken auth) {
        List<String> ret = new ArrayList<>();
        for (GrantedAuthority a : auth.getAuthorities()) {
            ret.add(a.getAuthority());
        }
        return ret;
    }

    // ---- the cache ----

    @Test
    @DisplayName("cache: a miss asks keySmith once, the next request is a hit and asks nothing")
    void cache_missThenHit() {
        CountingClient client = new CountingClient(Mono.just(ADMIN));
        ClaimsCache cache = new ClaimsCache(client, 60_000L);

        assertThat(cache.claimsOf("mainadmin").block(WAIT)).isEqualTo(ADMIN);
        assertThat(cache.claimsOf("mainadmin").block(WAIT)).isEqualTo(ADMIN);
        assertThat(client.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("cache: the login id goes in lower case -- one user whatever the token's case, keySmith asked in lower case")
    void cache_loginIdLowerCase() {
        CountingClient client = new CountingClient(Mono.just(ADMIN));
        ClaimsCache cache = new ClaimsCache(client, 60_000L);

        cache.claimsOf("esq-hauberk-S@mir0n.pro").block(WAIT);
        cache.claimsOf("esq-hauberk-s@mir0n.pro").block(WAIT);
        assertThat(client.calls.get()).isEqualTo(1);
        assertThat(client.askedFor).isEqualTo("esq-hauberk-s@mir0n.pro");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("cache: evict by uid removes the user, so the next request asks keySmith again")
    void cache_evict() {
        CountingClient client = new CountingClient(Mono.just(ADMIN));
        ClaimsCache cache = new ClaimsCache(client, 60_000L);

        cache.claimsOf("mainadmin").block(WAIT);
        cache.evict("5");
        assertThat(cache.size()).isZero();
        cache.claimsOf("mainadmin").block(WAIT);
        assertThat(client.calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("cache: evict by uid removes the user under every login id it is held by, and no one else")
    void cache_evictByUidScans() {
        EsqUserClaims other = new EsqUserClaims("teller", "9", "1.3.", List.of("TREE"), 1L);
        IUserClaimsClient client = loginId -> Mono.just("teller".equals(loginId) ? other : ADMIN);
        ClaimsCache cache = new ClaimsCache(client, 60_000L);
        cache.claimsOf("mainadmin").block(WAIT);
        cache.claimsOf("main.admin@mir0n.pro").block(WAIT);
        cache.claimsOf("teller").block(WAIT);
        assertThat(cache.size()).isEqualTo(3);

        cache.evict("5");

        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("cache: a failed lookup is not cached -- the next request asks again")
    void cache_failureNotStored() {
        CountingClient client = new CountingClient(Mono.error(new BadCredentialsException("none")));
        ClaimsCache cache = new ClaimsCache(client, 60_000L);

        assertThatThrownBy(() -> cache.claimsOf("nobody").block(WAIT)).isInstanceOf(BadCredentialsException.class);
        assertThat(cache.size()).isZero();
        assertThatThrownBy(() -> cache.claimsOf("nobody").block(WAIT)).isInstanceOf(BadCredentialsException.class);
        assertThat(client.calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("cache: an answer asked for before an eviction is not stored -- keySmith is asked again")
    void cache_evictedWhileAsked() {
        EsqUserClaims moved = new EsqUserClaims("mainadmin", "5", "1.2.", List.of("SUPERVIZOR", "TREE"), 1L);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ClaimsCache> holder = new AtomicReference<>();
        IUserClaimsClient client = loginId -> Mono.fromCallable(() -> {
            EsqUserClaims ret = moved;
            if (calls.incrementAndGet() == 1) {
                holder.get().evict("5");
                ret = ADMIN;
            }
            return ret;
        });
        ClaimsCache cache = new ClaimsCache(client, 60_000L);
        holder.set(cache);

        assertThat(cache.claimsOf("mainadmin").block(WAIT)).isEqualTo(moved);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(cache.claimsOf("mainadmin").block(WAIT)).isEqualTo(moved);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("listener: an access-profile DELETE evicts the uid; any other event is ignored")
    void listener_evicts() {
        CountingClient client = new CountingClient(Mono.just(ADMIN));
        ClaimsCache cache = new ClaimsCache(client, 60_000L);
        EvictionListener listener = new EvictionListener(cache);
        cache.claimsOf("mainadmin").block(WAIT);

        listener.onEvent(new RodEvent(RodEvent.Op.UPDATE, EsqConstants.KIND_ACCESS_PROFILE, "5", null, null,
                0L, null, null, null, null, BusConstants.MSG_TYPE_ENTITY_BROADCASTS, Map.of()));
        assertThat(cache.size()).isEqualTo(1);
        listener.onEvent(new RodEvent(RodEvent.Op.DELETE, EsqConstants.KIND_ACCESS_PROFILE, "5", null, null,
                0L, null, null, null, null, BusConstants.MSG_TYPE_ENTITY_BROADCASTS, Map.of()));
        assertThat(cache.size()).isZero();
    }

    // ---- the converter ----

    @Test
    @DisplayName("converter: the roles come from the claims, and the claims ride on the details")
    void converter_rolesFromClaims() {
        ClaimsCache cache = new ClaimsCache(new CountingClient(Mono.just(ADMIN)), 60_000L);
        AuthenticationConverter converter = new AuthenticationConverter(cache);

        AbstractAuthenticationToken auth = converter.convert(token("esq-angular", "mainadmin")).block(WAIT);

        assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(authorities(auth)).containsExactly("ROLE_SUPERVIZOR", "ROLE_TREE");
        assertThat(auth.getDetails()).isEqualTo(ADMIN);
        assertThat(auth.getName()).isEqualTo("mainadmin");
    }

    @Test
    @DisplayName("converter: keySmith down is a refused login (AuthenticationServiceException), never a 500")
    void converter_keySmithDown() {
        ClaimsCache cache = new ClaimsCache(
                new CountingClient(Mono.error(new IllegalStateException("connection refused"))), 60_000L);
        AuthenticationConverter converter = new AuthenticationConverter(cache);

        assertThatThrownBy(() -> converter.convert(token("esq-angular", "mainadmin")).block(WAIT))
                .isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    @DisplayName("converter: an unknown login id stays a BadCredentialsException")
    void converter_unknownLogin() {
        ClaimsCache cache = new ClaimsCache(
                new CountingClient(Mono.error(new BadCredentialsException("none"))), 60_000L);
        AuthenticationConverter converter = new AuthenticationConverter(cache);

        assertThatThrownBy(() -> converter.convert(token("esq-angular", "nobody")).block(WAIT))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ---- the header filter ----

    private static ServerWebExchange passThrough(MockServerWebExchange exchange,
                                                 Mono<SecurityContextImpl> context) {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        WebFilterChain chain = next -> {
            seen.set(next);
            return Mono.empty();
        };
        Mono<Void> run = new HeaderFilter().filter(exchange, chain);
        if (context != null) {
            run = run.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(context.map(c -> c)));
        }
        run.block(WAIT);
        return seen.get();
    }

    private static MockServerWebExchange forgedRequest() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/esq-tree")
                .header(EsqConstants.X_ESQ_UID, "1")
                .header(EsqConstants.X_ESQ_ROOT_PATH, "1.")
                .header(EsqConstants.X_ESQ_ROLES, "SYSADMIN"));
    }

    @Test
    @DisplayName("header filter: forged claim headers from an anonymous caller are stripped")
    void headers_strippedForAnonymous() {
        HttpHeaders out = passThrough(forgedRequest(), null).getRequest().getHeaders();

        assertThat(out.containsKey(EsqConstants.X_ESQ_UID)).isFalse();
        assertThat(out.containsKey(EsqConstants.X_ESQ_ROOT_PATH)).isFalse();
        assertThat(out.containsKey(EsqConstants.X_ESQ_ROLES)).isFalse();
    }

    @Test
    @DisplayName("header filter: a caller with no claims on its authentication gets its forged headers stripped and none set")
    void headers_strippedWithoutClaims() {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(token("esq-angular", "mainadmin"));
        HttpHeaders out = passThrough(forgedRequest(), Mono.just(new SecurityContextImpl(auth)))
                .getRequest().getHeaders();

        assertThat(out.containsKey(EsqConstants.X_ESQ_UID)).isFalse();
    }

    @Test
    @DisplayName("header filter: a signed-in caller gets the gateway's own claims, the forged ones replaced")
    void headers_setFromClaims() {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(token("esq-angular", "mainadmin"));
        auth.setDetails(ADMIN);
        HttpHeaders out = passThrough(forgedRequest(), Mono.just(new SecurityContextImpl(auth)))
                .getRequest().getHeaders();

        assertThat(out.get(EsqConstants.X_ESQ_UID)).containsExactly("5");
        assertThat(out.get(EsqConstants.X_ESQ_ROOT_PATH)).containsExactly("1.");
        assertThat(out.get(EsqConstants.X_ESQ_ROLES)).containsExactly("SUPERVIZOR,TREE");
    }
}

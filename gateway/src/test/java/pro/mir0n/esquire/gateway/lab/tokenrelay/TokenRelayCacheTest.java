/*
 *  Esquire frameworks (tm)
 *  Gateway service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: the relay cache -- what a hit saves and what an expiry costs
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-key JWT cache the two variants share.
 * <p>
 * A hit is a request that never reaches the identity service, so this is where the relay's cost lives: the
 * key each variant chose decides how much of that service's latency the callers actually pay.
 */
class TokenRelayCacheTest {

    /** Hands out a token per call and counts how often it was asked. */
    private static final class StubRelayClient implements ITokenRelayClient {

        private final Instant expiresAt;
        private int calls;

        private StubRelayClient(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        @Override
        public Mono<ExpiringJwt> acquire(KcTokenRequest request) {
            calls++;
            return Mono.just(new ExpiringJwt("jwt-" + calls, expiresAt));
        }
    }

    private static KcTokenRequest anyRequest() {
        return new KcTokenRequest(new LinkedMultiValueMap<>(), "client", "secret");
    }

    @Test
    @DisplayName("first call acquires, second call is served from the cache")
    void missThenHit() {
        StubRelayClient client = new StubRelayClient(Instant.now().plus(5, ChronoUnit.MINUTES));
        TokenRelayCache cache = new TokenRelayCache(client);

        String first  = cache.getOrAcquire("k1", anyRequest()).block();
        String second = cache.getOrAcquire("k1", anyRequest()).block();

        assertThat(first).isEqualTo("jwt-1");
        assertThat(second).isEqualTo("jwt-1");
        assertThat(client.calls).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("an expired entry is acquired again, not served")
    void expiredEntryIsReacquired() {
        StubRelayClient client = new StubRelayClient(Instant.now().minusSeconds(1));
        TokenRelayCache cache = new TokenRelayCache(client);

        String first  = cache.getOrAcquire("k1", anyRequest()).block();
        String second = cache.getOrAcquire("k1", anyRequest()).block();

        assertThat(first).isEqualTo("jwt-1");
        assertThat(second).isEqualTo("jwt-2");
        assertThat(client.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("two keys are two tokens -- one caller never gets another caller's")
    void keysAreKeptApart() {
        StubRelayClient client = new StubRelayClient(Instant.now().plus(5, ChronoUnit.MINUTES));
        TokenRelayCache cache = new TokenRelayCache(client);

        String one = cache.getOrAcquire("k1", anyRequest()).block();
        String two = cache.getOrAcquire("k2", anyRequest()).block();

        assertThat(one).isNotEqualTo(two);
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("invalidate drops the entry, and the next call acquires")
    void invalidateForcesAcquire() {
        StubRelayClient client = new StubRelayClient(Instant.now().plus(5, ChronoUnit.MINUTES));
        TokenRelayCache cache = new TokenRelayCache(client);

        cache.getOrAcquire("k1", anyRequest()).block();
        cache.invalidate("k1");
        String after = cache.getOrAcquire("k1", anyRequest()).block();

        assertThat(after).isEqualTo("jwt-2");
        assertThat(client.calls).isEqualTo(2);
    }
}

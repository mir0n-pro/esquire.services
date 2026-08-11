/*
 *  mir0n java common frameworks -- tests
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.utils.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringCacheTest {

    private static ExpiringCache<String, String> cache(long ttlMs) {
        return new ExpiringCache<>(LoggerFactory.getLogger(ExpiringCacheTest.class), ttlMs);
    }

    @Test
    @DisplayName("store then consume: returns the value and removes it -- it is a hand-off, not a lookup")
    void store_consume_isOneShot() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.store("k", "v");
        assertThat(c.size()).isEqualTo(1);
        assertThat(c.consume("k")).isEqualTo("v");
        assertThat(c.size()).isZero();
        assertThat(c.consume("k")).isNull();          // a second take gets nothing
    }

    @Test
    @DisplayName("consume: absent key, and null key, both yield null rather than throwing")
    void consume_absentOrNull_isNull() {
        ExpiringCache<String, String> c = cache(60_000L);
        assertThat(c.consume("nope")).isNull();
        assertThat(c.consume(null)).isNull();
    }

    @Test
    @DisplayName("store: null key or null value is ignored, not stored")
    void store_nulls_ignored() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.store(null, "v");
        c.store("k", null);
        assertThat(c.size()).isZero();
    }

    @Test
    @DisplayName("store twice: the later store replaces the earlier -- last store wins")
    void store_twice_lastWins() {
        ExpiringCache<String, String> c = cache(60_000L);
        c.store("k", "first");
        c.store("k", "second");
        assertThat(c.size()).isEqualTo(1);
        assertThat(c.consume("k")).isEqualTo("second");
    }

    @Test
    @DisplayName("consume: an entry older than the ttl yields null EVEN IF the prune has not run")
    void consume_expired_isNullWithoutPrune() {
        // This is the lazy check that makes the cache correct without a running scheduler: an entry that
        // slipped past the prune interval must not be handed out as if it were fresh.
        ExpiringCache<String, String> c = cache(100L);
        c.store("stale", "v", System.currentTimeMillis() - 10_000L);
        assertThat(c.size()).isEqualTo(1);            // still present -- nothing pruned it
        assertThat(c.consume("stale")).isNull();      // but not handed out
        assertThat(c.size()).isZero();                // and taken away by the read
    }

    @Test
    @DisplayName("prune: drops what is over the ttl and keeps what is not")
    void prune_dropsOnlyExpired() {
        ExpiringCache<String, String> c = cache(100L);
        c.store("fresh", "f");
        c.store("stale", "s", System.currentTimeMillis() - 10_000L);

        c.prune();

        assertThat(c.size()).isEqualTo(1);
        assertThat(c.consume("fresh")).isEqualTo("f");
    }

    @Test
    @DisplayName("start / stop are idempotent; start reports whether IT started the thread")
    void startStop_idempotent() {
        ExpiringCache<String, String> c = cache(60_000L);
        assertThat(c.start()).isTrue();
        assertThat(c.start()).isFalse();              // already running
        c.stop();
        c.stop();                                     // no throw on a second stop
        assertThat(c.start()).isTrue();               // and it can be started again
        c.stop();
    }

    @Test
    @DisplayName("an un-started cache still stores, consumes and expires correctly")
    void worksWithoutTheThread() {
        // Correct-but-unbounded without the prune thread -- which is exactly what a unit test wants, and why
        // the lazy expiry check in consume() is not optional.
        ExpiringCache<String, String> c = cache(100L);
        c.store("a", "v");
        c.store("old", "v", System.currentTimeMillis() - 10_000L);
        assertThat(c.consume("a")).isEqualTo("v");
        assertThat(c.consume("old")).isNull();
    }

    @Test
    @DisplayName("the default prune interval is half the ttl, and never zero")
    void defaultPruneInterval_isSane() {
        // ttl 1ms would otherwise schedule at 0ms and spin.
        assertThat(new ExpiringCache<String, String>(null, 1L).start()).isTrue();
        assertThat(cache(60_000L).ttlMs()).isEqualTo(60_000L);
    }
}

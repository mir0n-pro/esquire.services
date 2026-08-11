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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringCacheStoreIfGreaterTest {

    /** A parked value that knows its own order -- the same shape kcMaster parks. */
    private record Seq(String value, long n) implements Comparable<Seq> {
        @Override
        public int compareTo(Seq other) {
            return Long.compare(n, other.n());
        }
    }

    private static ExpiringCache<String, Seq> cache(long ttlMs) {
        return new ExpiringCache<>(LoggerFactory.getLogger(ExpiringCacheStoreIfGreaterTest.class), ttlMs);
    }

    @Test
    @DisplayName("first store wins the empty slot")
    void firstStore_lands() {
        ExpiringCache<String, Seq> c = cache(60_000L);
        assertThat(c.storeIfGreater("k", new Seq("a", 1))).isTrue();
        assertThat(c.consume("k").value()).isEqualTo("a");
    }

    @Test
    @DisplayName("a GREATER value replaces the parked one")
    void greater_replaces() {
        ExpiringCache<String, Seq> c = cache(60_000L);
        c.storeIfGreater("k", new Seq("old", 1));
        assertThat(c.storeIfGreater("k", new Seq("new", 2))).isTrue();
        assertThat(c.consume("k").value()).isEqualTo("new");
    }

    @Test
    @DisplayName("THE BUG THIS FIXES: a LESSER value arriving later does NOT replace the parked one")
    void lesser_isRefused() {
        // With a plain store() the late arrival wins and the stale value is handed out -- that is precisely
        // the race-8c path-buffer defect.
        ExpiringCache<String, Seq> c = cache(60_000L);
        c.storeIfGreater("k", new Seq("new", 5));
        assertThat(c.storeIfGreater("k", new Seq("old", 2))).isFalse();
        assertThat(c.consume("k").value()).isEqualTo("new");
    }

    @Test
    @DisplayName("an EQUAL value does not replace -- a redelivery of the same change costs nothing")
    void equal_isRefused() {
        ExpiringCache<String, Seq> c = cache(60_000L);
        c.storeIfGreater("k", new Seq("first", 3));
        assertThat(c.storeIfGreater("k", new Seq("duplicate", 3))).isFalse();
        assertThat(c.consume("k").value()).isEqualTo("first");
    }

    @Test
    @DisplayName("an EXPIRED parked value is treated as absent, whatever the order says")
    void expired_isReplacedByAnything() {
        // A stale entry is not a fact about anything, so even a lesser arrival must take the slot --
        // otherwise a dead high value would block the park for good.
        ExpiringCache<String, Seq> c = cache(100L);
        c.store("k", new Seq("stale-but-high", 99), System.currentTimeMillis() - 10_000L);
        assertThat(c.storeIfGreater("k", new Seq("fresh-but-low", 1))).isTrue();
        assertThat(c.consume("k").value()).isEqualTo("fresh-but-low");
    }

    @Test
    @DisplayName("null key or value stores nothing and reports false")
    void nulls_areIgnored() {
        ExpiringCache<String, Seq> c = cache(60_000L);
        assertThat(c.storeIfGreater(null, new Seq("a", 1))).isFalse();
        assertThat(c.storeIfGreater("k", null)).isFalse();
        assertThat(c.size()).isZero();
    }

    @Test
    @DisplayName("CONCURRENT: many threads racing on ONE key leave the GREATEST parked, every run")
    void concurrent_greatestSurvives() throws Exception {
        // This is the case a read-compare-write cannot pass reliably: two threads read the same parked value,
        // both decide they are greater than it, and the slower write lands last -- leaving the LESSER value.
        // The merge() in storeIfGreater settles it inside the map, so there is no window to lose.
        final int threads = 16;
        final int rounds = 200;
        for (int round = 0; round < rounds; round++) {
            ExpiringCache<String, Seq> c = cache(60_000L);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger accepted = new AtomicInteger();
            for (int i = 1; i <= threads; i++) {
                final long n = i;
                pool.submit(() -> {
                    try {
                        go.await();
                        if (c.storeIfGreater("k", new Seq("v" + n, n))) {
                            accepted.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            Seq survivor = c.consume("k");
            assertThat(survivor).as("round " + round + ": something must be parked").isNotNull();
            assertThat(survivor.n()).as("round " + round + ": the greatest must survive").isEqualTo(threads);
            assertThat(accepted.get()).as("round " + round + ": at least the winner was accepted").isPositive();
        }
    }

    @Test
    @DisplayName("CONCURRENT: distinct keys do not interfere")
    void concurrent_perKeyIndependent() throws Exception {
        ExpiringCache<String, Seq> c = cache(60_000L);
        List<String> keys = List.of("a", "b", "c", "d");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        for (String k : keys) {
            for (int i = 1; i <= 10; i++) {
                final long n = i;
                pool.submit(() -> {
                    try {
                        go.await();
                        c.storeIfGreater(k, new Seq(k + n, n));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (String k : keys) {
            assertThat(c.consume(k).n()).as("key " + k).isEqualTo(10L);
        }
    }
}

package pro.mir0n.esquire.bizTree.taijitu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Side-by-side reproduction of the bizTree cache-load race (Phase 8a):
 *
 *   (1) LEGACY behaviour -- events applied on arrival, no buffering -- LOSES an
 *       UPDATE that arrives while the cache is loading.
 *   (2) YANG behaviour   -- events buffered until the load completes -- KEEPS it.
 *
 * The two tests share the SAME cache model, the SAME load, and the SAME update.
 * The only variable is the gating: legacy applies on arrival; yang buffers via
 * the real {@link Monad}. That isolation is the proof -- the buffering is what
 * closes the race, nothing else.
 *
 * Cache model: a map with H2-like semantics --
 *   bulkLoad = INSERT (the loader writing the DB snapshot),
 *   update   = UPDATE that only affects an existing row (computeIfPresent);
 *              an UPDATE for a not-yet-inserted row is a no-op == lost,
 *              exactly as a SQL UPDATE affecting 0 rows.
 */
class CacheLoadRaceComparisonTest {

    static final class FakeCache {
        final Map<String, String> rows = new ConcurrentHashMap<>();
        void   bulkLoad(Map<String, String> snapshot) { rows.putAll(snapshot); }
        void   update(String id, String val)          { rows.computeIfPresent(id, (k, old) -> val); }
        String get(String id)                         { return rows.get(id); }
    }

    @Test
    @DisplayName("(1) LEGACY apply-on-arrival: UPDATE during load is LOST -- race reproduced")
    void legacy_applyOnArrival_losesUpdate() throws Exception {
        FakeCache cache = new FakeCache();
        CountDownLatch snapshotRead  = new CountDownLatch(1);
        CountDownLatch updateApplied = new CountDownLatch(1);

        // Loader: read the DB snapshot (X=old), then -- after the concurrent
        // UPDATE has been applied -- bulk-INSERT the snapshot. This is the
        // window: the INSERT lands AFTER the live UPDATE.
        Thread loader = new Thread(() -> {
            Map<String, String> snapshot = Map.of("X", "old");
            snapshotRead.countDown();
            try { updateApplied.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            cache.bulkLoad(snapshot);            // INSERT X=old  (runs after the UPDATE)
        }, "legacy-loader");
        loader.start();

        // Legacy consumer applies the event the instant it arrives -- mid-load,
        // before X has been inserted. UPDATE on an absent row == no-op == LOST.
        snapshotRead.await();
        cache.update("X", "new");                // UPDATE X -> new  (X not present yet -> lost)
        updateApplied.countDown();

        loader.join(2000);

        // Race reproduced: the UPDATE vanished; cache holds the stale snapshot value.
        assertThat(cache.get("X")).as("legacy loses the concurrent UPDATE").isEqualTo("old");
    }

    @Test
    @DisplayName("(2) YANG buffered: UPDATE during load is KEPT -- race closed")
    void yang_buffered_keepsUpdate() throws Exception {
        FakeCache cache = new FakeCache();

        // Same loader shape: a load window, then the snapshot INSERT.
        ICacheLoad load = () -> {
            Thread.sleep(200);
            cache.bulkLoad(Map.of("X", "old"));  // INSERT X=old
        };
        CountDownLatch applied = new CountDownLatch(1);
        IEventSink sink = (type, id, kind, node) -> { cache.update(id, "new"); applied.countDown(); };

        MonadY monad = new MonadY("yang", 4096, load, sink, null);  // reads not exercised here
        monad.start();
        monad.setQueueEnabled(true);
        monad.submit(new IMonadCommand.Init());

        // Fire the UPDATE during the load window -- it BUFFERS (gate closed while LOADING).
        Thread.sleep(50);
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADING);
        monad.offer("UPDATE", "X", 20, null);

        // After the load completes, the buffered UPDATE applies on the loaded cache.
        assertThat(applied.await(3, TimeUnit.SECONDS)).as("buffered UPDATE eventually applied").isTrue();

        // Race closed: the UPDATE survived because it ran AFTER the snapshot INSERT.
        assertThat(cache.get("X")).as("yang keeps the concurrent UPDATE").isEqualTo("new");
        monad.stop();
    }
}

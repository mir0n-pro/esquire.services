package pro.mir0n.esquire.bizTree.taijitu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the cache-load race is closed: events offered while INIT is still
 * loading must be BUFFERED and applied only AFTER the load completes, in
 * arrival order. On a failed load, buffered events must be dropped (never
 * applied to a bad cache).
 */
class MonadRaceTest {

    /** Records the wall-clock nanos when load() finished. */
    private static final class SlowLoad implements ICacheLoad {
        final long sleepMillis;
        final AtomicLong finishedAtNanos = new AtomicLong(0);
        final AtomicBoolean fail = new AtomicBoolean(false);

        SlowLoad(long sleepMillis) { this.sleepMillis = sleepMillis; }

        @Override public void load() throws Exception {
            Thread.sleep(sleepMillis);
            if (fail.get()) {
                throw new RuntimeException("simulated load failure");
            }
            finishedAtNanos.set(System.nanoTime());
        }
    }

    /** Records the order + timestamp of applied events. */
    private static final class RecordingSink implements IEventSink {
        final List<String> appliedIds      = new CopyOnWriteArrayList<>();
        final List<Long>   appliedAtNanos  = new CopyOnWriteArrayList<>();
        final CountDownLatch latch;

        RecordingSink(int expected) { this.latch = new CountDownLatch(expected); }

        @Override public void apply(String eventType, String entityId, int kind, com.fasterxml.jackson.databind.JsonNode node) {
            appliedIds.add(entityId);
            appliedAtNanos.add(System.nanoTime());
            latch.countDown();
        }
    }

    @Test
    @DisplayName("events offered during INIT are buffered and applied AFTER load, in arrival order")
    void eventsDuringInit_bufferedThenAppliedInOrder() throws Exception {
        SlowLoad      load = new SlowLoad(300);
        RecordingSink sink = new RecordingSink(5);

        MonadY monad = new MonadY("test", 4096, load, sink, null);  // reads not exercised here
        monad.start();
        monad.setQueueEnabled(true);          // events accepted
        monad.submit(new IMonadCommand.Init()); // INIT enqueued; worker begins slow load

        // Fire 5 events while INIT is still loading (well within the 300ms window).
        Thread.sleep(50);
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADING);
        for (int i = 1; i <= 5; i++) {
            boolean accepted = monad.offer("UPDATE", "e" + i, 20, null);
            assertThat(accepted).as("event e%d accepted into queue", i).isTrue();
        }
        // None applied yet -- processing gate is closed during LOADING.
        assertThat(sink.appliedIds).isEmpty();

        // Wait for all 5 to be applied after the load completes.
        boolean done = sink.latch.await(3, TimeUnit.SECONDS);
        assertThat(done).as("all 5 events applied").isTrue();

        // Order preserved.
        assertThat(sink.appliedIds).containsExactly("e1", "e2", "e3", "e4", "e5");
        // Every event applied strictly AFTER the load finished.
        long loadFinished = load.finishedAtNanos.get();
        assertThat(loadFinished).isGreaterThan(0);
        for (long appliedAt : sink.appliedAtNanos) {
            assertThat(appliedAt).as("event applied after load finished").isGreaterThanOrEqualTo(loadFinished);
        }
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADED);

        monad.stop();
    }

    @Test
    @DisplayName("failed INIT drops buffered events -- nothing applied to a bad cache")
    void failedInit_dropsBufferedEvents() throws Exception {
        SlowLoad      load = new SlowLoad(200);
        load.fail.set(true);
        RecordingSink sink = new RecordingSink(1);  // expect zero; latch just for timing

        MonadY monad = new MonadY("test-fail", 4096, load, sink, null);
        monad.start();
        monad.setQueueEnabled(true);
        monad.submit(new IMonadCommand.Init());

        Thread.sleep(50);
        for (int i = 1; i <= 3; i++) {
            monad.offer("UPDATE", "e" + i, 20, null);
        }

        // Give the worker time to finish the (failing) load + reach the events.
        Thread.sleep(400);

        assertThat(monad.status()).isEqualTo(MonadStatus.FAILED);
        assertThat(sink.appliedIds).as("no events applied on failed load").isEmpty();

        monad.stop();
    }

    @Test
    @DisplayName("events offered before queueEnabled are dropped")
    void eventsBeforeQueueEnabled_dropped() throws Exception {
        SlowLoad      load = new SlowLoad(50);
        RecordingSink sink = new RecordingSink(1);

        MonadY monad = new MonadY("test-pre", 4096, load, sink, null);
        monad.start();
        // queueEnabled NOT set -> offers rejected
        boolean accepted = monad.offer("UPDATE", "early", 20, null);
        assertThat(accepted).as("event before queueEnabled is rejected").isFalse();

        monad.stop();
    }
}

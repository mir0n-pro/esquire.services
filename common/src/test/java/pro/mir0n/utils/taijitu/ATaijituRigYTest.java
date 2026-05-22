package pro.mir0n.utils.taijitu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test of the generalized Taijitu: a concrete {@link ATaijituRigY} director driving
 * a concrete {@link AMonadY} monad, in the SYNCHRONOUS bootstrap model. bootstrap() blocks until
 * LOADED (so the test runs it on its own thread), retrying after a failed load -- and each attempt
 * starts from a clean slate (clearMonad). Proves: events arriving during LOAD are BUFFERED and
 * applied only after a successful load (in order); a failed attempt's buffered events are cleared
 * and never applied; the retry loop eventually LOADs (and never hangs).
 */
class ATaijituRigYTest {

    /** A monad whose _processItem runs a (slow / fail-then-succeed) load on CMD/LOAD and records messages. */
    private static final class TestMonad extends AMonadY {
        final long           loadMillis;
        final AtomicInteger  failTimes       = new AtomicInteger(0);   // # of LOAD attempts to fail before succeeding
        final AtomicInteger  loadAttempts    = new AtomicInteger(0);
        final AtomicLong     finishedAtNanos = new AtomicLong(0);
        final List<String>   applied         = new CopyOnWriteArrayList<>();
        final List<Long>     appliedAtNanos  = new CopyOnWriteArrayList<>();
        final CountDownLatch  latch;

        TestMonad(long loadMillis, int expectedMsgs) {
            super("monad", 64);
            this.loadMillis = loadMillis;
            this.latch      = new CountDownLatch(expectedMsgs);
        }

        @Override protected void _processItem(QueueItem item) {
            if (item.eventType() == MonadCmd.CMD) {
                if (MonadCmd.LOAD.equals(item.entityId())) {
                    loadAttempts.incrementAndGet();
                    sleep(loadMillis);
                    if (failTimes.getAndDecrement() > 0) {
                        throw new RuntimeException("simulated load failure");
                    }
                    finishedAtNanos.set(System.nanoTime());
                }
                // CLEAR / CHECKSUM: no-op for the test monad (no real table)
            } else {
                applied.add(item.entityId());
                appliedAtNanos.add(System.nanoTime());
                latch.countDown();
            }
        }

        private static void sleep(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private static ATaijituRigY director(AMonadY monad) {
        return new ATaijituRigY(monad) { };   // ATaijituRigY is fully concrete (no abstract methods)
    }

    private static void fire(ATaijituRigY director, String id) {
        director.onEntityBroadcast("UPDATE", id, 20, null, null, null, null);
    }

    private static void waitForStatus(AMonadY m, MonadStatus s, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (m.status() != s && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(m.status()).as("reached status " + s).isEqualTo(s);
    }

    @Test
    @DisplayName("bootstrap: events during LOAD are buffered and applied AFTER a successful load, in order")
    void bootstrap_buffersEventsDuringLoad_appliesAfter() throws Exception {
        TestMonad    monad    = new TestMonad(300, 5);
        ATaijituRigY director = director(monad);

        Thread boot = new Thread(director::start, "bootstrap-test");   // bootstrap BLOCKS until LOADED
        boot.start();

        waitForStatus(monad, MonadStatus.LOADING, 2000);   // LOAD is running (after the no-op CLEAR)
        for (int i = 1; i <= 5; i++) {
            fire(director, "e" + i);
        }
        Thread.sleep(50);
        assertThat(monad.applied).as("held during load").isEmpty();   // processing off during load

        assertThat(monad.latch.await(3, TimeUnit.SECONDS)).as("all 5 applied").isTrue();
        assertThat(monad.applied).containsExactly("e1", "e2", "e3", "e4", "e5");

        long loadFinished = monad.finishedAtNanos.get();
        assertThat(loadFinished).isGreaterThan(0);
        for (long appliedAt : monad.appliedAtNanos) {
            assertThat(appliedAt).as("applied after load finished").isGreaterThanOrEqualTo(loadFinished);
        }
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADED);
        boot.join(2000);
        director.shutdown();
    }

    @Test
    @DisplayName("failed LOAD is retried until it LOADs -- bootstrap never hangs")
    void failedLoad_retriesUntilLoaded() throws Exception {
        TestMonad    monad    = new TestMonad(30, 0);
        monad.failTimes.set(2);                         // fail twice, succeed on the 3rd attempt
        ATaijituRigY director = director(monad);
        director.retryDelayMs = 20;                     // fast retries

        Thread boot = new Thread(director::start, "bootstrap-test");
        boot.start();
        boot.join(5000);

        assertThat(boot.isAlive()).as("bootstrap returned (no hang)").isFalse();
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADED);
        assertThat(monad.loadAttempts.get()).as("2 failures + 1 success").isEqualTo(3);
        director.shutdown();
    }
}

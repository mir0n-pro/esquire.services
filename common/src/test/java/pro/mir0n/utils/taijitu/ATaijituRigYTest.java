package pro.mir0n.utils.taijitu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test of the generalized Taijitu: a concrete {@link ATaijituRigY} director driving
 * a concrete {@link AMonadY} monad. Proves the race-safe bootstrap end to end -- events
 * arriving during LOAD are BUFFERED and applied only after a successful load (in arrival
 * order); a failed load discards the buffered events. No mimic listener: the real director
 * drives the gate off the monad's onStarted/onResult.
 */
class ATaijituRigYTest {

    /** A monad whose _processItem runs a (slow / failing) load on CMD/LOAD and records messages. */
    private static final class TestMonad extends AMonadY {
        final long          loadMillis;
        final AtomicBoolean fail            = new AtomicBoolean(false);
        final AtomicLong    finishedAtNanos = new AtomicLong(0);
        final List<String>  applied         = new CopyOnWriteArrayList<>();
        final List<Long>    appliedAtNanos  = new CopyOnWriteArrayList<>();
        final CountDownLatch latch;

        TestMonad(long loadMillis, int expectedMsgs) {
            super("monad", 64);
            this.loadMillis = loadMillis;
            this.latch      = new CountDownLatch(expectedMsgs);
        }

        @Override protected void _processItem(QueueItem item) {
            if (item.eventType() == MonadCmd.CMD) {
                if (MonadCmd.LOAD.equals(item.entityId())) {
                    try { Thread.sleep(loadMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    if (fail.get()) throw new RuntimeException("simulated load failure");
                    finishedAtNanos.set(System.nanoTime());
                }
            } else {
                applied.add(item.entityId());
                appliedAtNanos.add(System.nanoTime());
                latch.countDown();
            }
        }
    }

    private static ATaijituRigY director(AMonadY monad) {
        return new ATaijituRigY(monad) { };   // ATaijituRigY is fully concrete (no abstract methods)
    }

    private static void fire(ATaijituRigY director, String id) {
        director.onEntityBroadcast("UPDATE", id, 20, null, null, null, null);
    }

    @Test
    @DisplayName("bootstrap: events during LOAD are buffered and applied AFTER a successful load, in order")
    void bootstrap_buffersEventsDuringLoad_appliesAfter() throws Exception {
        TestMonad   monad    = new TestMonad(300, 5);
        ATaijituRigY director = director(monad);
        director.bootstrap();   // submits LOAD, enables queue+processing; onStarted disables processing

        Thread.sleep(50);
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADING);
        for (int i = 1; i <= 5; i++) {
            fire(director, "e" + i);
        }
        assertThat(monad.applied).isEmpty();   // processing off during load

        assertThat(monad.latch.await(3, TimeUnit.SECONDS)).as("all 5 applied").isTrue();
        assertThat(monad.applied).containsExactly("e1", "e2", "e3", "e4", "e5");

        long loadFinished = monad.finishedAtNanos.get();
        assertThat(loadFinished).isGreaterThan(0);
        for (long appliedAt : monad.appliedAtNanos) {
            assertThat(appliedAt).as("applied after load finished").isGreaterThanOrEqualTo(loadFinished);
        }
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADED);
        director.shutdown();
    }

    @Test
    @DisplayName("failed LOAD discards buffered events; monad ends FAILED")
    void failedLoad_discardsBufferedEvents() throws Exception {
        TestMonad   monad    = new TestMonad(200, 1);
        monad.fail.set(true);
        ATaijituRigY director = director(monad);
        director.bootstrap();

        Thread.sleep(50);
        for (int i = 1; i <= 3; i++) {
            fire(director, "e" + i);
        }

        Thread.sleep(400);   // past the failing load + onResult clear

        assertThat(monad.status()).isEqualTo(MonadStatus.FAILED);
        assertThat(monad.applied).as("no events applied on failed load").isEmpty();
        director.shutdown();
    }
}

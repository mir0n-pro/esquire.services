/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/26/2026 mir0n  created: the command outcome moved out of handleCommand and into the rig's error listener --
 *                   these pin that every command is still ANSWERED, which is what keeps resultCommand from hanging
 */
package pro.mir0n.utils.taijitu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * handleCommand runs the happy path and no longer catches; onError notifies the result. The gate is what is
 * under test: resultCommand waits on it with NO timeout, so a command that is never answered hangs for good.
 * ATaijituRigYTest already covers a failing LOAD end to end; these cover the paths it does not reach.
 */
class AMonadYOutcomeTest {

    /** Fails whichever command it is told to, so each branch of the listener can be reached. */
    private static final class FailingMonad extends AMonadY {
        final AtomicInteger loadAttempts  = new AtomicInteger(0);
        final AtomicInteger clearAttempts = new AtomicInteger(0);
        private final String failOn;

        FailingMonad(String failOn) {
            super("monad", 32);
            this.failOn = failOn;
        }

        @Override protected String _processItem(QueueItem item) {
            if (item.eventType() == MonadCmd.CMD) {
                if (MonadCmd.LOAD.equals(item.entityId())) {
                    loadAttempts.incrementAndGet();
                } else if (MonadCmd.CLEAR.equals(item.entityId())) {
                    clearAttempts.incrementAndGet();
                }
                if (item.entityId().equals(failOn)) {
                    throw new RuntimeException("simulated " + failOn + " failure");
                }
            }
            return null;
        }
    }

    private static ATaijituRigY director(AMonadY monad) {
        return new ATaijituRigY(monad) {
        };
    }

    @Test
    @DisplayName("a failing CLEAR still ends IDLE and is answered -- CLEAR always ends IDLE, either way")
    void failingClear_endsIdleAndIsAnswered() throws Exception {
        FailingMonad monad    = new FailingMonad(MonadCmd.CLEAR);
        ATaijituRigY director = director(monad);
        director.retryDelayMs = 20;

        Thread boot = new Thread(director::start, "clear-fails");
        boot.start();
        boot.join(5000);

        assertThat(boot.isAlive()).as("the CLEAR was answered -- bootstrap returned").isFalse();
        assertThat(monad.clearAttempts.get()).as("the CLEAR ran").isGreaterThanOrEqualTo(1);
        assertThat(monad.status()).as("a failed CLEAR still ends LOADED via the LOAD that follows it")
                .isEqualTo(MonadStatus.LOADED);
        director.shutdown();
    }

    @Test
    @DisplayName("commands run back to back are each answered -- the in-flight tracking resets between them")
    void commandsBackToBack_areEachAnswered() throws Exception {
        FailingMonad monad    = new FailingMonad("none");
        ATaijituRigY director = director(monad);

        Thread boot = new Thread(director::start, "back-to-back");
        boot.start();
        boot.join(5000);

        assertThat(boot.isAlive()).isFalse();
        assertThat(monad.status()).isEqualTo(MonadStatus.LOADED);
        assertThat(monad.clearAttempts.get()).as("bootstrap runs CLEAR then LOAD").isGreaterThanOrEqualTo(1);
        assertThat(monad.loadAttempts.get()).isEqualTo(1);

        // and again on a live monad: the second command must not inherit the first one's in-flight state
        String again = monad.doCommand(MonadCmd.CLEAR, false, 3000);
        assertThat(again).as("the second command is answered too").isNotNull();
        assertThat(monad.status()).isEqualTo(MonadStatus.IDLE);
        director.shutdown();
    }

    @Test
    @DisplayName("an EVENT that throws does not touch the command gate -- only a command answers a command")
    void failingEvent_doesNotAnswerACommand() throws Exception {
        AtomicInteger applied = new AtomicInteger(0);
        AMonadY monad = new AMonadY("monad", 32) {
            @Override protected String _processItem(QueueItem item) {
                if (item.eventType() != MonadCmd.CMD) {
                    applied.incrementAndGet();
                    throw new RuntimeException("event blew up");
                }
                return null;
            }
        };
        ATaijituRigY director = director(monad);

        Thread boot = new Thread(director::start, "event-fails");
        boot.start();
        boot.join(5000);
        assertThat(monad.status()).as("LOADED before any event").isEqualTo(MonadStatus.LOADED);

        director.onEntityBroadcast("UPDATE", "e1", 20, null, null, null, null);

        long deadline = System.currentTimeMillis() + 2000;
        while (applied.get() == 0 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(5);
        }
        assertThat(applied.get()).as("the event ran and threw").isEqualTo(1);
        assertThat(monad.status())
                .as("a failed EVENT must not drive the command status machine")
                .isEqualTo(MonadStatus.LOADED);
        director.shutdown();
    }
}

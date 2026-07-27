package pro.mir0n.utils.taijitu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skeleton test of the DARK Taijitu director ({@link ATaijituRig}) driving two dark monads
 * ({@link AMonad}) whose off-worker CHECKSUM yields a FIXED digest. Proves the night-watch sweep
 * end-to-end without a real cache:
 *   - both legs return the SAME digest (the "DUMMY" stub) -> checksums MATCH -> NO swap, serving
 *     stays the current yang() (even with onMismatch=SWAP, a match must not promote);
 *   - the legs return DIFFERENT digests under SWAP -> mismatch -> the shadow is promoted to serving.
 */
class ATaijituRigTest {

    /** A dark monad with a fixed off-worker CHECKSUM digest; LOAD/CLEAR/message are no-ops (no table). */
    private static final class TestDarkMonad extends AMonad {
        private final String digest;

        TestDarkMonad(String monadId, String digest) {
            super(monadId, 64);
            this.digest = digest;
        }

        @Override
        protected String _processItem(QueueItem item) {
            return null;   // LOAD / CLEAR / message: nothing to do in the test (no real cache)
        }

        @Override
        protected String _processItemCancellable(ICmdResponseListener listener, QueueItem item) {
            listener.onStarted(MonadCmd.CHECKSUM, null);   // mirror the real monad (no cancelable yet)
            return digest;                                 // off-worker digest -> flows back via onResult
        }
    }

    private static ATaijituRig director(IMonad monad, IMonad danom) {
        return new ATaijituRig(monad, danom) {
        };
    }

    @Test
    @DisplayName("matching checksums (both DUMMY) -> no swap, keep serving yang()")
    void matchingChecksums_keepServing() {
        TestDarkMonad monad = new TestDarkMonad("monad", "DUMMY");
        TestDarkMonad danom = new TestDarkMonad("danom", "DUMMY");
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);   // even in SWAP mode, a MATCH must NOT promote

        director.start();                              // serving=monad LOADED; shadow=danom idle
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();                              // both CHECKSUM=DUMMY -> match -> no swap

        assertThat(director.yang().id()).as("match -> serving unchanged").isEqualTo("monad");
        director.shutdown();
    }

    @Test
    @DisplayName("different checksums in SWAP mode -> promote the shadow to serving")
    void mismatchInSwapMode_promotesShadow() {
        TestDarkMonad monad = new TestDarkMonad("monad", "AAA");
        TestDarkMonad danom = new TestDarkMonad("danom", "BBB");
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();                              // AAA != BBB -> mismatch -> SWAP

        assertThat(director.yang().id()).as("mismatch+SWAP -> shadow promoted").isEqualTo("danom");
        director.shutdown();
    }
}
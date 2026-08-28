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
 *   - the legs return DIFFERENT digests under SWAP -> mismatch -> the shadow is promoted to serving;
 *   - a leg produces NO digest -> nothing is comparable, and two legs carrying the same non-digest must
 *     never be read as a match. What follows depends on WHICH non-digest: only TIMEDOUT (the data is
 *     there, the measurement was too slow) promotes the freshly loaded shadow, whatever the configured
 *     mismatch mode; FAILED (the leg's query threw) and INTERRUPTED (the process is going down) leave
 *     the legs where they are, and so does a TIMEDOUT once shutdown has begun.
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
            if (digest == null) {                          // null digest -> throw -> the worker reports FAILED
                throw new IllegalStateException("checksum failed");
            }
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

    @Test
    @DisplayName("bright checksum TIMEDOUT -> promote the loaded shadow")
    void brightTimedOut_promotesShadow() {
        TestDarkMonad monad = new TestDarkMonad("monad", AMonadY.RESULT_TIMEDOUT);
        TestDarkMonad danom = new TestDarkMonad("danom", "BBB");
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();

        assertThat(director.yang().id()).as("bright TIMEDOUT -> shadow promoted").isEqualTo("danom");
        director.shutdown();
    }

    @Test
    @DisplayName("both legs TIMEDOUT -> not a match: promote the loaded shadow")
    void bothTimedOut_promotesShadow() {
        TestDarkMonad monad = new TestDarkMonad("monad", AMonadY.RESULT_TIMEDOUT);
        TestDarkMonad danom = new TestDarkMonad("danom", AMonadY.RESULT_TIMEDOUT);
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();

        assertThat(director.yang().id()).as("two TIMEDOUT legs -> shadow promoted").isEqualTo("danom");
        director.shutdown();
    }

    @Test
    @DisplayName("TIMEDOUT in LOG mode -> still promote: the swap is not gated by onMismatch")
    void timedOutInLogMode_promotesShadow() {
        TestDarkMonad monad = new TestDarkMonad("monad", AMonadY.RESULT_TIMEDOUT);
        TestDarkMonad danom = new TestDarkMonad("danom", "BBB");
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.LOG);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();

        assertThat(director.yang().id()).as("TIMEDOUT under LOG -> shadow promoted").isEqualTo("danom");
        director.shutdown();
    }

    @Test
    @DisplayName("TIMEDOUT while shutting down -> keep serving: no leg moves on the way out")
    void timedOutWhileShuttingDown_keepsServing() {
        TestDarkMonad monad = new TestDarkMonad("monad", AMonadY.RESULT_TIMEDOUT);
        TestDarkMonad danom = new TestDarkMonad("danom", "BBB");
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");
        director.shuttingDown = true;

        director.sweep();

        assertThat(director.yang().id()).as("TIMEDOUT during shutdown -> serving unchanged").isEqualTo("monad");
        director.shutdown();
    }

    @Test
    @DisplayName("bright checksum FAILED -> keep serving: its query threw, no storage to trust")
    void brightChecksumFailed_keepsServing() {
        TestDarkMonad monad = new TestDarkMonad("monad", null);
        TestDarkMonad danom = new TestDarkMonad("danom", "BBB");
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();

        assertThat(director.yang().id()).as("bright FAILED -> serving unchanged").isEqualTo("monad");
        director.shutdown();
    }

    @Test
    @DisplayName("shadow checksum FAILED -> keep serving: never promote a leg whose own query threw")
    void shadowChecksumFailed_keepsServing() {
        TestDarkMonad monad = new TestDarkMonad("monad", "AAA");
        TestDarkMonad danom = new TestDarkMonad("danom", null);
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();

        assertThat(director.yang().id()).as("shadow FAILED -> serving unchanged").isEqualTo("monad");
        director.shutdown();
    }

    @Test
    @DisplayName("both checksums FAILED -> keep serving, and never read as a match")
    void bothChecksumsFailed_keepsServing() {
        TestDarkMonad monad = new TestDarkMonad("monad", null);
        TestDarkMonad danom = new TestDarkMonad("danom", null);
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();

        assertThat(director.yang().id()).as("both FAILED -> serving unchanged").isEqualTo("monad");
        director.shutdown();
    }

    @Test
    @DisplayName("a leg INTERRUPTED -> keep serving: the process is going down")
    void checksumInterrupted_keepsServing() {
        TestDarkMonad monad = new TestDarkMonad("monad", AMonadY.RESULT_INTERRUPTED);
        TestDarkMonad danom = new TestDarkMonad("danom", "BBB");
        ATaijituRig   director = director(monad, danom);
        director.setOnMismatch(MismatchAction.SWAP);

        director.start();
        assertThat(director.yang().id()).isEqualTo("monad");

        director.sweep();

        assertThat(director.yang().id()).as("INTERRUPTED -> serving unchanged").isEqualTo("monad");
        director.shutdown();
    }
}
package pro.mir0n.esquire.backend.o11y;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Unit coverage for the async-boundary primitive (v1.2.11 O2/T3). The overriding contract: the handed-off work
// ALWAYS runs -- tracing must never drop it -- and capture() yields null when there is no span to carry.
class EsqAsyncTraceTest {

    private static final String CORRELATION = "0af7651916cd43dd8448eb211c80319c";
    private static final String TRACEPARENT = "00-" + CORRELATION + "-b7ad6b7169203331-01";

    @Test
    void capture_returnsNullWhenNoCurrentSpan() {
        assertThat(EsqAsyncTrace.capture(CORRELATION)).isNull();
    }

    @Test
    void continueIn_nullTraceparent_stillRunsWorker() {
        boolean[] ran = {false};

        EsqAsyncTrace.continueIn(null, CORRELATION, "move (async)", () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
    }

    @Test
    void continueIn_withParent_runsWorker() {
        boolean[] ran = {false};

        EsqAsyncTrace.continueIn(TRACEPARENT, CORRELATION, "cache apply", () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
    }

    @Test
    void continueIn_invalidCorrelation_runsWorkerPlain() {
        boolean[] ran = {false};

        // a non-W3C correlation id gives no anchor -> the work still runs, just untraced
        EsqAsyncTrace.continueIn(TRACEPARENT, "not-a-trace-id", "move (async)", () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/15/2026 mir0n  created (v1.2.11 T11/I33): LIVE check that around() records the whole Throwable hierarchy on
 *                   the span (incl. Error). The o11y-review "observe() misses Error" claim was FALSE; kept as a
 *                   regression guard.
 */
package pro.mir0n.esquire.backend.o11y;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I33 -- CHECKED, and it is NOT a defect. The o11y review claimed {@code around()}'s {@code Observation.observe(...)}
 * catches only {@code Exception}, so a thrown {@code java.lang.Error} would close the span with no error status. A
 * LIVE test refuted that: {@code observe()} records the WHOLE Throwable hierarchy (an {@code AssertionError} is
 * reported through the observation, which the tracing bridge turns into span-status ERROR). No code change was
 * needed -- these tests stay as the regression guard that error-recording holds through {@code around()}, and as
 * the record that the "misses Error" claim was verified false, not assumed.
 */
class EsqTraceMarkTest {

    /** Captures the error the Observation lifecycle reports -- what the tracing bridge would turn into span status. */
    private static final class ErrorCapture implements ObservationHandler<Observation.Context> {
        Throwable captured;
        int errors;
        @Override public boolean supportsContext(Observation.Context context) { return true; }
        @Override public void onError(Observation.Context context) { captured = context.getError(); errors++; }
    }

    private static ErrorCapture wire() {
        ObservationRegistry reg = ObservationRegistry.create();
        ErrorCapture h = new ErrorCapture();
        reg.observationConfig().observationHandler(h);
        EsqTraceMark.setRegistry(reg);
        return h;
    }

    @Test
    void aroundSupplier_recordsAnError_notOnlyAnException() {
        ErrorCapture h = wire();
        assertThatThrownBy(() ->
                EsqTraceMark.around("t.err", "t", (Supplier<Object>) () -> { throw new AssertionError("boom"); }))
                .isInstanceOf(AssertionError.class);
        assertThat(h.captured).isInstanceOf(AssertionError.class);   // observe() reports this (the I33 "misses Error" claim was false)
    }

    @Test
    void aroundRunnable_recordsAnError_notOnlyAnException() {
        ErrorCapture h = wire();
        assertThatThrownBy(() ->
                EsqTraceMark.around("t.err", "t", (Runnable) () -> { throw new AssertionError("boom"); }))
                .isInstanceOf(AssertionError.class);
        assertThat(h.captured).isInstanceOf(AssertionError.class);   // observe() reports this too
    }

    @Test
    void aroundSupplier_stillRecordsARuntimeException() {
        ErrorCapture h = wire();
        assertThatThrownBy(() ->
                EsqTraceMark.around("t.err", "t", (Supplier<Object>) () -> { throw new IllegalStateException("no"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(h.captured).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aroundSupplier_success_returnsValue_andRecordsNoError() {
        ErrorCapture h = wire();
        String out = EsqTraceMark.around("t.ok", "t", () -> "value");
        assertThat(out).isEqualTo("value");
        assertThat(h.errors).isZero();
    }
}

/*
 *  Esquire frameworks (tm)
 *  common library  --  test support
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * The by-design cover for I32. {@link EsqBizMeters} / {@link EsqTraceMark} / {@link EsqAsyncTrace} hold their
 * registry in a STATIC field -- unavoidable, because they instrument non-bean / final code the container cannot
 * inject. In production that field is set ONCE at startup by the real registrar and never reset, so nothing leaks.
 * In the TEST JVM, though, one JVM runs many tests over the same statics: a registry set by one test would bleed
 * into the next, and preventing that used to depend on every author remembering an {@code @AfterEach} reset.
 *
 * <p>This extension removes the remembering. It resets all three facilities to their defaults BEFORE and AFTER
 * every test, and it is registered MODULE-WIDE via JUnit auto-detection ({@code META-INF/services/
 * org.junit.jupiter.api.extension.Extension} + {@code junit-platform.properties}), so it applies to every test
 * with nothing to add and nothing to forget -- a leaked static registry is unreachable, not merely discouraged.
 * Test-only: no production code changes; the reset primitives are the facilities' own {@code setRegistry}.
 */
public final class EsqO11yRegistryReset implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        reset();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        reset();
    }

    private static void reset() {
        // EsqBizMeters: a throwaway registry first FLUSHES the PENDING hold -- setRegistry(non-null) replays and
        // CLEARS the held gauges -- then null turns the facility off. So neither a live registry nor a gauge that
        // was queued before a registrar arrived can leak into the next test.
        EsqBizMeters.setRegistry(new SimpleMeterRegistry());
        EsqBizMeters.setRegistry(null);
        // EsqTraceMark / EsqAsyncTrace default to NOOP, NOT null -- setRegistry(null) on EsqTraceMark would leave a
        // null registry and NPE its around(); reset to the real default.
        EsqTraceMark.setRegistry(ObservationRegistry.NOOP);
        EsqAsyncTrace.setRegistry(ObservationRegistry.NOOP);
    }
}

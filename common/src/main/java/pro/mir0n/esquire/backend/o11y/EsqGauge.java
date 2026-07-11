/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/11/2026 mir0n  created: the ONE place a Micrometer gauge is built (v1.2.11 O1/T7 phase A). Micrometer holds
 *                   a gauge's state object WEAKLY: when that state object IS the supplier lambda and nothing else
 *                   holds it, GC collects it and the gauge reports NaN -- a dead panel, no error, no stack trace.
 *                   register() always sets strongReference(true), so a caller cannot get it wrong; callers hand
 *                   over a name, an IntSupplier and tags, and never touch Gauge.builder. NoRawGaugeBuilderTest
 *                   fails the build if any other class references Gauge.builder, which is what makes the trap
 *                   UNREACHABLE rather than merely documented.
 */
package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.IntSupplier;

/**
 * The single seam for building a Micrometer gauge in Esquire.
 *
 * <p>Micrometer's {@code Gauge.builder(name, obj, fn)} keeps a WEAK reference to {@code obj}. That is right for a
 * gauge over a long-lived object (a pool, a cache) whose lifecycle the registry must not extend -- but Esquire's
 * gauges read a value through a supplier lambda ({@code feed::size}, {@code this::heldCount}), and that lambda IS
 * the state object. Nothing else holds it, so the next GC collects it and the gauge silently starts reporting
 * {@code NaN}. Holding it strongly is therefore always correct here, and never optional.
 *
 * <p>So the choice is not left to the call site: this class is the ONLY place {@code Gauge.builder} is allowed to
 * appear ({@code NoRawGaugeBuilderTest} enforces it against the whole source tree). A caller states WHAT to
 * measure; HOW to register it is settled here, once.
 */
public final class EsqGauge {

    private EsqGauge() {
    }

    /**
     * Register a gauge that reads its value from {@code value} whenever the registry is scraped.
     *
     * @param registry the meter registry (never null -- observability is on when a gauge is registered)
     * @param name     the meter name, e.g. {@code messaging.feed.depth}
     * @param value    the supplier the gauge reads; held STRONGLY, so a lambda is safe here
     * @param tags     key/value pairs, e.g. {@code "bus-id", busId, "slot", slotId}
     */
    public static void register(MeterRegistry registry, String name, IntSupplier value, String... tags) {
        Gauge.builder(name, value, IntSupplier::getAsInt)
                .strongReference(true)
                .tags(tags)
                .register(registry);
    }
}

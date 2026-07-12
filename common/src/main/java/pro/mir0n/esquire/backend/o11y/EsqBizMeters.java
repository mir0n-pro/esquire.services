/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/11/2026 mir0n  created: the shared esq.biz.* meter facility (v1.2.11 O1/T8 phase A) -- the ONE entry point
 *                   for every business meter, so per-service work stays THIN (a meter name, its tags, and the
 *                   call site at its own domain seam) and the machinery stays generic in common (D6).
 *                   count / time / gauge; gauge() delegates to EsqGauge so an esq.biz.* gauge is
 *                   strongReference'd by construction. STATIC + registrar-backed (the EsqTraceMark shape) --
 *                   the only shape that reaches BOTH Spring beans and the new-ed, never-proxied objects
 *                   (AcctTransactionProcessor*, the taijitu Monad, KeepSqlStore). Registry null while the
 *                   observability umbrella is off, so every call is a null check and nothing else.
 */
package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * The single seam for Esquire's BUSINESS meters -- the {@code esq.biz.*} family.
 *
 * <p>The free tiers (JVM, HTTP, pools) and the bus meters are inherited: a service gets them without writing a
 * line. Business meters cannot work that way, because they measure what a service DOES, not how it runs -- they
 * can only fire at the domain seam where the thing actually happens. What CAN stay generic is the machinery, and
 * this is it: a call site names its meter and its tags, and nothing else. It never touches a Micrometer builder,
 * never holds a registry, never decides how a gauge is referenced or whether a histogram is affordable.
 *
 * <p><b>Why static, when the framework rule is explicit {@code @Bean} + constructor injection.</b> Several of the
 * seams this must reach are not Spring beans at all: {@code AcctTransactionProcessorSingle} / {@code ...Transfer},
 * the taijitu {@code Monad} and {@code KeepSqlStore} are {@code new}-ed and never proxied -- which is exactly why
 * the T2 {@code @EsqTraced} annotation was INERT on them and needed the {@code EsqTraceMark.around} twin.
 * Constructor injection cannot reach them without inventing plumbing to carry a registry into objects the
 * container does not build. So this follows the established twin pattern: a static facility whose registry is
 * set ONCE at startup by an explicit {@code @Bean} in {@link ObservabilityConfig}. Same shape as
 * {@link EsqTraceMark} and {@link EsqAsyncTrace}.
 *
 * <p><b>Off is free.</b> The registry is null until the umbrella registers one, so with observability off every
 * method here is a null check and a return. Nothing is built, nothing is counted, nothing is held.
 */
public final class EsqBizMeters {

    private EsqBizMeters() {
    }

    /** Set once at startup by ObservabilityConfig; null while the observability umbrella is off. */
    private static volatile MeterRegistry registry;

    /**
     * Gauges asked for BEFORE the registry arrived -- see {@link #gauge}.
     *
     * <p>A gauge is registered ONCE, at start-up of whatever owns the value. But a bean's {@code @PostConstruct}
     * can run BEFORE this facility's registrar does (MoveQueueManager.start() is exactly that case), and a
     * gauge() call at that moment would find a null registry, quietly do nothing, and the gauge would never exist
     * -- a dead panel with no error anywhere. Counters and timers never hit this: they fire at request time, long
     * after start-up. So gauges are HELD here and registered when the registry arrives; start-up order stops
     * mattering, and no call site has to know about it.
     */
    private static final java.util.List<PendingGauge> PENDING = new java.util.concurrent.CopyOnWriteArrayList<>();

    private record PendingGauge(String name, IntSupplier value, String[] tags) {
    }

    /** Wire the app's MeterRegistry. Called from ObservabilityConfig, only when observability is enabled. */
    public static void setRegistry(MeterRegistry meterRegistry) {
        registry = meterRegistry;
        if (meterRegistry != null) {
            for (PendingGauge g : PENDING) {
                EsqGauge.register(meterRegistry, g.name(), g.value(), g.tags());
            }
            PENDING.clear();
        }
    }

    /**
     * Count a business event.
     *
     * @param name the meter name, e.g. {@code esq.biz.perm.check.total}
     * @param tags key/value pairs, e.g. {@code "cmd", "UPDATE", "result", "deny"} -- keep the VALUES bounded
     *             (an entity id or a correlation id here would blow the series count up)
     */
    public static void count(String name, String... tags) {
        MeterRegistry reg = registry;
        if (reg != null) {
            reg.counter(name, tags).increment();
        }
    }

    /**
     * Record how long a business step took.
     *
     * @param nanos elapsed time; the caller owns the clock (System.nanoTime around its own seam)
     */
    public static void time(String name, long nanos, String... tags) {
        MeterRegistry reg = registry;
        if (reg != null) {
            reg.timer(name, tags).record(nanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Register a gauge that reads a live value (a queue depth, a cache size) whenever the registry is scraped.
     *
     * <p>Delegates to {@link EsqGauge}, which is the ONLY place a Micrometer gauge is built and always holds the
     * supplier STRONGLY -- without that the supplier lambda is collected and the gauge silently reports NaN.
     * Register ONCE, at start-up of whatever owns the value; not per event.
     */
    public static void gauge(String name, IntSupplier value, String... tags) {
        MeterRegistry reg = registry;
        if (reg != null) {
            EsqGauge.register(reg, name, value, tags);
        } else {
            // The registry is not here YET (a @PostConstruct beat the registrar) -- or the umbrella is off and it
            // never will be. Hold the gauge either way: if the registry arrives, setRegistry() registers it; if it
            // does not, this is a handful of tiny records retained for the life of the process, and nothing else.
            // Registering nothing and saying nothing is the outcome we are removing.
            PENDING.add(new PendingGauge(name, value, tags));
        }
    }
}

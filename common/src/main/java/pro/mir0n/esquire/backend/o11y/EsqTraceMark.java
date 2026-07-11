/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/07/2026 mir0n  created: the programmatic half of the Esquire trace-mark facility (v1.2.11 O2).
 *                   around(name, label, () -> ...) / aroundChecked() wrap a processing step in an
 *                   Observation the tracing handler renders as a span nested in the request trace. The
 *                   twin of the @EsqTraced annotation, for code Spring AOP cannot proxy (non-bean or final
 *                   classes) -- the dataKeep RodEventDbWriter apply, and the pacMan acct transaction /
 *                   transfer processors. Both entry points share the ObservationRegistry handed in by
 *                   TracingConfig; when tracing is off the registry is NOOP, so the action runs with zero
 *                   span overhead.
 * 07/09/2026 mir0n  contextualName(label): the span name no longer carries the instance id
 */

package pro.mir0n.esquire.backend.o11y;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

// The single, uniform trace mark used across all services -- the programmatic twin of @EsqTraced.
public final class EsqTraceMark {

    private EsqTraceMark() {}

    private static volatile ObservationRegistry registry = ObservationRegistry.NOOP;

    // Wire the app's ObservationRegistry (the one carrying the tracing handlers). Called from ObservabilityConfig.
    public static void setRegistry(ObservationRegistry observationRegistry) {
        registry = observationRegistry;
    }

    // Mark a value-returning processing step as a span. name = observation/metric name (low cardinality);
    // label = the span name shown in the trace. WHICH replica acted shows in the span's service badge (the
    // collector rewrites service.name to service.instance.id on the traces pipeline), so the label is plain.
    public static <T> T around(String name, String label, Supplier<T> action) {
        return Observation.createNotStarted(name, registry).contextualName(label).observe(action);
    }

    // Mark a void processing step as a span.
    public static void around(String name, String label, Runnable action) {
        Observation.createNotStarted(name, registry).contextualName(label).observe(action);
    }

    // Mark a processing step that throws a checked exception (e.g. a bus handler's handle()).
    public static <T, E extends Throwable> T aroundChecked(
            String name, String label, Observation.CheckedCallable<T, E> action) throws E {
        return Observation.createNotStarted(name, registry).contextualName(label).observeChecked(action);
    }
}

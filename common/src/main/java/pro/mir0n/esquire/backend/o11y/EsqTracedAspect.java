/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/07/2026 mir0n  created: the aspect that turns @EsqTraced into a span (v1.2.11 O2). Around an annotated
 *                   Spring-managed method it opens an Observation named by the annotation; the shared
 *                   ObservationRegistry's tracing handler renders it as a span nested in the request trace,
 *                   and TracingConfig's ObservationPredicate decides whether that span is populated. Our own
 *                   aspect (not Micrometer's ObservedAspect) so the annotation and its label are Esquire's.
 * 07/09/2026 mir0n  contextualName(label): the span name no longer carries the instance id
 */

package pro.mir0n.esquire.backend.o11y;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

// Wraps every @EsqTraced service method in an Observation. Registered as an explicit @Bean by ObservabilityConfig
// (D3); only loaded when esquire.observability.enabled=true, so with observability off the annotation is inert.
@Aspect
public class EsqTracedAspect {

    private final ObservationRegistry registry;

    public EsqTracedAspect(ObservationRegistry observationRegistry) {
        this.registry = observationRegistry;
    }

    @Around("@annotation(esqTraced)")
    public Object trace(ProceedingJoinPoint pjp, EsqTraced esqTraced) throws Throwable {
        // Typed callable so observeChecked resolves to the value-returning overload (proceed() returns Object).
        Observation.CheckedCallable<Object, Throwable> body = pjp::proceed;
        return Observation.createNotStarted(esqTraced.name(), registry)
                .contextualName(esqTraced.label())   // replica shows in the span's service badge, not the name
                .observeChecked(body);
    }
}

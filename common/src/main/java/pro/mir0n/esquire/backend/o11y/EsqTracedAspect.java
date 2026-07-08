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
 */

package pro.mir0n.esquire.backend.o11y;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

// Wraps every @EsqTraced service method in an Observation. Registered as an explicit @Bean by TracingConfig
// (D3); only loaded when esquire.tracing.enabled=true, so with tracing off the annotation is inert.
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
                .contextualName(esqTraced.label())
                .observeChecked(body);
    }
}

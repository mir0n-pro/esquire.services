/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/10/2026 mir0n  created: generalized from per-service implementations;
 *                   pointcut pro.mir0n.esquire..jpa.*.* covers all service JPA packages
 * 03/20/2026 mir0n  ScopeNotActiveException guard: skip metrics when no active request scope (startup loaders)
 * 07/11/2026 mir0n  v1.2.11 O1/T5-B -- the JPA timing is now also collected when observability is on, not only
 *                   when the caller asked for it with the X-Capture-Metrics header: that repository time is the DB
 *                   band of the 4-layer latency breakdown, so it must be there on EVERY request. @RequiredArgsConstructor
 *                   dropped for an explicit ctor taking (RequestPerformance, ObjectProvider<MeterRegistry>) --
 *                   observability-on is resolved ONCE at construction (registry present) instead of per invocation.
 *                   The request-scope probe (RequestPerformance.isMetricsCaptured) is now made FIRST and ALWAYS, and
 *                   the ScopeNotActiveException it raises off-request keeps driving the skip -- it must NOT be folded
 *                   into 'observabilityOn || isMetricsCaptured()', because || short-circuits the probe away and the
 *                   aspect then calls addJpaTime() on an off-request thread (the taijitu cache loader runs JPA on a
 *                   monad worker) and the request fails with HTTP 500
 * 07/11/2026 mir0n  v1.2.11 O1/T7 phase B -- the exception is no longer the detector. The aspect asks
 *                   RequestContextHolder.getRequestAttributes() != null FIRST, and only then whether anyone wants
 *                   the number; the try / catch (ScopeNotActiveException) is gone, and the @RequestScope bean is
 *                   never touched on a thread that has no request. The || that caused the 500 is now harmless --
 *                   whichever side answers it, the thread is already known to be serving a request -- so there is
 *                   no ordering left in the condition for a later edit to break
 * 07/17/2026 mir0n  the request-thread test is extracted to isRequestThread() and made the FIRST && operand, so
 *                   the @RequestScope bean is read only on a request thread (no exception-as-detector).
 * 08/26/2026 mir0n  observabilityOn requires esquire.observability.metrics.enabled as well as a registry
 */
package pro.mir0n.esquire.backend.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/*
Note:
    The Bean (RequestPerformance): Since it is in @RequestScope, Spring only creates it when a request starts.
    If you never call its methods, it's just a tiny object with two primitive values (boolean and long).
    It won't affect performance.

    The Aspect Logic: the "heavy lifting" (capturing timestamps, calculating durations, updating the bean) only
    happens when someone has ASKED for the number -- never "just in case". Two consumers can ask:
      - the load-test instrument: the request carries ESQ_CAPTURE_METRICS (the 4-layer timing headers), or
      - observability: the umbrella is on, so the esq.srv.inner DB latency band is being charted.
    Neither asking -> a single boolean check per JPA call and nothing else. The observability answer is resolved
    ONCE at construction (the MeterRegistry bean exists only when the umbrella is on), not per call.

    Off-request: plenty of JPA runs with no request at all -- the taijitu cache loader drives it on a monad
    worker. RequestPerformance is @RequestScope, so there is nothing there to attribute the time to, and touching
    it would fail. The aspect therefore asks FIRST whether this thread is serving a request, and only then whether
    anyone wants the number. Asking in that order is what keeps the scoped bean off a non-request thread.
 */

@Aspect
@Component
public class PerformanceAspect {

    private final RequestPerformance performance;

    // True when the observability umbrella is on -- the MeterRegistry bean exists only then. Resolved once.
    private final boolean observabilityOn;

    public PerformanceAspect(RequestPerformance performance, ObjectProvider<MeterRegistry> registryProvider,
                             @Value("${esquire.observability.metrics.enabled:false}") boolean metricsOn) {
        this.performance = performance;
        this.observabilityOn = metricsOn && registryProvider.getIfAvailable() != null;
    }

    @Around("execution(* pro.mir0n.esquire..jpa.*.*(..))")
    public Object trackJpaTime(ProceedingJoinPoint joinPoint) throws Throwable {
        Object ret;
        // isRequestThread() MUST be the FIRST operand: it gates the @RequestScope read on the right of the && so
        // the scoped bean (performance.isMetricsCaptured()) is only ever touched on a thread that HAS a request.
        // Off-request there is nothing to attribute the time to, so there is nothing to do.
        boolean wanted = isRequestThread() && (observabilityOn || performance.isMetricsCaptured());
        if (!wanted) {
            ret = joinPoint.proceed();     // nobody asked -> do no timing work at all
        } else {
            long start = System.currentTimeMillis();
            try {
                ret = joinPoint.proceed();
            } finally {
                performance.addJpaTime(System.currentTimeMillis() - start);
            }
        }
        return ret;
    }

    // Am I serving a request? Ask DIRECTLY -- never find out by touching the @RequestScope bean and catching what
    // it throws off-request. The name carries the contract: this must be tested FIRST, before any read of a
    // request-scoped bean (see trackJpaTime's guard). Reversing that order once turned a clean 403 into a 500.
    private static boolean isRequestThread() {
        return RequestContextHolder.getRequestAttributes() != null;
    }
}

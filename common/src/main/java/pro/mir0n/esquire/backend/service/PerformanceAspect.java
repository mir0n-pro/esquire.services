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
 */
package pro.mir0n.esquire.backend.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;

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
 */

@Aspect
@Component
public class PerformanceAspect {

    private final RequestPerformance performance;

    // True when the observability umbrella is on -- the MeterRegistry bean exists only then. Resolved once.
    private final boolean observabilityOn;

    public PerformanceAspect(RequestPerformance performance, ObjectProvider<MeterRegistry> registryProvider) {
        this.performance = performance;
        this.observabilityOn = registryProvider.getIfAvailable() != null;
    }

    @Around("execution(* pro.mir0n.esquire..jpa.*.*(..))")
    public Object trackJpaTime(ProceedingJoinPoint joinPoint) throws Throwable {
        Object ret;
        boolean wanted;
        try {
            // Probe the request scope FIRST and ALWAYS. isMetricsCaptured() touches the @RequestScope bean, so it
            // THROWS when there is no active request -- and plenty of JPA runs off-request (the taijitu cache
            // loader drives it on a monad worker). That probe is what the catch below relies on.
            // Do NOT fold this into `observabilityOn || performance.isMetricsCaptured()`: `||` short-circuits, so
            // with observability on the probe would never run, and the finally block would then call addJpaTime()
            // off-request and blow up (ScopeNotActiveException -> 500).
            boolean captured = performance.isMetricsCaptured();
            wanted = observabilityOn || captured;
        } catch (ScopeNotActiveException e) {
            // No active request scope (e.g. startup / cache loaders) -- nothing to attribute the time to, so skip.
            wanted = false;
        }
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
}

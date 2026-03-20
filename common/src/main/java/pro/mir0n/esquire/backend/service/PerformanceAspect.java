/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/10/2026 mir0n  created: generalized from per-service implementations;
 *                   pointcut pro.mir0n.esquire..jpa.*.* covers all service JPA packages
 * 03/20/2026 mir0n  ScopeNotActiveException guard: skip metrics when no active request scope (startup loaders)
 */
package pro.mir0n.esquire.backend.service;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;

/*
Note:
    The Bean (RequestPerformance): Since it is in @RequestScope, Spring only creates it when a request starts.
    If you never call its methods, it's just a tiny object with two primitive values (boolean and long).
    It won't affect performance.

    The Aspect Logic: With the check added above, the "heavy lifting"
    (capturing timestamps, calculating durations, updating the bean) only happens
    when ESQ_CAPTURE_METRICS is "true".
    For regular requests, it's just a single if check.
 */

@Aspect
@Component
@RequiredArgsConstructor
public class PerformanceAspect {

    private final RequestPerformance performance;

    @Around("execution(* pro.mir0n.esquire..jpa.*.*(..))")
    public Object trackJpaTime(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean capture;
        try {
            capture = performance.isMetricsCaptured();
        } catch (ScopeNotActiveException e) {
            // No active request scope (e.g. startup loaders) — skip metrics
            return joinPoint.proceed();
        }
        if (!capture) {
            return joinPoint.proceed();
        }
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            performance.addJpaTime(System.currentTimeMillis() - start);
        }
    }
}

/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/24/2026  mir0n cleanup: @Around("execution(* pro.mir0n.esquire.pacMan.jpa.*.*(..))")
 */
package pro.mir0n.esquire.pacMan.service;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
//import pro.mir0n.esquire.pacMan.service.RequestPerformance;

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

    @Around("execution(* pro.mir0n.esquire.pacMan.jpa.*.*(..))")
    public Object trackJpaTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // If the flag is false, just proceed immediately without timing
        if (!performance.isMetricsCaptured()) {
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
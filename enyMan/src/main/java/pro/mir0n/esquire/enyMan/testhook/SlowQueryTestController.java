/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/29/2026 mir0n  created: the R6 slow-query test endpoint (capped + opt-out). Hit DIRECTLY on the service
 *                   (not via the gateway). Gated by esq.test.slow-query-enabled (default false) -- NEVER in prod.
 */
package pro.mir0n.esquire.enyMan.testhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints to observe the R6 cap firing (capped path) and not firing (opt-out path). */
@RestController
@RequestMapping("/test")
@ConditionalOnProperty(name = "esq.test.slow-query-enabled", havingValue = "true")
public class SlowQueryTestController {

    private final SlowQueryTestService svc;

    public SlowQueryTestController(SlowQueryTestService svc) {
        this.svc = svc;
    }

    /** Slow query on the capped path -- with the cap on, expect timedOut=true and elapsedMs ~ the cap. */
    @GetMapping("/slow-query")
    public SlowQueryResult slowQuery(@RequestParam(name = "seconds", defaultValue = "20") int seconds) {
        return svc.runCapped(seconds);
    }

    /** Slow query on the opt-out path -- even with the cap on, expect timedOut=false and elapsedMs ~ seconds. */
    @GetMapping("/slow-query-optout")
    public SlowQueryResult slowQueryOptOut(@RequestParam(name = "seconds", defaultValue = "20") int seconds) {
        return svc.runOptOut(seconds);
    }
}

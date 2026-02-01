/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.keySmith.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import lombok.Getter;
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

@Component
@RequestScope
@Getter
public class RequestPerformance {
    private boolean metricsCaptured = false;
    private long totalJpaTime = 0;

    public void setMetricsCaptured(boolean captureMetrics) {
        this.metricsCaptured = captureMetrics;
    }

    public void addJpaTime(long millis) {
        this.totalJpaTime += millis;
    }

}

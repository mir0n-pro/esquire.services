/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/23/2026 mir0n  created: readiness health indicator gating k8s readiness on the cache being
 *                   loaded + serving. The bootstrap blocks ApplicationReadyEvent AFTER the HTTP server
 *                   is up, so plain /actuator/health is UP before the cache loads -- this contributor
 *                   reports DOWN until IBizTreeDirector.isReady(). Wired into the readiness GROUP only
 *                   (management.endpoint.health.group.readiness), NOT liveness, so a slow load delays
 *                   traffic without crashlooping the pod.
 */
package pro.mir0n.esquire.bizTree.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;

/**
 * Reports UP only once the cache director is serving ({@link IBizTreeDirector#isReady()}); DOWN while
 * the bootstrap is still loading. Registered under the health name {@code cacheReadiness} and added to
 * the readiness probe group so k8s holds traffic until the cache is actually serving (no premature
 * Ready -> CacheNotReady 503 window). Deliberately absent from the liveness group.
 */
@Component
public class CacheReadinessHealthIndicator implements HealthIndicator {

    private final IBizTreeDirector director;

    public CacheReadinessHealthIndicator(IBizTreeDirector director) {
        this.director = director;
    }

    @Override
    public Health health() {
        return director.isReady()
                ? Health.up().withDetail("cache", "serving").build()
                : Health.down().withDetail("cache", "loading").build();
    }
}

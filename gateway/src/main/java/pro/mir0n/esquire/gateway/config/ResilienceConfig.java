/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/28/2026 mir0n  created: gateway -> backend resilience. Builds the Resilience4j
 *                   CircuitBreaker + TimeLimiter (the per-route timeout) from the
 *                   esq.gateway.resilience.circuit-breaker knobs. An open breaker /
 *                   timed-out call propagates to GatewayErrorWebExceptionHandler,
 *                   which renders the 503/504 ProblemDetail (no separate fallback).
 * 07/14/2026 mir0n  the BULKHEAD is now declared and sized here (T10). circuitBreakerConfig()
 *                   adds ignoreExceptions(BulkheadFullException) -- a bulkhead rejection is
 *                   "too many at once", not a backend fault, and must not open the breaker.
 *                   New @Bean bulkheadCustomizer() (Customizer<ReactiveResilience4jBulkheadProvider>)
 *                   replaces the library default of 25 concurrent calls; bulkheadCap() reads
 *                   max-concurrent-calls, and 0 (the default) DERIVES it from the pool it
 *                   backstops: spring.cloud.gateway.httpclient.pool.max-connections x
 *                   queue-per-connection. slidingWindowType is now a knob (TIME_BASED default,
 *                   was hard-coded COUNT_BASED); half-open-calls default 5 -> 20
 */
package pro.mir0n.esquire.gateway.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4jBulkheadProvider;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4jBulkheadConfigurationBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the gateway's downstream resilience (no @Autowired -- an explicit @Bean
 * factory reading the esq.gateway.resilience.* knobs).
 *
 * The CircuitBreaker filter on each route (application.yml) carries an R4j
 * TimeLimiter; that TimeLimiter -- NOT the Netty response-timeout -- is the real
 * per-route deadline. Spring Cloud Gateway defaults it to 1s, which would cancel
 * any legitimate slow call (move / acct / create), so it is configured here:
 * a default of {@code timeout-seconds} for fast routes and {@code slow-timeout-
 * seconds} for the slow-write circuit breakers. A call that exceeds its limit, or
 * a backend failing past the breaker threshold, surfaces as an error that the
 * existing GatewayErrorWebExceptionHandler renders as a 503/504 ProblemDetail.
 *
 * THE BULKHEAD, AND WHY THE BREAKER MUST IGNORE IT (T10).
 * Spring Cloud puts a Resilience4j BULKHEAD in front of every circuit breaker --
 * a semaphore capping CONCURRENT calls per route. Nothing here asked for it and
 * nothing named it, so it ran on the library default: maxConcurrentCalls = 25,
 * maxWaitDuration = 0, i.e. reject the 26th call INSTANTLY.
 *
 * That alone would only shed load. The damage is what the breaker then did with
 * the rejection: a BulkheadFullException is an ordinary exception, so the breaker
 * counted it as a FAILURE OF THE BACKEND. Ten of them inside the window crosses
 * the failure-rate threshold and the breaker OPENS -- on a backend that is not
 * failing, not slow, and in fact was never called at all.
 *
 * Measured (2026-07-13, docker, healthy biztree serving 200s at p99 32ms):
 *   burst of 24 concurrent /esq-enode -> 24 x 200, zero errors
 *   burst of 40 concurrent            -> 13 x 503
 *   burst of 70 concurrent            -> 28 x 503, breaker OPEN, then stuck HALF_OPEN
 * The only variable is concurrency crossing 25. /esq-enode is the low-traffic
 * lookup EVERY load scenario calls at start-up, so it takes the whole burst at
 * once while enyman-cb's 30,000 calls arrive spread out -- which is why this bit
 * the quietest route in the system and left the busiest ones untouched.
 *
 * So: "too many at once" is NOT "the backend is broken". A breaker may open on a
 * FAULT; it must never open because callers arrived together. ignoreExceptions()
 * below is the fix -- the bulkhead can still shed (that one call gets a 503), but
 * it can no longer poison the breaker into refusing everybody else.
 */
@Configuration
public class ResilienceConfig {

    /** Circuit-breaker instance ids that need the longer (slow-write) TimeLimiter. */
    private static final String[] SLOW_CB_IDS = { "enyman-move-cb", "pacman-acct-cb", "enyman-new-cb" };

    @Value("${esq.gateway.resilience.circuit-breaker.timeout-seconds:10}")
    private long timeoutSeconds;
    @Value("${esq.gateway.resilience.circuit-breaker.slow-timeout-seconds:30}")
    private long slowTimeoutSeconds;
    // COUNT_BASED judges the last N CALLS; TIME_BASED judges the last N SECONDS. On a low-traffic
    // route the count window is a trap: /esq-enode's "last 20 calls" can span minutes, so a verdict
    // reached during one burst is still being served long after the cause is gone -- which is exactly
    // how biztree-cb carried an open breaker from one load run into the next. A time window forgets.
    @Value("${esq.gateway.resilience.circuit-breaker.sliding-window-type:TIME_BASED}")
    private String slidingWindowType;
    /** Window SIZE -- calls when COUNT_BASED, seconds when TIME_BASED. */
    @Value("${esq.gateway.resilience.circuit-breaker.sliding-window:30}")
    private int slidingWindow;
    @Value("${esq.gateway.resilience.circuit-breaker.minimum-calls:10}")
    private int minimumCalls;
    @Value("${esq.gateway.resilience.circuit-breaker.failure-rate:50}")
    private float failureRate;
    @Value("${esq.gateway.resilience.circuit-breaker.slow-call-rate:100}")
    private float slowCallRate;
    @Value("${esq.gateway.resilience.circuit-breaker.slow-call-seconds:8}")
    private long slowCallSeconds;
    @Value("${esq.gateway.resilience.circuit-breaker.open-wait-seconds:10}")
    private long openWaitSeconds;
    // The recovery probe window. In HALF_OPEN the breaker permits exactly this many calls and REFUSES
    // the rest -- so with the old value of 5, a route that receives a start-up burst of ~70 had 5 calls
    // probed and ~65 rejected with a 503. Recovery itself was the outage. It must be wide enough to
    // admit a realistic burst, while still being a probe rather than the full flood.
    @Value("${esq.gateway.resilience.circuit-breaker.half-open-calls:20}")
    private int halfOpenCalls;

    // The bulkhead: the cap on CONCURRENT in-flight calls per BREAKER. It is a BACKSTOP, never the
    // primary limiter -- concurrency is really bounded by the connection pool, which QUEUES.
    //
    // 0 = DERIVE it from the pool (the default, and the only sizing that stays correct by itself).
    // A fixed number cannot: it silently becomes the primary limiter the moment demand grows past it,
    // and then it sheds traffic the system could have served. That is not hypothetical -- it is what
    // a hand-picked 100 did (T10):
    //
    //   * The bulkhead is per BREAKER, and a breaker guards SEVERAL ROUTES. enyman-cb fronts
    //     /esq-cmd + /esq-cmd-save + /esq-cmd-del + /esq-cmd-tree, so their concurrency ADDS UP.
    //     Under a 200-VU load that is 64 + 32 + 32 = 128 against a limit of 100. Nobody chose to
    //     couple reads and writes into one shared budget; it fell out of the config.
    //   * Worse, it depended on REPLICA COUNT: at x2 the load split ~64 per pod and nothing shed;
    //     at x1 one pod carried all 128 and shed the overflow as 503. The same load, the same code,
    //     a different answer depending on how many pods happened to be running.
    //   * And it was shedding far too early. Measured: enyman-cb the ONLY bulkhead to saturate
    //     (0 of 100 slots free) while every other sat idle, breaker never opened, 34 x 503 returned.
    //
    // THE DERIVATION. A call waits in the bulkhead for a connection. With C connections and service
    // time S, a call queued at depth N waits N*S/C. Shedding is only CORRECT when that wait would
    // exceed the deadline D -- otherwise we are rejecting work we could have finished:
    //
    //     shed correctly when   N > D * C / S      (10s * 64 / 0.15s ~= 4,266)
    //
    // So any limit below a few thousand sheds servable work. Rather than pick another magic number,
    // size it as QUEUE-PER-CONNECTION: allow queue-per-connection calls to wait behind each connection.
    // At 16 deep and ~150ms of service that is a 2.4s wait -- comfortably inside the 10s deadline --
    // and it SCALES WITH THE POOL automatically (docker's 16-connection pool -> 256; the local-k8s
    // 64-connection pool -> 1024). The TimeLimiter, not an arbitrary count, stays the thing that sheds.
    @Value("${esq.gateway.resilience.bulkhead.max-concurrent-calls:0}")
    private int bulkheadMaxConcurrentCalls;
    @Value("${esq.gateway.resilience.bulkhead.queue-per-connection:16}")
    private int bulkheadQueuePerConnection;
    /** The pool this bulkhead backstops -- the real bound on how many calls can be in flight. */
    @Value("${spring.cloud.gateway.httpclient.pool.max-connections:16}")
    private int poolMaxConnections;
    // 0 = reject the overflow immediately rather than hold the caller. Kept at 0 deliberately: a wait
    // here would stall the reactive event loop, and the caller already has the connection pool's
    // acquire-timeout to wait on.
    @Value("${esq.gateway.resilience.bulkhead.max-wait-ms:0}")
    private long bulkheadMaxWaitMs;

    /**
     * Default config for every circuit breaker, plus the slow-write override.
     * Unlisted breaker ids inherit the default (fast) TimeLimiter.
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> resilienceCustomizer() {
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(circuitBreakerConfig())
                    .timeLimiterConfig(timeLimiterConfig(timeoutSeconds))
                    .build());
            factory.configure(builder -> builder
                    .circuitBreakerConfig(circuitBreakerConfig())
                    .timeLimiterConfig(timeLimiterConfig(slowTimeoutSeconds)),
                    SLOW_CB_IDS);
        };
    }

    /**
     * The bulkhead that sits in front of every breaker. Declared here so its size is a decision with a
     * name and a knob, instead of the Resilience4j default of 25 that nothing in this codebase chose.
     */
    @Bean
    public Customizer<ReactiveResilience4jBulkheadProvider> bulkheadCustomizer() {
        final int cap = bulkheadCap();
        return provider -> provider.configureDefault(id -> new Resilience4jBulkheadConfigurationBuilder()
                .bulkheadConfig(BulkheadConfig.custom()
                        .maxConcurrentCalls(cap)
                        .maxWaitDuration(Duration.ofMillis(bulkheadMaxWaitMs))
                        .build())
                .build());
    }

    /**
     * The bulkhead's size: an explicit override when one is set, otherwise DERIVED from the connection
     * pool it backstops. See the field comment for why a fixed default is the wrong shape.
     */
    private int bulkheadCap() {
        int ret;
        if (bulkheadMaxConcurrentCalls > 0) {
            ret = bulkheadMaxConcurrentCalls;
        } else {
            ret = Math.max(1, poolMaxConnections) * Math.max(1, bulkheadQueuePerConnection);
        }
        return ret;
    }

    private CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.valueOf(slidingWindowType))
                .slidingWindowSize(slidingWindow)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRate)
                .slowCallRateThreshold(slowCallRate)
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallSeconds))
                .waitDurationInOpenState(Duration.ofSeconds(openWaitSeconds))
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // A bulkhead rejection means "too many callers arrived at once", NOT "the backend is
                // broken" -- the backend was never even called. Counting it as a failure is what let a
                // burst on a HEALTHY route open the breaker and turn a spike into a 503 outage. The
                // breaker opens on FAULTS only; the bulkhead still sheds the overflow on its own.
                .ignoreExceptions(BulkheadFullException.class)
                .build();
    }

    private TimeLimiterConfig timeLimiterConfig(long seconds) {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(seconds))
                .cancelRunningFuture(true)
                .build();
    }
}

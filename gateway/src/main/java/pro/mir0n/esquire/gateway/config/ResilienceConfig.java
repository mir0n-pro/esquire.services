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
 */
package pro.mir0n.esquire.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
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
 */
@Configuration
public class ResilienceConfig {

    /** Circuit-breaker instance ids that need the longer (slow-write) TimeLimiter. */
    private static final String[] SLOW_CB_IDS = { "enyman-move-cb", "pacman-acct-cb", "enyman-new-cb" };

    @Value("${esq.gateway.resilience.circuit-breaker.timeout-seconds:10}")
    private long timeoutSeconds;
    @Value("${esq.gateway.resilience.circuit-breaker.slow-timeout-seconds:30}")
    private long slowTimeoutSeconds;
    @Value("${esq.gateway.resilience.circuit-breaker.sliding-window:20}")
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
    @Value("${esq.gateway.resilience.circuit-breaker.half-open-calls:5}")
    private int halfOpenCalls;

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

    private CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindow)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRate)
                .slowCallRateThreshold(slowCallRate)
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallSeconds))
                .waitDurationInOpenState(Duration.ofSeconds(openWaitSeconds))
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    private TimeLimiterConfig timeLimiterConfig(long seconds) {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(seconds))
                .cancelRunningFuture(true)
                .build();
    }
}

/*
 *  Esquire frameworks (tm)
 *  gateWard service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/14/2026 mir0n  created: the one scheduler the cache reads run on, so a blocking JDBC call never lands on
 *                   a Netty event-loop thread; sized from gateward.cache-read.pool-size against the H2 pool
 */

package pro.mir0n.esquire.gateWard;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * The boundary between the gate and the cache.
 *
 * <p>The gate is WebFlux on a small, fixed set of Netty event-loop threads. The cache is
 * {@code jdbc:h2:mem:biztree} behind a Hikari pool, so every read is a BLOCKING JDBC call -- in memory and
 * fast, but blocking, and bounded by that pool. Run one on an event-loop thread and it is not the tree read
 * that suffers: that thread stops serving EVERY route it carries until the call returns. At the ingress that
 * is an availability problem, and it is the whole reason this class exists rather than a direct call.
 *
 * <p>So the reads get their own scheduler, and nothing else runs on it. It is sized to the H2 pool rather
 * than guessed: more threads than connections only queues the wait somewhere less visible, and fewer wastes
 * the pool. The monads that APPLY events already own their threads and do not come here.
 *
 * <p>The name is deliberate: {@code gateward-cache-read} shows up in a thread dump, so a stall is attributed
 * to the cache instead of looking like a sick gateway.
 */
@Slf4j
// named explicitly: the class's own default bean name would be "cacheReadScheduler", which is the name the
// @Bean below takes -- the context refuses to register both.
@Configuration("cacheReadSchedulerConfig")
public class CacheReadScheduler {

    /** Matches the H2 cache pool (biztree.h2.pool.maximum-pool-size) by default -- the reads cannot go wider than it. */
    @Value("${gateward.cache-read.pool-size:${biztree.h2.pool.maximum-pool-size:10}}")
    private int poolSize;

    /** How many reads may WAIT for a thread before the scheduler rejects; a full queue fails fast. */
    @Value("${gateward.cache-read.queue-capacity:1000}")
    private int queueCapacity;

    private Scheduler scheduler;

    @Bean(name = "cacheReadScheduler")
    public Scheduler cacheReadScheduler() {
        this.scheduler = Schedulers.newBoundedElastic(poolSize, queueCapacity, "gateward-cache-read");
        log.info("GATEWARD | cache-read scheduler: threads={} queueCapacity={}", poolSize, queueCapacity);
        return this.scheduler;
    }

    /** Released with the context so a shutdown does not leave the read threads behind. */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.dispose();
            log.info("GATEWARD | cache-read scheduler disposed");
        }
    }
}

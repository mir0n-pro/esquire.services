/*
 *  Esquire frameworks (tm)
 *  bizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/14/2026 mir0n  created: the CACHE half of bizTree as a set of beans -- the one place its packages are
 *                   named. Excludes BizTreeApplication; leaves the web layer (bizTree.controller) and the
 *                   servlet security (backend.security) to the standalone process
 */

package pro.mir0n.esquire.bizTree;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * bizTree as an ACTIVE CACHE, as a set of beans: the director and its monads, the H2 cache and its loader,
 * the JPA read side that fills it, and the entity-bus consumer that keeps it in step.
 * <p>
 * Everything that says WHICH packages make up that cache lives here and nowhere else, so a process that
 * wants the cache inside it imports this one class instead of copying the list.
 * {@code BizTreeApplication} is the bizTree PROCESS: a main(), the startup listener, the bus lifecycle, and
 * the web layer that publishes the cache over HTTP.
 * <p>
 * <b>What is deliberately NOT here: every package that is bound to the SERVLET stack.</b>
 * {@code bizTree.controller} (an MVC controller), {@code backend.security} (a servlet security chain),
 * {@code backend.exception} (a {@code ResponseEntityExceptionHandler}, which exists only in servlet MVC),
 * and {@code backend.service} -- whose beans are a {@code jakarta.servlet} filter, an aspect, and a
 * {@code @RequestScope} bean; request scope does not exist in WebFlux, so resolving it throws on every
 * request. The classes the cache actually uses from that package ({@code EsqContextHolder},
 * {@code EntityFieldUtils}, {@code EsqRequestContext}, {@code RequestContextUtils}) are plain classes,
 * not beans, so they come along with the jar and need no scanning. A composed process reaches the
 * cache by method call and has its own security boundary in front of it, so taking them in would drag
 * {@code spring-boot-starter-web} onto the classpath -- which would force a servlet context and stop a
 * WebFlux edge from starting at all.
 * <p>
 * The application class is excluded from the scan on purpose. Standing alone it is the primary source and
 * is registered directly, so the exclusion changes nothing; inside a composed process it must not be picked
 * up at all.
 */
@Configuration
@ComponentScan(
        basePackages = {
                "pro.mir0n.esquire.bizTree.access",
                "pro.mir0n.esquire.bizTree.cache",
                "pro.mir0n.esquire.bizTree.h2",
                "pro.mir0n.esquire.bizTree.jpa",
                "pro.mir0n.esquire.bizTree.messaging",
                "pro.mir0n.esquire.bizTree.service",
                "pro.mir0n.esquire.bizTree.taijitu",
                "pro.mir0n.esquire.bizTree.health"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = BizTreeApplication.class))
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.bizTree.jpa")
public class BizTreeCacheConfig {
}

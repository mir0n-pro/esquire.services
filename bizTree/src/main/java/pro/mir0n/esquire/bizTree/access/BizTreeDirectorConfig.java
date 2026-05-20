/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: explicit @Bean wiring for the active IBizTreeDirector
 *                   (v1.2.5 Taijitu refactor Step 1). Single declaration point with
 *                   configurable selection via `biztree.director` property
 *                   (legacy | yang | taijitu); choice logged at startup; each impl is
 *                   plain Java, all wiring visible in this one switch.
 */
package pro.mir0n.esquire.bizTree.access;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.mir0n.esquire.bizTree.access.legacy.BizTreeDirectorLegacy;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheLoader;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import pro.mir0n.esquire.bizTree.access.yang.BizTreeDirectorYang;
import pro.mir0n.esquire.bizTree.taijitu.MonadY;

/**
 * The single declaration point of the active {@link IBizTreeDirector}.
 *
 * One property selects the implementation:
 * <pre>
 *   biztree.director = legacy   (default) -- pre-refactor mechanics
 *                    = yang                -- single-monad, race-safe
 *                    = taijitu             -- two-monad + night-watch (Step 3)
 * </pre>
 *
 * Everything is visible here: which impl is live, what each is constructed
 * with, and (logged at startup) which one won. No @Component scanning of
 * implementations, no @Profile, no @Conditional sprinkled across files --
 * the impls are plain Java classes and this switch is the only place that
 * names them. The chosen director's bootstrap() is fired once by
 * {@code BizTreeBootstrapRunner} on ApplicationReadyEvent.
 *
 * See: services/doc/Esquire.BizTree.md "Migration plan".
 */
@Slf4j
@Configuration
public class BizTreeDirectorConfig {

    @Value("${biztree.director:legacy}")
    private String directorKind;

    @Value("${biztree.monad.queue.capacity:4096}")
    private int queueCapacity;

    @Bean
    public IBizTreeDirector bizTreeDirector(IBizTreeService         bizTreeService,
                                            IBizTreeCacheRepository cacheRepository,
                                            BizTreeCacheLoader      cacheLoader) {

        String kind = directorKind == null ? "legacy" : directorKind.trim().toLowerCase();

        IBizTreeDirector ret = switch (kind) {
            case "legacy" -> new BizTreeDirectorLegacy(bizTreeService, cacheLoader, cacheRepository);

            case "yang"   -> new BizTreeDirectorYang(new MonadY(
                    "yang",
                    queueCapacity,
                    cacheLoader::load,                                  // ICacheLoad -> INIT load
                    new MessageHandlerHub(cacheRepository)::dispatch,   // IEventSink (eventHub) -> buffered apply
                    bizTreeService));                                   // read backend

            // case "taijitu" -> new Taijitu(...);   // Step 3

            default -> throw new IllegalStateException(
                    "unknown biztree.director='" + directorKind + "' (expected: legacy | yang | taijitu)");
        };

        log.info("bizTree director = '{}' -> {} (queue.capacity={})",
                kind, ret.getClass().getSimpleName(), queueCapacity);
        return ret;
    }
}

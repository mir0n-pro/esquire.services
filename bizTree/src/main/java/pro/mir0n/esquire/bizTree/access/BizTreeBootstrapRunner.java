/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: single bizTree bootstrap entry (v1.2.5 Taijitu refactor).
 *                   On ApplicationReadyEvent, fires the active director's bootstrap().
 *                   Replaces the BizTreeCacheLoader ApplicationReadyEvent auto-fire --
 *                   loading is now the director's responsibility, uniform across
 *                   legacy / yang / taijitu.
 */
package pro.mir0n.esquire.bizTree.access;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * The sprint entry. Exactly one place fires the cache's startup workflow:
 * when the Spring context is ready (DB reachable), this listener calls
 * {@link IBizTreeDirector#bootstrap()} on whichever director
 * {@code BizTreeDirectorConfig} selected.
 *
 * Flow:  ApplicationReadyEvent  ->  director.bootstrap()  ->  actual workflow
 *        (sprint entry)             (configurable director)   (load / INIT / sweep)
 *
 * Observability: the director choice is logged by BizTreeDirectorConfig at
 * bean-creation; the bootstrap firing is logged here; the workflow itself
 * logs inside each director. Reading the startup log top-to-bottom tells you
 * exactly which director ran and how it came up.
 */
@Slf4j
@Component
public class BizTreeBootstrapRunner implements ApplicationListener<ApplicationReadyEvent> {

    private final IBizTreeDirector director;

    public BizTreeBootstrapRunner(IBizTreeDirector director) {
        this.director = director;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("bizTree bootstrap: firing {}.bootstrap()", director.getClass().getSimpleName());
        director.bootstrap();
    }
}

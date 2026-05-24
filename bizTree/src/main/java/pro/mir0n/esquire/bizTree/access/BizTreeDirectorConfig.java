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
 * 05/20/2026 mir0n  generalization: @Bean takes ObjectMapper; constructs MonadY with instance id
 *                   "monad" (not the role "yang") + cacheLoader / eventHub / readBackend /
 *                   ObjectMapper; passes ObjectMapper to BizTreeDirectorLegacy too.
 * 05/22/2026 mir0n  wired the "taijitu" case (two Monads "monad" + "danom" -> BizTreeDirectorTaijitu);
 *                   removed the "yang" case (MonadY / BizTreeDirectorYang); options: legacy | taijitu.
 * 05/23/2026 mir0n  night-watch wiring: the "taijitu" case builds a per-monad cache backend inline --
 *                   buildCache(table) creates the monad's own H2 table (CacheSqlSet.forTable + DDL),
 *                   repository, loader and read service on the shared cacheJdbcTemplate; tableFor(id)
 *                   suffixes the base table (ESQ_TREE_MONAD / ESQ_TREE_DANOM); applies the configurable
 *                   sweep interval / timeout / on-mismatch (parseMismatch) to the director.
 */
package pro.mir0n.esquire.bizTree.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import pro.mir0n.esquire.bizTree.access.legacy.BizTreeDirectorLegacy;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheLoader;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheSql;
import pro.mir0n.esquire.bizTree.cache.CacheSqlSet;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.cache.impl.BizTreeCacheRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqAcctRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqOrgRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqUsrRepository;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import pro.mir0n.esquire.bizTree.service.impl.BizTreeService;
import pro.mir0n.esquire.bizTree.access.taijitu.BizTreeDirectorTaijitu;
import pro.mir0n.esquire.bizTree.taijitu.Monad;
import pro.mir0n.utils.taijitu.MismatchAction;

import java.util.Locale;

/**
 * The single declaration point of the active {@link IBizTreeDirector}.
 *
 * One property selects the implementation:
 * <pre>
 *   biztree.director = legacy   (default) -- pre-refactor mechanics
 *                    = taijitu             -- two-monad + night-watch
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

    @Value("${biztree.taijitu.sweep.interval-ms:10000}")
    private long sweepIntervalMs;

    @Value("${biztree.taijitu.sweep.timeout-ms:10000}")
    private long sweepTimeoutMs;

    @Value("${biztree.taijitu.on-mismatch:LOG}")
    private String onMismatch;

    /** Base cache table name; taijitu suffixes it per monad (e.g. ESQ_TREE_MONAD / ESQ_TREE_DANOM). */
    @Value("${biztree.cache.table:ESQ_TREE}")
    private String cacheTable;

    // ingredients for building a per-monad cache backend in the taijitu case (one H2 datasource, table-per-monad)
    @Autowired @Qualifier("cacheJdbcTemplate")
    private JdbcTemplate     cacheJdbcTemplate;
    @Autowired private BizTreeCacheSql   cacheSqlTemplates;
    @Autowired private EsqOrgRepository  orgRepo;
    @Autowired private EsqUsrRepository  usrRepo;
    @Autowired private EsqAcctRepository acctRepo;

    @Bean
    public IBizTreeDirector bizTreeDirector(IBizTreeService         bizTreeService,
                                            IBizTreeCacheRepository cacheRepository,
                                            BizTreeCacheLoader      cacheLoader,
                                            ObjectMapper            objectMapper) {

        String kind = directorKind == null ? "legacy" : directorKind.trim().toLowerCase();

        IBizTreeDirector ret = switch (kind) {
            case "legacy" -> new BizTreeDirectorLegacy(bizTreeService, cacheLoader, cacheRepository, objectMapper);

            case "taijitu" -> {
                MonadCache yang = buildCache(tableFor("monad"));   // serving -- own table
                MonadCache yin  = buildCache(tableFor("danom"));   // shadow  -- own table
                BizTreeDirectorTaijitu taijitu = new BizTreeDirectorTaijitu(
                        new Monad("monad", queueCapacity, yang.loader(), yang.repo(),
                                new MessageHandlerHub(yang.repo())::dispatch, yang.read(), objectMapper),
                        new Monad("danom", queueCapacity, yin.loader(), yin.repo(),
                                new MessageHandlerHub(yin.repo())::dispatch, yin.read(), objectMapper));
                MismatchAction action = parseMismatch(onMismatch);
                taijitu.setSweepIntervalMs(sweepIntervalMs);
                taijitu.setSweepTimeoutMs(sweepTimeoutMs);
                taijitu.setOnMismatch(action);
                log.info("bizTree taijitu night-watch: tables=[{}, {}], interval={}ms, per-leg timeout={}ms, onMismatch={}",
                        tableFor("monad"), tableFor("danom"), sweepIntervalMs, sweepTimeoutMs, action);
                yield taijitu;
            }

            default -> throw new IllegalStateException(
                    "unknown biztree.director='" + directorKind + "' (expected: legacy | taijitu)");
        };

        log.info("bizTree director = '{}' -> {} (queue.capacity={})",
                kind, ret.getClass().getSimpleName(), queueCapacity);
        return ret;
    }

    /**
     * Build a dedicated cache backend for one monad: its own H2 table (DDL run here), repository,
     * loader and read service -- all bound to that table, sharing the one H2 datasource. This is
     * what makes the night-watch possible: the shadow loads/clears its OWN table without colliding
     * with the serving monad's.
     */
    private MonadCache buildCache(String table) {
        CacheSqlSet sql = CacheSqlSet.forTable(cacheSqlTemplates, table);
        cacheJdbcTemplate.execute(sql.createTable());
        cacheJdbcTemplate.execute(sql.createIndexParent());
        cacheJdbcTemplate.execute(sql.createIndexEntityPk());
        BizTreeCacheRepository repo   = new BizTreeCacheRepository(cacheJdbcTemplate, sql);
        BizTreeCacheLoader     loader = new BizTreeCacheLoader(orgRepo, usrRepo, acctRepo, cacheJdbcTemplate, sql);
        return new MonadCache(loader, repo, new BizTreeService(repo));
    }

    /** Per-monad cache table: the base table suffixed with the monad instance id (ESQ_TREE_MONAD / ESQ_TREE_DANOM). */
    private String tableFor(String monadId) {
        return cacheTable + "_" + monadId.toUpperCase(Locale.ROOT);
    }

    /** The per-monad cache backend triplet (each bound to its own table). */
    private record MonadCache(BizTreeCacheLoader loader, IBizTreeCacheRepository repo, IBizTreeService read) {
    }

    /** Parse the configured night-watch reaction (case-insensitive); unknown / blank -> LOG. */
    private MismatchAction parseMismatch(String raw) {
        MismatchAction ret = MismatchAction.LOG;
        if (raw != null && !raw.isBlank()) {
            try {
                ret = MismatchAction.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("unknown biztree.taijitu.on-mismatch='{}' -- defaulting to LOG", raw);
            }
        }
        return ret;
    }
}

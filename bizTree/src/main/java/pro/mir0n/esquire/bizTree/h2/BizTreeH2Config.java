/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: H2 cache configuration; BizTreeCacheSql and cacheJdbcTemplate beans
 *                   explicit HikariCP pool config (pool-name, max-size, min-idle, timeouts)
 * 03/26/2026 mir0n  delete-node SQL property wired into BizTreeCacheSql.Repo
 * 04/02/2026 mir0n  added 3 Repo queries:  moveNode, moveAcctLink, findFolderPks
 * 05/14/2026 mir0n  select-subtree SQL property wired into BizTreeCacheSql.Repo
 * 05/20/2026 mir0n  Taijitu refactor (v1.2.5): build per-table CacheSqlSet bean via
 *                   CacheSqlSet.forTable (biztree.cache.table, default ESQ_TREE);
 *                   cacheJdbcTemplate + DDL executed from the resolved set
 */
package pro.mir0n.esquire.bizTree.h2;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheSql;
import pro.mir0n.esquire.bizTree.cache.CacheSqlSet;

/**
 * H2-specific configuration for the in-memory tree cache.
 * Active when profile cache-h2 is active (BIZTREE_CACHE_VENDOR=cache-h2).
 *
 * Loads h2-cache-sql.properties and provides BizTreeCacheSql and cacheJdbcTemplate beans.
 * A different embedded DB vendor would provide its own *Config class with its own
 * properties file — no changes to the cache package required.
 */
@Configuration
@Profile("cache-h2")
@PropertySource("classpath:META-INF/h2-cache-sql.properties")
public class BizTreeH2Config {

    @Value("${biztree.h2.url:jdbc:h2:mem:biztree;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}")
    private String  h2Url;
    @Value("${biztree.cache.table:ESQ_TREE}")
    private String  cacheTable;
    @Value("${biztree.h2.pool.pool-name:biztree-h2-cache}")
    private String  poolName;
    @Value("${biztree.h2.pool.maximum-pool-size:10}")
    private int     maximumPoolSize;
    @Value("${biztree.h2.pool.minimum-idle:10}")
    private int     minimumIdle;
    @Value("${biztree.h2.pool.connection-timeout:5000}")
    private long    connectionTimeout;
    @Value("${biztree.h2.pool.max-lifetime:1800000}")
    private long    maxLifetime;
    @Value("${biztree.h2.pool.idle-timeout:600000}")
    private long    idleTimeout;

    @Bean
    public BizTreeCacheSql bizTreeCacheSql(Environment env) {
        return new BizTreeCacheSql(
                new BizTreeCacheSql.Ddl(
                        env.getRequiredProperty("biztree.cache.sql.ddl.create-table"),
                        env.getRequiredProperty("biztree.cache.sql.ddl.create-index-parent"),
                        env.getRequiredProperty("biztree.cache.sql.ddl.create-index-entity-pk")
                ),
                new BizTreeCacheSql.Repo(
                        env.getRequiredProperty("biztree.cache.sql.repo.select-cols"),
                        env.getRequiredProperty("biztree.cache.sql.repo.select-one"),
                        env.getRequiredProperty("biztree.cache.sql.repo.find-root"),
                        env.getRequiredProperty("biztree.cache.sql.repo.find-nodes"),
                        env.getRequiredProperty("biztree.cache.sql.repo.find-path"),
                        env.getRequiredProperty("biztree.cache.sql.repo.find-by-entity-id"),
                        env.getRequiredProperty("biztree.cache.sql.repo.find-by-name-kind"),
                        env.getRequiredProperty("biztree.cache.sql.repo.update-node"),
                        env.getRequiredProperty("biztree.cache.sql.repo.delete-node"),
                        env.getRequiredProperty("biztree.cache.sql.repo.move-node"),
                        env.getRequiredProperty("biztree.cache.sql.repo.move-acct-link"),
                        env.getRequiredProperty("biztree.cache.sql.repo.find-folder-pks"),
                        env.getRequiredProperty("biztree.cache.sql.repo.find-subtree")
                ),
                new BizTreeCacheSql.Loader(
                        env.getRequiredProperty("biztree.cache.sql.loader.insert-node"),
                        env.getRequiredProperty("biztree.cache.sql.loader.update-path"),
                        env.getRequiredProperty("biztree.cache.sql.loader.select-paths")
                )
        );
    }

    @Bean
    public CacheSqlSet cacheSqlSet(BizTreeCacheSql templates) {
        return CacheSqlSet.forTable(templates, cacheTable);
    }

    @Bean("cacheJdbcTemplate")
    public JdbcTemplate cacheJdbcTemplate(CacheSqlSet sql) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(poolName);
        ds.setJdbcUrl(h2Url);
        ds.setDriverClassName("org.h2.Driver");
        ds.setMaximumPoolSize(maximumPoolSize);
        ds.setMinimumIdle(minimumIdle);
        ds.setConnectionTimeout(connectionTimeout);
        ds.setMaxLifetime(maxLifetime);
        ds.setIdleTimeout(idleTimeout);
        JdbcTemplate jt = new JdbcTemplate(ds);
        jt.execute(sql.createTable());
        jt.execute(sql.createIndexParent());
        jt.execute(sql.createIndexEntityPk());
        return jt;
    }
}

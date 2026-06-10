/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the AUDIT director -- the first IRodDirector. Hands each event to the reused
 *                   common.xrod.XXRod worker pool, which applies it via the kind->IRodRepository registry
 *                   (-> AuditLogWriter -> *_log). No ordering/grouping; parallelism = the pool size, itself
 *                   kept <= the audit-DB connection-pool size.
 * 06/06/2026 mir0n  self-configuring: gated by xxrod.director.type=audit (default); init() reads its OWN
 *                   xxrod.director.audit.* properties (pool-size, virtual-threads) and the active vendor,
 *                   then builds the AuditLogWriter + AuditKinds registry + XXRod pool. shutdown() stops it.
 */
package pro.mir0n.esquire.xxRod.director;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.audit.AuditKinds;
import pro.mir0n.esquire.common.audit.AuditLogWriter;
import pro.mir0n.esquire.common.xrod.RodEvent;
import pro.mir0n.esquire.common.xrod.RodRepositoryRegistry;
import pro.mir0n.esquire.common.xrod.XXRod;

import javax.sql.DataSource;

@Component
@ConditionalOnProperty(prefix = "xxrod.director", name = "type",
        havingValue = IRodDirector.TYPE_AUDIT, matchIfMissing = true)
public class AuditRodDirector implements IRodDirector {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditRodDirector.class.getName());

    private final DataSource dataSource;
    private final String appName;
    private XXRod pool;

    public AuditRodDirector(DataSource dataSource,
                            @org.springframework.beans.factory.annotation.Value("${spring.application.name}") String appName) {
        this.dataSource = dataSource;
        this.appName    = appName;
    }

    @Override
    public String type() {
        return TYPE_AUDIT;
    }

    @Override
    public void init(Environment env) {
        int     poolSize       = env.getProperty("xxrod.director.audit.pool-size", Integer.class, 8);
        boolean virtualThreads = env.getProperty("xxrod.director.audit.virtual-threads", Boolean.class, false);
        String  profile        = env.getProperty("spring.profiles.active", "dev-postgres");
        boolean oracle         = profile.contains("oracle");

        AuditLogWriter writer = new AuditLogWriter(dataSource, oracle);
        RodRepositoryRegistry registry = new RodRepositoryRegistry();
        AuditKinds.all(EsqObjectKindStorage.getInstance())
                .forEach((kind, sqlKey) -> registry.register(kind, e -> writer.applyEvent(sqlKey, e)));
        pool = new XXRod(registry, poolSize, virtualThreads);
        pool.start(appName, devLog);
        devLog.info("audit director init: poolSize={}, virtual={}, oracle={}", poolSize, virtualThreads, oracle);
    }

    @Override
    public void accept(RodEvent event) {
        pool.submit(event);
    }

    @Override
    public void shutdown() {
        if (pool != null) {
            pool.shutdown();
        }
    }
}

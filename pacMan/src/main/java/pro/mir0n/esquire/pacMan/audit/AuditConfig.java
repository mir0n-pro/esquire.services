/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: pacMan audit-logging wiring over the generic x-Rod (common.audit). pacMan
 *                   owns account UPDATE / DELETE / balance -> esq_account_log (enyMan owns CREATE). Reads
 *                   pacman.audit-logging.* and maps the account kinds to the ACCOUNT statement.
 */
package pro.mir0n.esquire.pacMan.audit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.audit.AuditLogSql;
import pro.mir0n.esquire.common.audit.AuditRod;
import pro.mir0n.esquire.common.audit.AuditSettings;
import pro.mir0n.esquire.common.xrod.XYRod;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class AuditConfig {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditConfig.class.getName());

    @Value("${pacman.audit-logging.enabled:false}")                private boolean enabled;
    @Value("${pacman.audit-logging.x-rod.pool-size:4}")            private int     poolSize;
    @Value("${pacman.audit-logging.x-rod.virtual-threads:false}")  private boolean virtualThreads;
    @Value("${pacman.audit-logging.x-rod.feed-capacity:4096}")     private int     feedCapacity;
    @Value("${pacman.audit-logging.x-rod.log-datastore:shared}")   private String  logDatastore;
    @Value("${pacman.audit-logging.x-rod.log-db.vendor:dev-postgres}")  private String logDbVendor;
    @Value("${pacman.audit-logging.x-rod.log-db.url:}")                 private String logDbUrl;
    @Value("${pacman.audit-logging.x-rod.log-db.username:}")            private String logDbUsername;
    @Value("${pacman.audit-logging.x-rod.log-db.password:}")            private String logDbPassword;
    @Value("${pacman.audit-logging.x-rod.log-db.pool-size:8}")          private int    logDbPoolSize;
    @Value("${spring.profiles.active:dev-postgres}")                    private String businessProfile;

    private final DataSource serviceDataSource;
    private AuditRod.Handle handle;

    public AuditConfig(DataSource serviceDataSource) {
        this.serviceDataSource = serviceDataSource;
    }

    @Bean
    public XYRod xyRod() {
        AuditSettings settings = new AuditSettings(enabled, poolSize, virtualThreads, feedCapacity,
                logDatastore, logDbVendor, logDbUrl, logDbUsername, logDbPassword, logDbPoolSize, businessProfile);
        handle = AuditRod.build("pacman", settings, kindToSqlKey(), serviceDataSource, devLog);
        return handle.xyRod();
    }

    // pacMan writes account UPDATE / DELETE / balance. The account ENTITY kinds are taken from the
    // esq-object-kinds dictionary by the acct flag rather than hardcoded numbers. The kind storage is
    // loaded on ApplicationStartingEvent, before this @Bean runs, so getAll() is already populated here.
    private static Map<Integer, String> kindToSqlKey() {
        Map<Integer, String> m = new HashMap<>();
        for (EsqObjectKind k : EsqObjectKindStorage.getInstance().getAll()) {
            if (k.isAcct()) {
                m.put(k.getId(), AuditLogSql.ACCOUNT);
            }
        }
        return m;
    }

    @PreDestroy
    public void stop() {
        if (handle != null) {
            handle.shutdown();
        }
    }
}

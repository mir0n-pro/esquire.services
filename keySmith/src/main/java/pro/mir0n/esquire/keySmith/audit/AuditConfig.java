/*
 *  Esquire frameworks (tm)
 *  KeySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: keySmith audit-logging wiring over the generic x-Rod (common.audit). keySmith
 *                   owns the auth UPDATE -> esq_auth_log. Reads keysmith.audit-logging.* and maps the
 *                   access-profile kind to the AUTH statement.
 */
package pro.mir0n.esquire.keySmith.audit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.mir0n.esquire.common.EsqConstants;
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

    @Value("${keysmith.audit-logging.enabled:false}")                private boolean enabled;
    @Value("${keysmith.audit-logging.x-rod.pool-size:4}")            private int     poolSize;
    @Value("${keysmith.audit-logging.x-rod.virtual-threads:false}")  private boolean virtualThreads;
    @Value("${keysmith.audit-logging.x-rod.feed-capacity:4096}")     private int     feedCapacity;
    @Value("${keysmith.audit-logging.x-rod.log-datastore:shared}")   private String  logDatastore;
    @Value("${keysmith.audit-logging.x-rod.log-db.vendor:dev-postgres}")  private String logDbVendor;
    @Value("${keysmith.audit-logging.x-rod.log-db.url:}")                 private String logDbUrl;
    @Value("${keysmith.audit-logging.x-rod.log-db.username:}")            private String logDbUsername;
    @Value("${keysmith.audit-logging.x-rod.log-db.password:}")            private String logDbPassword;
    @Value("${keysmith.audit-logging.x-rod.log-db.pool-size:8}")          private int    logDbPoolSize;
    @Value("${spring.profiles.active:dev-postgres}")                      private String businessProfile;

    private final DataSource serviceDataSource;
    private AuditRod.Handle handle;

    public AuditConfig(DataSource serviceDataSource) {
        this.serviceDataSource = serviceDataSource;
    }

    @Bean
    public XYRod xyRod() {
        AuditSettings settings = new AuditSettings(enabled, poolSize, virtualThreads, feedCapacity,
                logDatastore, logDbVendor, logDbUrl, logDbUsername, logDbPassword, logDbPoolSize, businessProfile);
        handle = AuditRod.build("keysmith", settings, kindToSqlKey(), serviceDataSource, devLog);
        return handle.xyRod();
    }

    // keySmith writes the auth UPDATE.
    private static Map<Integer, String> kindToSqlKey() {
        Map<Integer, String> m = new HashMap<>();
        m.put(EsqConstants.KIND_ACCESS_PROFILE, AuditLogSql.AUTH);
        return m;
    }

    @PreDestroy
    public void stop() {
        if (handle != null) {
            handle.shutdown();
        }
    }
}

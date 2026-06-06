/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created (was enyMan.rod.RodConfig): enyMan audit-logging wiring.
 * 06/05/2026 mir0n  thinned onto common.audit.AuditRod: this class only reads enyman.audit-logging.* and
 *                   declares enyMan's kind -> AuditLogSql key map; the generic wiring (datasource shared/
 *                   dedicated, xx/xy-Rod, lifecycle) lives in common.audit. x-Rod stays generic in common.xrod.
 */
package pro.mir0n.esquire.enyMan.audit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
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

    // Audit logging = the FEATURE (option-0 baseline: default OFF, deployer opts in). x-Rod = the
    // generic fan-out IMPLEMENTATION; the audit sink lives in common.audit.
    @Value("${enyman.audit-logging.enabled:false}")                private boolean enabled;
    @Value("${enyman.audit-logging.x-rod.pool-size:4}")            private int     poolSize;
    @Value("${enyman.audit-logging.x-rod.virtual-threads:false}")  private boolean virtualThreads;
    @Value("${enyman.audit-logging.x-rod.feed-capacity:4096}")     private int     feedCapacity;
    @Value("${enyman.audit-logging.x-rod.log-datastore:shared}")   private String  logDatastore;
    @Value("${enyman.audit-logging.x-rod.log-db.vendor:dev-postgres}")  private String logDbVendor;
    @Value("${enyman.audit-logging.x-rod.log-db.url:}")                 private String logDbUrl;
    @Value("${enyman.audit-logging.x-rod.log-db.username:}")            private String logDbUsername;
    @Value("${enyman.audit-logging.x-rod.log-db.password:}")            private String logDbPassword;
    @Value("${enyman.audit-logging.x-rod.log-db.pool-size:8}")          private int    logDbPoolSize;
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
        handle = AuditRod.build("enyman", settings, kindToSqlKey(), serviceDataSource, devLog);
        return handle.xyRod();
    }

    // enyMan writes org / org-param / user / person / address / usr-param / account-CREATE.
    // The org / user / account ENTITY kinds are taken from the esq-object-kinds dictionary by their
    // semantic flags (org / usr / acct) rather than hardcoded numbers; sub-entity and parameter kinds
    // use the named EsqConstants. The kind storage is loaded on ApplicationStartingEvent, before this
    // @Bean runs, so getAll() is already populated here.
    private static Map<Integer, String> kindToSqlKey() {
        Map<Integer, String> m = new HashMap<>();
        for (EsqObjectKind k : EsqObjectKindStorage.getInstance().getAll()) {
            if (k.isAcct()) {
                m.put(k.getId(), AuditLogSql.ACCOUNT);
            } else if (k.isUsr()) {
                m.put(k.getId(), AuditLogSql.USER);
            } else if (k.isOrg()) {
                m.put(k.getId(), AuditLogSql.ORG);
            }
        }
        m.put(EsqConstants.KIND_ORG_PAR, AuditLogSql.ORG_PAR);
        m.put(EsqConstants.KIND_USR_PAR, AuditLogSql.USR_PAR);
        for (int k : new int[]{EsqConstants.KIND_PERSON_PRIMARY, EsqConstants.KIND_PERSON_SECONDARY,
                EsqConstants.KIND_PERSON_JOINT}) {
            m.put(k, AuditLogSql.PERSON);
        }
        for (int k : new int[]{EsqConstants.KIND_ADDRESS_POSTAL, EsqConstants.KIND_ADDRESS_BIZ}) {
            m.put(k, AuditLogSql.ADDRESS);
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

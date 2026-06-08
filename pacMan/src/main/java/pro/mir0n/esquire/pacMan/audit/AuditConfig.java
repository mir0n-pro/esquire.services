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
 * 06/06/2026 mir0n  mode-aware (option c): mode=bus publishes RodEvents to the audit QUEUE via
 *                   RodEventBusPublisher (no local writer/datasource); mode=in-process keeps (b). kind map
 *                   via shared AuditKinds.
 * 06/06/2026 mir0n  bus publisher pool: x-rod.bus.publisher-pool-size=0 keeps the single feed-worker sync
 *                   publish; N>0 wires AuditRod.buildBusPool over a dedicated useAsyncSend connection
 *                   (N async senders), CF closed in @PreDestroy.
 * 06/08/2026 mir0n  option (d): mode=redis builds a RodRedisPublisher (XADD to the audit Redis Stream via
 *                   the injected StringRedisTemplate), wired through buildBus / buildBusPool; stream key and
 *                   approximate MAXLEN from x-rod.redis.*.
 */
package pro.mir0n.esquire.pacMan.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.audit.AuditLogSql;
import pro.mir0n.esquire.common.audit.AuditRod;
import pro.mir0n.esquire.common.audit.AuditSettings;
import pro.mir0n.esquire.common.audit.RodEventBusPublisher;
import pro.mir0n.esquire.common.audit.RodRedisPublisher;
import pro.mir0n.esquire.common.xrod.XYRod;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class AuditConfig {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + AuditConfig.class.getName());

    @Value("${pacman.audit-logging.enabled:false}")                private boolean enabled;
    @Value("${pacman.audit-logging.x-rod.mode:in-process}")        private String  mode;
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
    @Value("${spring.application.name}")                                private String appName;
    @Value("${spring.activemq.broker-url:tcp://localhost:61616}")       private String  brokerUrl;
    // bus publisher pool (option c): 0 = current single feed-worker synchronous publish; N>0 = N async
    // publisher threads over a dedicated useAsyncSend connection (the same thread-per-event pool as b).
    // The pool size also drives the (d) redis path.
    @Value("${pacman.audit-logging.x-rod.bus.publisher-pool-size:0}")   private int     publisherPoolSize;
    // (d) redis: the audit stream key (blank -> EsqMsgConstants.STREAM_ROD_AUDIT) and approximate MAXLEN (0 = uncapped).
    @Value("${pacman.audit-logging.x-rod.redis.stream:}")              private String  redisStream;
    @Value("${pacman.audit-logging.x-rod.redis.max-len:0}")            private long    redisMaxLen;

    private final DataSource serviceDataSource;
    private final JmsTemplate jmsQueueTemplate;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private AuditRod.Handle handle;
    private CachingConnectionFactory auditConnectionFactory;

    public AuditConfig(DataSource serviceDataSource,
                       @Qualifier("jmsQueueTemplate") JmsTemplate jmsQueueTemplate,
                       ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                       ObjectMapper objectMapper) {
        this.serviceDataSource     = serviceDataSource;
        this.jmsQueueTemplate      = jmsQueueTemplate;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper          = objectMapper;
    }

    @Bean
    public XYRod xyRod() {
        AuditSettings settings = new AuditSettings(enabled, poolSize, virtualThreads, feedCapacity,
                logDatastore, logDbVendor, logDbUrl, logDbUsername, logDbPassword, logDbPoolSize, businessProfile);
        if (AuditRod.MODE_BUS.equalsIgnoreCase(mode)) {
            // (c) producer: publish to the audit queue; the standalone xxRod consumer writes the *_log.
            if (publisherPoolSize > 0) {
                // async publisher pool: dedicated useAsyncSend connection (scoped to audit), N publisher threads.
                ActiveMQConnectionFactory amq = new ActiveMQConnectionFactory(brokerUrl);
                amq.setUseAsyncSend(true);
                auditConnectionFactory = new CachingConnectionFactory(amq);
                auditConnectionFactory.setSessionCacheSize(publisherPoolSize);
                JmsTemplate asyncTemplate = new JmsTemplate(auditConnectionFactory);
                asyncTemplate.setPubSubDomain(false);
                RodEventBusPublisher publisher =
                        new RodEventBusPublisher(asyncTemplate, EsqMsgConstants.QUEUE_ROD_AUDIT, objectMapper);
                handle = AuditRod.buildBusPool(appName, settings, publisher, publisherPoolSize, devLog);
            } else {
                // current: single feed worker publishes synchronously over the shared queue template.
                RodEventBusPublisher publisher =
                        new RodEventBusPublisher(jmsQueueTemplate, EsqMsgConstants.QUEUE_ROD_AUDIT, objectMapper);
                handle = AuditRod.buildBus(appName, settings, publisher, devLog);
            }
        } else if (AuditRod.MODE_REDIS.equalsIgnoreCase(mode)) {
            // (d) producer: XADD each event to the Redis Stream (the stream IS the audit log; no consumer).
            String stream = redisStream.isBlank() ? EsqMsgConstants.STREAM_ROD_AUDIT : redisStream;
            RodRedisPublisher publisher =
                    new RodRedisPublisher(redisTemplateProvider.getObject(), stream, redisMaxLen, objectMapper);
            handle = (publisherPoolSize > 0)
                    ? AuditRod.buildBusPool(appName, settings, publisher, publisherPoolSize, devLog)
                    : AuditRod.buildBus(appName, settings, publisher, devLog);
        } else {
            // (b) in-process: write the *_log here.
            handle = AuditRod.build(appName, settings, kindToSqlKey(), serviceDataSource, devLog);
        }
        return handle.xyRod();
    }

    // pacMan writes only the account table in-process; account kinds via the dictionary acct flag
    // (matches pacMan's own META-INF/audit-log-*.xml, which ships only the account statement).
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
        if (auditConnectionFactory != null) {
            auditConnectionFactory.destroy();
        }
    }
}

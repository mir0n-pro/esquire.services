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
 * 06/06/2026 mir0n  mode-aware (option c): mode=bus publishes RodEvents to the audit QUEUE via
 *                   RodEventBusPublisher (no local writer/datasource); mode=in-process keeps (b).
 * 06/06/2026 mir0n  bus publisher pool: x-rod.bus.publisher-pool-size=0 keeps the single feed-worker sync
 *                   publish; N>0 wires AuditRod.buildBusPool over a dedicated useAsyncSend connection
 *                   (N async senders), CF closed in @PreDestroy.
 * 06/08/2026 mir0n  option (d): mode=redis builds a RodRedisPublisher (XADD to the audit Redis Stream via
 *                   the injected StringRedisTemplate), wired through buildBus / buildBusPool; stream key and
 *                   approximate MAXLEN from x-rod.redis.*.
 * 06/08/2026 mir0n  option (c) over Kafka: mode=bus + x-rod.bus.transport=kafka builds a RodKafkaPublisher
 *                   over the autoconfigured KafkaTemplate (key = entityId) -> buildBus; transport=activemq
 *                   (default) keeps the queue path.
 */
package pro.mir0n.esquire.enyMan.audit;

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
import org.springframework.kafka.core.KafkaTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.audit.AuditLogSql;
import pro.mir0n.esquire.common.audit.AuditRod;
import pro.mir0n.esquire.common.audit.AuditSettings;
import pro.mir0n.esquire.common.audit.RodEventBusPublisher;
import pro.mir0n.esquire.common.audit.RodKafkaPublisher;
import pro.mir0n.esquire.common.audit.RodRedisPublisher;
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
    @Value("${enyman.audit-logging.x-rod.mode:in-process}")        private String  mode;
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
    @Value("${spring.application.name}")                                private String appName;
    @Value("${spring.activemq.broker-url:tcp://localhost:61616}")       private String  brokerUrl;
    // bus publisher pool (option c): 0 = current single feed-worker synchronous publish; N>0 = N async
    // publisher threads over a dedicated useAsyncSend connection (the same thread-per-event pool as b).
    // The pool size also drives the (d) redis path.
    @Value("${enyman.audit-logging.x-rod.bus.publisher-pool-size:0}")   private int     publisherPoolSize;
    // (c) bus transport: activemq (default) | kafka. kafka -> publish to the audit Kafka topic instead.
    @Value("${enyman.audit-logging.x-rod.bus.transport:activemq}")      private String  busTransport;
    // (d) redis: the audit stream key (blank -> EsqMsgConstants.STREAM_ROD_AUDIT) and approximate MAXLEN (0 = uncapped).
    @Value("${enyman.audit-logging.x-rod.redis.stream:}")              private String  redisStream;
    @Value("${enyman.audit-logging.x-rod.redis.max-len:0}")            private long    redisMaxLen;

    private final DataSource serviceDataSource;
    private final JmsTemplate jmsQueueTemplate;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<KafkaTemplate<?, ?>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;
    private AuditRod.Handle handle;
    private CachingConnectionFactory auditConnectionFactory;

    public AuditConfig(DataSource serviceDataSource,
                       @Qualifier("jmsQueueTemplate") JmsTemplate jmsQueueTemplate,
                       ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                       ObjectProvider<KafkaTemplate<?, ?>> kafkaTemplateProvider,
                       ObjectMapper objectMapper) {
        this.serviceDataSource     = serviceDataSource;
        this.jmsQueueTemplate      = jmsQueueTemplate;
        this.redisTemplateProvider = redisTemplateProvider;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper          = objectMapper;
    }

    @Bean
    public XYRod xyRod() {
        AuditSettings settings = new AuditSettings(enabled, poolSize, virtualThreads, feedCapacity,
                logDatastore, logDbVendor, logDbUrl, logDbUsername, logDbPassword, logDbPoolSize, businessProfile);
        if (AuditRod.MODE_BUS.equalsIgnoreCase(mode) && AuditRod.TRANSPORT_KAFKA.equalsIgnoreCase(busTransport)) {
            // (c) over Kafka: publish to the audit topic (keyed by entityId); xxRod consumes + writes the *_log.
            // KafkaTemplate.send is async/batched, so the single feed worker suffices (no publisher pool).
            @SuppressWarnings("unchecked")
            KafkaTemplate<String, String> kt = (KafkaTemplate<String, String>) kafkaTemplateProvider.getObject();
            RodKafkaPublisher publisher = new RodKafkaPublisher(kt, EsqMsgConstants.TOPIC_ROD_AUDIT, objectMapper);
            handle = AuditRod.buildBus(appName, settings, publisher, devLog);
        } else if (AuditRod.MODE_BUS.equalsIgnoreCase(mode)) {
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
        if (auditConnectionFactory != null) {
            auditConnectionFactory.destroy();
        }
    }
}

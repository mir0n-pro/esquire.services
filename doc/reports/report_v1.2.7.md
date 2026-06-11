# Release Report: v1.2.6 → v1.2.7

**Repo:** `esquire.services/develop`  
**Top commit:** `b760a63`

---

## Release Notes

### doc/release_notes.txt


**v1.2.7-2606.1016**  v1.2.7 -- CI/CD pipeline established on GitHub Actions; release finalized  
&nbsp;: Config:      esquire.version bumped 1.2.6 -> 1.2.7 across the reactor (release finalization)  
&nbsp;: Feature:     CI/CD pipeline established -- hosted CI (mvn verify + Testcontainers IT) on every push/PR;  
&nbsp;                 self-hosted local-k8s deploy on push to pending-** ; OKE production deploy on a pending-* PR  
&nbsp;                 merged into develop, behind a manual-approval Environment with e2e + load validation  
&nbsp;: Fix:         OKE deploy logs in to GHCR with the GHCR_TOKEN PAT (mir0n-pro) instead of the repo  
&nbsp;                 GITHUB_TOKEN, which the pre-existing packages reject for write  
&nbsp;: Doc:         README gains a plain-language CI/CD section; Esquire.GitHubActions.md sec 4.3 finalized  
&nbsp;   Components:   build/version,  
&nbsp;                 .github (CI/CD),  
&nbsp;                 doc  

**v1.2.7-2606.0923**  v1.2.7 -- audit logging: option (c) becomes the default deploy topology; docs consolidated  
&nbsp;: Config:      Docker and local k8s now ship option (c) by default -- producers audit-enabled, mode=bus over  
&nbsp;                 ActiveMQ (already in the stack), async publisher pool=4; new esquire-xxrod helm chart wired  
&nbsp;                 into k8s-rebuild / up / down; postgres image bakes create.log so a fresh cluster seeds the  
&nbsp;* _log tables. Every other topology (b / d / Kafka) stays a config flip.  
&nbsp;: Config:      OKE overlays set audit off -> option (a) DB triggers (no xxRod pod, no extra broker load on  
&nbsp;                 the Always-Free tier); the trigger DDL is applied to the OKE postgres  
&nbsp;: Fix:         bizTree cache children (find-nodes) query gets a deterministic ORDER BY (kind, name, tree_pk)  
&nbsp;: Fix:         k8s-up.bat readiness loop sleeps via powershell Start-Sleep instead of 'timeout' (which  
&nbsp;                 aborts under the self-hosted runner's redirected stdin -- "Input redirection is not supported")  
&nbsp;: Doc:         audit research consolidated into one Esquire.AuditLoggingStack.md  
&nbsp;                 README / Vision / DatabaseDictionary  
&nbsp;                 reframe audit as an optional, pluggable concern; component model refreshed  
&nbsp;   Components:   bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 xxRod  

**v1.2.7-2606.0822**  v1.2.7 -- audit logging (option c-k, d-k): Kafka as the bus transport  
&nbsp;: Feature:     option (c) (d) over Kafka -- producers publish committed audit events to a Kafka topic (keyed  
&nbsp;                 by entityId);  
&nbsp;: Config:      spring.kafka producer tuning (acks / batch-size / compression / linger.ms) +  
&nbsp;                 the Kafka Connect Redis sink (kafka-sink profile) added to the dev compose  
&nbsp;: Doc:         Kafka transport + partitioning / consumer model (Design sec 14); c-k / d-k rows added to  
&nbsp;                 the request-processing-time matrices; Kafka config knobs in services.configuring  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 xxRod  

**v1.2.7-2606.0820**  v1.2.7 -- audit logging (option d): events streamed straight to Redis  
&nbsp;: Feature:     option (d) -- the producer XADDs each committed audit event to a Redis Stream; the stream  
&nbsp;                 is the append-only audit log itself (no consumer service, read with XRANGE)  
&nbsp;: Config:      audit-logging.x-rod.mode=redis + x-rod.redis.stream / max-len; spring.data.redis.host/port;  
&nbsp;                 redis:8 and RedisInsight (tools profile) added to the dev compose  
&nbsp;: Doc:         request-processing-time matrix refreshed same-day with the (d) row; RedisJSON-local  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith  

**v1.2.7-2606.0622**  v1.2.7 -- audit logging (option c): distributed bus path + standalone xxRod consumer  
&nbsp;: Feature:     option (c) -- producers publish committed audit events to a durable ActiveMQ queue; the  
&nbsp;                 new standalone xxRod service consumes the queue and writes the *_log tables off-box  
&nbsp;: Feature:     bus publisher pool -- N async sender threads lift the publish-rate cap under high load  
&nbsp;                 (in-process (b) and single-worker bus publish kept as options)  
&nbsp;: Feature:     audit SQL externalized to per-module META-INF/audit/{postgres,oracle}.xml (deploy-time opt-in)  
&nbsp;: Fix:         pacMan entity-broadcast publisher qualifies jmsTopicTemplate (two JmsTemplate beans now exist)  
&nbsp;: Config:      audit-logging.x-rod.mode (in-process|bus) + bus.publisher-pool-size; *_log dedup unique indexes  
&nbsp;: Doc:         request-processing-time (srvOuter) cost matrices for options (a)/(b)/(c) at normal + high load  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 xxRod  

**v1.2.7-2606.0615**  v1.2.7 -- audit logging (option b): entity changes recorded to the *_log tables  
&nbsp;: Feature:     opt-in audit logging records committed entity / sub-entity / account / auth changes to  
&nbsp;                 the *_log tables, off the business transaction (the generic x-Rod producer / consumer)  
&nbsp;: Feature:     enyMan audits org / user / person / address / params (+ account CREATE); pacMan audits  
&nbsp;                 account update / delete / balance; keySmith audits the auth update (secrets excluded)  
&nbsp;: Fix:         custom parameter edits now save without a prior /esq-dict fetch (dictionary completed lazily)  
&nbsp;: Config:      per-service audit-logging.* (enabled default OFF; pool / feed / shared|dedicated log store)  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith  

**v1.2.7-2606.0417**  v1.2.7 -- unified per-request context: uid / rootPath obtained like crl_id / req_id  
&nbsp;: Refactoring: uid and rootPath move into the per-request EsqRequestContext, captured once and read  
&nbsp;                 via RequestContextUtils  
&nbsp;: Fix:         a move no longer overwrites the moved entity's crl_id / req_id audit columns with null  
&nbsp;                 (the move worker re-establishes the request context on its own thread)  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 bizTree  

**v1.2.7-2606.0318**  v1.2.7 -- audit-trigger decoupling: account funded-date moved to the service  
&nbsp;: Refactoring: account funded-date is stamped by pacMan on the balance update  
&nbsp;   Components:   pacMan  

---

## Code Changes

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**06/09/2026** mir0n  v1.2.7 -- deterministic ORDER BY on the cache children query  
**resources/META-INF/h2-cache-sql.properties**  
&nbsp;- find-nodes query: appended ORDER BY tree_et_pk, tree_name, tree_pk so the children result set has a  
&nbsp;   stable kind / name / pk order (was unspecified, surfaced downstream as a non-deterministic account pick)  

**06/04/2026** mir0n  v1.2.7 -- uid / rootPath read from the unified request context (not method params)  
**access.IBizTreeDirector**  
&nbsp;- esquire / esquirePath / esquireEntityNode / esquireSubtree: rootPath + uid params removed  
**access.taijitu.BizTreeDirectorTaijitu**  
**access.legacy.BizTreeDirectorLegacy**  
&nbsp;- read rootPath / uid via RequestContextUtils and forward to IBizTreeService (service + cache  
&nbsp;   repository signatures unchanged -- they keep the params)  
**controller.BizTreeController**  
&nbsp;- stops extracting rootPath / uid from claims; forwards only id / kind / name to the director  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**06/05/2026** mir0n  v1.2.7 -- audit logging (b): IMappable body carriers for the x-Rod  
jpa.IMappable  (new)  
&nbsp;- fillMap(Map) capability: an object presents its data fields by property name (no reflection)  
**jpa.EsqEntityJpa**  
&nbsp;- implements IMappable; fillMap() emits name/desc/parentId  
jpa.entity.EsqOrgJpa / EsqUsrJpa / EsqAcctJpa / EsqAddressJpa / EsqPersonJpa  
&nbsp;- fillMap() overrides emitting each entity's data fields for the matching *_log body  
jpa.entity.EsqParRow  (new)  
&nbsp;- parameter read projection (name/etPk/value), IMappable; shared org_par / usr_par  
jpa.access.EsqAuthJpa  (new)  
&nbsp;- esq_auth audit body: managed non-secret fields (loginId/email/connectFlg/tfaMethod/forceChangeFlg);  
&nbsp;   security question / answer excluded  

**06/04/2026** mir0n  v1.2.7 -- unified per-request context (uid / rootPath read like crl_id / req_id)  
service.EsqRequestContext  (new)  
&nbsp;- record (correlationId, requestId, uid, rootPath): the per-request context, captured once and  
&nbsp;   re-hydratable on any thread  
service.EsqContextHolder  (new)  
&nbsp;- ThreadLocal holder for EsqRequestContext; set by the auth filter / move worker, cleared in a finally  
**service.RequestContextUtils**  
&nbsp;- reads EsqContextHolder first, header fallback for crl/req; getUid() / getRootPath() / getContext() added  
**security.JwtAuthenticationFilter**  
&nbsp;- on a valid token builds EsqRequestContext (crl/req from headers, uid/rootPath from claims),  
&nbsp;   sets the holder + MDC uid, clears both in a finally  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**06/08/2026** mir0n  v1.2.7 -- audit logging (c) over Kafka transport  
audit.RodKafkaPublisher  (new)  
&nbsp;- Consumer that sends the event to the audit Kafka topic via KafkaTemplate (key = entityId,  
&nbsp;   value = RodEventCodec.toJson); async send, delivery failure logged in the callback; best-effort  
**audit.RodEventCodec**  
&nbsp;- toJson / fromJson added (the toProps envelope serialized to a single JSON string -- the Kafka value payload)  
**audit.AuditRod**  
&nbsp;- TRANSPORT_ACTIVEMQ / TRANSPORT_KAFKA constants added (the x-rod.bus.transport selector)  
EsqMsgConstants  
&nbsp;- TOPIC_ROD_AUDIT added (the audit Kafka topic)  
**pom.xml**  
&nbsp;- spring-kafka (provided) added  

**06/08/2026** mir0n  v1.2.7 -- audit logging (d): Redis Stream producer  
audit.RodRedisPublisher  (new)  
&nbsp;- Consumer that XADDs the event to a Redis Stream via StringRedisTemplate.opsForStream (fields  
&nbsp;   from RodEventCodec.toProps, null fields omitted; optional approximate MAXLEN); best-effort  
EsqMsgConstants  
&nbsp;- STREAM_ROD_AUDIT added (the audit Redis Stream key)  
**audit.AuditRod**  
&nbsp;- MODE_REDIS constant added (the redis publisher dispatches through buildBus / buildBusPool)  
**pom.xml**  
&nbsp;- spring-data-redis (provided) added  

**06/06/2026** mir0n  v1.2.7 -- audit logging (c): bus transport + async publisher pool + SQL externalized  
audit.RodEventCodec  (new)  
&nbsp;- RodEvent  FIX-JSON envelope (JMS header props + body as Text JSON); shared by producer + xxRod consumer  
audit.RodEventBusPublisher  (new)  
&nbsp;- Consumer the xy-Rod feed worker calls; serializes via RodEventCodec, sends to QUEUE_ROD_AUDIT;  
&nbsp;   best-effort (broker failure logged, not thrown)  
audit.AuditKinds  (new)  
&nbsp;- the one audit kind -> AuditLogSql-key map (entity kinds via esq-object-kinds flags; sub / param / auth  
&nbsp;   via EsqConstants); used by every producer and the xxRod consumer  
**audit.AuditRod**  
&nbsp;- MODE_IN_PROCESS / MODE_BUS constants; buildBus() wires the xy-Rod feed to a bus dispatcher (no local  
&nbsp;   writer / registry / datasource); buildBusPool() wires the feed to an XXRod publisher pool (N async senders)  
**audit.AuditLogSql**  
&nbsp;- rewritten as a generic DOM loader of /META-INF/audit/{vendor}.xml (key -> SQL); SQL no longer in code,  
&nbsp;   tolerates an absent resource (audit opt-in at packaging)  
**audit.AuditLogWriter**  
&nbsp;- header bind-param names -> PARAM_* constants; *_log action codes -> ACTION_INSERT / UPDATE / DELETE  
**xrod.XXRod**  
&nbsp;- generic XXRod(Consumer worker, poolSize, useVirtualThreads) ctor for the producer publisher  
&nbsp;   pool; the registry ctor delegates via applyViaRegistry()  
EsqMsgConstants  
&nbsp;- QUEUE_ROD_AUDIT, MSG_TYPE_ROD_AUDIT, BUS_ID_ROD, SERVICE_ID_ROD_AUDIT, FIELD_SUB_ID / FIELD_UID /  
&nbsp;   FIELD_ACTION_TIME added  
**pom.xml**  
&nbsp;- spring-jms (provided) added  

**06/05/2026** mir0n  v1.2.7 -- audit logging (b): generic x-Rod fan-out substrate + audit sink  
xrod.RodEvent  (new)  
&nbsp;- record (op/kind/entityId/subId/actionTime/crl/req/uid/body): one self-contained relayed change  
xrod.IRodRepository  (new)  
&nbsp;- apply(RodEvent) contract; one per *_log table, must be thread-safe  
xrod.RodRepositoryRegistry  (new)  
&nbsp;- kind -> IRodRepository map (ConcurrentHashMap)  
xrod.XXRod  (new)  
&nbsp;- Semaphore(poolSize)-bounded worker pool with no queue of its own; submit() resolves kind and applies  
xrod.XYRod  (new)  
&nbsp;- producer facade: per-tx ThreadLocal buffer, flush after commit via single-worker BoundedQueueRig;  
&nbsp;   post() overloads (IMappable / Map / no-body); self-guards on enabled  
audit.AuditLogSql  (new)  
&nbsp;- vendor-keyed *_log INSERT..ON CONFLICT (Postgres) / MERGE (Oracle) per statement key  
audit.AuditLogWriter  (new)  
&nbsp;- applyEvent(): uniform header + RodEvent body bound to AuditLogSql via NamedParameterJdbcTemplate;  
&nbsp;   TolerantSource binds an absent :param to NULL (empty-body DELETE)  
audit.AuditRod  (new)  
&nbsp;- build(): resolves the log datasource (shared / dedicated Hikari), wires registry + XXRod + XYRod; Handle  
audit.AuditSettings  (new)  
&nbsp;- record of the per-service audit-logging settings  
EsqConstants  
&nbsp;- KIND_ORG_PAR (972) / KIND_USR_PAR (970) added: synthetic x-Rod param routing kinds  
**pom.xml**  
&nbsp;- HikariCP (provided) added for audit.AuditRod dedicated pool; jjwt-impl (test) added  

**06/04/2026** mir0n  v1.2.7 -- PD_UID for the unified request context  
EsqConstants  
&nbsp;- PD_UID ("uid") added: MDC key for the acting user  

### common/src/main/java/pro/mir0n/utils/changes.txt


### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**06/08/2026** mir0n  v1.2.7 -- audit logging (c) over Kafka transport  
**audit.AuditConfig**  
&nbsp;- mode=bus + x-rod.bus.transport=kafka builds a RodKafkaPublisher over the autoconfigured KafkaTemplate  
&nbsp;   (key = entityId) -> AuditRod.buildBus; transport=activemq (default) keeps the queue path  
**src/main/resources/application.yml**  
&nbsp;- spring.kafka.bootstrap-servers + producer String key/value serializers + acks / batch-size /  
&nbsp;   compression-type / linger.ms tuning; audit-logging.x-rod.bus.transport added  
**pom.xml**  
&nbsp;- spring-kafka added  

**06/08/2026** mir0n  v1.2.7 -- audit logging (d): redis producer mode  
**audit.AuditConfig**  
&nbsp;- mode=redis builds a RodRedisPublisher over the injected StringRedisTemplate (ObjectProvider), wired  
&nbsp;   through buildBus / buildBusPool; reads x-rod.redis.stream / max-len  
**src/main/resources/application.yml**  
&nbsp;- audit-logging.x-rod.redis.{stream,max-len} + spring.data.redis.host/port +  
&nbsp;   management.health.redis.enabled=false added  
**pom.xml**  
&nbsp;- spring-boot-starter-data-redis added  

**06/06/2026** mir0n  v1.2.7 -- audit logging (c): mode-aware producer (bus) + async publisher pool + SQL externalized  
**audit.AuditConfig**  
&nbsp;- mode-aware: mode=bus builds RodEventBusPublisher + AuditRod.buildBus (no local writer / datasource);  
&nbsp;   publisher-pool-size>0 builds a dedicated useAsyncSend connection + AuditRod.buildBusPool (N async  
&nbsp;   senders, CF closed in @PreDestroy); mode=in-process keeps (b)  
src/main/resources/META-INF/audit/postgres.xml  (new)  
src/main/resources/META-INF/audit/oracle.xml  (new)  
&nbsp;- the org / org_par / user / person / address / usr_par / account *_log INSERT / MERGE statements enyMan  
&nbsp;   writes, externalized out of code (loaded by common AuditLogSql)  
src/main/resources/META-INF/audit/oracle-par.xml, postgres-par.xml  (moved + renamed from META-INF/*-rodlog.xml)  
&nbsp;- the listOrgPar / listUsrPar param re-SELECT JPA named-queries (audit producer support) relocated under  
&nbsp;   META-INF/audit/ and de-legacied off the "rodlog" name  
**src/main/resources/application.yml**  
&nbsp;- audit-logging.x-rod.mode + bus.publisher-pool-size added; jpa.mapping-resources point to the moved par files  

**06/05/2026** mir0n  v1.2.7 -- audit logging (b): x-Rod wiring + posts; custom-param save fix  
audit.AuditConfig  (new)  
&nbsp;- reads enyman.audit-logging.*; maps org / org_par / user / person / address / usr_par / account-CREATE  
&nbsp;   kinds to AuditLogSql keys; builds the x-Rod via AuditRod; @PreDestroy shutdown  
**service.impl.AEnyManService**  
&nbsp;- completedDictionary(kind) helper: lazy custom-param merge from esq_parameter (guarded by completed  
&nbsp;   flag); fixes custom params silently skipped on create/save unless /esq-dict was fetched first  
**service.impl.OrgService**  
&nbsp;- XYRod injected; audit posts at create/save/delete/move + per-param org_par (listOrgPar, enabled-gated);  
&nbsp;   create/save resolve the dictionary via completedDictionary  
**service.impl.UsrService**  
&nbsp;- XYRod injected; audit posts across user/person/address/usr_par (create/update/delete + move parent-ref);  
&nbsp;   delete enumerates child pks before cascade; per-param usr_par (listUsrPar); completedDictionary  
**service.impl.AcctService**  
&nbsp;- XYRod injected; account CREATE posts an audit event  
**service.impl.EnyManService**  
&nbsp;- XYRod ctor param added + passed to OrgService / UsrService / AcctService  
**queue.MoveQueueManager**  
&nbsp;- XYRod ctor param added + threaded into the OrgService / UsrService it builds  
**jpa.EsqOrgRepository**  
&nbsp;- listOrgPar re-SELECT (EsqParRow) added (feeds ORG_PAR audit events)  
**jpa.EsqUsrRepository**  
&nbsp;- listUsrPar re-SELECT (EsqParRow) added (feeds USR_PAR audit events)  
src/main/resources/META-INF/postgres-rodlog.xml, oracle-rodlog.xml  (new)  
&nbsp;- listOrgPar / listUsrPar re-SELECT named queries + EsqParRowMapping  
**src/main/resources/application.yml**  
&nbsp;- enyman.audit-logging.* block added; {oracle,postgres}-rodlog.xml registered in jpa.mapping-resources  

**06/04/2026** mir0n  v1.2.7 -- uid / rootPath dropped from service signatures; read from the request context  
**service.IEnyManService**  
&nbsp;- esquireCommand / Save / New / Delete / Move / Tree: rootPath + uid params removed  
**controller.EnyManController**  
&nbsp;- stops extracting rootPath / uid from claims; delegates without them (roles still extracted)  
**service.impl.EnyManService**  
&nbsp;- reads uid / rootPath via RequestContextUtils where needed (self-update + self-move guards, move item)  
**service.impl.OrgService**  
&nbsp;- esquireCommand* read rootPath / uid via RequestContextUtils instead of params  
**service.impl.UsrService**  
&nbsp;- esquireCommand* read rootPath / uid via RequestContextUtils instead of params  
**service.impl.AcctService**  
&nbsp;- esquireCommandNew reads uid via RequestContextUtils; unsupported-op signatures updated  
**queue.MoveQueueManager**  
&nbsp;- processMove hydrates EsqContextHolder from the queued item, cleared in finally; calls  
&nbsp;   esquireCommandMove without rootPath / uid  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**06/08/2026** mir0n  v1.2.7 -- audit logging (c) over Kafka transport  
**audit.AuditConfig**  
&nbsp;- mode=bus + x-rod.bus.transport=kafka builds a RodKafkaPublisher over the autoconfigured KafkaTemplate  
&nbsp;   (key = entityId) -> AuditRod.buildBus; transport=activemq (default) keeps the queue path  
**src/main/resources/application.yml**  
&nbsp;- spring.kafka.bootstrap-servers + producer String key/value serializers + acks / batch-size /  
&nbsp;   compression-type / linger.ms tuning; audit-logging.x-rod.bus.transport added  
**pom.xml**  
&nbsp;- spring-kafka added  

**06/08/2026** mir0n  v1.2.7 -- audit logging (d): redis producer mode  
**audit.AuditConfig**  
&nbsp;- mode=redis builds a RodRedisPublisher over the injected StringRedisTemplate (ObjectProvider), wired  
&nbsp;   through buildBus / buildBusPool; reads x-rod.redis.stream / max-len  
**src/main/resources/application.yml**  
&nbsp;- audit-logging.x-rod.redis.{stream,max-len} + spring.data.redis.host/port +  
&nbsp;   management.health.redis.enabled=false added  
**pom.xml**  
&nbsp;- spring-boot-starter-data-redis added  

**06/06/2026** mir0n  v1.2.7 -- audit logging (c): mode-aware producer (bus) + async publisher pool + SQL externalized  
**audit.AuditConfig**  
&nbsp;- mode-aware: mode=bus builds RodEventBusPublisher + AuditRod.buildBus; publisher-pool-size>0 builds a  
&nbsp;   dedicated useAsyncSend connection + AuditRod.buildBusPool (CF closed in @PreDestroy); mode=in-process keeps (b)  
src/main/resources/META-INF/audit/postgres.xml  (new)  
src/main/resources/META-INF/audit/oracle.xml  (new)  
&nbsp;- the auth *_log statement keySmith writes, externalized out of code (loaded by common AuditLogSql)  
**src/main/resources/application.yml**  
&nbsp;- audit-logging.x-rod.mode + bus.publisher-pool-size added  

**06/05/2026** mir0n  v1.2.7 -- audit logging (b): auth UPDATE -> esq_auth_log  
audit.AuditConfig  (new)  
&nbsp;- reads keysmith.audit-logging.*; maps the access-profile kind to the AUTH statement; builds the x-Rod  
**service.impl.KeySmithService**  
&nbsp;- XYRod injected; auth UPDATE posts an esq_auth_log audit event (managed non-secret fields;  
&nbsp;   security question / answer excluded)  
**src/main/resources/application.yml**  
&nbsp;- keysmith.audit-logging.* block added  

**06/04/2026** mir0n  v1.2.7 -- uid / rootPath read from the unified request context (not method params)  
**service.IKeySmithService**  
&nbsp;- esquireKey / esquireKeySave: rootPath + uid params removed  
**service.impl.KeySmithService**  
&nbsp;- esquireKey / esquireKeySave read rootPath / uid via RequestContextUtils; passed to saveAccess  
**controller.KeySmithController**  
&nbsp;- stops extracting rootPath / uid from claims; delegates without them (roles still extracted)  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**06/08/2026** mir0n  v1.2.7 -- audit logging (c) over Kafka transport  
**audit.AuditConfig**  
&nbsp;- mode=bus + x-rod.bus.transport=kafka builds a RodKafkaPublisher over the autoconfigured KafkaTemplate  
&nbsp;   (key = entityId) -> AuditRod.buildBus; transport=activemq (default) keeps the queue path  
**src/main/resources/application.yml**  
&nbsp;- spring.kafka.bootstrap-servers + producer String key/value serializers + acks / batch-size /  
&nbsp;   compression-type / linger.ms tuning; audit-logging.x-rod.bus.transport added  
**pom.xml**  
&nbsp;- spring-kafka added  

**06/08/2026** mir0n  v1.2.7 -- audit logging (d): redis producer mode  
**audit.AuditConfig**  
&nbsp;- mode=redis builds a RodRedisPublisher over the injected StringRedisTemplate (ObjectProvider), wired  
&nbsp;   through buildBus / buildBusPool; reads x-rod.redis.stream / max-len  
**src/main/resources/application.yml**  
&nbsp;- audit-logging.x-rod.redis.{stream,max-len} + spring.data.redis.host/port +  
&nbsp;   management.health.redis.enabled=false added  
**pom.xml**  
&nbsp;- spring-boot-starter-data-redis added  

**06/06/2026** mir0n  v1.2.7 -- audit logging (c): mode-aware producer (bus) + async publisher pool + SQL externalized  
**audit.AuditConfig**  
&nbsp;- mode-aware: mode=bus builds RodEventBusPublisher + AuditRod.buildBus; publisher-pool-size>0 builds a  
&nbsp;   dedicated useAsyncSend connection + AuditRod.buildBusPool (CF closed in @PreDestroy); mode=in-process keeps (b)  
**messaging.PacManJmsConfig**  
&nbsp;- jmsQueueTemplate (pubSubDomain=false) bean added for the audit bus producer  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- @Qualifier("jmsTopicTemplate") on the ctor param (a 2nd JmsTemplate bean now exists -> disambiguate)  
src/main/resources/META-INF/audit/postgres.xml  (new)  
src/main/resources/META-INF/audit/oracle.xml  (new)  
&nbsp;- the account *_log statement pacMan writes, externalized out of code (loaded by common AuditLogSql)  
**src/main/resources/application.yml**  
&nbsp;- audit-logging.x-rod.mode + bus.publisher-pool-size added  

**06/05/2026** mir0n  v1.2.7 -- audit logging (b): account UPDATE / DELETE / balance -> esq_account_log  
audit.AuditConfig  (new)  
&nbsp;- reads pacman.audit-logging.*; maps the account kinds to the ACCOUNT statement; builds the x-Rod  
**service.impl.PacManService**  
&nbsp;- XYRod injected; saveAcct posts an account UPDATE, deleteAcct a DELETE audit event  
**acct.service.AcctTransactionService**  
&nbsp;- XYRod ctor param added + passed to the single / transfer processors  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- XYRod injected; balance change posts an account UPDATE (new balance + funded_dt mirror on first funding)  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- XYRod ctor param added + forwarded to super (both transfer legs audit the balance change)  
**src/main/resources/application.yml**  
&nbsp;- pacman.audit-logging.* block added  

**06/04/2026** mir0n  v1.2.7 -- uid / rootPath read from the unified request context (not method params)  
**service.IPacManService**  
&nbsp;- esquireCommand / Save / Delete: rootPath + uid params removed  
**service.impl.PacManService**  
&nbsp;- reads rootPath / uid via RequestContextUtils where needed; passed to saveAcct / deleteAcct  
**acct.service.AcctTransactionService**  
&nbsp;- esquireCommandAcct: rootPath + uid params removed; read via RequestContextUtils, passed to the  
&nbsp;   single / transfer processors (processor signatures unchanged)  
**controller.PacManController**  
&nbsp;- stops extracting rootPath / uid from claims; delegates without them (roles still extracted)  

**06/03/2026** mir0n  v1.2.7 -- account funded-date moved from DB trigger to the service  
src/main/resources/META-INF/postgres-acct.xml, oracle-acct.xml  
&nbsp;- updateAcctBalance: added acc_funded_dt = COALESCE(acc_funded_dt, CURRENT_TIMESTAMP|SYSDATE);  
&nbsp;   stamps the funded date on the first balance change and preserves it after -- replaces the  
&nbsp;   esq_account_briud trigger's funded-date logic so the audit triggers can be removed  

### xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt

Esquire xxRod Microservice  

**06/08/2026** mir0n  v1.2.7 -- audit logging (c) over Kafka transport  
messaging.RodKafkaConsumer  (new)  
&nbsp;- @KafkaListener on TOPIC_ROD_AUDIT (gated @ConditionalOnProperty xxrod.transport=kafka); decodes each  
&nbsp;   record value via RodEventCodec.fromJson -> director.accept; partition / offset in the msg log  
**messaging.RodAuditConsumer**  
&nbsp;- gated @ConditionalOnProperty xxrod.transport=activemq (matchIfMissing) -- the ActiveMQ intake  
**messaging.XxRodJmsConfig**  
&nbsp;- gated @ConditionalOnProperty xxrod.transport=activemq (matchIfMissing); not wired when transport=kafka  
**src/main/resources/application.yml**  
&nbsp;- spring.kafka.consumer (String deserializers, auto-offset-reset) + xxrod.transport + xxrod.kafka.group-id added  
**pom.xml**  
&nbsp;- spring-kafka added  

**06/06/2026** mir0n  v1.2.7 -- audit logging (c): standalone x-Rod audit consumer service  
XxRodApplication  (new)  
&nbsp;- @SpringBootApplication; an ApplicationStartingListener loads EsqObjectKindStorage before the context  
director.IRodDirector  (new)  
&nbsp;- the pluggable consumer-side strategy of the generic xRod host: type() (selection id), init(Environment)  
&nbsp;   (read own properties + wire the sink), accept(RodEvent), shutdown(); selected by xxrod.director.type  
director.AuditRodDirector  (new)  
&nbsp;- the first IRodDirector (self-configuring), gated by @ConditionalOnProperty xxrod.director.type=audit  
&nbsp;   (default); init() reads its own xxrod.director.audit.* (pool-size, virtual-threads) + the active vendor,  
&nbsp;   then builds the AuditLogWriter + AuditKinds registry + reused common.xrod.XXRod pool; accept() = submit;  
&nbsp;   shutdown() stops the pool  
director.RodDirectorHost  (new)  
&nbsp;- the generic, director-agnostic lifecycle: takes the selected IRodDirector, calls init() at startup and  
&nbsp;   shutdown() at stop (knows nothing about audit / replication / doc-DB specifics)  
messaging.RodAuditConsumer  (new)  
&nbsp;- @JmsListener on QUEUE_ROD_AUDIT; decodes via RodEventCodec.fromMessage -> director.accept; MDC from crl / req  
messaging.XxRodJmsConfig  (new)  
&nbsp;- @EnableJms; queue listener container factory (pubSubDomain=false, competing consumers), concurrency configurable  
src/main/resources/META-INF/audit/postgres.xml  (new)  
src/main/resources/META-INF/audit/oracle.xml  (new)  
&nbsp;- the FULL *_log statement set (the consumer writes every kind), loaded by common AuditLogSql  
src/main/resources/application.yml  (new)  
&nbsp;- dev-postgres / dev-oracle datasource + spring.activemq.broker-url + xxrod.director.type +  
&nbsp;   xxrod.director.audit.* + queue listener config  

---

## Commits

```

-- 2026-06-10 | commit: b760a63 | mir0n.the.programmer | v1.2.7 finalization --
M	.github/workflows/deploy-oke.yml
M	README.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.TestingStack.md
M	doc/release_notes.txt
M	pom.xml
 6 files changed, 56 insertions(+), 27 deletions(-)


-- 2026-06-10 | commit: 5fc72e2 | mir0n.the.programmer | prepare OKE Actions --
A	.github/scripts/deploy-oke.sh
A	.github/scripts/oke-build-push.sh
A	.github/workflows/deploy-oke.yml
M	README.md
M	doc/Esquire.GitHubActions.md
M	doc/model/ComponentModel.vsdx
 6 files changed, 451 insertions(+), 13 deletions(-)

-- 2026-06-10 | commit: 6bf65a2 | mir0n.the.programmer | v1.2.7-2606.0923  v1.2.7 -- audit logging: option (c) becomes the default deploy topology; docs consolidated --
M	README.md
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	compose/compose.yaml
M	doc/DatabaseDictionary.md
D	doc/Esquire.AuditLogging.Design.md
D	doc/Esquire.AuditLogging.md
A	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Vision.md
A	doc/img/audit-lifecycle.svg
A	doc/img/audit-pipeline.svg
A	doc/img/audit-seam.svg
A	doc/img/x-rod.7.svg
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
A	k8s/charts/esquire-xxrod/Chart.yaml
A	k8s/charts/esquire-xxrod/templates/configmap.yaml
A	k8s/charts/esquire-xxrod/templates/deployment.yaml
A	k8s/charts/esquire-xxrod/templates/secret.yaml
A	k8s/charts/esquire-xxrod/values.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
A	k8s/values/xxrod.yaml
M	postgres/Dockerfile
 43 files changed, 1174 insertions(+), 1239 deletions(-)

-- 2026-06-09 | commit: cdbe08a | mir0n.the.programmer | preparing git actions --
A	.github/scripts/ci.sh
A	.github/scripts/deploy-local.cmd
A	.github/workflows/ci.yml
A	.github/workflows/deploy-local.yml
A	doc/Esquire.GitHubActions.md
M	keySmith/pom.xml
M	keySmith/src/main/resources/application.yml
M	pacMan/pom.xml
M	pacMan/src/main/resources/application.yml
M	xxRod/pom.xml
 10 files changed, 492 insertions(+)

-- 2026-06-08 | commit: da7f249 | mir0n.the.programmer | v1.2.7-2606.0822  v1.2.7 -- audit logging (option c-k, d-k): Kafka as the bus transport --
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/AuditRod.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventCodec.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodKafkaPublisher.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	compose/compose.yaml
A	compose/kafka-connect/redis-audit-sink.json
M	doc/Esquire.AuditLogging.Design.md
M	doc/Esquire.AuditLogging.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
A	xxRod/logs/xxRod-develop.log
A	xxRod/logs/xxRod-develop.log.2026-06-06.gz
A	xxRod/logs/xxRod-msg.log
A	xxRod/logs/xxRod-msg.log.2026-06-06.gz
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumer.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodKafkaConsumer.java
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodJmsConfig.java
M	xxRod/src/main/resources/application.yml
 29 files changed, 619 insertions(+), 14 deletions(-)

-- 2026-06-08 | commit: 11c5476 | mir0n.the.programmer | v1.2.7-2606.0820  v1.2.7 -- audit logging (option d): events streamed straight to Redis --
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/AuditRod.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodRedisPublisher.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/common/audit/RodRedisPublisherTest.java
M	compose/compose.yaml
M	doc/Esquire.AuditLogging.Design.md
M	doc/Esquire.AuditLogging.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
R078	enyMan/src/main/resources/META-INF/oracle-rodlog.xml	enyMan/src/main/resources/META-INF/audit/oracle-par.xml
A	enyMan/src/main/resources/META-INF/audit/oracle.xml
R078	enyMan/src/main/resources/META-INF/postgres-rodlog.xml	enyMan/src/main/resources/META-INF/audit/postgres-par.xml
A	enyMan/src/main/resources/META-INF/audit/postgres.xml
M	enyMan/src/main/resources/application.yml
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
 27 files changed, 810 insertions(+), 56 deletions(-)

-- 2026-06-07 | commit: 7ea77e0 | mir0n.the.programmer | v1.2.7-2606.0622  v1.2.7 -- audit logging (option c): distributed bus path + standalone xxRod consumer --
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditKinds.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogSql.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogWriter.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/AuditRod.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventBusPublisher.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventCodec.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/java/pro/mir0n/esquire/common/xrod/XXRod.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/AuditKindsTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/AuditRodBusTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/RodEventCodecTest.java
A	common/src/test/resources/META-INF/audit/oracle.xml
A	common/src/test/resources/META-INF/audit/postgres.xml
M	compose/compose-rebuild.bat
M	compose/compose.yaml
M	doc/Esquire.AuditLogging.Design.md
M	doc/Esquire.AuditLogging.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
A	keySmith/src/main/resources/META-INF/audit/oracle.xml
A	keySmith/src/main/resources/META-INF/audit/postgres.xml
M	keySmith/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/PacManJmsConfig.java
A	pacMan/src/main/resources/META-INF/audit/oracle.xml
A	pacMan/src/main/resources/META-INF/audit/postgres.xml
M	pacMan/src/main/resources/application.yml
M	pom.xml
A	xxRod/Dockerfile
A	xxRod/compose.yaml
A	xxRod/docker-compose-build.bat
A	xxRod/pom.xml
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/XxRodApplication.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/AuditRodDirector.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/IRodDirector.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/RodDirectorHost.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumer.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodJmsConfig.java
A	xxRod/src/main/resources/META-INF/audit/oracle.xml
A	xxRod/src/main/resources/META-INF/audit/postgres.xml
A	xxRod/src/main/resources/application.yml
A	xxRod/src/main/resources/logback-spring.xml
A	xxRod/src/test/java/pro/mir0n/esquire/xxRod/RodBusIntegrationTest.java
A	xxRod/src/test/java/pro/mir0n/esquire/xxRod/director/AuditRodDirectorTest.java
A	xxRod/src/test/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumerTest.java
A	xxRod/src/test/resources/it-account-log.sql
 55 files changed, 2595 insertions(+), 226 deletions(-)

-- 2026-06-06 | commit: 5ced439 | mir0n.the.programmer | v1.2.7 -- audit logging (option b): entity changes recorded to the *_log tables --
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/IMappable.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqAuthJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAddressJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqParRow.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqPersonJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogSql.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogWriter.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditRod.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditSettings.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/main/java/pro/mir0n/esquire/common/xrod/IRodRepository.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/RodEvent.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistry.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/XXRod.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/XYRod.java
A	common/src/test/java/pro/mir0n/esquire/backend/jpa/EntityFillMapTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/AuditLogSqlTest.java
A	common/src/test/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistryTest.java
A	common/src/test/java/pro/mir0n/esquire/common/xrod/XXRodTest.java
A	common/src/test/java/pro/mir0n/esquire/common/xrod/XYRodTest.java
M	compose/compose.yaml
A	doc/Esquire.AuditLogging.Design.md
M	doc/Esquire.AuditLogging.md
M	doc/Object.Kind.enum.md
M	doc/release_notes.txt
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
A	enyMan/src/main/resources/META-INF/oracle-rodlog.xml
A	enyMan/src/main/resources/META-INF/postgres-rodlog.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 62 files changed, 2847 insertions(+), 44 deletions(-)

-- 2026-06-04 | commit: 9bb2a42 | mir0n.the.programmer | v1.2.7 -- unified per-request context: uid / rootPath obtained like crl_id / req_id --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilterTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/RequestContextUtilsTest.java
M	doc/Esquire.AuditLogging.md
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 41 files changed, 892 insertions(+), 349 deletions(-)

-- 2026-06-03 | commit: 4fc5823 | mir0n.the.programmer |  v1.2.7 -- audit-trigger decoupling: account funded-date moved to the service --
M	doc/release_notes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
 4 files changed, 12 insertions(+)

-- 2026-06-03 | commit: 1c72215 | mir0n.the.programmer | Update Esquire.AuditLogging.md --
M	doc/Esquire.AuditLogging.md
 1 file changed, 44 insertions(+), 40 deletions(-)

-- 2026-06-03 | commit: 12e184c | mir0n.the.programmer | v1.2.7 announced --
M	README.md
A	doc/Esquire.AuditLogging.md
 2 files changed, 366 insertions(+), 1 deletion(-)

-- 2026-06-02 | commit: 35dce97 | mir0n.the.programmer | Create report_v1.2.6.md --
A	doc/reports/report_v1.2.6.md
 1 file changed, 523 insertions(+)
```

---

## Files Modified

```
A	.github/scripts/ci.sh
A	.github/scripts/deploy-local.cmd
A	.github/scripts/deploy-oke.sh
A	.github/scripts/oke-build-push.sh
A	.github/workflows/ci.yml
A	.github/workflows/deploy-local.yml
A	.github/workflows/deploy-oke.yml
M	README.md
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/IMappable.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqAuthJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAddressJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqParRow.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqPersonJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditKinds.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogSql.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogWriter.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditRod.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditSettings.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventBusPublisher.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventCodec.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodKafkaPublisher.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/RodRedisPublisher.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/main/java/pro/mir0n/esquire/common/xrod/IRodRepository.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/RodEvent.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistry.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/XXRod.java
A	common/src/main/java/pro/mir0n/esquire/common/xrod/XYRod.java
A	common/src/test/java/pro/mir0n/esquire/backend/jpa/EntityFillMapTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilterTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/RequestContextUtilsTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/AuditKindsTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/AuditLogSqlTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/AuditRodBusTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/RodEventCodecTest.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/RodRedisPublisherTest.java
A	common/src/test/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistryTest.java
A	common/src/test/java/pro/mir0n/esquire/common/xrod/XXRodTest.java
A	common/src/test/java/pro/mir0n/esquire/common/xrod/XYRodTest.java
A	common/src/test/resources/META-INF/audit/oracle.xml
A	common/src/test/resources/META-INF/audit/postgres.xml
M	compose/compose-rebuild.bat
M	compose/compose.yaml
A	compose/kafka-connect/redis-audit-sink.json
M	doc/DatabaseDictionary.md
A	doc/Esquire.AuditLoggingStack.md
A	doc/Esquire.GitHubActions.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/Object.Kind.enum.md
A	doc/img/audit-lifecycle.svg
A	doc/img/audit-pipeline.svg
A	doc/img/audit-seam.svg
A	doc/img/x-rod.7.svg
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.6.md
M	doc/services.configuring.md
M	enyMan/pom.xml
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
A	enyMan/src/main/resources/META-INF/audit/oracle-par.xml
A	enyMan/src/main/resources/META-INF/audit/oracle.xml
A	enyMan/src/main/resources/META-INF/audit/postgres-par.xml
A	enyMan/src/main/resources/META-INF/audit/postgres.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
A	k8s/charts/esquire-xxrod/Chart.yaml
A	k8s/charts/esquire-xxrod/templates/configmap.yaml
A	k8s/charts/esquire-xxrod/templates/deployment.yaml
A	k8s/charts/esquire-xxrod/templates/secret.yaml
A	k8s/charts/esquire-xxrod/values.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
A	k8s/values/xxrod.yaml
M	keySmith/pom.xml
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
A	keySmith/src/main/resources/META-INF/audit/oracle.xml
A	keySmith/src/main/resources/META-INF/audit/postgres.xml
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/PacManJmsConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
A	pacMan/src/main/resources/META-INF/audit/oracle.xml
A	pacMan/src/main/resources/META-INF/audit/postgres.xml
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	postgres/Dockerfile
A	xxRod/Dockerfile
A	xxRod/compose.yaml
A	xxRod/docker-compose-build.bat
A	xxRod/logs/xxRod-develop.log
A	xxRod/logs/xxRod-develop.log.2026-06-06.gz
A	xxRod/logs/xxRod-msg.log
A	xxRod/logs/xxRod-msg.log.2026-06-06.gz
A	xxRod/pom.xml
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/XxRodApplication.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/AuditRodDirector.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/IRodDirector.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/RodDirectorHost.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumer.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodKafkaConsumer.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodJmsConfig.java
A	xxRod/src/main/resources/META-INF/audit/oracle.xml
A	xxRod/src/main/resources/META-INF/audit/postgres.xml
A	xxRod/src/main/resources/application.yml
A	xxRod/src/main/resources/logback-spring.xml
A	xxRod/src/test/java/pro/mir0n/esquire/xxRod/RodBusIntegrationTest.java
A	xxRod/src/test/java/pro/mir0n/esquire/xxRod/director/AuditRodDirectorTest.java
A	xxRod/src/test/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumerTest.java
A	xxRod/src/test/resources/it-account-log.sql
 183 files changed, 9347 insertions(+), 475 deletions(-)
```

---

*From `v1.2.6` till `v1.2.7`*

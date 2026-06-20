# Release Report: v1.2.7 → v1.2.8

**Repo:** `esquire.services/develop`  
**Top commit:** `6faf19f`

---

## Release Notes

### doc/release_notes.txt


**v1.2.8-2606.1917**  v1.2.8 -- finalization: plural bus-config keys, host-Postgres dev stack, documentation  
&nbsp;: Refactoring: the messaging-bus list-config keys are now plural -- a bus's legs are `slots` (was `slot`) and  
&nbsp;                 a request/response leg's network nodes are `nodes` (was `node`)  
&nbsp;: Config:      the shared topology (compose + k8s) and the three producers  
&nbsp;: Config:      docker compose no longer runs its own Postgres container;  
&nbsp;                 the bundled Postgres container is used only by the Kubernetes setup  
&nbsp;: Config:      the Kubernetes Postgres (local and OKE) now accepts up to 200 connections (was 100)  
&nbsp;: Config:      the k8s rebuild script now builds the Postgres image itself (docker compose no longer carries it)  
&nbsp;: Doc:         doc\DatabaseDictionary.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\Esquire.BizTree.md  
&nbsp;                 doc\Esquire.GitHubActions.md  
&nbsp;                 doc\Esquire.Haubergeon.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.TestingStack.md  
&nbsp;                 doc\Esquire.Vision.md  
&nbsp;                 doc\Logging.md  
&nbsp;                 doc\Message.Structure.md  
&nbsp;                 doc\Messaging.md  
&nbsp;                 doc\Object.Kind.enum.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;   Components:   messaging,  
&nbsp;                 enyman,  
&nbsp;                 pacman,  
&nbsp;                 keysmith,  
&nbsp;                 compose,  
&nbsp;                 k8s,  
&nbsp;                 oke  

**v1.2.8-2606.1822**  v1.2.8 -- Messaging Bus: a reusable "keep" engine, split out of the audit code  
&nbsp;: Refactoring: the part that saves incoming changes into a database is now a general-purpose engine of its  
&nbsp;                 own (esquire-dataKeep); audit is just one small set of rules on top of it (esquire-audit), and  
&nbsp;                 that audit code no longer sits inside the shared common library  
&nbsp;: Refactoring: the standalone audit-writer service was renamed from xxRod to auKeep and rebuilt on that engine  
&nbsp;: Refactoring: the messaging bus framework is now its own module (esquire-messaging) instead of living inside  
&nbsp;                 the shared common library, so a service that does not use the bus no longer pulls it in  
&nbsp;: Feature:     which database the writer targets is now chosen by name, so supporting another database is just  
&nbsp;                 dropping in its SQL file -- no code change (today: Postgres and Oracle)  
&nbsp;: Feature:     the in-process audit writer can either SHARE the service's own database connection pool or use  
&nbsp;                 its OWN dedicated pool (chosen per service) -- fewer connections vs isolation from the business work  
&nbsp;: Config:      each service's own audit setup now lives under its own name (e.g. enyman.messaging-bus) rather  
&nbsp;                 than a shared-looking key; the writer reads its database from its own settings group  
&nbsp;: Config:      the local services build script (build.services.bat) now builds the whole project at once  
&nbsp;                 instead of just one library, so a new module (e.g. esquire-messaging) is picked up automatically  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 dataKeep,  
&nbsp;                 audit,  
&nbsp;                 auKeep,  
&nbsp;                 enyMan,  
&nbsp;                 keySmith,  
&nbsp;                 pacMan  
&nbsp;   Doc:          doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Messaging.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\services.configuring.md  

**v1.2.8-2606.1720**  v1.2.8 -- Messaging Bus: code-review cleanup and hardening  
&nbsp;: Refactoring: the audit producer's transaction and commit handling moved out of the messaging core into  
&nbsp;                 its own bridge; the messaging module is now a plain send / receive relay  
&nbsp;: Refactoring: the shared send / receive engine (the queued feed plus the worker pool) lifted into one  
&nbsp;                 base that every messaging module extends, instead of wrapping a copy  
&nbsp;: Refactoring: a request / response bus reads its two stops from a typed model instead of raw config keys  
&nbsp;: Fix:         a setting that points at the instance identity (e.g. a client id) now resolves the same way  
&nbsp;                 for a single-destination bus and a request / response bus  
&nbsp;: Fix:         a misconfigured bus leg is reported at startup instead of failing quietly later; a duplicate  
&nbsp;                 leg in the catalog is warned about instead of silently taking the last  
&nbsp;: Fix:         on shutdown a service waits for in-flight audit sends to finish and ignores a late incoming  
&nbsp;                 message instead of erroring  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 keySmith,  
&nbsp;                 pacMan,  
&nbsp;                 xxRod,  
&nbsp;                 tp-activemq,  
&nbsp;                 tp-kafka,  
&nbsp;                 tp-redis  
&nbsp;   Doc:          doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\service.configuring  

**v1.2.8-2606.1617**  v1.2.8 -- Draft of Messaging Bus Concept implementation  
&nbsp;: Feature:     pluggable transport layer; uniform x-Rod messaging frontend  
&nbsp;: Refactoring: all messaging is unified behind one x-Rod frontend -- a shared catalog of buses and  
&nbsp;                 legs driven by an x-Rod manager  
&nbsp;: Refactoring: each running instance takes a stable per-instance identity from its host name  
&nbsp;: Feature:     transport vendor agnostic transport layer  
&nbsp;: Feature:     request and response are modeled as two stops on one bus, routed by role (client, server  
&nbsp;                 or broadcast); the audit destination is pluggable by name  
&nbsp;: Config:      the bus catalog lives in one shared topology file  
&nbsp;: Config:      the service configuration is the single source of truth  
&nbsp;: Config:      the build registers the three transport-provider modules  
&nbsp;: Config:      the instance number is taken from the pod / container name ordinal alone  
&nbsp;   Components:   common,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 kcMaster,  
&nbsp;                 keySmith,  
&nbsp;                 pacMan,  
&nbsp;                 xxRod,  
&nbsp;                 tp-activemq,  
&nbsp;                 tp-kafka,  
&nbsp;                 tp-redis,  
&nbsp;                 compose,  
&nbsp;                 k8s,  
&nbsp;                 build/version  
&nbsp;   Doc:          doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\Esquire.BizTree.md  
&nbsp;                 doc\Esquire.MessagingBus.md (new)  
&nbsp;                 doc\Esquire.TestingStack.md  
&nbsp;                 doc\Logging.md  
&nbsp;                 doc\Message.Structure.md  
&nbsp;                 doc\Messaging.md  
&nbsp;                 doc\services.configuring.md  

**v1.2.8-2606.1216**  v1.2.8 -- system entity flag (anti-deletion); admin-create and system-login fixes  
&nbsp;: Feature:     system entity flag -- offices and users flagged in the seed are protected from deletion;  
&nbsp;                 enyMan returns HTTP 409 on any attempt to delete one  
&nbsp;: Fix:         creating a new admin succeeds again (new-user deleted flag defaulted; person address FK nullable)  
&nbsp;: Fix:         the "system" KeyCloak user can log in -- password seeded in the realm import  
&nbsp;: Doc:         data dictionary documents the ORG_SYSTEM_FLG / USR_SYSTEM_FLG columns  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 keycloak,  
&nbsp;                 doc  

---

## Code Changes

### auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt

Esquire auKeep service  

**06/18/2026** mir0n  v1.2.8 -- auKeep: renamed from xxRod; the audit consumer rebuilt on the generic keep engine  
AuKeepApplication  (was XxRodApplication)  
&nbsp;- the only Boot app (image esquire.aukeep); scans dataKeep + audit + auKeep; excludes  
&nbsp;   DataSourceAutoConfiguration; loads EsqObjectKindStorage on ApplicationStartingEvent  
messaging.AuditConsumerConfig  (was XxRodAuditConsumerConfig)  
&nbsp;- builds the generic keep applier (the audit director's kinds + SQL, applied to the keep datasource group  
&nbsp;   esquire.keep.datasource) and runs it behind the bus consumer rods.consumer opens on the audit leg  
director.AuditRodDirector / RodDirectorHost / IRodDirector  (removed)  
&nbsp;- dissolved into the generic keep (esquire-dataKeep) plus the audit director (esquire-audit.AuditKeepDirector)  
**application.yml**  
&nbsp;- the keep datasource moved to its own group esquire.keep.datasource (db + pool); spring.application.name aukeep  
**pom.xml**  
&nbsp;- esquire-auKeep: depends on esquire-audit (brings dataKeep + the tp-* providers + the *_log SQL transitively)  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus cleanup: audit director config key + consumer call  
**director.AuditRodDirector**  
&nbsp;- init() reads the audit leg via the key built from EsqMsgConstants.BUS_KEY_AUDIT  
&nbsp;   (esquire.audit-bus.messaging-bus.*) -- was the wrong esquire.audit.messaging-bus.* (lookup blank ->  
&nbsp;   silent default pool); accept() calls rod.receive() (was rod.submit())  
**messaging.XxRodAuditConsumerConfig**  
&nbsp;- consumeLeg(busId, slotId, objectMapper) -- the Role.BROADCAST argument dropped (consumeLeg no longer  
&nbsp;   builds a selector)  

**06/15/2026** mir0n  v1.2.8 -- audit consumer migrated onto the x-Rod bus catalog  
messaging.XxRodAuditConsumerConfig  (new)  
&nbsp;- @Configuration with a rodAuditConsumer @Bean (destroyMethod=close); names the audit leg by  
&nbsp;   {bus-id, slot-id} (esquire.audit-bus.messaging-bus) into MessagingBusCatalog, opens the leg's  
&nbsp;   consumer (consumeLeg -> provider.openConsumer) feeding director::accept via RodTransportAdapter;  
&nbsp;   no bus reference or provider.supportsConsume()=false -> returns a no-op (stay idle)  
**director.AuditRodDirector**  
&nbsp;- imports moved common.xrod -> messaging.xrod (RodEvent / RodEventRepoRegistry / IXRod / XRods);  
&nbsp;   pool-size / rod-class / virtual-threads now resolved from the audit leg via  
&nbsp;   MessagingBusCatalog.resolve(bus-id, slot-id), defaulting when no leg; XXRod pool replaced by  
&nbsp;   IXRod rod = XRods.resolve(rodClass) started with registry.applier(devLog)  
**director.IRodDirector**  
&nbsp;- RodEvent import moved common.xrod -> messaging.xrod  
messaging.RodAuditConsumer  (removed)  
&nbsp;- @JmsListener intake folded onto the bus catalog (now opened by XxRodAuditConsumerConfig)  
messaging.RodKafkaConsumer  (removed)  
&nbsp;- @KafkaListener intake folded onto the bus catalog (now opened by XxRodAuditConsumerConfig)  
messaging.XxRodJmsConfig  (removed)  
&nbsp;- @EnableJms listener container factory dropped; the catalog provider supplies the consumer  
**pom.xml**  
&nbsp;- direct transport starters (spring-boot-starter-activemq, spring-kafka) dropped; added the  
&nbsp;   esquire-tp-activemq / esquire-tp-kafka / esquire-tp-redis provider modules (${tp.version}),  
&nbsp;   each bringing its transport client transitively  
**application.yml**  
&nbsp;- spring.activemq / spring.kafka.consumer + xxrod.transport / xxrod.kafka / xxrod.director.audit.*  
&nbsp;   / xxrod.messaging blocks removed; added spring.config.import of the shared topology.yml and the  
&nbsp;   esquire.audit-bus.messaging-bus reference (bus-id ${ESQUIRE_AUDIT_BUS_ID:audit-c},  
&nbsp;   slot-id ${ESQUIRE_AUDIT_SERVICE_ID:audit})  

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

### audit/src/main/java/pro/mir0n/esquire/audit/changes.txt

Esquire audit library  

**06/18/2026** mir0n  v1.2.8 -- created: esquire-audit, the audit specialization on the generic keep (left common)  
AuditBusBridge  (new, was common.audit.AuditBusBridge)  
&nbsp;- bridges the audit flows onto the messaging bus: post() buffers each change in the caller's transaction;  
&nbsp;   after commit it stamps ONE actionTime, snapshots the request context, builds the RodEvent and transmit()s it  
AuditKeepDirector  (new)  
&nbsp;- the single IKeepDirector for audit: declares only the SQL group ("audit") + the kinds it handles; used by  
&nbsp;   both an in-process producer keep and the auKeep consumer  
AuditKinds  (new)  
&nbsp;- the audit kind -> SQL-statement-key map (the *_log statement keys); entity kinds from the esq-object-kinds  
&nbsp;   dictionary by semantic flag, sub-entity / parameter / auth kinds named  
META-INF/audit/postgres.xml, oracle.xml  (new, moved from the consumer)  
&nbsp;- the audit *_log SQL data (the FULL statement set the keep loads through KeepSqlStore)  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**06/15/2026** mir0n  v1.2.8 -- entity-broadcast bus folded onto the x-Rod catalog  
messaging.BizTreeBroadcastConsumer  (new)  
&nbsp;- @Component (conditional on biztree.entity-broadcast-bus.consumer.enabled) opening the entity-broadcast  
&nbsp;   receive x-Rod via XRodManager.consumer(BUS_KEY_ENTITY, Role.BROADCAST, this::onRodEvent); app-level  
&nbsp;   console log per event, then director.onRodEvent(e). Replaces BizTreeBroadcastConfig.  
messaging.BizTreeJmsConfig  (removed)  
&nbsp;- JMS connection-factory / listener-container config retired; the bus transport is owned by the shared  
&nbsp;   XRodManager  
messaging.EsqEntityBroadcastConsumer  (removed)  
&nbsp;- JMS @JmsListener entity-broadcast consumer retired; folded into messaging.BizTreeBroadcastConsumer  
**access.IBizTreeDirector**  
&nbsp;- added default onRodEvent(RodEvent): unpacks the RodEvent onto the generic onEntityBroadcast intake  
&nbsp;   (body already parsed)  
**access.legacy.BizTreeDirectorLegacy**  
&nbsp;- onEntityBroadcast now takes the already-parsed Map body (was messageEncoding + raw text);  
&nbsp;   valueToTree(body) instead of readTree(text), no inline parse / try-catch  
**taijitu.Monad**  
&nbsp;- message branch applies the already-parsed item.body() via valueToTree; removed the private  
&nbsp;   parse(QueueItem) helper that did readTree(item.text())  
**pom.xml**  
&nbsp;- added esquire-tp-activemq dependency (shared JMS helper for the entity-broadcast bus); removed the  
&nbsp;   spring-boot-starter-activemq starter  
**application.yml**  
&nbsp;- added spring.config.import of the shared esquire-topology.yml; added the esquire.entity-bus.messaging-bus  
&nbsp;   bus REF (bus-id / slot-id / x-rod pool-size + concurrency) and biztree.entity-broadcast-bus.consumer.enabled;  
&nbsp;   removed the spring.activemq broker-url / user / password block  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**06/12/2026** mir0n  v1.2.8 -- system entity flag (anti-deletion)  
**jpa.EsqEntityJpa**  
&nbsp;- systemFlg field added (String); not emitted by fillMap()  
**validator.ValidatorFactory**  
&nbsp;- validateDelete(): throws DeleteRestrictedException when origin systemFlg='Y', ahead of the biz-validator chain  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**06/18/2026** mir0n  v1.2.8 -- messaging framework extracted to esquire-messaging; common freed of it  
messaging.* (removed)  
&nbsp;- the pro.mir0n.esquire.messaging package moved out to the new esquire-messaging module; common no longer  
&nbsp;   carries the bus framework (common does NOT depend on messaging, so the edge is one-directional)  
**pom.xml**  
&nbsp;- dropped the messaging-only deps: spring-boot (the catalog Binder), spring-boot-autoconfigure (the  
&nbsp;   XRodManager @AutoConfiguration), and the dead HikariCP (was for the dissolved common.audit.AuditRod)  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus code-review cleanup: audit bridge, x-rod engine base, typed nodes, fail-fast  
audit.AuditBusBridge  (new)  
&nbsp;- the audit producer onto the bus: post(op,kind,id,sub[,IMappable|Map]) buffers each change in the active  
&nbsp;   transaction and, after commit, stamps one actionTime + snapshots the request context, builds the RodEvent  
&nbsp;   (msg-type MSG_TYPE_AUDIT) and transmit()s it on the audit x-rod; no active transaction = transmit at once.  
&nbsp;   isEnabled() = the audit x-rod is not XRodDisabled. The post / commit machinery lifted OUT of XRod.  
messaging.xrod.impl.AXRod  (new)  
&nbsp;- abstract x-Rod transceiver ENGINE: the feed (BoundedQueueRig transmit leg) + the Semaphore-bounded reused  
&nbsp;   worker pool (receive leg) + msg-audit + lifecycle. startEngine(name,devLog,outbound,worker) wires the legs;  
&nbsp;   shutdown() stops the pool then awaitTermination(5s) (shutdownNow on timeout) so in-flight tasks drain;  
&nbsp;   receive() drops (logs) a late delivery during shutdown instead of throwing; require() validation helper.  
messaging.BusNode  (new)  
&nbsp;- record(nodeId, destination, topic, params): one R&R network node (transport.node[*]); provider / endpoint  
&nbsp;   stay base-owned.  
messaging.transport.TransportPublisher  (new)  
&nbsp;- interface extends Consumer, AutoCloseable; of(sink, closer) factory. The publish handle  
&nbsp;   ITransportProvider.openPublisher returns -- close() releases the provider's own broker connection.  
messaging.xrod.RodPublisher  (new)  
&nbsp;- interface extends Consumer, AutoCloseable; of(dispatch, closer). The closeable transmit-leg  
&nbsp;   outbound RodTransportAdapter.publisher returns.  
**messaging.xrod.IXRod**  
&nbsp;- post() declarations removed (the producer's transactional post lives in AuditBusBridge now); submit() ->  
&nbsp;   receive(); usesOutboundTransport() and bindInbound() removed; isEnabled() is now a default true;  
&nbsp;   validate(XRodParams) default-no-op added (the fail-fast hook)  
**messaging.xrod.impl.XRod**  
&nbsp;- extends AXRod (engine lifted out); keeps the transport (publisher / openConsumer / legTransport /  
&nbsp;   consumeSelector); start() resolves the leg + decides producer / consumer / in-process and calls startEngine();  
&nbsp;   shutdown() closes the inbound consumer, drains via super, then closes the outbound publisher; validate()  
&nbsp;   requires a declared transport's provider + endpoint + destination  
**messaging.xrod.impl.XRodRR**  
&nbsp;- legTransport() rewritten to a typed node model: select the request / response BusNode by id and refine the  
&nbsp;   base wire via BusTransport.refinedWith(); the flattened-key surgery + nodePrefix() removed; validate()  
&nbsp;   requires provider + endpoint and either the R&R nodes (with destinations) or a base destination  
**messaging.xrod.impl.XRodInfo**  
&nbsp;- dropped the composed inner XRod -- log-only directly: transmit() / receive() log the event line, no feed /  
&nbsp;   pool / transport; usesOutboundTransport() / bindInbound() removed  
**messaging.xrod.impl.XRodDisabled**  
&nbsp;- usesOutboundTransport() / bindInbound() removed; isEnabled() overrides false (the only x-rod that is off)  
**messaging.xrod.XRods**  
&nbsp;- the class declaration put on one line (formatting)  
**messaging.xrod.XRodManager**  
&nbsp;- configureXRod() calls rod.validate(eff) before configure / start (fail-fast on the leg's required params)  
**messaging.XRodParams**  
&nbsp;- transport() carries transport.params.* VERBATIM (token expansion removed from here); nodes() (new) parses  
&nbsp;   transport.node[*] into a typed List (the one place reading the flattened node keys);  
&nbsp;   expandIdentityTokens() removed  
**audit.XRodLogDb**  
&nbsp;- extends AXRod (was composing an inner XRod); validate() requires x-rod.log-db.url (moved out of configure);  
&nbsp;   the Hikari pool default renamed DEFAULT_DB_POOL (distinct from the engine worker pool)  
**messaging.xrod.RodEvent**  
&nbsp;- the 10-arg constructor (rodId, no msgType) removed (unused); javadoc {@link RodRepository} -> IRodEventRepo /  
&nbsp;   RodEventRepoRegistry; the msg-type list RDA -> UA  
**messaging.xrod.RodTransportAdapter**  
&nbsp;- publisher() returns a RodPublisher (closeable) instead of a bare Consumer  
**messaging.BusTransport**  
&nbsp;- refinedWith(BusNode) (new): the base wire refined with a node (node owns destination / topic / params; the  
&nbsp;   base owns provider / endpoint)  
**messaging.transport.BusIdentity**  
&nbsp;- expandTokens(Map) (new): resolves ${rod-id} / ${bus-id} / ${slot-id} in vendor params against this identity  
**messaging.transport.TransportSettings**  
&nbsp;- the constructor resolves the identity tokens in params (identity.expandTokens) -- one driver-facing point;  
&nbsp;   the clientId field / getter removed  
messaging.transport.PublishSettings / ConsumeSettings  
&nbsp;- the clientId constructor parameter removed  
**messaging.transport.ITransportProvider**  
&nbsp;- openPublisher returns TransportPublisher (closeable) instead of Consumer  
**messaging.transport.TransportProviders**  
&nbsp;- paramKey() removed (a vestige of the old per-provider param-group design)  
**messaging.MessagingBus**  
&nbsp;- the record component slot -> slots (a List); @Name("slot") keeps the config key `slot`  
**messaging.MessagingBusCatalog**  
&nbsp;- consumeLeg() drops the Role parameter + selector logic -- a whole-node consume (selector null; a message  
&nbsp;   selector is the x-rod's concern, XRodRR); find() warns on a duplicate (bus-id, slot-id) instead of silently  
&nbsp;   taking the last  
EsqMsgConstants  
&nbsp;- TOPIC_ENTITY_BROADCAST and ROD_AUDIT removed (dead destination constants; destinations are config / topology  
&nbsp;   values now); class javadoc refreshed to the FIX-JSON shared-envelope description  

**06/15/2026** mir0n  v1.2.8 -- audit stack + JMS messaging folded onto the transport-agnostic x-Rod layer  
audit.XRodLogDb  (new)  
&nbsp;- the in-process log-DB audit pod; a pluggable IXRod resolved by x-rod.rod-class. configure() reads its  
&nbsp;   OWN x-rod.log-db sub-block (XRodLogDbParams), builds + owns a Hikari pool (autoCommit), and binds the  
&nbsp;   kind->RodEventRepo registry from AuditKinds + AuditLogWriter; composes the default XRod transceiver  
&nbsp;   (feed + receive pool + msg-audit) with no codec (in-process, usesOutboundTransport()=false)  
audit.XRodLogDbParams  (new)  
&nbsp;- record(vendor, url, username, password, poolSize) bound from x-rod.custom; vendorOr / poolSizeOr helpers  
**audit.AuditLogWriter**  
&nbsp;- RodEvent import retargeted common.xrod -> messaging.xrod  
EsqMsgConstants  
&nbsp;- QUEUE_/TOPIC_/STREAM_ROD_AUDIT collapsed to the one logical ROD_AUDIT destination  
&nbsp;- FIELD_SERVICE_ID -> FIELD_SLOT_ID (SlotID); FIELD_CTRL_ID -> FIELD_ROD_ID (RodID)  
&nbsp;- MSG_TYPE_ROD_AUDIT -> MSG_TYPE_AUDIT ("UA")  
&nbsp;- hardcoded bus/slot value constants removed (BUS_ID_ROD, BUS_ID_ENTITY, SERVICE_ID_ROD_AUDIT,  
&nbsp;   SERVICE_ID_ENTITY_BROADCAST); replaced by logical bus KEYS BUS_KEY_AUDIT / BUS_KEY_KC / BUS_KEY_ENTITY  
&nbsp;   (the bus-id / slot-id VALUES are now config/topology, not constants)  
audit.AuditRod  (removed)  
&nbsp;- folded into the new messaging x-Rod layer  
audit.AuditSettings  (removed)  
&nbsp;- folded into the new messaging x-Rod layer  
audit.RodEventBusPublisher  (removed)  
&nbsp;- folded into the new messaging x-Rod layer  
audit.RodEventCodec  (removed)  
&nbsp;- folded into the new messaging x-Rod layer  
audit.RodKafkaPublisher  (removed)  
&nbsp;- folded into the new messaging x-Rod layer  
audit.RodRedisPublisher  (removed)  
&nbsp;- folded into the new messaging x-Rod layer  
**pom.xml**  
&nbsp;- transport-specific deps removed: jakarta.jms-api, spring-jms, spring-data-redis, spring-kafka  
&nbsp;- added spring-boot + spring-boot-autoconfigure (provided) for the messaging-bus catalog binder and the  
&nbsp;   shared XRodManager @AutoConfiguration; spring-test (test) for the catalog binding test  
EsqUtils  
&nbsp;- instanceNo() resolves from the host-name trailing ordinal only -- parsePodNameOrdinal(instanceHost());  
&nbsp;   the POD_INDEX / ESQUIRE_INSTANCE_NO env and esquire.instance.no sysprop sources removed  
&nbsp;- instanceHost() (new): HOSTNAME -> POD_NAME -> local hostname -- the one instance-identity source  
&nbsp;- setInstanceNoForTests(int) (new): test seam pinning the cached number; firstNonBlank() made public  

### common/src/main/java/pro/mir0n/esquire/messaging/changes.txt


### common/src/main/java/pro/mir0n/utils/changes.txt


**06/15/2026** mir0n  v1.2.8 -- Taijitu queue item carries a parsed body map instead of the raw wire body  
**taijitu.QueueItem**  
&nbsp;- the record field pair (messageEncoding, text) replaced by a single body Map; the  
&nbsp;   constructor is now (eventType, entityId, entityKind, requestId, correlationId, body). command items  
&nbsp;   pass body=null; event items carry the parsed map, applied by the monad with no re-parse  
**taijitu.ITaijituRig**  
&nbsp;- pass(...) contract changed: the (messageEncoding, text) params replaced by Map body  
&nbsp;   (null = bodiless event, e.g. DELETE); import java.util.Map added  
**taijitu.ATaijituRig**  
&nbsp;- pass(...) signature updated to the body-map form; forwards body into the body-map QueueItem  
**taijitu.ATaijituRigY**  
&nbsp;- pass(...) signature updated to the body-map form; forwards body into the body-map QueueItem  
**taijitu.AMonadY**  
&nbsp;- CMD QueueItem construction updated to the body-map ctor (raw messageEncoding+text args dropped;  
&nbsp;   command items pass body=null)  

### dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt

Esquire dataKeep library  

**06/18/2026** mir0n  v1.2.8 -- created: esquire-dataKeep, the generic DB-keep engine (extracted from the audit code)  
keep.KeepApplier  (new)  
&nbsp;- builds a keep's DB applier: a RodEventDbWriter over the KeepSqlStore + a kind->statement registry from the  
&nbsp;   director's kinds; applier() is the worker an x-rod runs. DEDICATED mode builds and owns its own auto-commit  
&nbsp;   Hikari pool from the datasource group; SHARED mode reuses a provided DataSource (the service's own pool) and  
&nbsp;   does NOT close it (close() closes only an owned pool)  
keep.RodEventDbWriter  (new, was common.audit.AuditLogWriter)  
&nbsp;- applies a RodEvent to a DB sink: the uniform identity/header params + the event body bound onto the  
&nbsp;   dialect-keyed SQL via NamedParameterJdbcTemplate; one INSERT/MERGE per call; a param the body lacks binds NULL  
keep.KeepSqlStore  (new, was common.audit.AuditLogSql)  
&nbsp;- dialect-keyed SQL store: statements live in META-INF//.xml, loaded on first use;  
&nbsp;   dialectOf() normalizes a vendor/profile label to the dialect token (which is the resource name)  
keep.KeepDataSourceParams  (new)  
&nbsp;- the keep datasource config group (the database + its connection pool) in its own group, not spring.datasource;  
&nbsp;   a shared flag (isShared()) selects reuse of the service's own pool over a dedicated keep pool  
keep.XRodInProcess  (new)  
&nbsp;- the generic in-process relay: an AXRod whose transmit feed loops into its own worker pool and runs the  
&nbsp;   worker start() is handed (no transport, no codec); resolved by rod-class, starts the worker pool a producer leg lacks  
director.IKeepDirector  (new)  
&nbsp;- the keep's declaration only: the SQL resource group + the kind->statement-key map; the engine does the rest  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**06/18/2026** mir0n  v1.2.8 -- Messaging Bus: audit module left common; leg-selected keep sink  
**audit.AuditConfig**  
&nbsp;- injects the service DataSource; the audit sink is selected from the leg: log-db.shared=true -> the  
&nbsp;   IN-PROCESS keep on the SERVICE's pool; a log-db url -> the IN-PROCESS keep with its OWN pool; else -> the  
&nbsp;   BUS producer; @PreDestroy closes a dedicated keep pool (a shared one is left to the service)  
service.impl.OrgService / UsrService / AcctService / EnyManService, queue.MoveQueueManager  
&nbsp;- AuditBusBridge moved out of common -> pro.mir0n.esquire.audit (the import repointed)  
**application.yml**  
&nbsp;- the in-process audit-b leg moved under the service-namespace key enyman.messaging-bus; rod-class  
&nbsp;   pro.mir0n.esquire.dataKeep.keep.XRodInProcess; log-db.shared + log-db.hikari.maximum-pool-size added  
**pom.xml**  
&nbsp;- depends on esquire-audit (the audit classes left common)  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus cleanup: audit via AuditBusBridge  
**audit.AuditConfig**  
&nbsp;- @Bean AuditBusBridge audit() wrapping rods.producer(BUS_KEY_AUDIT, BROADCAST) (was @Bean IXRod xRod())  
service.impl.OrgService / UsrService / AcctService / EnyManService, queue.MoveQueueManager  
&nbsp;- the audit producer field IXRod xyRod -> AuditBusBridge audit; post(...) calls drop the trailing  
&nbsp;   EsqMsgConstants.MSG_TYPE_AUDIT argument (the bridge stamps it); the isEnabled() guard reads audit  

**06/15/2026** mir0n  v1.2.8 -- messaging migrated onto the x-Rod bus catalog; audit via XRodManager  
**audit.AuditConfig**  
&nbsp;- audit producer resolved through the shared XRodManager: producer(BUS_KEY_AUDIT, Role.BROADCAST)  
&nbsp;   returns the IXRod; the leg's rod-class selects the sink (XRod=bus, XRodLogDb=in-process, XRodDisabled=OFF)  
&nbsp;- imports retargeted common.xrod -> messaging.xrod (IXRod / XRodManager); the @Value audit-logging.* /  
&nbsp;   log-db / mode wiring + datasource / *_log registry removed (XRodLogDb self-configures from the leg)  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- JMS producer (jmsTopicTemplate + FIX-JSON props) replaced by an x-Rod producer on the  
&nbsp;   {esquire.entity, entity} leg: producer(BUS_KEY_ENTITY, Role.BROADCAST); publish() builds a RodEvent  
&nbsp;   (opFromCode, msg-type UE) and calls rod.transmit  
&nbsp;- service-id / ctrl-id @Value, ObjectMapper, jms.Utils, FIX-JSON property map removed  
**messaging.KcRequestPublisher**  
&nbsp;- JMS producer (jmsQueueTemplate + FIX-JSON props) replaced by an x-Rod producer on the  
&nbsp;   {esquire.kc, kc-request} leg: producer(BUS_KEY_KC, Role.CLIENT); publishPathUpdate() builds a RodEvent  
&nbsp;   (op=UPDATE_PATH, msg-type URQ) and calls rod.transmit  
&nbsp;- ctrl-id @Value, ObjectMapper, jms.Utils removed; testReqId folded into the wire requestId  
**messaging.KcResponseListener**  
&nbsp;- @JmsListener(CtrlID selector) replaced by XRodManager.consumer(BUS_KEY_KC, Role.CLIENT) registered in  
&nbsp;   the ctor; onResponse(RodEvent) reads requestId / correlationId / msgType off the RodEvent (URS vs URR)  
&nbsp;- JMS Message property reads + jms.Utils + msgLog/devLog removed  
**queue.MoveQueueManager**  
&nbsp;- audit ctor param XYRod -> IXRod (import common.xrod -> messaging.xrod)  
**service.impl.EnyManService**  
&nbsp;- audit ctor param XYRod -> IXRod (import common.xrod -> messaging.xrod)  
**service.impl.AcctService**  
&nbsp;- audit dep XYRod -> IXRod; CREATE post() passes msgType EsqMsgConstants.MSG_TYPE_AUDIT  
**service.impl.OrgService**  
&nbsp;- audit dep XYRod -> IXRod; every org / org_par post() passes msgType EsqMsgConstants.MSG_TYPE_AUDIT  
**service.impl.UsrService**  
&nbsp;- audit dep XYRod -> IXRod; every user / person / address / usr_par post() passes msgType  
&nbsp;   EsqMsgConstants.MSG_TYPE_AUDIT  
messaging.EnyManJmsConfig  (removed)  
&nbsp;- JMS infrastructure config (queue/topic JmsTemplates + listener container factories) deleted; the  
&nbsp;   XRodManager + transport providers own the connection/lifecycle now  
messaging.EsqEntityBroadcastConsumer  (removed)  
&nbsp;- unused entity-broadcast consumer template deleted  
**pom.xml**  
&nbsp;- direct transport starters dropped (spring-boot-starter-activemq, spring-boot-starter-data-redis,  
&nbsp;   spring-kafka); transport-provider modules added: esquire-tp-activemq / esquire-tp-redis / esquire-tp-kafka  
&nbsp;   (each brings its transport client transitively)  
**application.yml**  
&nbsp;- spring.config.import of the shared esquire-topology.yml added; the esquire.*-messaging-bus refs declared  
&nbsp;   (audit-bus / kc-bus / entity-bus slot ids + per-service x-rod knobs) + the service-local audit-b leg  
&nbsp;   (rod-class XRodLogDb, log-db) under esquire.enyman-messaging-bus  
&nbsp;- removed: spring.activemq / spring.data.redis / spring.kafka / spring.jms.client-id blocks,  
&nbsp;   enyman.messaging.* (service-id / ctrl-id / client-id / consumer), enyman.audit-logging.* tree,  
&nbsp;   management.health.redis.enabled  

**06/12/2026** mir0n  v1.2.8 -- system entity flag (load) + admin-create fixes  
**service.impl.UsrService**  
&nbsp;- createUsr(): usr deleted set to 'N' when null before insertUsr (NOT NULL column, no dictionary default)  
**jpa.EsqUsrRepository**  
&nbsp;- insertPerson(): adPk / bizAdPk params long -> Long (nullable person address FK; admins have no address)  
**src/main/resources/META-INF/postgres-entity.xml**  
&nbsp;- EsqOrgJpa/EsqUsrJpa detailOrg/detailUsr + detailForUpdate select org_system_flg / usr_system_flg;  
&nbsp;   EsqOrgJpaMapping / EsqUsrJpaMapping bind systemFlg  
**src/main/resources/META-INF/oracle-entity.xml**  
&nbsp;- same: org_system_flg / usr_system_flg added to org/usr detail queries + mappings  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**06/15/2026** mir0n  v1.2.8 -- KC request/response + broadcast consumers migrated onto the x-Rod catalog  
**messaging.KcRequestConsumer**  
&nbsp;- @JmsListener(Message) onMessage replaced by onRodEvent(RodEvent); fields read off the RodEvent  
&nbsp;   (opCode/entityId/kind/rodId/requestId/correlationId), body convertValue'd to KcSyncRequest  
&nbsp;- registers via XRodManager.consumer(BUS_KEY_KC, Role.SERVER); no selector (shared request queue)  
&nbsp;- @ConditionalOnProperty kcmaster.kc-request-bus.consumer.enabled (matchIfMissing=true) gates the bean  
&nbsp;- requester rod-id echoed to the publisher; msgLog/Utils.formatProps removed  
**messaging.KcResponsePublisher**  
&nbsp;- JmsTemplate/Session-props publish replaced by IXRod from XRodManager.producer(BUS_KEY_KC, Role.SERVER)  
&nbsp;- publishSuccess/publishFailure build a RodEvent (MSG_TYPE_RESPONSE / MSG_TYPE_REJECT) and rod.transmit()  
&nbsp;- failure body carries the RFC-9457 error under "error" and the original request under "request";  
&nbsp;   requesterRodId param replaces ctrlId; requestText replaced by requestBody Map; testReqId param dropped  
**messaging.KcEntityBroadcastConsumer**  
&nbsp;- @JmsListener(topic, selector) onEntityBroadcast replaced by onRodEvent(RodEvent)  
&nbsp;- registers via XRodManager.consumer(BUS_KEY_ENTITY, Role.BROADCAST) in the constructor  
&nbsp;- move detection is RodEvent.Op.UPDATE_PATH; extractPath reads the path off the body Map (static  
&nbsp;   ObjectMapper/readTree removed); JMSException catch and msg-audit receipt removed  
messaging.KcMasterJmsConfig  (removed)  
&nbsp;- JMS ConnectionFactory / listener-factory / template config deleted; the receive pools, transport  
&nbsp;   consumers and the response producer are now built by the shared XRodManager  
**pom.xml**  
&nbsp;- spring-boot-starter-activemq dependency replaced by esquire-tp-activemq (${tp.version}); hosts the  
&nbsp;   shared messaging.jms.Utils helper for the KC request/response bus  
**src/main/resources/application.yml**  
&nbsp;- spring.activemq / spring.jms.client-id and kcmaster.messaging (broker-url/service-id/ctrl-id/client-id)  
&nbsp;   removed; spring.config.import of the shared esquire-topology.yml added (ESQUIRE_TOPOLOGY_IMPORT)  
&nbsp;- kcmaster.entity-broadcast-bus.consumer.enabled and kcmaster.kc-request-bus.consumer.enabled flags added  
&nbsp;- esquire.kc-bus / esquire.entity-bus messaging-bus refs (bus-id/slot-id) + x-rod pool/concurrency overrides  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**06/18/2026** mir0n  v1.2.8 -- Messaging Bus: audit module left common; leg-selected keep sink  
**audit.AuditConfig**  
&nbsp;- injects the service DataSource; the audit sink is selected from the leg: log-db.shared=true -> the  
&nbsp;   IN-PROCESS keep on the SERVICE's pool; a log-db url -> the IN-PROCESS keep with its OWN pool; else -> the  
&nbsp;   BUS producer; @PreDestroy closes a dedicated keep pool (a shared one is left to the service)  
**service.impl.KeySmithService**  
&nbsp;- AuditBusBridge moved out of common -> pro.mir0n.esquire.audit (the import repointed)  
**application.yml**  
&nbsp;- the in-process audit-b leg moved under the service-namespace key keysmith.messaging-bus; rod-class  
&nbsp;   pro.mir0n.esquire.dataKeep.keep.XRodInProcess; log-db.shared + log-db.hikari.maximum-pool-size added  
**pom.xml**  
&nbsp;- depends on esquire-audit (the audit classes left common)  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus cleanup: audit via AuditBusBridge  
**audit.AuditConfig**  
&nbsp;- @Bean AuditBusBridge audit() wrapping rods.producer(BUS_KEY_AUDIT, BROADCAST) (was @Bean IXRod xRod())  
**service.impl.KeySmithService**  
&nbsp;- the audit producer field IXRod xyRod -> AuditBusBridge audit; post(...) calls drop the trailing  
&nbsp;   EsqMsgConstants.MSG_TYPE_AUDIT argument (the bridge stamps it)  

**06/15/2026** mir0n  v1.2.8 -- KC sync publisher/listener migrated onto the x-Rod catalog; audit via XRodManager  
**messaging.KcSyncPublisher**  
&nbsp;- producer IXRod opened from XRodManager (rods.producer(BUS_KEY_KC, Role.CLIENT)); publish() builds a  
&nbsp;   RodEvent (msg-type URQ) and calls rod.transmit(); dropped JmsTemplate / ObjectMapper / ctrl-id @Value  
&nbsp;   and the manual FIX-props send; buildText() -> buildBody() returns the field Map (no JSON here)  
**messaging.KcSyncResponseListener**  
&nbsp;- consumes via XRodManager (rods.consumer(BUS_KEY_KC, Role.CLIENT, this::onResponse)); onResponse(RodEvent)  
&nbsp;   reads the typed envelope (msgType = URS/URR tag); dropped @JmsListener / Message field reads / msgLog  
**audit.AuditConfig**  
&nbsp;- ctor takes XRodManager; xRod() returns rods.producer(BUS_KEY_AUDIT, Role.BROADCAST); dropped the @Value  
&nbsp;   config block, the DataSource / JmsTemplate / Redis / Kafka publisher wiring, kindToSqlKey() and @PreDestroy  
**service.impl.KeySmithService**  
&nbsp;- audit field retyped XYRod -> IXRod (messaging.xrod); the auth UPDATE post() now passes an explicit  
&nbsp;   msgType (EsqMsgConstants.MSG_TYPE_AUDIT) as the trailing argument  
messaging.KeySmithJmsConfig  (removed)  
&nbsp;- JMS queue connection-factory / listener-factory config deleted (transport now owned by XRodManager)  
**pom.xml**  
&nbsp;- direct transport starters (spring-boot-starter-activemq, spring-boot-starter-data-redis, spring-kafka)  
&nbsp;   replaced by the esquire-tp-activemq / esquire-tp-redis / esquire-tp-kafka transport-provider modules  
**application.yml**  
&nbsp;- dropped spring.activemq/data.redis/kafka/jms blocks, keysmith.messaging.ctrl-id and the  
&nbsp;   keysmith.audit-logging.* tree; added spring.config.import of the shared esquire-topology.yml and the  
&nbsp;   esquire.* bus refs (audit-bus, kc-bus) + service-local keysmith-messaging-bus audit-b leg (XRodLogDb)  

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt

Esquire messaging framework classes  

**06/19/2026** mir0n  v1.2.8 -- bus list-config keys pluralized (slot -> slots, node -> nodes)  
MessagingBus  
&nbsp;- the slots component binds the plural config key `slots` directly; @Name("slot") dropped  
XRodParams  
&nbsp;- nodes() reads the R&R network nodes from the plural key transport.nodes[*] (was transport.node[*])  

**06/18/2026** mir0n  v1.2.8 -- extracted into its own module esquire-messaging (was inside esquire-common)  
(module)  
&nbsp;- the messaging bus framework (catalog, x-rod engine, transport SPI, codec; package  
&nbsp;   pro.mir0n.esquire.messaging, unchanged) is now the esquire-messaging module, depending on esquire-common  
&nbsp;   (one-directional, no cycle); the XRodAutoConfiguration registration (META-INF/spring) moved here too;  
&nbsp;   dependents (tp-*, dataKeep, audit, producers, bizTree, kcMaster) now declare an explicit esquire-messaging dep  

**06/18/2026** mir0n  v1.2.8 -- Messaging Bus: catalog service-overlay key moved to the service namespace  
MessagingBusCatalog  
&nbsp;- the service overlay binds .messaging-bus (was esquire.-messaging-bus);  
&nbsp;   the catalog is the shared esquire.messaging-bus topology UNION that service-namespace overlay  

**06/15/2026** mir0n  v1.2.8 -- pluggable transport layer + uniform x-Rod messaging frontend  
xrod.IXRod  (new)  
&nbsp;- the x-Rod pod SPI: a transmit/receive fan-out substrate; configure() prepares from XRodParams + Role,  
&nbsp;   start() runs, post / transmit buffer + feed out after commit, submit applies on a bounded pool  
xrod.XRodManager  (new)  
&nbsp;- the ONE per-service x-Rod frontend (one shared bean): producer / consumer build a rod in a single call  
&nbsp;   (resolve the bus key -> BusRef, merge the catalog leg with a service-level override, resolve + start the  
&nbsp;   pod); close() shuts every rod down  
&nbsp;- an unset leg rod-id defaults to the per-instance id . (EsqUtils.instanceNo()), so an  
&nbsp;   R&R CLIENT's RodID selector isolates its own instance's replies  
xrod.XRods  (new)  
&nbsp;- class-name-driven resolver for IXRod pods: x-rod.rod-class -> pro.mir0n.esquire.messaging.xrod.impl.  
&nbsp;   (or a full class name); DEFAULT = XRod, DISABLED = XRodDisabled  
xrod.XRodAutoConfiguration  (new)  
&nbsp;- @AutoConfiguration registering the one shared XRodManager bean (destroyMethod = close); imported via  
&nbsp;   META-INF/spring/...AutoConfiguration.imports  
xrod.RodEvent  (new)  
&nbsp;- record (op/kind/entityId/subId/actionTime/crl/req/uid/rodId/msgType/body): one self-contained relayed  
&nbsp;   change; opCode / opFromCode map Op  the EVENT_* wire code  
xrod.RodEventCodec  (new)  
&nbsp;- maps a RodEvent to/from the FIX-JSON envelope (header property map + body as the Text JSON field) via a  
&nbsp;   BusIdentity + the event's own msgType; fromProps tolerant of Number-or-String values  
xrod.RodTransportAdapter  (new)  
&nbsp;- codec bridge between the RodEvent and the generic transport seam: publisher() opens the provider sink and  
&nbsp;   returns a Consumer (key = entityId); handler() adapts a RodEvent sink into a TransportMessage handler  
xrod.IRodEventRepo  (new)  
&nbsp;- apply(RodEvent) contract: one per *_log table, called concurrently by the receive pool, must be thread-safe  
xrod.RodEventRepoRegistry  (new)  
&nbsp;- kind -> IRodEventRepo map (ConcurrentHashMap); applier(Logger) is a receive-leg worker that resolves each  
&nbsp;   event's repository by kind and applies it (missing repo logged + skipped)  
xrod.impl.XRod  (new)  
&nbsp;- the default x-Rod transceiver pod: both legs; post buffers in the current transaction and a feed worker  
&nbsp;   stamps actionTime + the audit triple after commit, submit applies on a Semaphore-bounded pool; builds its  
&nbsp;   own publisher / consumer from the leg transport; non-final  
xrod.impl.XRodRR  (new)  
&nbsp;- the Request/Response pod (specialised XRod): resolves the request vs response NODE by role (overlaid via  
&nbsp;   XRodParams.overlayGroups, provider / endpoint excepted) and the receive selector (CLIENT by rod-id,  
&nbsp;   SERVER by slot-id)  
xrod.impl.XRodInfo  (new)  
&nbsp;- a non-sending pod: log.info()s each event's full content to the leg's msg-audit (led by a directive from  
&nbsp;   its x-rod.info sub-block) instead of transmitting; composes the inner XRod feed  
xrod.impl.XRodInfoParams  (new)  
&nbsp;- record (dir): XRodInfo's own params (bound from the leg's x-rod.info) -- the directive logged in place of TX|RX  
xrod.impl.XRodDisabled  (new)  
&nbsp;- the OFF pod: a fully inert IXRod (both legs absent, no config, no transport); the default when a bus key  
&nbsp;   resolves to no leg, or set explicitly via rod-class = XRodDisabled  
transport.ITransportProvider  (new)  
&nbsp;- the transport-provider (tp) SPI: openPublisher / openConsumer over the neutral TransportMessage;  
&nbsp;   supportsConsume() lets a producer-only transport skip the consume leg  
transport.TransportProviders  (new)  
&nbsp;- class-name-driven resolver for ITransportProvider (a bare name -> pro.mir0n.esquire.tp..TransportProvider  
&nbsp;   by convention, or a full class name verbatim), reflectively instantiated + cached; paramKey() yields the  
&nbsp;   param-group name  
transport.TransportMessage  (new)  
&nbsp;- the transport-neutral message: a property-bag envelope (headers) + an optional routing / partition key  
transport.TransportSettings  (new)  
&nbsp;- base of the settings hierarchy: ObjectMapper, endpoint, client-id, destination kind (queue vs topic),  
&nbsp;   BusIdentity envelope, the provider's own params group; paramLong / param accessors  
transport.PublishSettings  (new)  
&nbsp;- publish-side (xy-rod) TransportSettings: adds the async publisher pool size (0 = the single feed worker)  
transport.ConsumeSettings  (new)  
&nbsp;- consume-side (xx-rod) TransportSettings: adds listener concurrency and an optional provider-specific  
&nbsp;   message selector (null = consume everything)  
transport.BusIdentity  (new)  
&nbsp;- record (busId, slotId, rodId): the x-Rod instance identity that rides the envelope  
MessagingBusCatalog  (new)  
&nbsp;- the messaging-bus catalog: the union of the shared esquire.messaging-bus topology and a service's own  
&nbsp;   esquire.-messaging-bus; resolve / find a leg by {bus-id, slot-id} -> XRodParams; publishLeg / consumeLeg  
&nbsp;   build the transport settings via the resolved provider  
MessagingBus  (new)  
&nbsp;- record (busId, slot): one bus in the catalog -- a bus-id grouping its slots  
BusSlot  (new)  
&nbsp;- record (slotId, xRod): one slot (leg) on a bus -- its slot-id + the raw x-Rod config node  
BusRef  (new)  
&nbsp;- record (busId, slotId, xRod): a service-level reference to a catalog leg, with an optional service-level  
&nbsp;   x-rod node that fully overwrites the catalog leg's x-rod when present  
BusTransport  (new)  
&nbsp;- record (provider, endpoint, destination, topic, params): the bound wire of one x-Rod leg  
XRodParams  (new)  
&nbsp;- record (busId, slotId, raw): a bound x-Rod leg -- its flattened config node plus the folded-in leg identity;  
&nbsp;   knobs read from raw by name (SCALARS), transport() binds the wire group, sub() binds a pod-owned sub-block,  
&nbsp;   merge / overlayGroups overlay an override per top-level group  
Role  (new)  
&nbsp;- enum CLIENT / SERVER / BROADCAST: a service's role on a bus (CLIENT/SERVER on a Request-Response bus,  
&nbsp;   BROADCAST on a single-node bus)  
META-INF/spring/...AutoConfiguration.imports  (new)  
&nbsp;- registers xrod.XRodAutoConfiguration as a Boot auto-configuration  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**06/18/2026** mir0n  v1.2.8 -- Messaging Bus: audit module left common; leg-selected keep sink  
**audit.AuditConfig**  
&nbsp;- injects the service DataSource; the audit sink is selected from the leg: log-db.shared=true -> the  
&nbsp;   IN-PROCESS keep on the SERVICE's pool; a log-db url -> the IN-PROCESS keep with its OWN pool; else -> the  
&nbsp;   BUS producer; @PreDestroy closes a dedicated keep pool (a shared one is left to the service)  
service.impl.PacManService, acct.service.AcctTransactionService / AcctTransactionProcessorSingle /  
AcctTransactionProcessorTransfer  
&nbsp;- AuditBusBridge moved out of common -> pro.mir0n.esquire.audit (the import / param type repointed)  
**application.yml**  
&nbsp;- the in-process audit-b leg moved under the service-namespace key pacman.messaging-bus; rod-class  
&nbsp;   pro.mir0n.esquire.dataKeep.keep.XRodInProcess; log-db.shared + log-db.hikari.maximum-pool-size added  
**pom.xml**  
&nbsp;- depends on esquire-audit (the audit classes left common)  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus cleanup: audit via AuditBusBridge  
**audit.AuditConfig**  
&nbsp;- @Bean AuditBusBridge audit() wrapping rods.producer(BUS_KEY_AUDIT, BROADCAST) (was @Bean IXRod xRod())  
service.impl.PacManService, acct.service.AcctTransactionService / AcctTransactionProcessorSingle /  
AcctTransactionProcessorTransfer  
&nbsp;- the audit producer field IXRod xyRod -> AuditBusBridge audit; post(...) calls drop the trailing  
&nbsp;   EsqMsgConstants.MSG_TYPE_AUDIT argument (the bridge stamps it)  

**06/15/2026** mir0n  v1.2.8 -- entity-broadcast migrated onto the x-Rod catalog; audit via XRodManager  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- rewired onto the x-Rod transport seam: ctor opens the producer via  
&nbsp;   rods.producer(BUS_KEY_ENTITY, Role.BROADCAST) on the entity-bus leg  
&nbsp;- publish() builds a RodEvent (msg-type UE) and calls rod.transmit; the manual JmsTemplate send,  
&nbsp;   ObjectMapper text serialization, service-id / ctrl-id @Value, and FIX-JSON property assembly removed  
&nbsp;- the shared XRodManager owns the rod start/stop; no per-class lifecycle  
**audit.AuditConfig**  
&nbsp;- audit producer now resolved through the shared XRodManager: xRod() returns  
&nbsp;   rods.producer(BUS_KEY_AUDIT, Role.BROADCAST); the leg's rod-class selects the pod (bus / log-db /  
&nbsp;   disabled), so the injected IXRod is never null  
&nbsp;- removed the local mode/transport branching and all datasource / JMS / Redis / Kafka wiring, the  
&nbsp;   kindToSqlKey map, and the @PreDestroy shutdown  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- audit producer field retyped messaging.xrod.IXRod (was common.xrod.XYRod); the balance-change post()  
&nbsp;   carries an explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT)  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- audit-producer ctor param retyped messaging.xrod.IXRod (was common.xrod.XYRod)  
**acct.service.AcctTransactionService**  
&nbsp;- audit-producer ctor param retyped messaging.xrod.IXRod (was common.xrod.XYRod)  
**service.impl.PacManService**  
&nbsp;- audit producer retyped messaging.xrod.IXRod (was common.xrod.XYRod); saveAcct UPDATE and deleteAcct  
&nbsp;   DELETE post() calls carry an explicit msgType (EsqMsgConstants.MSG_TYPE_AUDIT)  
messaging.PacManJmsConfig  (removed)  
&nbsp;- JMS configuration deleted; the jmsTopicTemplate / jmsQueueTemplate beans are no longer needed now  
&nbsp;   that entity-broadcast and audit publish through the x-Rod catalog  
**pom.xml**  
&nbsp;- transport-provider (tp) modules added: esquire-tp-activemq, esquire-tp-redis, esquire-tp-kafka  
&nbsp;   (each brings its transport client transitively)  
&nbsp;- direct transport starters removed: spring-boot-starter-activemq, spring-boot-starter-data-redis,  
&nbsp;   spring-kafka  
**src/main/resources/application.yml**  
&nbsp;- spring.config.import of the shared esquire-topology.yml (ESQUIRE_TOPOLOGY_IMPORT); esquire bus REFS  
&nbsp;   added: pacman-messaging-bus (service-local audit-b in-process leg, rod-class XRodLogDb), audit-bus,  
&nbsp;   entity-bus  
&nbsp;- removed spring.activemq / spring.data.redis / spring.kafka blocks, the pacman.messaging and  
&nbsp;   pacman.audit-logging.x-rod.* knobs, and management.health.redis.enabled  

### tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt

Esquire messaging helper classes  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus cleanup: closeable publisher  
**tp.activemq.TransportProvider**  
&nbsp;- openPublisher returns a TransportPublisher (close() destroys the CachingConnectionFactory) instead of a  
&nbsp;   bare Consumer; the ccf.setClientID(...) block removed (a client id is a  
&nbsp;   transport.params.jms.clientID entry now)  

**06/15/2026** mir0n  v1.2.8 -- ActiveMQ transport provider module  
tp.activemq.TransportProvider  (new)  
&nbsp;- ActiveMQ implementation of the transport-provider SPI; resolved by name (reflectively instantiated,  
&nbsp;   no Spring bean) and owns its own audit broker connection.  
&nbsp;- openPublisher builds its own JmsTemplate over a CachingConnectionFactory (poolSize>0 -> useAsyncSend  
&nbsp;   plus sessionCacheSize); send() stamps appl-msg-id + sending-time and writes the header bag via  
&nbsp;   messaging.jms.Utils.setProps.  
&nbsp;- openConsumer runs a programmatic DefaultMessageListenerContainer (selector, concurrency, topic/queue  
&nbsp;   from settings) lifting every JMS property into a neutral TransportMessage header map.  
&nbsp;- withParams appends the leg's transport.params verbatim to the broker URI; readProps lifts all JMS  
&nbsp;   properties into the header map.  
tp.activemq.TpActiveMqAutoConfigFilter  (new)  
&nbsp;- AutoConfigurationImportFilter suppressing Boot's ActiveMQAutoConfiguration + JmsAutoConfiguration so a  
&nbsp;   service that ships this provider stays transport-agnostic.  
pom.xml  (new)  
&nbsp;- esquire-tp-activemq module; depends on esquire-common + spring-boot-starter-activemq.  
META-INF/spring.factories  (new)  
&nbsp;- registers TpActiveMqAutoConfigFilter as an AutoConfigurationImportFilter.  

**04/06/2026** mir0n  JMS Utils: sorted key output  
**messaging.jms.Utils**  
&nbsp;- formatProps(Map): keys sorted alphabetically for consistent log output  

### tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt

Esquire Kafka transport provider classes  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus cleanup: closeable publisher  
**tp.kafka.TransportProvider**  
&nbsp;- openPublisher returns a TransportPublisher (close() destroys the DefaultKafkaProducerFactory) instead of a  
&nbsp;   bare Consumer; the clientId parameter + CLIENT_ID_CONFIG removed from buildTemplate /  
&nbsp;   buildConsumerFactory (a client id is a transport.params.client.id entry now)  

**06/15/2026** mir0n  v1.2.8 -- Kafka transport provider module  
tp.kafka.TransportProvider  (new)  
&nbsp;- Kafka implementation of the transport-provider SPI; resolved by name (reflectively instantiated,  
&nbsp;   no Spring bean) and owns its own audit producer / consumer.  
&nbsp;- openPublisher builds its own KafkaTemplate (String/String) from settings.endpoint() (bootstrap-servers)  
&nbsp;   and sends each TransportMessage as a JSON value keyed by TransportMessage.key (per-entity order).  
&nbsp;- openConsumer runs a programmatic ConcurrentMessageListenerContainer; the consumer group-id comes from  
&nbsp;   the provider param transport.kafka.group-id (PARAM_GROUP_ID, default esquire-xxrod-audit), read via  
&nbsp;   settings.params(); auto-offset-reset earliest.  
&nbsp;- buildTemplate / buildConsumerFactory apply every leg param verbatim as a Kafka config; the essentials  
&nbsp;   (bootstrap, serializers, group.id, client.id) win.  
tp.kafka.TpKafkaAutoConfigFilter  (new)  
&nbsp;- AutoConfigurationImportFilter suppressing Boot's KafkaAutoConfiguration so a service that ships this  
&nbsp;   provider stays transport-agnostic.  
pom.xml  (new)  
&nbsp;- esquire-tp-kafka module; depends on esquire-common, spring-kafka, spring-boot-autoconfigure, slf4j-api.  
META-INF/spring.factories  (new)  
&nbsp;- registers TpKafkaAutoConfigFilter as an AutoConfigurationImportFilter.  

### tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt

Esquire Redis transport provider classes  

**06/17/2026** mir0n  v1.2.8 -- Messaging Bus cleanup: closeable publisher  
**tp.redis.TransportProvider**  
&nbsp;- openPublisher returns a TransportPublisher (close() destroys the LettuceConnectionFactory) instead of a  
&nbsp;   bare Consumer  

**06/15/2026** mir0n  v1.2.8 -- Redis transport provider module  
tp.redis.TransportProvider  (new)  
&nbsp;- Redis-stream implementation of the transport-provider SPI; resolved by name (reflectively instantiated,  
&nbsp;   no Spring bean) and owns its own audit stream connection. Producer-only (supportsConsume()=false; the  
&nbsp;   stream IS the append-only log, read via XRANGE).  
&nbsp;- openPublisher XADDs each TransportMessage's header bag (plus stamped appl-msg-id + sending-time) to the  
&nbsp;   destination stream as string fields (null fields omitted); approximate-trimmed to max-len when set.  
&nbsp;- openConsumer throws UnsupportedOperationException (no consume leg).  
&nbsp;- max-len comes from the provider param transport.redis.max-len (PARAM_MAX_LEN, default unbounded), read  
&nbsp;   via settings.params(); buildTemplate builds a started StringRedisTemplate over a Lettuce connection,  
&nbsp;   appending every leg param except max-len to the redis:// URI verbatim.  
tp.redis.TpRedisAutoConfigFilter  (new)  
&nbsp;- AutoConfigurationImportFilter suppressing Boot's RedisAutoConfiguration + RedisReactiveAutoConfiguration  
&nbsp;   so a service that ships this provider stays transport-agnostic.  
pom.xml  (new)  
&nbsp;- esquire-tp-redis module; depends on esquire-common + spring-boot-starter-data-redis.  
META-INF/spring.factories  (new)  
&nbsp;- registers TpRedisAutoConfigFilter as an AutoConfigurationImportFilter.  

### xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt


---

## Commits

```

-- 2026-06-20 | commit: 6faf19f | mir0n.the.programmer | v1.2.8 -- deployment finalization --
A	k8s-oci/esquire-topology.yml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
 4 files changed, 51 insertions(+), 3 deletions(-)


-- 2026-06-20 | commit: de72813 | mir0n.the.programmer | Update deploy-local.yml --
M	.github/workflows/deploy-local.yml
 1 file changed, 11 insertions(+)

-- 2026-06-20 | commit: df61701 | mir0n.the.programmer | v1.2.8 -- finalization: plural bus-config keys, host-Postgres dev stack, documentation --
M	.github/scripts/deploy-oke.sh
M	README.md
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
A	dataKeep/src/main/resources/spring.properties
M	doc/DatabaseDictionary.md
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.BizTree.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/Logging.md
M	doc/Message.Structure.md
D	doc/Messaging.First.md
M	doc/Messaging.md
M	doc/Object.Kind.enum.md
M	doc/img/messaging-bus-architecture.svg
M	doc/img/messaging-bus-classes.svg
M	doc/img/messaging-bus-params.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v1.2.x.Planning.md
M	enyMan/src/main/resources/application.yml
M	k8s-oci/values/postgres.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/esquire-topology/templates/configmap.yaml
A	k8s/charts/esquire-topology/values.yaml
A	k8s/charts/infra/kafka/Chart.yaml
A	k8s/charts/infra/kafka/templates/deployment.yaml
A	k8s/charts/infra/kafka/templates/service.yaml
A	k8s/charts/infra/kafka/values.yaml
M	k8s/charts/infra/postgres/templates/statefulset.yaml
M	k8s/charts/infra/postgres/values.yaml
A	k8s/charts/infra/redis/Chart.yaml
A	k8s/charts/infra/redis/templates/deployment.yaml
A	k8s/charts/infra/redis/templates/service.yaml
A	k8s/charts/infra/redis/values.yaml
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java
M	pacMan/src/main/resources/application.yml
A	test/audit-smoke/README.md
A	test/audit-smoke/run.sh
 65 files changed, 1213 insertions(+), 1379 deletions(-)

-- 2026-06-19 | commit: 32637e1 | mir0n.the.programmer | v1.2.8 -- Messaging Bus: a reusable "keep" engine, split out of the audit code --
M	.github/scripts/ci.sh
M	.github/scripts/deploy-oke.sh
M	README.md
R100	xxRod/Dockerfile	auKeep/Dockerfile
R055	xxRod/compose.yaml	auKeep/compose.yaml
R100	xxRod/docker-compose-build.bat	auKeep/docker-compose-build.bat
R061	xxRod/pom.xml	auKeep/pom.xml
A	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
R082	xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
A	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
A	auKeep/src/main/resources/application.yml
R096	xxRod/src/main/resources/logback-spring.xml	auKeep/src/main/resources/logback-spring.xml
R084	xxRod/src/test/java/pro/mir0n/esquire/xxRod/RodBusIntegrationTest.java	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
R100	xxRod/src/test/resources/it-account-log.sql	auKeep/src/test/resources/it-account-log.sql
A	audit/pom.xml
R099	common/src/main/java/pro/mir0n/esquire/common/audit/AuditBusBridge.java	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
A	audit/src/main/java/pro/mir0n/esquire/audit/AuditKeepDirector.java
A	audit/src/main/java/pro/mir0n/esquire/audit/AuditKinds.java
A	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
R100	common/src/test/resources/META-INF/audit/oracle.xml	audit/src/main/resources/META-INF/audit/oracle.xml
R100	common/src/test/resources/META-INF/audit/postgres.xml	audit/src/main/resources/META-INF/audit/postgres.xml
R099	common/src/test/java/pro/mir0n/esquire/common/audit/AuditBusBridgeTest.java	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
A	audit/src/test/java/pro/mir0n/esquire/audit/AuditKeepDirectorTest.java
R062	common/src/test/java/pro/mir0n/esquire/common/audit/AuditKindsTest.java	audit/src/test/java/pro/mir0n/esquire/audit/AuditKindsTest.java
A	audit/src/test/java/pro/mir0n/esquire/audit/AuditSqlTest.java
M	bizTree/pom.xml
M	common/pom.xml
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditKinds.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogSql.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/XRodLogDb.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/XRodLogDbParams.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
D	common/src/test/java/pro/mir0n/esquire/common/audit/AuditLogSqlTest.java
M	compose/compose-rebuild.bat
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
A	dataKeep/pom.xml
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/director/IKeepDirector.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepSqlStore.java
R056	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogWriter.java	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcess.java
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.MessagingBus.md
M	doc/Messaging.md
M	doc/img/audit-pipeline.svg
A	doc/img/messaging-bus-architecture.svg
A	doc/img/messaging-bus-classes.svg
A	doc/img/messaging-bus-params.svg
M	doc/logo/gateway.svg
A	doc/logo/keep.svg
D	doc/logo/x-rod.svg
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
D	enyMan/src/main/resources/META-INF/audit/oracle.xml
D	enyMan/src/main/resources/META-INF/audit/postgres.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
A	k8s/charts/esquire-aukeep/Chart.yaml
R069	k8s/charts/esquire-xxrod/templates/configmap.yaml	k8s/charts/esquire-aukeep/templates/configmap.yaml
R071	k8s/charts/esquire-xxrod/templates/deployment.yaml	k8s/charts/esquire-aukeep/templates/deployment.yaml
R061	k8s/charts/esquire-xxrod/templates/secret.yaml	k8s/charts/esquire-aukeep/templates/secret.yaml
R079	k8s/charts/esquire-xxrod/values.yaml	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
D	k8s/charts/esquire-xxrod/Chart.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
R080	k8s/values/xxrod.yaml	k8s/values/aukeep.yaml
M	kcMaster/pom.xml
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
D	keySmith/src/main/resources/META-INF/audit/oracle.xml
D	keySmith/src/main/resources/META-INF/audit/postgres.xml
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
A	messaging/pom.xml
R100	common/src/main/java/pro/mir0n/esquire/messaging/BusNode.java	messaging/src/main/java/pro/mir0n/esquire/messaging/BusNode.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/BusRef.java	messaging/src/main/java/pro/mir0n/esquire/messaging/BusRef.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/BusSlot.java	messaging/src/main/java/pro/mir0n/esquire/messaging/BusSlot.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java	messaging/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
R083	common/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/Role.java	messaging/src/main/java/pro/mir0n/esquire/messaging/Role.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java	messaging/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java
R087	common/src/main/java/pro/mir0n/esquire/messaging/changes.txt	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/IRodEventRepo.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IRodEventRepo.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistry.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistry.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodAutoConfiguration.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodAutoConfiguration.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRods.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRods.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
R100	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
R100	common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports	messaging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
R100	common/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
R094	common/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
R100	common/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
A	pacMan/.mvn/.idea/.gitignore
A	pacMan/.mvn/.idea/.mvn.iml
A	pacMan/.mvn/.idea/libraries/maven_wrapper.xml
A	pacMan/.mvn/.idea/misc.xml
A	pacMan/.mvn/.idea/modules.xml
A	pacMan/.mvn/wrapper/maven-wrapper.jar
A	pacMan/.mvn/wrapper/maven-wrapper.properties
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
D	pacMan/src/main/resources/META-INF/audit/oracle.xml
D	pacMan/src/main/resources/META-INF/audit/postgres.xml
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	tp-activemq/pom.xml
M	tp-kafka/pom.xml
M	tp-redis/pom.xml
D	xxRod/logs/xxRod-msg.log.2026-06-06.gz
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/XxRodApplication.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/AuditRodDirector.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/IRodDirector.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/RodDirectorHost.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodAuditConsumerConfig.java
D	xxRod/src/main/resources/META-INF/audit/oracle.xml
D	xxRod/src/main/resources/META-INF/audit/postgres.xml
D	xxRod/src/main/resources/application.yml
D	xxRod/src/test/java/pro/mir0n/esquire/xxRod/director/AuditRodDirectorTest.java
 179 files changed, 2346 insertions(+), 2100 deletions(-)

-- 2026-06-17 | commit: e0672b2 | mir0n.the.programmer | v1.2.8 -- Messaging Bus: code-review cleanup and hardening --
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/AuditBusBridge.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/XRodLogDb.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/main/java/pro/mir0n/esquire/messaging/BusNode.java
M	common/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java
M	common/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
M	common/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
M	common/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java
M	common/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
M	common/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
M	common/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
M	common/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
M	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
M	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRods.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
M	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
A	common/src/test/java/pro/mir0n/esquire/common/audit/AuditBusBridgeTest.java
M	common/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
M	common/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
M	common/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
M	common/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
M	common/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
M	common/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java
M	common/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
M	common/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
A	compose/topology/esquire-topology.yml
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.MessagingBus.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s-oci/oke-rebuild.bat
M	k8s/k8s-rebuild.bat
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/AuditRodDirector.java
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodAuditConsumerConfig.java
M	xxRod/src/test/java/pro/mir0n/esquire/xxRod/RodBusIntegrationTest.java
 77 files changed, 1850 insertions(+), 1133 deletions(-)

-- 2026-06-17 | commit: 159396a | mir0n.the.programmer | v1.2.8 -- Draft of Messaging Bus Concept implementation --
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeBroadcastConsumer.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeJmsConfig.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/application.yml
D	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogWriter.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditRod.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditSettings.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventBusPublisher.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/RodKafkaPublisher.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/RodRedisPublisher.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/XRodLogDb.java
A	common/src/main/java/pro/mir0n/esquire/common/audit/XRodLogDbParams.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
D	common/src/main/java/pro/mir0n/esquire/common/xrod/RodEvent.java
D	common/src/main/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistry.java
D	common/src/main/java/pro/mir0n/esquire/common/xrod/XXRod.java
D	common/src/main/java/pro/mir0n/esquire/common/xrod/XYRod.java
A	common/src/main/java/pro/mir0n/esquire/messaging/BusRef.java
A	common/src/main/java/pro/mir0n/esquire/messaging/BusSlot.java
A	common/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java
A	common/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
A	common/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
A	common/src/main/java/pro/mir0n/esquire/messaging/Role.java
A	common/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java
M	common/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
A	common/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
R087	common/src/main/java/pro/mir0n/esquire/common/xrod/IRodRepository.java	common/src/main/java/pro/mir0n/esquire/messaging/xrod/IRodEventRepo.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java
R052	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventCodec.java	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistry.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodAutoConfiguration.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/XRods.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
A	common/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
M	common/src/main/java/pro/mir0n/utils/changes.txt
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
A	common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
M	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
D	common/src/test/java/pro/mir0n/esquire/common/audit/AuditRodBusTest.java
D	common/src/test/java/pro/mir0n/esquire/common/audit/RodRedisPublisherTest.java
D	common/src/test/java/pro/mir0n/esquire/common/xrod/XXRodTest.java
D	common/src/test/java/pro/mir0n/esquire/common/xrod/XYRodTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
A	common/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
A	common/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
R053	common/src/test/java/pro/mir0n/esquire/common/audit/RodEventCodecTest.java	common/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
R066	common/src/test/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistryTest.java	common/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
A	common/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
M	common/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
M	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
M	compose/compose.yaml
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.BizTree.md
A	doc/Esquire.MessagingBus.md
M	doc/Esquire.TestingStack.md
M	doc/Logging.md
M	doc/Message.Structure.md
M	doc/Messaging.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v128.tasks.md
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EnyManJmsConfig.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastConsumer.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListener.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
D	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisherTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisherTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListenerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EntityIdGeneratorTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/values.yaml
A	k8s/charts/esquire-topology/Chart.yaml
A	k8s/charts/esquire-topology/esquire-topology.yml
A	k8s/charts/esquire-topology/templates/configmap.yaml
M	k8s/charts/esquire-xxrod/templates/configmap.yaml
M	k8s/charts/esquire-xxrod/templates/deployment.yaml
M	k8s/charts/esquire-xxrod/values.yaml
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	k8s/values/xxrod.yaml
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcMasterJmsConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisherTest.java
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncResponseListener.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KeySmithJmsConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/PacManJmsConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
D	pacMan/src/test/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisherTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
A	tp-activemq/pom.xml
A	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
R093	common/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
A	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TpActiveMqAutoConfigFilter.java
A	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
A	tp-activemq/src/main/resources/META-INF/spring.factories
A	tp-kafka/pom.xml
A	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TpKafkaAutoConfigFilter.java
A	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
A	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
A	tp-kafka/src/main/resources/META-INF/spring.factories
A	tp-redis/pom.xml
A	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TpRedisAutoConfigFilter.java
A	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
A	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
A	tp-redis/src/main/resources/META-INF/spring.factories
D	xxRod/logs/xxRod-develop.log
D	xxRod/logs/xxRod-develop.log.2026-06-06.gz
D	xxRod/logs/xxRod-msg.log
M	xxRod/pom.xml
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/AuditRodDirector.java
M	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/IRodDirector.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumer.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodKafkaConsumer.java
A	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodAuditConsumerConfig.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodJmsConfig.java
M	xxRod/src/main/resources/application.yml
M	xxRod/src/test/java/pro/mir0n/esquire/xxRod/RodBusIntegrationTest.java
D	xxRod/src/test/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumerTest.java
 212 files changed, 7531 insertions(+), 5946 deletions(-)

-- 2026-06-12 | commit: 055bdf7 | mir0n.the.programmer | v1.2.8 -- system entity flag (anti-deletion); admin-create and system-login fixes --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	doc/DatabaseDictionary.md
M	doc/model/ComponentModel.vsdx
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
M	doc/v128.tasks.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	keycloak/import/esquire.json
 14 files changed, 83 insertions(+), 3 deletions(-)

-- 2026-06-12 | commit: 175f0d1 | mir0n.the.programmer | Esquire Logo updated, icons added --
M	README.md
M	doc/DatabaseDictionary.md
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.BizTree.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/H2BizTree.md
M	doc/Logging.md
M	doc/Messaging.md
M	doc/OCI.Pricing.md
M	doc/Object.Kind.enum.md
M	doc/Testing.md
M	doc/WhereToGo.md
A	doc/logo/activemq.png
A	doc/logo/angular.svg
A	doc/logo/bizTree.png
A	doc/logo/enyMan.3.png
A	doc/logo/esquire.png
A	doc/logo/gateway.svg
A	doc/logo/gatling.svg
A	doc/logo/h2.svg
A	doc/logo/hauberk.svg
A	doc/logo/java.svg
A	doc/logo/kafka.svg
A	doc/logo/kcMaster.png
A	doc/logo/keySmith.3.png
A	doc/logo/keycloak.png
A	doc/logo/node.js.svg
A	doc/logo/oracle.svg
A	doc/logo/pac-man.2.svg
A	doc/logo/pac-man.svg
A	doc/logo/postgres.svg
A	doc/logo/redis.svg
A	doc/logo/spring-boot.svg
A	doc/logo/x-rod.svg
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/services.configuring.md
M	doc/v1.2.x.Planning.md
M	favicon.ico
A	helm.svg
 44 files changed, 1561 insertions(+), 67 deletions(-)

-- 2026-06-10 | commit: 9ba03c6 | mir0n.the.programmer | v1.2.8 bump --
M	README.md
A	doc/v128.tasks.md
M	pom.xml
 3 files changed, 28 insertions(+), 1 deletion(-)

-- 2026-06-10 | commit: 8ed354c | mir0n.the.programmer | Create report_v1.2.7.md --
A	doc/reports/report_v1.2.7.md
 1 file changed, 1016 insertions(+)
```

---

## Files Modified

```
M	.github/scripts/ci.sh
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-local.yml
M	README.md
R100	xxRod/Dockerfile	auKeep/Dockerfile
R055	xxRod/compose.yaml	auKeep/compose.yaml
R100	xxRod/docker-compose-build.bat	auKeep/docker-compose-build.bat
R058	xxRod/pom.xml	auKeep/pom.xml
A	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
A	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
A	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
A	auKeep/src/main/resources/application.yml
R096	xxRod/src/main/resources/logback-spring.xml	auKeep/src/main/resources/logback-spring.xml
R061	xxRod/src/test/java/pro/mir0n/esquire/xxRod/RodBusIntegrationTest.java	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
R100	xxRod/src/test/resources/it-account-log.sql	auKeep/src/test/resources/it-account-log.sql
A	audit/pom.xml
A	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
A	audit/src/main/java/pro/mir0n/esquire/audit/AuditKeepDirector.java
A	audit/src/main/java/pro/mir0n/esquire/audit/AuditKinds.java
A	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
R100	common/src/test/resources/META-INF/audit/oracle.xml	audit/src/main/resources/META-INF/audit/oracle.xml
R100	common/src/test/resources/META-INF/audit/postgres.xml	audit/src/main/resources/META-INF/audit/postgres.xml
A	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
A	audit/src/test/java/pro/mir0n/esquire/audit/AuditKeepDirectorTest.java
R062	common/src/test/java/pro/mir0n/esquire/common/audit/AuditKindsTest.java	audit/src/test/java/pro/mir0n/esquire/audit/AuditKindsTest.java
A	audit/src/test/java/pro/mir0n/esquire/audit/AuditSqlTest.java
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeBroadcastConsumer.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeJmsConfig.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/application.yml
D	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditKinds.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogSql.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditRod.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/AuditSettings.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventBusPublisher.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/RodKafkaPublisher.java
D	common/src/main/java/pro/mir0n/esquire/common/audit/RodRedisPublisher.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
D	common/src/main/java/pro/mir0n/esquire/common/xrod/RodEvent.java
D	common/src/main/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistry.java
D	common/src/main/java/pro/mir0n/esquire/common/xrod/XXRod.java
D	common/src/main/java/pro/mir0n/esquire/common/xrod/XYRod.java
D	common/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	common/src/main/java/pro/mir0n/utils/changes.txt
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
D	common/src/test/java/pro/mir0n/esquire/common/audit/AuditLogSqlTest.java
D	common/src/test/java/pro/mir0n/esquire/common/audit/AuditRodBusTest.java
D	common/src/test/java/pro/mir0n/esquire/common/audit/RodRedisPublisherTest.java
D	common/src/test/java/pro/mir0n/esquire/common/xrod/XXRodTest.java
D	common/src/test/java/pro/mir0n/esquire/common/xrod/XYRodTest.java
M	common/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
M	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
M	compose/compose-rebuild.bat
M	compose/compose.yaml
A	compose/topology/esquire-topology.yml
A	dataKeep/pom.xml
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/director/IKeepDirector.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepSqlStore.java
R056	common/src/main/java/pro/mir0n/esquire/common/audit/AuditLogWriter.java	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcess.java
A	dataKeep/src/main/resources/spring.properties
M	doc/DatabaseDictionary.md
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.BizTree.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.Haubergeon.md
A	doc/Esquire.MessagingBus.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/H2BizTree.md
M	doc/Logging.md
M	doc/Message.Structure.md
D	doc/Messaging.First.md
M	doc/Messaging.md
M	doc/OCI.Pricing.md
M	doc/Object.Kind.enum.md
M	doc/Testing.md
M	doc/WhereToGo.md
M	doc/img/audit-pipeline.svg
A	doc/img/messaging-bus-architecture.svg
A	doc/img/messaging-bus-classes.svg
A	doc/img/messaging-bus-params.svg
A	doc/logo/activemq.png
A	doc/logo/angular.svg
A	doc/logo/bizTree.png
A	doc/logo/enyMan.3.png
A	doc/logo/esquire.png
A	doc/logo/gateway.svg
A	doc/logo/gatling.svg
A	doc/logo/h2.svg
A	doc/logo/hauberk.svg
A	doc/logo/java.svg
A	doc/logo/kafka.svg
A	doc/logo/kcMaster.png
A	doc/logo/keep.svg
A	doc/logo/keySmith.3.png
A	doc/logo/keycloak.png
A	doc/logo/node.js.svg
A	doc/logo/oracle.svg
A	doc/logo/pac-man.2.svg
A	doc/logo/pac-man.svg
A	doc/logo/postgres.svg
A	doc/logo/redis.svg
A	doc/logo/spring-boot.svg
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
A	doc/reports/report_v1.2.7.md
M	doc/services.configuring.md
M	doc/v1.2.x.Planning.md
A	doc/v128.tasks.md
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EnyManJmsConfig.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastConsumer.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListener.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
D	enyMan/src/main/resources/META-INF/audit/oracle.xml
D	enyMan/src/main/resources/META-INF/audit/postgres.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/application.yml
D	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisherTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisherTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListenerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EntityIdGeneratorTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	favicon.ico
A	helm.svg
A	k8s-oci/esquire-topology.yml
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s-oci/values/postgres.yaml
A	k8s/charts/esquire-aukeep/Chart.yaml
A	k8s/charts/esquire-aukeep/templates/configmap.yaml
R059	k8s/charts/esquire-xxrod/templates/deployment.yaml	k8s/charts/esquire-aukeep/templates/deployment.yaml
R061	k8s/charts/esquire-xxrod/templates/secret.yaml	k8s/charts/esquire-aukeep/templates/secret.yaml
A	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/values.yaml
A	k8s/charts/esquire-topology/Chart.yaml
A	k8s/charts/esquire-topology/esquire-topology.yml
A	k8s/charts/esquire-topology/templates/configmap.yaml
A	k8s/charts/esquire-topology/values.yaml
D	k8s/charts/esquire-xxrod/Chart.yaml
D	k8s/charts/esquire-xxrod/templates/configmap.yaml
D	k8s/charts/esquire-xxrod/values.yaml
A	k8s/charts/infra/kafka/Chart.yaml
A	k8s/charts/infra/kafka/templates/deployment.yaml
A	k8s/charts/infra/kafka/templates/service.yaml
A	k8s/charts/infra/kafka/values.yaml
M	k8s/charts/infra/postgres/templates/statefulset.yaml
M	k8s/charts/infra/postgres/values.yaml
A	k8s/charts/infra/redis/Chart.yaml
A	k8s/charts/infra/redis/templates/deployment.yaml
A	k8s/charts/infra/redis/templates/service.yaml
A	k8s/charts/infra/redis/values.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
R069	k8s/values/xxrod.yaml	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcMasterJmsConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisherTest.java
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncResponseListener.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KeySmithJmsConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
D	keySmith/src/main/resources/META-INF/audit/oracle.xml
D	keySmith/src/main/resources/META-INF/audit/postgres.xml
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/import/esquire.json
A	messaging/pom.xml
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusNode.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusRef.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusSlot.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/Role.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
R087	common/src/main/java/pro/mir0n/esquire/common/xrod/IRodRepository.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IRodEventRepo.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java
R052	common/src/main/java/pro/mir0n/esquire/common/audit/RodEventCodec.java	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistry.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodAutoConfiguration.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRods.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
A	messaging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
A	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
R053	common/src/test/java/pro/mir0n/esquire/common/audit/RodEventCodecTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
R066	common/src/test/java/pro/mir0n/esquire/common/xrod/RodRepositoryRegistryTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
A	pacMan/.mvn/.idea/.gitignore
A	pacMan/.mvn/.idea/.mvn.iml
A	pacMan/.mvn/.idea/libraries/maven_wrapper.xml
A	pacMan/.mvn/.idea/misc.xml
A	pacMan/.mvn/.idea/modules.xml
A	pacMan/.mvn/wrapper/maven-wrapper.jar
A	pacMan/.mvn/wrapper/maven-wrapper.properties
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/PacManJmsConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
D	pacMan/src/main/resources/META-INF/audit/oracle.xml
D	pacMan/src/main/resources/META-INF/audit/postgres.xml
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
D	pacMan/src/test/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisherTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
A	test/audit-smoke/README.md
A	test/audit-smoke/run.sh
A	tp-activemq/pom.xml
A	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
R093	common/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
A	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TpActiveMqAutoConfigFilter.java
A	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
A	tp-activemq/src/main/resources/META-INF/spring.factories
A	tp-kafka/pom.xml
A	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TpKafkaAutoConfigFilter.java
A	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
A	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
A	tp-kafka/src/main/resources/META-INF/spring.factories
A	tp-redis/pom.xml
A	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TpRedisAutoConfigFilter.java
A	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
A	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
A	tp-redis/src/main/resources/META-INF/spring.factories
D	xxRod/logs/xxRod-develop.log
D	xxRod/logs/xxRod-develop.log.2026-06-06.gz
D	xxRod/logs/xxRod-msg.log
D	xxRod/logs/xxRod-msg.log.2026-06-06.gz
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/XxRodApplication.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/changes.txt
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/AuditRodDirector.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/IRodDirector.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/director/RodDirectorHost.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumer.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/RodKafkaConsumer.java
D	xxRod/src/main/java/pro/mir0n/esquire/xxRod/messaging/XxRodJmsConfig.java
D	xxRod/src/main/resources/META-INF/audit/oracle.xml
D	xxRod/src/main/resources/META-INF/audit/postgres.xml
D	xxRod/src/main/resources/application.yml
D	xxRod/src/test/java/pro/mir0n/esquire/xxRod/director/AuditRodDirectorTest.java
D	xxRod/src/test/java/pro/mir0n/esquire/xxRod/messaging/RodAuditConsumerTest.java
 346 files changed, 13369 insertions(+), 8311 deletions(-)
```

---

*From `v1.2.7` till `v1.2.8`*

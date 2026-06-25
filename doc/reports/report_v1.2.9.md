# Release Report: v1.2.8 → v1.2.9

**Repo:** `esquire.services/develop`  
**Top commit:** `399b915`

---

## Release Notes

### doc/release_notes.txt


**v1.2.9-2606.2417**  v1.2.9 -- hardening: the bus keep-alive is now optional with a clearer health and role model; the bus wire constants moved into the messaging module  
&nbsp;: Feature:     the bus keep-alive (heartbeat) is an opt-in per-leg setting  
&nbsp;: Feature:     a Redis or Kafka leg wired as a consumer now fails to start with a clear message  
&nbsp;: Feature:     a keep-alive on Redis/Kafka stays out of the audit log -- a separate ".admin" stream/topic carries it  
&nbsp;: Refactoring: the bus message field names and type codes (the wire protocol) moved out of the shared common  
&nbsp;                 library into the messaging module  
&nbsp;: Refactoring: the unused BOTH role was removed -- a leg is a client or a server  
&nbsp;: Doc:         doc\services.configuring.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Message.Structure.md  
&nbsp;                 doc\img\messaging-bus-classes.svg  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 audit,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 bizTree,  
&nbsp;                 auKeep,  
&nbsp;                 tp-activemq,  
&nbsp;                 tp-redis,  
&nbsp;                 tp-kafka  

**v1.2.9-2606.2318**  v1.2.9 -- hardening: the messaging-bus connection sends a regular keep-alive so an outage is caught on every transport  
&nbsp;: Feature:     each messaging-bus connection now suppports keep-alive protocol  
&nbsp;: Refactoring: the keep-alive runs from ONE shared timer  
&nbsp;: Fix:         the keep database health check now reports the database DOWN within a few seconds  
&nbsp;: Config:      the OKE deploy now installs the shared messaging-bus topology (was missing); dead legacy broker fields dropped from the OKE values  
&nbsp;: Doc:         doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Message.Structure.md  
&nbsp;                 doc\Messaging.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\img\messaging-bus-classes.svg  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 dataKeep,  
&nbsp;                 auKeep,  
&nbsp;                 oke  

**v1.2.9-2606.2223**  v1.2.9 -- hardening: each service reports its messaging-bus connection on its health check (feature)  
&nbsp;: Feature:     each service now reports whether its messaging-bus connection is up on the standard health check;  
&nbsp;: Feature:     the health check is split into "ready to serve" and "still alive"  
&nbsp;: Feature:     auKeep additionally reports the keep database (the apply side) on its "ready to serve" check  
&nbsp;: Config:      each service application.yml gained a management block (health probes, a readiness group);  
&nbsp;                 the k8s charts point the readiness and liveness probes at the split health paths  
&nbsp;: Config:      the shared messaging topology now reaches ActiveMQ through a failover endpoint with a 3s  
&nbsp;                 connect timeout, so a slow broker fails fast instead of hanging  
&nbsp;: Doc:         doc\Esquire.MessagingBus.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\Messaging.md  
&nbsp;   Components:   messaging,  
&nbsp;                 dataKeep,  
&nbsp;                 tp-activemq,  
&nbsp;                 tp-kafka,  
&nbsp;                 tp-redis,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 bizTree,  
&nbsp;                 auKeep,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.9-2606.2220**  v1.2.9 -- hardening: Messaging Bus framework -- a single Facade API + a service lifecycle (refactoring)  
&nbsp;: Refactoring: introduces the Messaging Bus framework's Facade API  
&nbsp;                 and a defined framework lifecycle the facade drives  
&nbsp;: Refactoring: each service's two old per-channel handlers (one to send, one to receive) fold into a single  
&nbsp;                 adapter per channel  
&nbsp;: Fix:         a service that names a bus it does not actually have now fails fast at boot with a clear error  
&nbsp;                 instead of silently doing nothing; to run without a bus it must say so on purpose  
&nbsp;: Config:      a new "audit-off" bus turns the bus audit off explicitly  
&nbsp;: Doc:         doc\Esquire.MessagingBus.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;   Components:   messaging,  
&nbsp;                 dataKeep,  
&nbsp;                 audit,  
&nbsp;                 auKeep,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 bizTree,  
&nbsp;                 tp-activemq,  
&nbsp;                 tp-kafka,  
&nbsp;                 tp-redis  

**v1.2.9-2606.2118**  v1.2.9 -- hardening: messaging-bus topology validation; a misconfigured bus disables, not crashes  
&nbsp;: Refactoring: the bus catalog is checked at startup -- a duplicate bus / slot / network-node id fails fast  
&nbsp;                 with a clear error; a bus key that points at nothing now disables that leg (with a one-line  
&nbsp;                 notice in the log) instead of crashing the service at boot  
&nbsp;: Feature:     a service's own bus definitions now layer onto the shared topology by id  
&nbsp;: Config:      the shared-topology import is now optional (a service may define its buses inline, or use none)  
&nbsp;: Config:      the bus slot-id environment variables renamed -- AUDIT_SERVICE_ID / KC_SERVICE_ID /  
&nbsp;                 ENTITY_SERVICE_ID -> AUDIT_SLOT_ID / KC_SLOT_ID / ENTITY_SLOT_ID (a bus leg is a "slot")  
&nbsp;: Doc:         doc\Esquire.MessagingBus.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;   Components:   messaging,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 auKeep,  
&nbsp;                 bizTree,  
&nbsp;                 kcMaster,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.9-2606.2114**  v1.2.9 -- hardening: dataKeep reads its SQL dialect from the database URL; audit config keys tidied  
&nbsp;: Refactoring: the keep figures out its SQL dialect from the database URL (jdbc:postgresql.. -> Postgres,  
&nbsp;                 jdbc:oracle.. -> Oracle); the separate vendor setting it used before is removed  
&nbsp;: Config:      the in-process audit datastore block renamed: log-db -> datasource (its vendor entry dropped)  
&nbsp;: Config:      audit and topology env vars renamed -- ESQUIRE_AUDIT_* -> AUDIT_*,  
&nbsp;                 ESQUIRE_AUDIT_LOG_DB_* -> DB_DATAKEEP_*, ESQUIRE_TOPOLOGY_IMPORT -> TOPOLOGY_IMPORT  
&nbsp;                 (application.yml, compose, k8s charts, and the shared topology)  
&nbsp;: Doc:         doc\services.configuring.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\Messaging.md  
&nbsp;   Components:   dataKeep,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 auKeep,  
&nbsp;                 messaging,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.9-2606.2111**  v1.2.9 -- hardening: the audit/broadcast message decoder shrugs off a bad field, refuses an unknown format  
&nbsp;: Fix:         one malformed field in an incoming bus message no longer discards the whole message -- the bad  
&nbsp;                 field falls back to a default and the message is still processed  
&nbsp;: Fix:         a message stamped with a different (newer) wire-format version is refused outright instead of  
&nbsp;                 being silently mis-read  
&nbsp;   Components:   messaging  

**v1.2.9-2606.2110**  v1.2.9 -- schema dictionary synced to the db.seed schema changes  
&nbsp;: Doc:         doc\DatabaseDictionary.md -- entity created-timestamp columns (ORG/USR/ACC_CREATED_TS) and  
&nbsp;                 the optional, apply-on-demand *_log *_ACTION_TS indexes added; mirrors the v1.2.9 db.seed change  

**v1.2.9-2606.2108**  v1.2.9 -- hardening: activemq "pubSubDomain" instead of "topic"  
&nbsp;: Refactoring: "topic" moved under vendor params as "pubSubDomain"  (ActiveMQ specific)  
&nbsp;: Config:      use ActiveMQ pubSubDomain instead of topic  
&nbsp;: Doc:         doc\services.configuring.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;   Components:   messaging,  
&nbsp;                 tp-activemq,  
&nbsp;                 compose,  
&nbsp;                 k8s,  
&nbsp;                 oke  

---

## Code Changes

### auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
AuKeepApplication  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.AuditConsumerConfig**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  

**06/22/2026** mir0n  messaging-bus health indicator + keepDatasource health + readiness/liveness  
AuKeepApplication  
&nbsp;- the MessagingBusLifecycleRegistrar, at ApplicationReadyEvent, registers (programmatically, no @Bean) a  
&nbsp;   BusHealthIndicator for the bus facade AND a TransportHealthIndicator named "keepDatasource" over  
&nbsp;   AuditConsumerConfig.keepHealth() (the keep DB) into the Actuator HealthContributorRegistry -> /actuator/health  
**messaging.AuditConsumerConfig**  
&nbsp;- added keepHealth() -> Supplier over the keep applier (UP when no keep is active); the  
&nbsp;   lifecycle registrar registers it as the "keepDatasource" health contributor  
**application.yml**  
&nbsp;- added a management block: health probes.enabled=true, show-details=always, validate-group-membership=false,  
&nbsp;   and a readiness group including readinessState + messagingBus + keepDatasource  
k8s chart esquire-aukeep/templates/deployment.yaml  
&nbsp;- split the probes: readinessProbe -> /actuator/health/readiness, livenessProbe -> /actuator/health/liveness  

**06/22/2026** mir0n  v1.2.9 -- bus two-phase lifecycle; audit consumer rewired onto the facade  
AuKeepApplication  
&nbsp;- wires the bus lifecycle via the MessagingBusLifecycleRegistrar inner listener (LOWEST_PRECEDENCE):  
&nbsp;   env-prepared -> bus.init(env, {BUS_KEY_AUDIT}); ready -> bus.start(); context-closed -> bus.close();  
&nbsp;   the ApplicationStartingEvent kind-storage load is unchanged  
**messaging.AuditConsumerConfig**  
&nbsp;- rewired onto the facade: takes the audit rod from MessagingBus.getXRod (audit-bus ref role CLIENT) and sets  
&nbsp;   the keep applier as its receive worker; guarded with isEnabled() so an explicitly-disabled audit bus leaves  
&nbsp;   the consumer idle  

**06/21/2026** mir0n  v1.2.9 -- topology import optional; AUDIT_SLOT_ID  
**application.yml**  
&nbsp;- spring.config.import -> optional:file:... (the topology import is now optional); AUDIT_SERVICE_ID -> AUDIT_SLOT_ID  

**06/21/2026** mir0n  v1.2.9 -- keep datasource vendor dropped; audit/topology env vars renamed  
**application.yml**  
&nbsp;- esquire.keep.datasource no longer carries a vendor (the dialect comes from the datasource URL); env vars  
&nbsp;   renamed ESQUIRE_AUDIT_* -> AUDIT_*, ESQUIRE_TOPOLOGY_IMPORT -> TOPOLOGY_IMPORT  

### audit/src/main/java/pro/mir0n/esquire/audit/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
AuditBusBridge  
&nbsp;- EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)  

**06/22/2026** mir0n  v1.2.9 -- AuditBusBridge wraps the facade-built audit x-rod  
AuditBusBridge  
&nbsp;- the constructor takes the IXRod the facade builds (MessagingBus.getInstance().getXRod(BUS_KEY_AUDIT));  
&nbsp;   IXRod / RodEvent imports moved to messaging.xrod  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
BizTreeApplication  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**access.MessageHandlerHub**  
&nbsp;- EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)  
**messaging.EntityBusAdapter**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.handler.CreateAcctHandler**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.handler.CreateOrgHandler**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.handler.CreateUsrHandler**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.handler.MoveAcctHandler**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.handler.MoveOrgHandler**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.handler.MoveUsrHandler**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.handler.UpdateEntityHandler**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  

**06/22/2026** mir0n  messaging-bus health indicator + readiness/liveness  
BizTreeApplication  
&nbsp;- the MessagingBusLifecycleRegistrar, at ApplicationReadyEvent, registers a BusHealthIndicator (the bus  
&nbsp;   facade handed in explicitly) into the Actuator HealthContributorRegistry programmatically -- no @Bean --  
&nbsp;   exposing the bus connection at /actuator/health  
**application.yml**  
&nbsp;- extended the existing management block: added show-details=always, validate-group-membership=false, and  
&nbsp;   messagingBus to the readiness group (now readinessState, cacheReadiness, messagingBus)  
k8s chart esquire-biztree/templates/deployment.yaml  
&nbsp;- probes already split (readiness -> /actuator/health/readiness, liveness -> /actuator/health/liveness); no change  

**06/22/2026** mir0n  v1.2.9 -- entity-bus client adapter + two-phase bus lifecycle  
BizTreeApplication  
&nbsp;- added MessagingBusLifecycleRegistrar inner class (ApplicationListener, Ordered  
&nbsp;   LOWEST_PRECEDENCE so each phase runs after the service's same-event listeners), registered via  
&nbsp;   app.addListeners; drives the bus in two phases: env-prepared -> bus.init(env, {BUS_KEY_ENTITY})  
&nbsp;   builds the rods paused, ready -> bus.start() runs them, context-closed -> bus.close() drains + closes  
&nbsp;- collapsed the prior three separate bus listener inner classes into this one registrar  
messaging.EntityBusAdapter  (new, was BizTreeBroadcastConsumer)  
&nbsp;- @Component, the bizTree end of the entity bus (CLIENT role): receive-only; constructor gets the entity  
&nbsp;   x-Rod via MessagingBus.getInstance().getXRod(BUS_KEY_ENTITY) and rod.setWorker(this::onRodEvent)  
&nbsp;- onRodEvent logs the app-level ENTITY console line, then hands the event to director.onRodEvent(e)  
&nbsp;- replaces BizTreeBroadcastConsumer (deleted) which opened the rod via XRodManager.consumer(..)  
**access.IBizTreeDirector**  
&nbsp;- import update: RodEvent moved to messaging.xrod (was messaging)  
**application.yml**  
&nbsp;- added entity-broadcast-bus.consumer.enabled ${BIZTREE_MESSAGING_CONSUMER_ENABLED:true}; removed the  
&nbsp;   leg role: CLIENT (the CLIENT role is set in code by the adapter, not in the topology)  

**06/21/2026** mir0n  v1.2.9 -- topology import optional; ENTITY_SLOT_ID  
**application.yml**  
&nbsp;- spring.config.import -> optional:file:... (the topology import is now optional); ENTITY_SERVICE_ID -> ENTITY_SLOT_ID  

**06/21/2026** mir0n  v1.2.9 -- topology import env var renamed  
**application.yml**  
&nbsp;- ESQUIRE_TOPOLOGY_IMPORT -> TOPOLOGY_IMPORT  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants split: the non-wire app constants land in EsqConstants  
EsqConstants  
&nbsp;- BUS_KEY_AUDIT/KC/ENTITY, TEXT_* (id/kind/parentId/path/name/desc/status/deleted/ccy),  
&nbsp;   FLAG_OPEN, CCY_DEFAULT moved here from common.EsqMsgConstants (the non-wire app constants)  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**06/23/2026** mir0n  EsqMsgConstants split + moved out of common  
EsqMsgConstants  (removed)  
&nbsp;- deleted; the FIX-JSON wire constants (FIELD_* / MSG_TYPE_* / SCHEMA_VERSION / MSG_ENCODING_JSON / EVENT_*)  
&nbsp;   moved to pro.mir0n.esquire.messaging.BusConstants; common no longer carries any bus-wire definition  
EsqConstants  
&nbsp;- gained the non-wire app constants moved out of EsqMsgConstants: BUS_KEY_AUDIT/KC/ENTITY, TEXT_*  
&nbsp;   (id/kind/parentId/path/name/desc/status/deleted/ccy), FLAG_OPEN, CCY_DEFAULT  

**06/23/2026** mir0n  x-rod alive-protocol session msg-types  
EsqMsgConstants  
&nbsp;- MSG_TYPE_HEARTBEAT = "0" and MSG_TYPE_TEST_REQUEST = "1" -- the FIX-canonical session msg-types for the  
&nbsp;   x-rod alive protocol, beside the U-prefixed application types  

### dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt


**06/23/2026** mir0n  keep datasource: driver connection properties (data-source-properties)  
**keep.KeepDataSourceParams**  
&nbsp;- Hikari record gained dataSourceProperties (bound from data-source-properties, like  
&nbsp;   spring.datasource.hikari.data-source-properties); import java.util.Map  
**keep.KeepApplier**  
&nbsp;- buildPool forwards hikari.data-source-properties to the JDBC driver via addDataSourceProperty -- so pgjdbc  
&nbsp;   socketTimeout / tcpKeepAlive make health() fail fast on a vanished DB instead of hanging on a half-open socket  

**06/22/2026** mir0n  v1.2.9 -- keep datasource health  
**keep.KeepApplier**  
&nbsp;- added health(): pings the keep pool -- a pooled connection that validates within 2s -> UP; any failure  
&nbsp;   (cannot reach / validate the database) -> DOWN. The keep-datasource health source.  
**keep.XRodInProcessKeep**  
&nbsp;- added health(): the in-process keep has no broker, so its "down" risk is the DB it applies to -- it  
&nbsp;   reports keepApplier.health() (the receiver-side DB the generic in-process relay otherwise hides)  

**06/22/2026** mir0n  v1.2.9 -- in-process keep x-rod; shared keep pool mode dropped  
keep.XRodInProcessKeep  (new, was audit.XRodAuditKeep, generalized)  
&nbsp;- the in-process keep x-rod: extends the generic XRodInProcess (moved to the messaging library); validate()  
&nbsp;   requires the leg's datasource.url + a director; init() builds the KeepApplier from the leg's datasource +  
&nbsp;   config-named IKeepDirector, OPENs the engine (super.init -> receive pool, paused), THEN setWorker(applier)  
&nbsp;   (the build-engine-before-setWorker order is required -- setWorker needs the pool to exist); shutdown() drains  
&nbsp;   then closes the keep pool  
**keep.KeepApplier**  
&nbsp;- dropped SHARED pool mode: removed the shared-DataSource constructor and the ownsPool field; a keep is always  
&nbsp;   a DEDICATED pool now -- close() closes its own pool unconditionally; RodEvent/RodEventRepoRegistry imports  
&nbsp;   moved to messaging.xrod  
**keep.KeepDataSourceParams**  
&nbsp;- dropped the shared field and isShared(); the record carries only url/username/password/hikari  
**keep.RodEventDbWriter**  
&nbsp;- RodEvent import moved to messaging.xrod  

**06/21/2026** mir0n  v1.2.9 -- keep dialect derived from the database URL; the datasource vendor setting removed  
**keep.KeepDataSourceParams**  
&nbsp;- removed the vendor field (the record is now url/username/password/hikari/shared) and vendorOr(); when the  
&nbsp;   keep is shared the dialect comes from the service's spring.datasource.url  
**keep.KeepApplier**  
&nbsp;- dedicated-mode dialect is KeepSqlStore.dialectOf(ds.url()) -- read from the datasource URL subprotocol  
&nbsp;   instead of the vendor/profile label  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
EnyManApplication  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**audit.AuditConfig**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.EntityBusAdapter**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  
**messaging.KcBusAdapter**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  
**queue.MoveQueueManager**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  
**service.impl.AcctService**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**service.impl.EnyManService**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  

**06/22/2026** mir0n  messaging-bus health indicator + readiness/liveness  
EnyManApplication  
&nbsp;- the MessagingBusLifecycleRegistrar, at ApplicationReadyEvent, registers a BusHealthIndicator (the bus  
&nbsp;   facade handed in explicitly) into the Actuator HealthContributorRegistry programmatically -- no @Bean --  
&nbsp;   exposing the bus connection at /actuator/health  
**application.yml**  
&nbsp;- added a management block: health probes.enabled=true, show-details=always, validate-group-membership=false,  
&nbsp;   and a readiness group including readinessState + messagingBus  
k8s chart esquire-enyman/templates/deployment.yaml  
&nbsp;- split the probes: readinessProbe -> /actuator/health/readiness, livenessProbe -> /actuator/health/liveness  

**06/22/2026** mir0n  messaging-bus: BusAdapters + lifecycle registrar  
**messaging.KcBusAdapter**  
&nbsp;- created (was KcRequestPublisher + the response worker): the enyMan end of the kc bus (CLIENT) -- one rod,  
&nbsp;   both legs, pulled via MessagingBus.getInstance().getXRod(BUS_KEY_KC) in the ctor; setWorker(onResponse) +  
&nbsp;   transmit(null) probe the receive/transmit legs; publishPathUpdate() transmits an EVENT_UPDATE_PATH URQ to  
&nbsp;   kcMaster after a USR move; onResponse() handles the URS/URR reply for this instance (rod-id selector)  
**messaging.EntityBusAdapter**  
&nbsp;- created (was EsqEntityBroadcastPublisher): the enyMan end of the entity bus (SERVER) -- the transmit leg  
&nbsp;   onto the entity-broadcast topic, rod pulled via getXRod(BUS_KEY_ENTITY) in the ctor + transmit(null) probe;  
&nbsp;   publish() builds a RodEvent (msg-type UE) and transmits it post-commit  
EnyManApplication  
&nbsp;- the 3 separate bus listener inner classes collapsed into one MessagingBusLifecycleRegistrar  
&nbsp;   (ApplicationListener + Ordered.LOWEST_PRECEDENCE) registered last; env-prepared ->  
&nbsp;   bus.init(env, {entity,kc,audit}), ready -> bus.start(), context-closed -> bus.close(); the ReadyListener  
&nbsp;   keeps only the roles load  
**audit.AuditConfig**  
&nbsp;- the audit @Bean wraps MessagingBus.getInstance().getXRod(BUS_KEY_AUDIT) in an AuditBusBridge; the per-leg  
&nbsp;   sink-selection logic + the service-DataSource inject removed (the keep owns its datasource, the leg the sink)  
queue.MoveQueueManager, service.impl.EnyManService  
&nbsp;- bus-adapter rename in fields/ctor params/imports: EsqEntityBroadcastPublisher -> EntityBusAdapter,  
&nbsp;   KcRequestPublisher -> KcBusAdapter (KcBusAdapter only in MoveQueueManager)  
service.impl.AcctService / OrgService / UsrService  
&nbsp;- RodEvent import retargeted messaging.xrod.RodEvent -> messaging.RodEvent (package move)  
**application.yml**  
&nbsp;- each bus ref gains a role: entity SERVER, kc CLIENT, audit SERVER; the audit-b in-process keep rod-class  
&nbsp;   renamed XRodInProcess -> XRodInProcessKeep with director AuditKeepDirector; the datasource block's shared  
&nbsp;   knob dropped (the keep now uses its OWN dedicated *_log pool from url/hikari)  

**06/21/2026** mir0n  v1.2.9 -- audit leg disables (not crashes) on an unknown bus; topology import optional; SLOT_ID  
**audit.AuditConfig**  
&nbsp;- resolves the audit leg via catalog.find() (was the strict resolve()), so an unknown audit bus-id disables  
&nbsp;   the producer (XRodDisabled) instead of crashing at boot  
**application.yml**  
&nbsp;- spring.config.import -> optional:file:... (the topology import is now optional); the bus-ref slot-id env  
&nbsp;   vars (*_SERVICE_ID) renamed -> *_SLOT_ID  

**06/21/2026** mir0n  v1.2.9 -- audit keep: datasource sub-block + URL-derived dialect  
**audit.AuditConfig**  
&nbsp;- binds the keep datasource from the leg's "datasource" sub-block (was "log-db"); the in-process keep's  
&nbsp;   dialect comes from spring.datasource.url (shared) instead of the spring.profiles.active label  
**application.yml**  
&nbsp;- the in-process audit-b leg's log-db block renamed to datasource (vendor entry dropped); env vars renamed  
&nbsp;   ESQUIRE_AUDIT_* -> AUDIT_*, ESQUIRE_AUDIT_LOG_DB_* -> DB_DATAKEEP_*, ESQUIRE_TOPOLOGY_IMPORT -> TOPOLOGY_IMPORT  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
KcMasterApplication  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.EntityBusAdapter**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.KcBusAdapter**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  
**messaging.KcRequestHandler**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  

**06/22/2026** mir0n  messaging-bus health indicator + readiness/liveness  
KcMasterApplication  
&nbsp;- the MessagingBusLifecycleRegistrar, at ApplicationReadyEvent, registers a BusHealthIndicator (the bus  
&nbsp;   facade handed in explicitly) into the Actuator HealthContributorRegistry programmatically -- no @Bean --  
&nbsp;   exposing the bus connection at /actuator/health  
**application.yml**  
&nbsp;- added a management block: health probes.enabled=true, show-details=always, validate-group-membership=false,  
&nbsp;   and a readiness group including readinessState + messagingBus  
k8s chart esquire-kcmaster/templates/deployment.yaml  
&nbsp;- split the probes: readinessProbe -> /actuator/health/readiness, livenessProbe -> /actuator/health/liveness  

**06/22/2026** mir0n  messaging-bus: BusAdapters + lifecycle registrar  
KcMasterApplication  
&nbsp;- MessagingBusLifecycleRegistrar inner class (ApplicationListener, Ordered.LOWEST_PRECEDENCE),  
&nbsp;   registered; ApplicationEnvironmentPreparedEvent -> MessagingBus.init(env, {BUS_KEY_KC, BUS_KEY_ENTITY}),  
&nbsp;   ApplicationReadyEvent -> start(), ContextClosedEvent -> close() (no roles-Ready listener -- the registrar  
&nbsp;   owns start)  
messaging.KcBusAdapter  (new, was KcRequestConsumer + KcResponsePublisher)  
&nbsp;- one kc-SERVER rod (MessagingBus.getXRod(BUS_KEY_KC)), both legs: onRodEvent() set as the rod worker receives  
&nbsp;   a URQ (no selector -- shared work), converts the body to KcSyncRequest, dispatches to KcRequestHandler,  
&nbsp;   publishSuccess() transmits URS / publishFailure() transmits URR (RFC-9457 error + original request) with the  
&nbsp;   requester rod-id echoed; setWorker + transmit(null) probe the legs  
&nbsp;- KcRequestConsumer + KcResponsePublisher deleted  
messaging.EntityBusAdapter  (new, was KcEntityBroadcastConsumer)  
&nbsp;- entity-CLIENT receive worker (MessagingBus.getXRod(BUS_KEY_ENTITY), receive-only); onRodEvent() handles a  
&nbsp;   move (Op.UPDATE_PATH), parks the new path in KcPathBuffer when the KC user does not exist yet (race-8c  
&nbsp;   safety net), else stays passive (the URQ handler owns existing users)  
&nbsp;- KcEntityBroadcastConsumer deleted  
**application.yml**  
&nbsp;- kc bus ref role: SERVER; entity bus ref role: CLIENT; the kcmaster.entity-broadcast-bus /  
&nbsp;   kcmaster.kc-request-bus consumer.enabled toggles removed  

**06/21/2026** mir0n  v1.2.9 -- topology import optional; *_SLOT_ID  
**application.yml**  
&nbsp;- spring.config.import -> optional:file:... (the topology import is now optional); KC_SERVICE_ID / ENTITY_SERVICE_ID -> *_SLOT_ID  

**06/21/2026** mir0n  v1.2.9 -- topology import env var renamed  
**application.yml**  
&nbsp;- ESQUIRE_TOPOLOGY_IMPORT -> TOPOLOGY_IMPORT  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
KeySmithApplication / audit.AuditConfig / messaging.KcBusAdapter  
&nbsp;- EsqMsgConstants references repointed: wire constants -> messaging.BusConstants, app constants -> common.EsqConstants  

**06/22/2026** mir0n  messaging-bus health indicator + readiness/liveness  
KeySmithApplication  
&nbsp;- the MessagingBusLifecycleRegistrar, at ApplicationReadyEvent, registers a BusHealthIndicator (the bus  
&nbsp;   facade handed in explicitly) into the Actuator HealthContributorRegistry programmatically -- no @Bean --  
&nbsp;   exposing the bus connection at /actuator/health  
**application.yml**  
&nbsp;- added a management block: health probes.enabled=true, show-details=always, validate-group-membership=false,  
&nbsp;   and a readiness group including readinessState + messagingBus  
k8s chart esquire-keysmith/templates/deployment.yaml  
&nbsp;- split the probes: readinessProbe -> /actuator/health/readiness, livenessProbe -> /actuator/health/liveness  

**06/22/2026** mir0n  messaging-bus: BusAdapters + lifecycle registrar  
KeySmithApplication  
&nbsp;- MessagingBusLifecycleRegistrar inner class (ApplicationListener, Ordered.LOWEST_PRECEDENCE),  
&nbsp;   registered LAST; ApplicationEnvironmentPreparedEvent -> MessagingBus.init(env, {BUS_KEY_KC, BUS_KEY_AUDIT}),  
&nbsp;   ApplicationReadyEvent -> start() (after roles load), ContextClosedEvent -> close()  
messaging.KcBusAdapter  (new, was KcSyncPublisher + KcSyncResponseListener)  
&nbsp;- one kc-CLIENT rod (MessagingBus.getXRod(BUS_KEY_KC)), both legs: publish() transmits the URQ  
&nbsp;   (delete/create/update branch off oldConnectFlg vs connectFlg), onResponse() set as the rod worker  
&nbsp;   receives URS/URR; setWorker + transmit(null) probe the receive/transmit legs  
&nbsp;- KcSyncPublisher + KcSyncResponseListener deleted  
**service.impl.KeySmithService**  
&nbsp;- kcSyncPublisher field/import KcSyncPublisher -> KcBusAdapter; RodEvent import messaging.xrod.RodEvent ->  
&nbsp;   messaging.RodEvent  
**audit.AuditConfig**  
&nbsp;- auditBusBridge() returns new AuditBusBridge(MessagingBus.getXRod(BUS_KEY_AUDIT)); the leg-selecting keep  
&nbsp;   wiring (XRodManager/MessagingBusCatalog, KeepApplier/KeepSqlStore, datasource sub-block, @PreDestroy close)  
&nbsp;   removed -- the facade builds the audit rod from the role-SERVER ref, the keep owns its datasource  
**application.yml**  
&nbsp;- kc bus ref role: CLIENT; audit bus ref role: SERVER; audit-b leg rod-class  
&nbsp;   XRodInProcess -> XRodInProcessKeep + director AuditKeepDirector; datasource sub-block dropped shared (own  
&nbsp;   dedicated *_log pool)  

**06/21/2026** mir0n  v1.2.9 -- audit leg disables (not crashes) on an unknown bus; topology import optional; SLOT_ID  
**audit.AuditConfig**  
&nbsp;- resolves the audit leg via catalog.find() (was the strict resolve()), so an unknown audit bus-id disables  
&nbsp;   the producer (XRodDisabled) instead of crashing at boot  
**application.yml**  
&nbsp;- spring.config.import -> optional:file:... (the topology import is now optional); the bus-ref slot-id env  
&nbsp;   vars (*_SERVICE_ID) renamed -> *_SLOT_ID  

**06/21/2026** mir0n  v1.2.9 -- audit keep: datasource sub-block + URL-derived dialect  
**audit.AuditConfig**  
&nbsp;- binds the keep datasource from the leg's "datasource" sub-block (was "log-db"); the in-process keep's  
&nbsp;   dialect comes from spring.datasource.url (shared) instead of the spring.profiles.active label  
**application.yml**  
&nbsp;- the in-process audit-b leg's log-db block renamed to datasource (vendor entry dropped); env vars renamed  
&nbsp;   ESQUIRE_AUDIT_* -> AUDIT_*, ESQUIRE_AUDIT_LOG_DB_* -> DB_DATAKEEP_*, ESQUIRE_TOPOLOGY_IMPORT -> TOPOLOGY_IMPORT  

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants split (wire -> BusConstants); alive protocol opt-in + worst-of health + supportsBothLegs role guard + Role.BOTH removed  
BusConstants  (new)  
&nbsp;- the messaging-bus FIX-JSON wire constants -- moved from common.EsqMsgConstants into  
&nbsp;   the messaging module (where the bus framework lives); the non-wire app constants  
&nbsp;   (BUS_KEY_* / TEXT_* / FLAG_OPEN / CCY_DEFAULT) split out to common.EsqConstants, so common no  
&nbsp;   longer carries any bus-wire definition.  
RodEvent  
&nbsp;- EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)  
RodEventCodec  
&nbsp;- EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)  
IXRod  
&nbsp;- configure() javadoc: role list CLIENT/SERVER (BOTH removed)  
Role  
&nbsp;- BOTH removed from the role list -- the constants are now CLIENT / SERVER (BOTH was unused)  
XRodParams  
&nbsp;- 'alive' on/off knob in SCALARS + aliveOr(boolean) getter -- the session is OPT-IN, default off  
ITransportProvider  
&nbsp;- supportsBothLegs() default-true SPI method -- whether a single rod can run both legs (transmit  
&nbsp;+ receive) on the transport's node; false for a produce-only transport, so the bus fails a  
&nbsp;   CLIENT role fast over one that cannot  
XRod  
&nbsp;- alive is OPT-IN: init builds the AliveSession only when the 'alive' param is set; health() =  
&nbsp;   worst(transport indicator [worst of the transmit + receive legs], alive metric when enabled);  
&nbsp;   transmits() role+alive-aware (a single-node CLIENT opens a producer leg only to self-heartbeat,  
&nbsp;   i.e. when alive on); receives() = role==CLIENT (BOTH removed); init FAILS FAST when a receiving  
&nbsp;   role hits a transport that cannot run the needed legs (supportsBothLegs / supportsConsume)  
XRodRR  
&nbsp;- buildKeepAlive() javadoc: "an R&R SERVER" (BOTH removed from the role list)  

**06/23/2026** mir0n  x-rod alive protocol (HeartBeat / TestRequest session layer)  
AliveSession  (new)  
&nbsp;- the x-rod session collaborator: producer/consumer leg timestamps + lastSendAttempt, timestamp-age health  
&nbsp;   (producer leg only; sendBroken fast-path when alive-fail-fast), the keep-alive cadence step tick(), and  
&nbsp;   receivedSession() (advance the consumer ts + run the rod's session-msg handler)  
&nbsp;- tick() driven externally (no own thread); start() seeds the timestamps; injectable-clock test seam  
RodEvent  
&nbsp;- bodyText component added (a prepared JSON Text string; null = serialize the body Map) + an 11-arg delegating  
&nbsp;   constructor so existing callers are unchanged  
&nbsp;- isSession; session(msgType,corr,req,rod,body) factory (decode); heartbeat(corr,req,rod) / testRequest(corr,rod)  
&nbsp;   build the prepared bodyText (constant HEARTBEAT_BODY + concat templates); opCode() null-safe (no op on a session event)  
RodEventCodec  
&nbsp;- toProps/fromProps branch on isSession: a session message rides the reduced field set (no EventType / EntityKind /  
&nbsp;   EntityID / SubID / ActionTime / Uid / header TestReqID; RequestID omitted on an unsolicited HeartBeat)  
&nbsp;- textOf() writes a prepared bodyText straight to Text (no Map, no Jackson), else serializes the body Map  
IXRod  
&nbsp;- idle() default no-op added: the per-rod maintenance hook the MessagingBus idle ticker fires  
AXRod  
&nbsp;- session field; sendOut marks the alive send-attempt/sent/failed; receive intercepts a session message  
&nbsp;   (receivedSession, not forwarded to the worker) and marks an app receive; runEngine seeds the session; idle()  
&nbsp;   drives session.tick()  
XRod  
&nbsp;- init builds the AliveSession (heartbeat-interval / alive-timeout / alive-fail-fast); transmits() now always  
&nbsp;   true (a broadcast CLIENT auto-opens a producer leg to self-heartbeat); health() = session.health();  
&nbsp;   buildKeepAlive() (unsolicited HeartBeat) + onSessionMsg() (no-op) + newCorrelationId() hooks; role protected  
XRodRR  
&nbsp;- buildKeepAlive() (CLIENT emits TestRequest, SERVER/BOTH an unsolicited HeartBeat); onSessionMsg() (SERVER  
&nbsp;   echoes a received TestRequest back as a HeartBeat, routing echoed)  
MessagingBus  
&nbsp;- one per-service idle ticker (scheduleWithFixedDelay, daemon "messaging-idle"): start()/close() manage it;  
&nbsp;   idleSweep() fires IXRod.idle() on every rod and catches Throwable per rod (a fixed-delay task that lets a  
&nbsp;   Throwable escape is silently cancelled), logging on the develop tier  
XRodParams  
&nbsp;- SCALARS + getters for heartbeat-interval (10s) / alive-timeout (3x) / alive-fail-fast; boolOr with a default  

**06/22/2026** mir0n  messaging-bus connection health indicator  
TransportHealth  
&nbsp;- new connection health a transport leg reports: UP (broker connection established), DOWN (dropped / failed  
&nbsp;   to connect), UNKNOWN (this transport cannot observe its connection)  
&nbsp;- worst() folds two legs (DOWN worst, then UNKNOWN, then UP)  
TransportPublisher  
&nbsp;- health() default added (UNKNOWN unless the provider can observe it)  
&nbsp;- of(sink,closer) delegates to a new of(sink,closer,healthSupplier) overload that surfaces the supplier  
TransportConsumer  
&nbsp;- health() default added (UNKNOWN unless the provider can observe it)  
&nbsp;- of(starter,closer) delegates to a new of(starter,closer,healthSupplier) overload that surfaces the supplier  
RodPublisher  
&nbsp;- health() default added; of(dispatcher,closer) surfaces the closer's TransportPublisher health (the closer's  
&nbsp;   health() iff it is a TransportPublisher, else UNKNOWN)  
IXRod  
&nbsp;- health() default added = TransportHealth.UP (an in-process / disabled / log-only rod has no broker  
&nbsp;   connection that can drop); a transport-backed rod overrides it  
XRod  
&nbsp;- health() = worst of the transmit (outboundCloser) + receive (inbound) legs, each ignored when null (a leg  
&nbsp;   the role does not run)  
&nbsp;- outboundCloser retyped AutoCloseable -> RodPublisher  
MessagingBus  
&nbsp;- health() added: a busKey -> TransportHealth map (each built rod's health()), the source the bus health  
&nbsp;   indicator forwards to /actuator/health  
BusHealthIndicator  
&nbsp;- new Actuator HealthIndicator that forwards MessagingBus.health() to /actuator/health: DOWN if any bus  
&nbsp;   connection is down; UNKNOWN buses reported but do NOT fail it  
&nbsp;- register(ctx,bus) registers it programmatically (no @Bean) into the Actuator HealthContributorRegistry under  
&nbsp;   name "messagingBus"; the per-service readiness group includes it (not liveness)  
TransportHealthIndicator  
&nbsp;- new generic Actuator HealthIndicator over a SINGLE TransportHealth source (a keep datasource, a leg, ...):  
&nbsp;   DOWN when the source is DOWN, UP otherwise (UNKNOWN reported as a detail)  
&nbsp;- registered programmatically (no @Bean) into the Actuator registry; used by auKeep for its keep-datasource  

**06/22/2026** mir0n  Messaging Bus Facade API + framework lifecycle (refactoring): MessagingBus facade replaces XRodManager; package restructure; #17 fail-fast  
MessagingBus  
&nbsp;- new per-service messaging facade: a singleton (getInstance) with a two-phase lifecycle -- init(Environment)  
&nbsp;   loads + validates the catalog and BUILDS every bus ref that declares a role  
&nbsp;   (esquire..messaging-bus.role) into a busKey -> x-rod map, PAUSED; start() runs every built rod;  
&nbsp;   getXRod(busKey) hands a rod to a publisher; close() shuts them down. Absorbs the former XRodManager  
&nbsp;- getXRod THROWS for an unbuilt bus key (a role-declared ref with no leg fails fast at init); no silent OFF  
&nbsp;- replaces XRodManager / XRods / XRodAutoConfiguration (removed)  
IXRod  
&nbsp;- moved to messaging (was messaging.xrod); lifecycle split: start(name,devLog,worker) ->  
&nbsp;   init(name,devLog) (create the legs paused) + setWorker(worker) + start(); Role is CLIENT/SERVER/BOTH  
&nbsp;   (BROADCAST removed)  
RodEvent / IRodEventRepo / RodEventRepoRegistry  
&nbsp;- moved to messaging (was messaging.xrod)  
AXRod  
&nbsp;- two-phase engine: startEngine -> buildEngine (create the pool/feed idle) + runEngine (run them); start()  
&nbsp;   runs runEngine  
&nbsp;- setWorker(worker) added (the live receive callback); throws if the rod has NO receive pool  
&nbsp;- transmit() throws if the rod has NO transmit feed and ignores a null event (the publisher-leg probe)  
&nbsp;- import Role/XRodParams from messaging.catalog, IXRod/RodEvent from messaging  
XRod  
&nbsp;- start(name,devLog,worker) split into init(name,devLog) (create the legs by role; the transport consumer  
&nbsp;   created PAUSED) + start() (runEngine, then inbound.start() begins consumer delivery)  
&nbsp;- transmits()/receives() added (legs from role); validate() now REQUIRES a complete transport  
&nbsp;- inbound is a TransportConsumer; role is CLIENT/SERVER/BOTH  
XRodRR  
&nbsp;- transmits()/receives() overridden to true (R&R runs both legs for its role); validate() now REQUIRES a  
&nbsp;   complete transport  
XRodInProcess  
&nbsp;- moved to messaging.xrod.impl (was dataKeep.keep); start(name,devLog,worker) split into init (buildEngine,  
&nbsp;   the feed loops into the pool) + start() (inherited runEngine); worker set via setWorker; no longer final  
XRodDisabled  
&nbsp;- start(name,devLog,worker) split into setWorker (no-op) + init (no-op) + start (no-op); selected ON PURPOSE  
&nbsp;   via rod-class=XRodDisabled (the facade no longer falls back to it)  
XRodInfo  
&nbsp;- start(name,devLog,worker) split into setWorker (no-op) + init (the log-line setup) + start (no-op)  
ITransportProvider  
&nbsp;- openConsumer returns a TransportConsumer (created PAUSED) instead of a bare AutoCloseable -- the listener  
&nbsp;   subscribes but delivers nothing until TransportConsumer.start()  
TransportConsumer  
&nbsp;- new consume-side handle: a listener created PAUSED plus start() (begin delivery) and close() (stop +  
&nbsp;   release the broker connection); the two-phase mirror of TransportPublisher  
catalog (package)  
&nbsp;- BusNode / BusRef / BusSlot / BusTransport / MessagingBus / MessagingBusCatalog / Role / XRodParams moved to  
&nbsp;   messaging.catalog (was messaging)  
BusRef  
&nbsp;- role component added (CLIENT/SERVER/BOTH); a ref that declares a role is one the facade builds at init  
Role  
&nbsp;- BROADCAST -> BOTH; the constants are CLIENT/SERVER/BOTH  
MessagingBusCatalog  
&nbsp;- the bind+merge moved out of the constructor into an explicit synchronized load() (buses null until load(),  
&nbsp;   buses() accessor throws if unloaded); javadoc XRodManager -> MessagingBus  
RodEventCodec / RodTransportAdapter  
&nbsp;- import RodEvent from messaging (was messaging.xrod)  
RodPublisher  
&nbsp;- import RodEvent from messaging (was the same xrod package)  

**06/21/2026** mir0n  v1.2.9 -- topology functional validation; catalog overlay merge-by-id; javadoc genericized  
MessagingBusCatalog  
&nbsp;- validate(): fail-fast at construction on a duplicate bus-id (catalog) / slot-id (within a bus) / node-id  
&nbsp;   (within an x-rod's transport.nodes), run PER SOURCE (shared catalog + overlay) before the merge  
&nbsp;- mergeOverlay / mergeSlots: the .messaging-bus overlay MERGES onto the shared esquire.messaging-bus BY  
&nbsp;   ID -- a service bus/slot REPLACES the shared one with the same id, a new one is appended (was: addAll)  
&nbsp;- find() returns the FIRST match and stops; the scan-all + take-LAST + log.warn removed  
XRodManager  
&nbsp;- a bus key that resolves to no leg logs a CONSOLE info ("...has no leg -> DISABLED...") and runs XRodDisabled  
&nbsp;   (was a develop-tier note); an explicit rod-class=XRodDisabled leg disables without the info  
framework javadoc + doc\Esquire.MessagingBus.md  
&nbsp;- genericized to describe the bus MECHANISM, not where an event is applied (audit / *_log / keep / asset / SQL  
&nbsp;   removed from IRodEventRepo, RodEventRepoRegistry, RodEvent, etc.); xx-Rod -> x-rod  

**06/21/2026** mir0n  v1.2.9 -- RodEventCodec decode hardening  
RodEventCodec  
&nbsp;- fromProps: requireSchemaVersion() gate -- a SchemaVersion present but != the codec's SCHEMA_VERSION is  
&nbsp;   rejected (logged + IllegalStateException); an absent version is tolerated  
&nbsp;- intOf / longOf take (Map, key) and guard the parse: a non-numeric value logs a warn and falls back to 0  
&nbsp;   instead of throwing NumberFormatException (which dropped the whole event); added a develop-tier logger  

**06/21/2026** mir0n  v1.2.9 -- queue-vs-topic removed from the bus (now the ActiveMQ pubSubDomain vendor param)  
TransportSettings  
&nbsp;- the topic field + topic() getter removed; the constructor drops the topic parameter  
PublishSettings / ConsumeSettings  
&nbsp;- the constructors drop the topic parameter (no longer passed to super)  
BusTransport  
&nbsp;- the topic component + topicOrFalse() removed; refinedWith(BusNode) no longer carries topic  
BusNode  
&nbsp;- the topic component removed  
XRodParams  
&nbsp;- transport() builds BusTransport without topic; bindNode() drops the topic parse  
MessagingBusCatalog  
&nbsp;- consumeLeg() builds ConsumeSettings without the topic argument  
XRod  
&nbsp;- publisher() / openConsumer() build the Publish / ConsumeSettings without the topic argument  
XRodRR  
&nbsp;- legTransport() doc: a node owns destination / params (topic dropped)  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
PacManApplication  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**audit.AuditConfig**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.EntityBusAdapter**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  
**service.impl.PacManService**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  

**06/22/2026** mir0n  messaging-bus health indicator + readiness/liveness  
PacManApplication  
&nbsp;- the MessagingBusLifecycleRegistrar, at ApplicationReadyEvent, registers a BusHealthIndicator (the bus  
&nbsp;   facade handed in explicitly) into the Actuator HealthContributorRegistry programmatically -- no @Bean --  
&nbsp;   exposing the bus connection at /actuator/health  
**application.yml**  
&nbsp;- added a management block: health probes.enabled=true, show-details=always, validate-group-membership=false,  
&nbsp;   and a readiness group including readinessState + messagingBus  
k8s chart esquire-pacman/templates/deployment.yaml  
&nbsp;- split the probes: readinessProbe -> /actuator/health/readiness, livenessProbe -> /actuator/health/liveness  

**06/22/2026** mir0n  messaging-bus: EntityBusAdapter + lifecycle registrar  
**messaging.EntityBusAdapter**  
&nbsp;- new (replaces messaging.EsqEntityBroadcastPublisher): the pacMan end of the entity bus (SERVER) -- the  
&nbsp;   transmit leg onto the entity-broadcast TOPIC  
&nbsp;- ctor pulls its rod via MessagingBus.getInstance().getXRod(BUS_KEY_ENTITY) and probes the transmit leg via  
&nbsp;   transmit(null); publish() builds a RodEvent (msg-type UE) and transmits it post-commit  
PacManApplication  
&nbsp;- the 3 bus listener inner classes collapsed into ONE MessagingBusLifecycleRegistrar (Ordered  
&nbsp;   LOWEST_PRECEDENCE, registered last): env-prepared -> bus.init(env, {BUS_KEY_ENTITY, BUS_KEY_AUDIT});  
&nbsp;   ready -> bus.start(); context-closed -> bus.close()  
&nbsp;- the ReadyListener keeps only the roles load  
**service.impl.PacManService**  
&nbsp;- broadcastPublisher retyped EsqEntityBroadcastPublisher -> EntityBusAdapter; RodEvent import  
&nbsp;   messaging.xrod.RodEvent -> messaging.RodEvent (package move)  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- RodEvent import messaging.xrod.RodEvent -> messaging.RodEvent (package move)  
**audit.AuditConfig**  
&nbsp;- the audit @Bean wraps MessagingBus.getInstance().getXRod(BUS_KEY_AUDIT) in AuditBusBridge; the per-leg  
&nbsp;   sink-selection logic + the service-DataSource inject removed (the keep owns its datasource, the leg the sink)  
**application.yml**  
&nbsp;- bus refs carry a role: entity-bus SERVER, audit-bus SERVER; audit-b leg rod-class XRodInProcessKeep +  
&nbsp;   director AuditKeepDirector; the keep datasource "shared" knob dropped (dedicated *_log pool from url/hikari)  

**06/21/2026** mir0n  v1.2.9 -- audit leg disables (not crashes) on an unknown bus; topology import optional; SLOT_ID  
**audit.AuditConfig**  
&nbsp;- resolves the audit leg via catalog.find() (was the strict resolve()), so an unknown audit bus-id disables  
&nbsp;   the producer (XRodDisabled) instead of crashing at boot  
**application.yml**  
&nbsp;- spring.config.import -> optional:file:... (the topology import is now optional); the bus-ref slot-id env  
&nbsp;   vars (*_SERVICE_ID) renamed -> *_SLOT_ID  

**06/21/2026** mir0n  v1.2.9 -- audit keep: datasource sub-block + URL-derived dialect  
**audit.AuditConfig**  
&nbsp;- binds the keep datasource from the leg's "datasource" sub-block (was "log-db"); the in-process keep's  
&nbsp;   dialect comes from spring.datasource.url (shared) instead of the spring.profiles.active label  
**application.yml**  
&nbsp;- the in-process audit-b leg's log-db block renamed to datasource (vendor entry dropped); env vars renamed  
&nbsp;   ESQUIRE_AUDIT_* -> AUDIT_*, ESQUIRE_AUDIT_LOG_DB_* -> DB_DATAKEEP_*, ESQUIRE_TOPOLOGY_IMPORT -> TOPOLOGY_IMPORT  

### tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt


**06/24/2026** mir0n  v1.2.9 -- EsqMsgConstants references repointed  
&nbsp;- EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)  

### tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt

Esquire ActiveMQ transport provider classes  

**06/22/2026** mir0n  v1.2.9 -- connection health (TransportListener + send outcome)  
**tp.activemq.TransportProvider**  
&nbsp;- a TransportListener set on the ActiveMQConnectionFactory flips an AtomicReference: transportInterupted /  
&nbsp;   onException -> DOWN, transportResumed -> UP; the listener propagates to the connection the factory creates  
&nbsp;- both the publisher and consumer handles report this connection state as their TransportHealth (conn::get)  
&nbsp;- a send outcome also refreshes it: a successful send -> UP, a failed send -> DOWN  
&nbsp;- imports added: org.apache.activemq.transport.TransportListener, messaging.transport.TransportHealth,  
&nbsp;   java.io.IOException, java.util.concurrent.atomic.AtomicReference  

**06/22/2026** mir0n  v1.2.9 -- two-phase consumer (created paused, started by the bus)  
**tp.activemq.TransportProvider**  
&nbsp;- openConsumer returns a TransportConsumer (start + close legs) instead of a bare AutoCloseable  
&nbsp;- the DefaultMessageListenerContainer is created PAUSED: setAutoStartup(false); afterPropertiesSet  
&nbsp;   subscribes but does not start delivery -- delivery begins only when the bus start() invokes the  
&nbsp;   returned start leg (c::start)  
&nbsp;- import added: messaging.transport.TransportConsumer  

**06/21/2026** mir0n  v1.2.9 -- queue-vs-topic read from the pubSubDomain vendor param  
**tp.activemq.TransportProvider**  
&nbsp;- reads the JMS pub/sub flag from transport.params.pubSubDomain (setPubSubDomain on the template /  
&nbsp;   listener container) instead of settings.topic(); excludes pubSubDomain from the broker-URI append in  
&nbsp;   withParams (a setter call, not a URI option)  

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

### tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt


**06/24/2026** mir0n  session messages routed to a separate .admin topic  
**tp.kafka.TransportProvider**  
&nbsp;- openPublisher: a session (alive) message -- HeartBeat / TestRequest, via RodEvent.isSession(MsgType) -- is  
&nbsp;   sent to .admin instead of the log topic; topicFor() helper  
&nbsp;- ensureAdminTopic(): the .admin topic is created with a short retention (retention.ms / segment.ms /  
&nbsp;   cleanup.policy=delete) via AdminClient, best-effort -- Kafka has no per-message TTL  
&nbsp;- imports added: kafka.clients.admin Admin/AdminClientConfig/NewTopic, common.config.TopicConfig,  
&nbsp;   messaging.BusConstants, messaging.RodEvent  

**06/22/2026** mir0n  v1.2.9 -- send-outcome health on the publisher  
**tp.kafka.TransportProvider**  
&nbsp;- Kafka has no clean connection-state callback, so the publisher handle's TransportHealth is send-outcome  
&nbsp;   only (best-effort): an acked send -> UP, a failed send (callback exception / throw) -> DOWN; reported  
&nbsp;   via conn::get  
&nbsp;- imports added: messaging.transport.TransportHealth, java.util.concurrent.atomic.AtomicReference  

**06/22/2026** mir0n  v1.2.9 -- two-phase consumer (created paused, started by the bus)  
**tp.kafka.TransportProvider**  
&nbsp;- openConsumer returns a TransportConsumer (start + close legs) instead of a bare AutoCloseable  
&nbsp;- the ConcurrentMessageListenerContainer is created PAUSED: setAutoStartup(false), no container.start()  
&nbsp;- - delivery begins only when the bus start() invokes the returned start leg (container::start)  
&nbsp;- import added: messaging.transport.TransportConsumer  

### tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt


**06/24/2026** mir0n  session messages routed to a separate .admin stream  
**tp.redis.TransportProvider**  
&nbsp;- openPublisher: a session (alive) message -- HeartBeat / TestRequest, via RodEvent.isSession(MsgType) -- is  
&nbsp;   XADD'd to .admin (a capped admin stream) instead of the log stream; streamFor() helper  
&nbsp;- supportsBothLegs() override = false (XADD-only / produce-only -- no CLIENT role here)  

**06/22/2026** mir0n  v1.2.9 -- send-outcome health on the publisher  
**tp.redis.TransportProvider**  
&nbsp;- no clean connection-state callback, so the publisher handle's TransportHealth is XADD send-outcome only  
&nbsp;   (best-effort; producer-only stream): a good XADD -> UP, a failed XADD -> DOWN; reported via conn::get  
&nbsp;- imports added: messaging.transport.TransportHealth, java.util.concurrent.atomic.AtomicReference  

**06/22/2026** mir0n  v1.2.9 -- consumer SPI signature follows the two-phase change  
**tp.redis.TransportProvider**  
&nbsp;- openConsumer signature returns a TransportConsumer instead of a bare AutoCloseable; still producer-only  
&nbsp;- - the body throws UnsupportedOperationException  
&nbsp;- import added: messaging.transport.TransportConsumer  

---

## Commits

```

-- 2026-06-25 | commit: 399b915 | mir0n.the.programmer | OKE deployment fix --
M	k8s-oci/esquire-topology.yml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
 4 files changed, 23 insertions(+), 10 deletions(-)


-- 2026-06-25 | commit: 5a3ecb9 | mir0n.the.programmer | v1,2.9 -- version finalization --
M	README.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/v1.2.x.Planning.md
 4 files changed, 85 insertions(+), 47 deletions(-)

-- 2026-06-24 | commit: 5a279fd | mir0n.the.programmer | v1.2.9 -- hardening: the bus keep-alive is now optional with a clearer health and role model; the bus wire constants moved into the messaging module --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateAcctHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateOrgHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateUsrHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveAcctHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveOrgHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveUsrHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/UpdateEntityHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
D	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.md
M	doc/Message.Structure.md
M	doc/img/messaging-bus-classes.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
M	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
A	tp-kafka/src/test/java/pro/mir0n/esquire/tp/kafka/TransportProviderTest.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
 77 files changed, 1101 insertions(+), 507 deletions(-)

-- 2026-06-23 | commit: 3338f97 | mir0n.the.programmer | v1.2.9 -- hardening: the messaging-bus connection sends a regular keep-alive so an outage is caught on every transport --
M	auKeep/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
A	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.md
M	doc/Message.Structure.md
M	doc/Messaging.md
M	doc/img/messaging-bus-classes.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	k8s-oci/oke-up.bat
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSession.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSessionTest.java
A	test/health-smoke/README.md
A	test/health-smoke/run.sh
 33 files changed, 1320 insertions(+), 103 deletions(-)

-- 2026-06-23 | commit: ed58643 | mir0n.the.programmer | v1.2.9 -- hardening: each service reports its messaging-bus connection on its health check --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	compose/topology/esquire-topology.yml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
M	doc/Esquire.MessagingBus.md
M	doc/Messaging.md
M	doc/img/messaging-bus-classes.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/pom.xml
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/TransportHealthIndicator.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportHealth.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/BusHealthIndicatorTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/TransportHealthIndicatorTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportHealthTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
 54 files changed, 901 insertions(+), 49 deletions(-)

-- 2026-06-22 | commit: ef722e2 | mir0n.the.programmer | v1.2.9 -- hardening: Messaging Bus framework -- a single Facade API + a service lifecycle (refactoring) --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/application.yml
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeBroadcastConsumer.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/resources/application.yml
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcess.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
M	doc/Esquire.MessagingBus.md
M	doc/img/messaging-bus-architecture.svg
M	doc/img/messaging-bus-classes.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisher.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListener.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
D	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisherTest.java
D	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListenerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
R066	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
M	kcMaster/src/main/resources/application.yml
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumerTest.java
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisherTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
R069	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncResponseListener.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
R092	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IRodEventRepo.java	messaging/src/main/java/pro/mir0n/esquire/messaging/IRodEventRepo.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
R097	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
R096	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistry.java	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEventRepoRegistry.java
R090	messaging/src/main/java/pro/mir0n/esquire/messaging/BusNode.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusNode.java
R051	messaging/src/main/java/pro/mir0n/esquire/messaging/BusRef.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusRef.java
R085	messaging/src/main/java/pro/mir0n/esquire/messaging/BusSlot.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusSlot.java
R095	messaging/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusTransport.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBus.java
R079	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalog.java
R051	messaging/src/main/java/pro/mir0n/esquire/messaging/Role.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
R099	messaging/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodAutoConfiguration.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRods.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInProcess.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
D	messaging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
A	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
R093	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
 136 files changed, 2551 insertions(+), 2282 deletions(-)

-- 2026-06-21 | commit: 0998467 | mir0n.the.programmer |  v1.2.9 -- hardening: messaging-bus topology validation; a misconfigured bus disables, not crashes --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.MessagingBus.md
M	doc/Messaging.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IRodEventRepo.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistry.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
M	test/audit-smoke/run.sh
 49 files changed, 545 insertions(+), 284 deletions(-)

-- 2026-06-21 | commit: 274f240 | mir0n.the.programmer | 06/21/2026 mir0n  v1.2.9 -- keep datasource vendor dropped; audit/topology env vars renamed --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
M	doc/img/messaging-bus-params.svg
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
D	pacMan/.mvn/.idea/.gitignore
D	pacMan/.mvn/.idea/.mvn.iml
D	pacMan/.mvn/.idea/libraries/maven_wrapper.xml
D	pacMan/.mvn/.idea/misc.xml
D	pacMan/.mvn/.idea/modules.xml
D	pacMan/.mvn/wrapper/maven-wrapper.jar
D	pacMan/.mvn/wrapper/maven-wrapper.properties
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
M	pom.xml
M	test/audit-smoke/README.md
 43 files changed, 187 insertions(+), 184 deletions(-)

-- 2026-06-21 | commit: 781d986 | mir0n.the.programmer |  v1.2.9 -- hardening: the audit/broadcast message decoder shrugs off a bad field, refuses an unknown format --
M	doc/DatabaseDictionary.md
M	doc/release_notes.txt
M	k8s/values/postgres.yaml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
 6 files changed, 121 insertions(+), 10 deletions(-)

-- 2026-06-21 | commit: e43bdbf | mir0n.the.programmer | v1.2.9 -- hardening: activemq "pubSubDomain" instead of "topic" --
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	compose/topology/esquire-topology.yml
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.MessagingBus.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	k8s-oci/esquire-topology.yml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusNode.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
A	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
 24 files changed, 160 insertions(+), 108 deletions(-)

-- 2026-06-20 | commit: 8aa1ff7 | mir0n.the.programmer | v1.2.9 -- version bump --
M	README.md
D	doc/v128.tasks.md
M	pom.xml
 3 files changed, 3 insertions(+), 77 deletions(-)

-- 2026-06-20 | commit: 1953b73 | mir0n.the.programmer | Create report_v1.2.8.md --
A	doc/reports/report_v1.2.8.md
 1 file changed, 1890 insertions(+)
```

---

## Files Modified

```
M	README.md
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/application.yml
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeBroadcastConsumer.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateAcctHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateOrgHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateUsrHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveAcctHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveOrgHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveUsrHandler.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/UpdateEntityHandler.java
M	bizTree/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
D	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcess.java
A	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
M	doc/DatabaseDictionary.md
M	doc/Esquire.AuditLoggingStack.md
A	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/Message.Structure.md
M	doc/Messaging.md
M	doc/img/messaging-bus-architecture.svg
M	doc/img/messaging-bus-classes.svg
M	doc/img/messaging-bus-params.svg
M	doc/release_notes.txt
A	doc/reports/report_v1.2.8.md
M	doc/services.configuring.md
M	doc/v1.2.x.Planning.md
D	doc/v128.tasks.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisher.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListener.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
D	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisherTest.java
D	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListenerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s-oci/esquire-topology.yml
M	k8s-oci/oke-up.bat
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
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
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/values/postgres.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
R064	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
M	kcMaster/src/main/resources/application.yml
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisherTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
R062	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncResponseListener.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	messaging/pom.xml
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
R054	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IRodEventRepo.java	messaging/src/main/java/pro/mir0n/esquire/messaging/IRodEventRepo.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBusCatalog.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
R080	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistry.java	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEventRepoRegistry.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/Role.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/TransportHealthIndicator.java
R065	messaging/src/main/java/pro/mir0n/esquire/messaging/BusNode.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusNode.java
R051	messaging/src/main/java/pro/mir0n/esquire/messaging/BusRef.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusRef.java
R085	messaging/src/main/java/pro/mir0n/esquire/messaging/BusSlot.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusSlot.java
R064	messaging/src/main/java/pro/mir0n/esquire/messaging/BusTransport.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusTransport.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBus.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalog.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
R084	messaging/src/main/java/pro/mir0n/esquire/messaging/XRodParams.java	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportHealth.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/IXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodAutoConfiguration.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRodManager.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/XRods.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSession.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInProcess.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
D	messaging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
A	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/BusHealthIndicatorTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/TransportHealthIndicatorTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
R061	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusCatalogTest.java	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportHealthTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodManagerTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSessionTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
D	pacMan/.mvn/.idea/.gitignore
D	pacMan/.mvn/.idea/.mvn.iml
D	pacMan/.mvn/.idea/libraries/maven_wrapper.xml
D	pacMan/.mvn/.idea/misc.xml
D	pacMan/.mvn/.idea/modules.xml
D	pacMan/.mvn/wrapper/maven-wrapper.jar
D	pacMan/.mvn/wrapper/maven-wrapper.properties
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
A	test/health-smoke/README.md
A	test/health-smoke/run.sh
M	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
A	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
A	tp-kafka/src/test/java/pro/mir0n/esquire/tp/kafka/TransportProviderTest.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
 210 files changed, 8688 insertions(+), 3462 deletions(-)
```

---

*From `v1.2.8` till `v1.2.9`*

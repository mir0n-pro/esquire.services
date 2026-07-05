# Release Report: v1.2.9 → v1.2.10

**Repo:** `esquire.services/develop`  
**Top commit:** `728cfc6`

---

## Release Notes

### doc/release_notes.txt


**v1.2.10-2607.0416**  v1.2.10 -- OKE high-availability deployment (two of each service)  
&nbsp;- each backend service now runs as two copies on OKE, placed on separate machines, so  
&nbsp;     losing one machine keeps the site running  
&nbsp;- the web/login backend (the BFF) stays a single copy on OKE to save room on the small machines  
&nbsp;- the free cloud cluster grows to four small machines: one for the shared infrastructure  
&nbsp;     and three for the services  
&nbsp;- services are given a longer start-up grace period, so a slow start on a busy machine is  
&nbsp;     not mistaken for a failure  
&nbsp;- the reliability limits and the message resend-on-failure, first proven on the local  
&nbsp;     cluster, now apply on OKE as well  
&nbsp;- the KeyCloak sign-in address is corrected to the full site address, which the newer  
&nbsp;     KeyCloak requires  
&nbsp;- a single release label now drives both the image upload and the deployment  
&nbsp;: Feature:     two copies of each backend service on OKE, spread across machines where possible  
&nbsp;: Config:      machine placement, copy-spread, and start-up grace added to the deployment  
&nbsp;                 templates; switched on in the OKE settings and left off for the single-machine  
&nbsp;                 local setup  
&nbsp;: Config:      the reliability limits and message resend now set in the OKE settings too  
&nbsp;: Config:      the OKE machine pool sized to the full free allowance (four machines)  
&nbsp;: Fix:         KeyCloak address set to the full site address so the newer KeyCloak accepts it  
&nbsp;   Components:   k8s,  
&nbsp;                 gateway,  
&nbsp;                 biztree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 BFF,  
&nbsp;                 keycloak  

**v1.2.10-2607.0300**  v1.2.10 -- collected-backlog fixes  
&nbsp;- X-Request-ID required on write operations  
&nbsp;- messaging send-retry: a dropped (dead) message is now logged on the main app log  
&nbsp;- send-retry-backoff config keys given the -sec unit suffix  
&nbsp;- KeyCloak login Cancel link  
&nbsp;- JaCoCo code-coverage tooling  
&nbsp;- Javadoc generation for the library modules  
&nbsp;: Doc:         doc\Esquire.TestingStack.md  
&nbsp;                 doc\Esquire.Haubergeon.md  
&nbsp;                 doc\Esquire.DevProcess.md  
&nbsp;                 doc\Esquire.DevSetup.md  
&nbsp;                 doc\Esquire.Q&A.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Esquire.MessagingBus.Guides.md  
&nbsp;                 doc\Message.Structure.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\Esquire.HighAvailability.md  
&nbsp;                 doc\Testing.md  (removed -- folded into Haubergeon + TestingStack)  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 keycloak,  
&nbsp;                 k8s  

**v1.2.10-2607.0121**  v1.2.10 -- the messaging worker pools gain a virtual-threads option, Java 25 runtime  
&nbsp;: Feature:     each messaging worker pool can run on ordinary (platform) threads or on virtual threads, chosen  
&nbsp;                 by a single setting per pool  
&nbsp;: Refactoring: the pool that receives and applies messages is now one building  
&nbsp;: Refactoring: a producer holding a message to retry now lets go at once when the service is shutting down  
&nbsp;: Config:      the pool settings are regrouped into a receiver-pool and a publisher-pool block  
&nbsp;: Config:      the services build for Java 24 and run on the Java 25 runtime  
&nbsp;: Doc:         doc\Esquire.HighAvailability.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 auKeep,  
&nbsp;                 gateway,  
&nbsp;                 k8s  

**v1.2.10-2606.3021**  v1.2.10 -- session sublayers: the producer send path becomes pluggable session layers  
&nbsp;- - the keep-alive and the new send-retry protocols both live on one seam  
&nbsp;: Refactoring: the single send worker now owns the whole send (prepare the message once, then deliver and  
&nbsp;                 retry); the producer's session protocols become pluggable layers it drives at each send  
&nbsp;: Feature:     send-retry: when the message broker is down a producer holds the message and keeps retrying  
&nbsp;                 until the broker recovers  
&nbsp;: Config:      send-retry is off by default  
&nbsp;: Doc:         doc\Esquire.HighAvailability.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\img\messaging-bus-classes.svg  
&nbsp;   Components:   messaging,  
&nbsp;                 tp-activemq,  
&nbsp;                 tp-redis,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 k8s  

**v1.2.10-2606.2910**  v1.2.10 -- full coverage of REST-stack HA tuning: a bound at every tier of the request path  
&nbsp;: Feature:     every tier of a request now has a tunable limit  
&nbsp;: Feature:     the gateway puts a time limit on each downstream call, steps back from a failing service whil  
&nbsp;                 it recovers, retries a read when the connection failed (a write is never resent), and bounds  
&nbsp;                 its own connection pool to the backends  
&nbsp;: Feature:     each service can cap a database query/transaction, size its worker-thread and connection pools  
&nbsp;: Config:      every knob defaults to the no-redundancy setting  
&nbsp;: Doc:         doc\Esquire.HighAvailability.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;   Components:   common,  
&nbsp;                 dataKeep,  
&nbsp;                 bizTree,  
&nbsp;                 gateway,  
&nbsp;                 enyMan,  
&nbsp;                 keySmith,  
&nbsp;                 pacMan,  
&nbsp;                 auKeep,  
&nbsp;                 k8s  

**v1.2.10-2606.2722**  v1.2.10 -- full service redundancy: every service can run as more than one copy  
&nbsp;: Feature:     every service can run as more than one copy at once  
&nbsp;: Feature:     a record created on one copy of enyMan while another copy is moving its branch is now corrected  
&nbsp;: Feature:     the backend (BFF) can run as more than one copy: its login sessions move to a shared store  
&nbsp;                 (Redis) where configured  
&nbsp;: Fix:         the shared entity-field dictionary is now completed safely when several creates warm it at once  
&nbsp;: Refactoring: a broadcast leg can now publish and listen on ONE connection so the message broker drops the  
&nbsp;                 leg's own messages  
&nbsp;: Config:      every service runs as an ordinal-numbered set (StatefulSet) capped at ten copies; the k8s  
&nbsp;                 charts, the per-service values, and the rebuild / start scripts updated to match  
&nbsp;: Doc:         doc\Esquire.HighAvailability.md  
&nbsp;                 doc\img\ha-failure-domains.svg  
&nbsp;                 doc\Messaging.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;   Components:   enyMan,  
&nbsp;                 messaging,  
&nbsp;                 tp-activemq,  
&nbsp;                 common,  
&nbsp;                 k8s  

---

## Code Changes

### auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt


**06/29/2026** mir0n  v1.2.10 -- R6 per-apply query-timeout (keep surface)  
**resources/application.yml**  
&nbsp;- keep query-timeout-seconds on both keep datasources (ESQ_KEEP_QUERY_TIMEOUT_S, 0 = uncapped, pre-HA default)  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**06/29/2026** mir0n  v1.2.10 -- R6 cache-load opt-out + H2 query cap; R1-R5 env wiring  
**cache.BizTreeCacheLoader**  
&nbsp;- the whole-tree entity read runs in one read-only TransactionTemplate built over the JPA tx manager; its  
&nbsp;   timeout opts out of the request-path cap via QueryTimeouts.resolveOptOut (biztree.cache-load.tx-timeout-s,  
&nbsp;   0 = uncapped, pre-HA default)  
**h2.BizTreeH2Config**  
&nbsp;- biztree.h2.query-timeout-s @Value: when > 0 sets the cache JdbcTemplate setQueryTimeout; 0 = uncapped (pre-HA)  
**access.BizTreeDirectorConfig**  
&nbsp;- inject the JPA PlatformTransactionManager + biztree.cache-load.tx-timeout-s @Value and pass them to  
&nbsp;   BizTreeCacheLoader  
**resources/application.yml**  
&nbsp;- spring.transaction.default-timeout (ESQ_TX_TIMEOUT_S, -1 = no cap); biztree.cache-load.tx-timeout-s  
&nbsp;   (BIZTREE_CACHE_LOAD_TX_TIMEOUT_S) + biztree.h2.query-timeout-s (BIZTREE_H2_QUERY_TIMEOUT_S), both 0 = uncapped;  
&nbsp;   Tomcat threads.max/accept-count, Hikari pool/connect-timeout, pgjdbc socketTimeout/tcpKeepAlive env-bound  
&nbsp;   (pre-HA defaults unchanged)  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**06/27/2026** mir0n  v1.2.10 -- entity dictionary completion flag made thread-safe  
**dto.EsqEntityDictionary**  
&nbsp;- completed field made volatile -- AEnyManService double-checks it OUTSIDE the lock, so the warm fast-path  
&nbsp;   read must see the completing thread's merged layers (happens-before)  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**07/03/2026** mir0n  v1.2.10 -- X-Request-ID required on write commands  
MissingRequestIdException  (new)  
&nbsp;- 400 Bad Request when a write command arrives without X-Request-ID  
RequestContextUtils  
&nbsp;- requireRequestId(): X-Request-ID presence guard (null/blank -> MissingRequestIdException / 400) for write commands  

**06/29/2026** mir0n  v1.2.10 -- R6 request-path query-timeout opt-out helper  
QueryTimeouts  (new)  
&nbsp;- NO_PRACTICAL_LIMIT_SECONDS sentinel + resolveOptOut(int): a configured > 0 seconds is used as-is, else the  
&nbsp;   sentinel (no practical cap); the sentinel survives the JDBC seconds->millis int conversion  

### common/src/main/java/pro/mir0n/utils/changes.txt


**07/01/2026** mir0n  v1.2.10 -- a common bounded worker pool with three thread models (platform / virtual / virtual-per-task)  
concurrent.WorkerPool  (new)  
&nbsp;- a bounded worker pool with three thread models: PLATFORM / VIRTUAL = a FIXED pool of `size` reused workers  
&nbsp;   (OS threads, or `size` virtual threads); VIRTUAL_PER_TASK = one virtual thread per task, capped by a  
&nbsp;   Semaphore(size) (uncapped when size=0). create(name, size, mode); submit(Runnable) applies the bound  
&nbsp;   (acquire / execute / release); shutdown(awaitSeconds) drains; capacity(); Mode.of(String) parses the mode  

### dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt


**06/29/2026** mir0n  v1.2.10 -- R6 per-apply query-timeout (keep surface)  
**keep.KeepDataSourceParams**  
&nbsp;- added queryTimeoutSeconds (Integer; null / <= 0 = no cap, pre-HA default) -- the per-apply JDBC statement cap  
**keep.RodEventDbWriter**  
&nbsp;- constructor takes queryTimeoutSeconds: when > 0 applies it via JdbcTemplate setQueryTimeout; null / <= 0 leaves  
&nbsp;   it uncapped; added queryTimeoutSeconds() accessor  
**keep.KeepApplier**  
&nbsp;- passes ds.queryTimeoutSeconds() to the RodEventDbWriter  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**07/03/2026** mir0n  v1.2.10 -- X-Request-ID required on write commands; send-retry-backoff key -sec suffix  
**service.impl.EnyManService**  
&nbsp;- write commands (esquireCommandSave / New / Delete / Move) read requestId via requireRequestId() -- X-Request-ID mandatory on writes  
**resources/application.yml**  
&nbsp;- send-retry-backoff -> send-retry-backoff-sec (env *_SEND_RETRY_BACKOFF -> *_SEND_RETRY_BACKOFF_SEC) mirroring the XRodParams key rename  

**06/29/2026** mir0n  v1.2.10 -- R6 move-tx opt-out + query-timeout test hook; R1-R5 env wiring  
**queue.MoveQueueManager**  
&nbsp;- move worker runs on a dedicated TransactionTemplate that opts out of the request-path cap via  
&nbsp;   QueryTimeouts.resolveOptOut (enyman.move-queue.tx-timeout-s, 0 = uncapped, pre-HA default)  
testhook.SlowQueryTestController  (new)  
&nbsp;- flag-gated /test/slow-query (capped) + /test/slow-query-optout endpoints; @ConditionalOnProperty  
&nbsp;   esq.test.slow-query-enabled=true (default false; never in prod)  
testhook.SlowQueryTestService  (new)  
&nbsp;- runs a deliberately long DB statement (pg_sleep / DBMS_SESSION.SLEEP) on a capped tx and on the  
&nbsp;   QueryTimeouts opt-out tx; a QueryTimeoutException is the cap firing  
testhook.SlowQueryResult  (new)  
&nbsp;- result record (mode, requestedSeconds, elapsedMs, timedOut, error)  
**resources/application.yml**  
&nbsp;- spring.transaction.default-timeout (ESQ_TX_TIMEOUT_S, -1 = no cap); enyman.move-queue.tx-timeout-s  
&nbsp;   (ENYMAN_MOVE_TX_TIMEOUT_S, 0 = uncapped); keep query-timeout-seconds (ESQ_KEEP_QUERY_TIMEOUT_S); Tomcat  
&nbsp;   threads.max/accept-count, Hikari pool/connect-timeout, pgjdbc socketTimeout/tcpKeepAlive env-bound (pre-HA  
&nbsp;   defaults unchanged)  

**06/27/2026** mir0n  v1.2.10 -- Goal-4 cross-instance reconcile (entity rod runs both legs; peer CREATE -> reconcile) + test race-8b create-window lever  
**messaging.EntityBusAdapter**  
&nbsp;- one entity rod (BUS_KEY_ENTITY, role CLIENT) now runs BOTH legs on a shared connection: transmit (publish)  
&nbsp;+ receive. onPeerCreate(sink) sets a broker subscription selector (EventType = 'C') so the receive leg  
&nbsp;   forwards a PEER instance's CREATE to the move-queue reconcile intake; the slot's noLocal drops THIS  
&nbsp;   instance's own publications -- closes the cross-instance race-8b gap the per-instance inMove() left open  
**queue.MoveQueueManager**  
&nbsp;- registers broadcastPublisher.onPeerCreate(this::submitReconcile) once the rig is live (a peer instance's  
&nbsp;   CREATE feeds the reconcile intake); wired here so MoveQueueManager -> EntityBusAdapter stays one-way  
**service.impl.AEnyManService**  
&nbsp;- completedDictionary() completes the SHARED dictionary singleton once under its own monitor (synchronized +  
&nbsp;   a volatile double-check) so concurrent first-callers do not sort the layers list mid-mutation  
&nbsp;- testCreateDelayMs() reads ENYMAN_TEST_CREATE_DELAY_MS (the test-only race-8b create-window lever, 0 = off)  
**service.impl.OrgService**  
&nbsp;- test-only createOrg window widener: sleepCreateWindow() (ENYMAN_TEST_CREATE_DELAY_MS) holds the create  
&nbsp;   transaction open between the parent-path read and the child insert (race-8b repro lever; 0 = off)  
**service.impl.UsrService**  
&nbsp;- test-only createUsr window widener: sleepCreateWindow() (ENYMAN_TEST_CREATE_DELAY_MS) holds the create  
&nbsp;   transaction open between the parent-path read and the user insert (race-8b repro lever; 0 = off)  
**application.yml**  
&nbsp;- entity slot role SERVER -> CLIENT (the rod runs both legs on one shared connection -- publishes AND listens  
&nbsp;   for peer instances) + entity-rx pool-size / concurrency params  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**06/29/2026** mir0n  v1.2.10 -- R1 gateway-to-backend resilience (circuit breaker + per-route timeout/retry + pool)  
config.ResilienceConfig  (new)  
&nbsp;- @Bean Customizer building the Resilience4j CircuitBreaker + per-route TimeLimiter from the  
&nbsp;   esq.gateway.resilience.circuit-breaker knobs; a default (fast) TimeLimiter, with a longer one for the  
&nbsp;   slow-write breakers (enyman-move-cb, pacman-acct-cb, enyman-new-cb)  
**error.GatewayErrorWebExceptionHandler**  
&nbsp;- messageOf() yields a non-null message for any throwable (own / root-cause / class name) so the renderer  
&nbsp;   never NPEs on a null message; added a CallNotPermittedException branch that renders an open-circuit 503  
**resources/application.yml**  
&nbsp;- esq.gateway.resilience block (circuit-breaker + retry attempts); httpclient connect/response-timeout +  
&nbsp;   backend connection pool (GW_POOL_MAX_CONNECTIONS / GW_POOL_ACQUIRE_TIMEOUT_MS); per-route CircuitBreaker  
&nbsp;   (outer) + Retry (inner) filters -- read routes retry on connect-failure + timeout (GET), write routes on  
&nbsp;   connect-failure only  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**07/03/2026** mir0n  v1.2.10 -- send-retry-backoff config key -sec unit suffix  
**resources/application.yml**  
&nbsp;- send-retry-backoff -> send-retry-backoff-sec (env *_SEND_RETRY_BACKOFF -> *_SEND_RETRY_BACKOFF_SEC) mirroring the XRodParams key rename  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**07/03/2026** mir0n  v1.2.10 -- X-Request-ID required on write commands; send-retry-backoff key -sec suffix  
**service.impl.KeySmithService**  
&nbsp;- esquireKeySave reads requestId via requireRequestId() -- X-Request-ID mandatory on writes  
**resources/application.yml**  
&nbsp;- send-retry-backoff -> send-retry-backoff-sec (env *_SEND_RETRY_BACKOFF -> *_SEND_RETRY_BACKOFF_SEC) mirroring the XRodParams key rename  

**06/29/2026** mir0n  v1.2.10 -- R1-R6 env wiring (request-path cap, keep cap, pool/threads/fail-fast DB)  
**resources/application.yml**  
&nbsp;- spring.transaction.default-timeout (ESQ_TX_TIMEOUT_S, -1 = no cap); keep query-timeout-seconds  
&nbsp;   (ESQ_KEEP_QUERY_TIMEOUT_S); Tomcat threads.max/accept-count, Hikari pool/connect-timeout, pgjdbc  
&nbsp;   socketTimeout/tcpKeepAlive env-bound (pre-HA defaults unchanged)  
KeySmithApplication  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**audit.AuditConfig**  
&nbsp;- EsqMsgConstants app constants -> common.EsqConstants (references repointed)  
**messaging.KcBusAdapter**  
&nbsp;- EsqMsgConstants references -> messaging.BusConstants (wire) + common.EsqConstants (app)  

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt


**07/03/2026** mir0n  v1.2.10 -- send-retry drop visibility; send-retry-backoff config key -sec unit suffix  
XRodParams  
&nbsp;- config key send-retry-backoff -> send-retry-backoff-sec (unit suffix): SCALARS entry + sendRetryBackoff() getter now read send-retry-backoff-sec  
SendRetrySublayer  
&nbsp;- drop() also logs to the MAIN app log (msgAudit rides the msg-log channel, OFF in prod) so a dropped (dead) message is visible in prod logs  

**07/01/2026** mir0n  v1.2.10 -- x-rod worker pool = the common WorkerPool; the pool thread model becomes per-leg config (receiver-pool / publisher-pool {size, mode}); SendRetry shutdown lifecycle  
XRodParams  
&nbsp;- the worker pool regrouped: pool-size / virtual-threads / publisher-pool-size dropped from SCALARS, replaced  
&nbsp;   by the receiver-pool / publisher-pool {size, mode} GROUPS -- getters receiverPoolSizeOr / receiverPoolMode /  
&nbsp;   publisherPoolSizeOr / publisherPoolMode; the flat-key getters + fallback removed  
AXRod  
&nbsp;- the receive/apply pool is now the common WorkerPool: the inline 3-way executor + Semaphore + drain-on-shutdown  
&nbsp;   lift out; poolSize / poolMode read from receiver-pool.size / receiver-pool.mode (WorkerPool.Mode.of); shutdown()  
&nbsp;   calls pool.shutdown(SHUTDOWN_AWAIT_SECONDS)  
XRod  
&nbsp;- the async-publish pool's thread model reads publisher-pool.mode -- init sets poolMode via WorkerPool.Mode.of(publisherPoolMode())  
SendRetrySublayer  
&nbsp;- shutdown() lifecycle: a volatile 'stopping' releases a held worker AT ONCE on service stop and refuses new  
&nbsp;   holds (re-checked under the monitor so a shutdown notify is never lost); the holds map moved to a  
&nbsp;   ConcurrentHashMap and the lock narrowed to the wait/notify monitor only  

**06/30/2026** mir0n  v1.2.10 -- x-rod session-sublayer stack (the feed worker owns the send): keep-alive + the new send-retry as session protocols on one seam  
AXRod  
&nbsp;- the feed (tx) worker OWNS the send: send() stamps the ApplMsgID once, runs beforeSend, then sendInProcess (a  
&nbsp;   non-transport outbound) or sendOut (encode once + the dispatch loop)  
&nbsp;- the alive session field replaced by a session-sublayer list (installSessionStack via SessionSublayerFactory);  
&nbsp;   the worker fans the hooks out -- beforeSend / onSendSuccess / onSendError(ev, enc, Throwable) / onReceiveSessn /  
&nbsp;   sessionHealth -- and idle() ticks them  
&nbsp;- the raw msgLog replaced by the MsgAudit module (TX / TX-ERR with the cause / RX); publisher / outbound / feed /  
&nbsp;   sendSublayers made protected  
XRod  
&nbsp;- the inline AliveSession build + the alive constants + buildKeepAlive / onSessionMsg / newCorrelationId removed  
&nbsp;- - init calls installSessionStack (the base builds the broadcast sublayer stack); health() = worst(transport  
&nbsp;   indicator, sessionHealth())  
XRodRR  
&nbsp;- installSessionStack override builds the sublayers via SessionSublayerFactory (identity + role); buildKeepAlive /  
&nbsp;   onSessionMsg removed (the role-driven keep-alive + the SERVER TestRequest echo move to AliveSessionRR)  
XRodInfo  
&nbsp;- the msgLog logger replaced by the MsgAudit module (built from the identity at init); logInfo logs via  
&nbsp;   msgAudit.info  
ISessionSublayer  (new)  
&nbsp;- the x-rod SESSION-sublayer interface + producer extension point, in the engine package so the x-rod depends on  
&nbsp;   THIS, not on a concrete sublayer (implementations in the .sublayer sub-package, built in via  
&nbsp;   SessionSublayerFactory); hooks beforeSend / onSendSuccess / onSendError / onReceiveSessn / tick / health /  
&nbsp;   start / shutdown  
MsgAudit  (new)  
&nbsp;- the x-rod MESSAGE-AUDIT module -- a null-safe wrapper over the per-leg msg.. logger, built  
&nbsp;   from the leg identity (no logger when the leg has no bus-id); centralises the leg-trace / transmit-error line  
&nbsp;   format; raw info / warn passthroughs  
SessionSublayerFactory  (new)  
&nbsp;- builds the x-rod producer session-sublayer LIST so the engine (AXRod) never names a concrete sublayer: the  
&nbsp;   alive keepalive (opt-in 'alive'; AliveSessionRR by R&R role, else the base AliveSession) and the send-retry  
&nbsp;   policy (opt-in 'send-retry', only with a transport publisher), ordered alive FIRST then send-retry  
SendRetrySublayer  (new)  
&nbsp;- the producer SEND-RETRY sublayer (an ISessionSublayer): on a dispatch throw onSendError marks the holds map  
&nbsp;   (keyed by the stable ApplMsgID) and reacts -- past the max-attempts cap DROP (null), else HOLD the worker over  
&nbsp;   the backoff ladder (a monitor wait released by tick()) and return the same encoded unit to re-dispatch;  
&nbsp;   onSendSuccess clears the hold; a SESSION event is never retried; the trail logs to MsgAudit  
&nbsp;- health() override: DOWN while any request is held (heldCount > 0), else UP -- folded into the leg session  
&nbsp;   health, so a send-retry-only leg (no alive protocol) depools on a broker outage  
AliveSession  
&nbsp;- moved to the .sublayer sub-package; now an ISessionSublayer -- an event-driven session sublayer the feed (tx)  
&nbsp;   worker drives (beforeSend / onSendSuccess / onSendError(ev, msg, Throwable) / tick / health); the keep-alive is  
&nbsp;   PUT on the feed (IQueueRig) instead of a transmit callback; ctor takes the feed + BusIdentity  
AliveSessionRR  (new)  
&nbsp;- the R&R ALIVE-PROTOCOL session -- AliveSession specialised by R&R role: a CLIENT keep-alive is a TestRequest  
&nbsp;   (its rod-id rides so the SERVER HeartBeat reply routes back), a SERVER keep-alive the base HeartBeat; on  
&nbsp;   receive a SERVER echoes an arriving TestRequest back as a HeartBeat  
RodEvent  
&nbsp;- applMsgId component (the ApplMsgID / FIX 1181 wire dedup id) + a 12-arg canonical ctor; the former 11-arg ctor  
&nbsp;   delegates with applMsgId null (stamped once on the send path); withApplMsgId() copy  
RodEventCodec  
&nbsp;- the ApplMsgID wire dedup id rides as a header when the event carries one (stamped once on the send path,  
&nbsp;   frozen across a resend); fromProps carries it back via RodEvent.withApplMsgId  
RodPublisher  
&nbsp;- send-retry seam: encode(RodEvent) throws + dispatch(Object) throws defaults (encode once, dispatch the same  
&nbsp;   unit throwing on failure), both routed through accept  
RodTransportAdapter  
&nbsp;- publisher(TransportPublisher, ObjectMapper, BusIdentity) returns a full RodPublisher -- accept / encode /  
&nbsp;   dispatch / health / close delegating to the transport publisher (the send-retry encode-once + throwing-dispatch  
&nbsp;   path); toMessage() helper for the wire codec  
TransportPublisher  
&nbsp;- send-retry seam: encode(TransportMessage) (build the concrete send unit ONCE, broker-free) + dispatch(Object)  
&nbsp;   throws (send the unit, throwing on a transport failure) defaults, both routed through accept  
XRodParams  
&nbsp;- producer send-retry knobs in SCALARS + getters: send-retry (opt-in, default off) / send-retry-backoff (seconds  
&nbsp;   ladder) / send-retry-max-attempts (0 = block, N = drop)  

**06/27/2026** mir0n  v1.2.10 -- broadcast dual-leg on one connection (broker noLocal own-exclusion) + setWorker subscription selector  
BusConstants  
&nbsp;- PARAM_NO_LOCAL ("noLocal") added -- the transport-leg vendor param key for the shared-connection  
&nbsp;   own-exclusion (broadcast only): a receive leg sharing the publisher's connection drops THIS connection's  
&nbsp;   own publications  
IXRod  
&nbsp;- setWorker(subscription, worker) default added -- a broker-side selector narrowing what the receive leg  
&nbsp;   consumes; the default ignores the subscription (R&R / non-transport rods too)  
&nbsp;- rodId() default null added -- the leg's ., null for in-process/disabled/info  
ITransportProvider  
&nbsp;- openConsumerOn(publisher, destination, settings, handler) default added -- open a consumer leg that REUSES  
&nbsp;   an already-open publisher's connection (the dual-leg, one-connection shape); the default falls back to  
&nbsp;   openConsumer (a separate connection)  
RodTransportAdapter  
&nbsp;- publisher(TransportPublisher, ObjectMapper, BusIdentity) overload added -- wrap an ALREADY-OPEN transport  
&nbsp;   publisher (so an XRod opening its consumer on the SAME connection keeps the raw publisher handle); the  
&nbsp;   original publisher(sink, settings) opens the sink and delegates here  
AXRod  
&nbsp;- name / devLog made protected (XRodRR reads them); rodId() override returns identity.rodId() (null when the  
&nbsp;   rod has no identity)  
XRod  
&nbsp;- dual-leg on ONE connection: a single-node CLIENT that shares its connection opens a producer leg too and  
&nbsp;   ADDs the consumer onto it (openConsumerOn), so the broker's noLocal drops this connection's own  
&nbsp;   publications; setWorker(subscription) re-opens the receive consumer with a broker selector  
&nbsp;   (effectiveSelector; re-open only when it CHANGES); separate-connection fallback drops own events in code  
&nbsp;   (filterOwnInCode); the raw transportPublisher is kept so the shared consumer can reuse its connection  
XRodRR  
&nbsp;- sharesConnection() = false (R&R keeps two connections on different nodes -- never the shared dual-leg path,  
&nbsp;   so never noLocal); setWorker(subscription) override warns and ignores the selector (R&R selects by rod-id /  
&nbsp;   slot-id)  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**07/03/2026** mir0n  v1.2.10 -- X-Request-ID required on write commands; send-retry-backoff key -sec suffix  
**service.impl.PacManService**  
&nbsp;- saveAcct / deleteAcct read requestId via requireRequestId() -- X-Request-ID mandatory on writes  
**acct.service.AcctTransactionService**  
&nbsp;- esquireCommandAcct guards X-Request-ID presence via requireRequestId()  
**resources/application.yml**  
&nbsp;- send-retry-backoff -> send-retry-backoff-sec (env *_SEND_RETRY_BACKOFF -> *_SEND_RETRY_BACKOFF_SEC) mirroring the XRodParams key rename  

**06/29/2026** mir0n  v1.2.10 -- R1-R6 env wiring (request-path cap, keep cap, pool/threads/fail-fast DB)  
**resources/application.yml**  
&nbsp;- spring.transaction.default-timeout (ESQ_TX_TIMEOUT_S, -1 = no cap); keep query-timeout-seconds  
&nbsp;   (ESQ_KEEP_QUERY_TIMEOUT_S); Tomcat threads.max/accept-count, Hikari pool/connect-timeout, pgjdbc  
&nbsp;   socketTimeout/tcpKeepAlive env-bound (pre-HA defaults unchanged)  

### tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt


**06/30/2026** mir0n  v1.2.10 -- publisher send-retry seam (encode-once / throwing dispatch)  
**tp.activemq.TransportProvider**  
&nbsp;- AmqPublisher implements the send-retry seam: encode() prepares the broker-free property bag (a stable  
&nbsp;   ApplMsgID minted ONCE, absent-only), dispatch() materializes the JMS message + sends it THROWING on a  
&nbsp;   transport failure (+ SendingTime per physical send), accept() is the best-effort (retry-off) encode+dispatch  
&nbsp;   swallowing path  
&nbsp;- the swallowing send-sink Consumer removed; AmqPublisher now holds the JmsTemplate + destination  

**06/27/2026** mir0n  v1.2.10 -- shared-connection consumer with broker noLocal own-exclusion  
**tp.activemq.TransportProvider**  
&nbsp;- openConsumerOn() added -- a consumer that SHARES the AmqPublisher's connection and sets pubSubNoLocal (the  
&nbsp;   broker drops the shared connection's own publications, the real JMS noLocal)  
&nbsp;- openPublisher returns an AmqPublisher carrying the ccf so a dual-leg consumer can reuse this connection  
&nbsp;- consumer() factored out with a noLocal flag; a separate-connection openConsumer passes false  

### tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt


**06/30/2026** mir0n  v1.2.10 -- publisher send-retry seam (encode-once / throwing dispatch)  
**tp.redis.TransportProvider**  
&nbsp;- RedisPublisher (extracted class) implements the send-retry seam: encode() prepares the broker-free property  
&nbsp;   bag (a stable ApplMsgID minted ONCE, absent-only), dispatch() builds the stream record + XADDs THROWING on a  
&nbsp;   failure (+ SendingTime per physical send), accept() the best-effort (retry-off) path  
&nbsp;- health() / close() on the handle; the inline TransportPublisher.of(...) lambda replaced by RedisPublisher  

---

## Commits

```

-- 2026-07-05 | commit: 728cfc6 | mir0n.the.programmer | v1.2.10 - GHA script fix --
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
 2 files changed, 12 insertions(+), 5 deletions(-)


-- 2026-07-05 | commit: 4b71fb5 | mir0n.the.programmer | v1.2.10 -- version finalizing --
A	.github/scripts/deploy-compose.cmd
M	.github/workflows/deploy-local.yml
M	README.md
M	doc/Esquire.DevProcess.md
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
 6 files changed, 109 insertions(+), 34 deletions(-)

-- 2026-07-04 | commit: c2e1e5c | mir0n.the.programmer | v1.2.10 -- OKE high-availability deployment (two of each service) --
M	doc/release_notes.txt
A	doc/review/v1.2.10-oke-latency-decomposition.md
M	k8s-oci/README.md
M	k8s-oci/cluster/node-labels.bat
M	k8s-oci/create-nodepool.bat
D	k8s-oci/ghcr-push-rest.sh
M	k8s-oci/ghcr-push.bat
D	k8s-oci/ghcr-push.log
D	k8s-oci/ghcr-repush-spring.sh
D	k8s-oci/ghcr-repush.log
M	k8s-oci/oke-up.bat
M	k8s-oci/values/activemq.yaml
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keycloak.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s-oci/values/postgres.yaml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/activemq/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/postgres/templates/statefulset.yaml
 37 files changed, 657 insertions(+), 297 deletions(-)

-- 2026-07-03 | commit: f39df79 | mir0n.the.programmer | v1.2.10 -- collected-backlog fixes --
A	build-with-JaCoCo.bat
A	common/src/main/java/pro/mir0n/esquire/backend/error/MissingRequestIdException.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilterTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/RequestContextUtilsTest.java
A	common/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
M	compose/compose.yaml
A	doc/Esquire.DevProcess.md
A	doc/Esquire.DevSetup.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
A	doc/Esquire.MessagingBus.Guides.md
M	doc/Esquire.MessagingBus.md
A	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/Message.Structure.md
D	doc/Testing.md
A	doc/media/jacoco.png
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/themes/esquire-explorer/login/login.ftl
M	keycloak/themes/esquire-explorer/login/messages/messages_en.properties
A	make-javadoc.bat
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
A	test/JaCoCo/framed.html
 60 files changed, 1465 insertions(+), 216 deletions(-)

-- 2026-07-01 | commit: 3ac8ded | mir0n.the.programmer | v1.2.10 -- the messaging worker pools gain a virtual-threads option, Java 25 runtime --
M	.github/workflows/ci.yml
M	.github/workflows/deploy-oke.yml
M	auKeep/Dockerfile
M	auKeep/src/main/resources/application.yml
M	bizTree/Dockerfile
M	bizTree/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/utils/changes.txt
A	common/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/Dockerfile
M	enyMan/src/main/resources/application.yml
M	gateway/Dockerfile
M	k8s-oci/esquire-topology.yml
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
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	kcMaster/Dockerfile
M	kcMaster/src/main/resources/application.yml
M	keySmith/Dockerfile
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
M	pacMan/Dockerfile
M	pacMan/src/main/resources/application.yml
M	pom.xml
 56 files changed, 801 insertions(+), 248 deletions(-)

-- 2026-06-30 | commit: ea6a4fe | mir0n.the.programmer | v1.2.10 -- session sublayers: the producer send path becomes pluggable session layers --
M	compose/compose.yaml
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.md
M	doc/img/messaging-bus-classes.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/resources/application.yml
A	k8s/addIngressNginx.bat
A	k8s/addMetalLB.bat
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
A	k8s/metallb-config.yaml
M	k8s/values/enyman.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSession.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/ISessionSublayer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SessionSublayerFactory.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSessionTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
M	pacMan/src/main/resources/application.yml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
 51 files changed, 2072 insertions(+), 534 deletions(-)

-- 2026-06-29 | commit: 78d75b2 | mir0n.the.programmer | v1.2.10 -- full coverage of REST-stack HA tuning: a bound at every tier of the request path --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/resources/application.yml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	build.all.bat
A	common/src/main/java/pro/mir0n/esquire/common/QueryTimeouts.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/common/QueryTimeoutsTest.java
A	compose/compose.ha-smoke.yaml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
A	dataKeep/src/test/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriterTest.java
M	doc/Esquire.HighAvailability.md
A	doc/img/redundancy-browser-tier.svg
A	doc/img/redundancy-fleet.svg
A	doc/img/resilience-retry-timeout.svg
A	doc/img/resilience-scenarios.svg
A	doc/img/rest-stack.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryResult.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryTestController.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryTestService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DictionaryCompletionConcurrencyTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryCapIntegrationTest.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
A	gateway/src/main/java/pro/mir0n/esquire/gateway/config/ResilienceConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/GatewayErrorWebExceptionHandler.java
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
M	k8s/charts/esquire-backend/templates/deployment.yaml
A	k8s/charts/esquire-backend/templates/spa-config.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/activemq/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/values.yaml
M	k8s/charts/infra/postgres/templates/statefulset.yaml
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
 86 files changed, 2843 insertions(+), 87 deletions(-)

-- 2026-06-27 | commit: b78a015 | mir0n.the.programmer | v1.2.10 -- full service redundancy: every service can run as more than one copy --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	compose/topology/esquire-topology.yml
A	doc/Esquire.HighAvailability.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.md
M	doc/Messaging.md
A	doc/img/ha-failure-domains.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	k8s-oci/esquire-topology.yml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
A	k8s/charts/esquire-aukeep/templates/service.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
M	k8s/charts/esquire-backend/templates/deployment.yaml
M	k8s/charts/esquire-backend/templates/service.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/templates/service.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/templates/service.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/templates/service.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/templates/service.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/templates/service.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
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
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
M	tp-activemq/pom.xml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
A	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
A	tp-redis/src/test/java/pro/mir0n/esquire/tp/redis/TransportProviderTest.java
 52 files changed, 1290 insertions(+), 58 deletions(-)

-- 2026-06-25 | commit: 4d964c0 | mir0n.the.programmer | fix local deploy --
M	.github/workflows/deploy-local.yml
 1 file changed, 31 insertions(+), 9 deletions(-)

-- 2026-06-25 | commit: 7adaa66 | mir0n.the.programmer | v1.2.10 -- version bump --
M	README.md
M	pom.xml
 2 files changed, 3 insertions(+), 1 deletion(-)

-- 2026-06-25 | commit: 760c95d | mir0n.the.programmer | Create report_v1.2.9.md --
A	doc/reports/report_v1.2.9.md
 1 file changed, 1601 insertions(+)
```

---

## Files Modified

```
A	.github/scripts/deploy-compose.cmd
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/ci.yml
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	auKeep/Dockerfile
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/Dockerfile
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/resources/application.yml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
A	build-with-JaCoCo.bat
M	build.all.bat
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/MissingRequestIdException.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
A	common/src/main/java/pro/mir0n/esquire/common/QueryTimeouts.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/java/pro/mir0n/utils/changes.txt
A	common/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
M	common/src/test/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilterTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/RequestContextUtilsTest.java
A	common/src/test/java/pro/mir0n/esquire/common/QueryTimeoutsTest.java
A	common/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
A	compose/compose.ha-smoke.yaml
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
A	dataKeep/src/test/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriterTest.java
M	doc/Esquire.AuditLoggingStack.md
A	doc/Esquire.DevProcess.md
A	doc/Esquire.DevSetup.md
M	doc/Esquire.Haubergeon.md
A	doc/Esquire.HighAvailability.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
A	doc/Esquire.MessagingBus.Guides.md
M	doc/Esquire.MessagingBus.md
A	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/Message.Structure.md
M	doc/Messaging.md
D	doc/Testing.md
A	doc/img/ha-failure-domains.svg
M	doc/img/messaging-bus-classes.svg
A	doc/img/redundancy-browser-tier.svg
A	doc/img/redundancy-fleet.svg
A	doc/img/resilience-retry-timeout.svg
A	doc/img/resilience-scenarios.svg
A	doc/img/rest-stack.svg
M	doc/media/ComponentModel.png
A	doc/media/jacoco.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.9.md
A	doc/review/v1.2.10-oke-latency-decomposition.md
M	doc/services.configuring.md
M	enyMan/Dockerfile
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryResult.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryTestController.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryTestService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DictionaryCompletionConcurrencyTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/testhook/SlowQueryCapIntegrationTest.java
M	gateway/Dockerfile
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
A	gateway/src/main/java/pro/mir0n/esquire/gateway/config/ResilienceConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/GatewayErrorWebExceptionHandler.java
M	gateway/src/main/resources/application.yml
M	k8s-oci/README.md
M	k8s-oci/cluster/node-labels.bat
M	k8s-oci/create-nodepool.bat
M	k8s-oci/esquire-topology.yml
D	k8s-oci/ghcr-push-rest.sh
M	k8s-oci/ghcr-push.bat
D	k8s-oci/ghcr-push.log
D	k8s-oci/ghcr-repush-spring.sh
D	k8s-oci/ghcr-repush.log
M	k8s-oci/oke-up.bat
M	k8s-oci/values/activemq.yaml
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keycloak.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s-oci/values/postgres.yaml
A	k8s/addIngressNginx.bat
A	k8s/addMetalLB.bat
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
A	k8s/charts/esquire-aukeep/templates/service.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
M	k8s/charts/esquire-backend/templates/deployment.yaml
M	k8s/charts/esquire-backend/templates/service.yaml
A	k8s/charts/esquire-backend/templates/spa-config.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/templates/service.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/templates/service.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/templates/service.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/templates/service.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/templates/service.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/infra/activemq/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/values.yaml
M	k8s/charts/infra/postgres/templates/statefulset.yaml
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
A	k8s/metallb-config.yaml
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	kcMaster/Dockerfile
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
M	keySmith/Dockerfile
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/themes/esquire-explorer/login/login.ftl
M	keycloak/themes/esquire-explorer/login/messages/messages_en.properties
A	make-javadoc.bat
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSession.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/ISessionSublayer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SessionSublayerFactory.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/AliveSessionTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
M	pacMan/Dockerfile
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
A	test/JaCoCo/framed.html
M	tp-activemq/pom.xml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
A	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
A	tp-redis/src/test/java/pro/mir0n/esquire/tp/redis/TransportProviderTest.java
 217 files changed, 10741 insertions(+), 1346 deletions(-)
```

---

*From `v1.2.9` till `v1.2.10`*

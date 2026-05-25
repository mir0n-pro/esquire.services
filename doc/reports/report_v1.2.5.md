# Release Report: v1.2.4 → v1.2.5

**Repo:** `esquire.services/develop`  
**Top commit:** `e1e2e33`

---

## Release Notes

### doc/release_notes.txt


**v1.2.5-2605.2322**  v1.2.5 bizTree sprint -- Taijitu night-watch (anti-entropy sweep)  
&nbsp;: Feature:     night-watch sweep -- periodically reloads the shadow monad, checksums both legs  
&nbsp;                 against the serving monad, and reacts to drift (log / swap / terminate)  
&nbsp;: Feature:     cache readiness gate -- k8s readiness holds until the cache is loaded + serving  
&nbsp;: Feature:     async REST force-sweep (/esq-sweep), routed through the gateway  
&nbsp;: Fix:         bizTree topic subscription made non-durable -- unblocks k8s rolling updates  
&nbsp;: Config:      configurable sweep cadence + mismatch reaction (interval / timeout / on-mismatch)  
&nbsp;: Doc:         Esquire.BizTree.md  
&nbsp;                 H2BizTree.md  
&nbsp;                 DatabaseDictionary.md  
&nbsp;                 Esquire.TestingStack.md  
&nbsp;                 keyCloak-gateway.JWE.md  
&nbsp;                 README.md  
&nbsp;   Components:   common, bizTree, gateway  

**v1.2.5-2605.2221**  v1.2.5 bizTree sprint -- Taijitu dark side (two-monad director)  
&nbsp;: Feature:     two-monad Taijitu director (serving + shadow) with off-queue CHECKSUM dispatch  
&nbsp;- - dummy night-watch for now (shadow idle; real sweep + reactions to come)  
&nbsp;: Refactoring: command result flows through onResult/doCommand (3-arg); per-monad gateFor listener  
&nbsp;: Refactoring: removed the yang single-monad director (MonadY / BizTreeDirectorYang)  
&nbsp;: Config:      compose BIZTREE_DIRECTOR yang -> taijitu (director options: legacy | taijitu)  
&nbsp;   Components:   common, bizTree  

**v1.2.5-2605.2210**  v1.2.5 bizTree sprint -- Taijitu synchronous command model  
&nbsp;: Refactoring: synchronous monad command -- AMonadY.doCommand() blocks until the worker completes,  
&nbsp;                 with timeout + cancel; bootstrap is a synchronous load-retry loop  
&nbsp;: Refactoring: IMonad control contract -- the director drives the monad only through the interface  
&nbsp;: Refactoring: director lifecycle renamed bootstrap() -> start() (symmetric with shutdown())  
&nbsp;   Components:   common, bizTree  

**v1.2.5-2605.2114**  v1.2.5 bizTree sprint -- Taijitu Step 3 generalization  
&nbsp;: Refactoring: Taijitu monad framework extracted from bizTree to common  
&nbsp;                 (pro.mir0n.utils.taijitu + pro.mir0n.utils.concurrent)  
&nbsp;: Refactoring: bizTree cache monad and director reduced to thin extensions of the common framework  
&nbsp;: Refactoring: command/event queue flattened to one QueueItem record + MonadCmd vocabulary  
&nbsp;   Components:   common, bizTree  

**v1.2.5-2605.2012**  v1.2.5 bizTree sprint -- Taijitu cache refactor (Steps 1+2)  
&nbsp;: Refactoring: bizTree REST + JMS entry points reduced to pass-throughs over IBizTreeDirector  
&nbsp;: Refactoring: configurable cache director (biztree.director = legacy | yang | taijitu)  
&nbsp;: Feature:     yang single-monad director closes the cache-load race  
&nbsp;: Refactoring: per-monad precomposed SQL (CacheSqlSet)  
&nbsp;: Config:      esquire.version 1.2.4 -> 1.2.5; compose BIZTREE_DIRECTOR=yang  
&nbsp;: Doc:         new doc/Esquire.BizTree.md  
&nbsp;   Components:   bizTree, pom, compose, doc  

---

## Code Changes

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**05/23/2026** mir0n  v1.2.5 Taijitu night-watch -- two-table cache, readiness gate, non-durable subscription  
health.CacheReadinessHealthIndicator  (new)  
&nbsp;- HealthIndicator (health name cacheReadiness) -- UP once IBizTreeDirector.isReady(), else DOWN;  
&nbsp;   added to the readiness probe group so k8s holds traffic until the cache is serving  
cache.CancelableStatement  (new)  
&nbsp;- record(Connection, PreparedStatement) implements AutoCloseable: close() closes the statement then  
&nbsp;   the connection, so a try-with-resources releases both on every path (the cancelable CHECKSUM)  
**taijitu.Monad**  
&nbsp;- ctor takes IBizTreeCacheRepository; CLEAR -> cacheRepository.clear() (TRUNCATE), was a stub  
&nbsp;- _processItemCancellable: real off-worker CHECKSUM -- prepareCancelable(command) + executeQuery,  
&nbsp;   reads the digest, registers an inner PrepareStatementCancelable (volatile ps, cancel() -> ps.cancel())  
&nbsp;   via listener.onStarted so a sweep timeout aborts the query; try-with-resources on CancelableStatement  
**access.BizTreeDirectorConfig**  
&nbsp;- "taijitu" case builds a per-monad cache backend inline: buildCache(table) (own H2 table via  
&nbsp;   CacheSqlSet.forTable + DDL, repository, loader, read service on the shared cacheJdbcTemplate);  
&nbsp;   tableFor(id) suffixes the base table (ESQ_TREE_MONAD / ESQ_TREE_DANOM); parseMismatch + applies  
&nbsp;   the configurable sweep interval / timeout / on-mismatch to the director  
**access.legacy.BizTreeDirectorLegacy**  
&nbsp;- added isReady() backed by a volatile ready flag set true after the synchronous load (readiness gate)  
**cache.IBizTreeCacheRepository**  
&nbsp;- added clear() (TRUNCATE) and prepareCancelable(command) returning a CancelableStatement  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- clear() = cache.execute(sql.clearAll()); prepareCancelable(CHECKSUM) opens a connection + prepares  
&nbsp;   sql.checksum() into a CancelableStatement (closeQuietly the connection if prepareStatement throws)  
**cache.BizTreeCacheSql**  
&nbsp;- Repo record: added clearAll + checksum fields  
**cache.CacheSqlSet**  
&nbsp;- forTable: substitute {table} into and carry the new clearAll + checksum SQL  
**h2.BizTreeH2Config**  
&nbsp;- wired the repo.clear-all + repo.checksum SQL properties into BizTreeCacheSql.Repo  
**controller.BizTreeController**  
&nbsp;- POST /esq-sweep: async force-sweep -> director.sweepAsync(); returns 202 (ACCEPTED)  
**messaging.BizTreeJmsConfig**  
&nbsp;- non-durable subscriber: bean jmsDurableTopicListenerFactory -> jmsTopicListenerFactory; removed  
&nbsp;   the clientId (@Value + setClientId) and setSubscriptionDurable(true); header url -> www.mir0n.pro  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- @JmsListener uses jmsTopicListenerFactory (non-durable); removed the SUBSCRIPTION_NAME constant  
**resources/application.yml**  
&nbsp;- management.endpoint.health: probes.enabled + readiness group include cacheReadiness; removed the  
&nbsp;   dead spring.jms.client-id + biztree.messaging.client-id (BIZTREE_MESSAGING_CLIENT_ID gone)  
**resources/META-INF/h2-cache-sql.properties**  
&nbsp;- added repo.clear-all (TRUNCATE TABLE {table}) + repo.checksum (order-independent MD5 over the table)  

**05/22/2026** mir0n  v1.2.5 Taijitu dark side -- two-monad director; yang removed  
taijitu.Monad  (new)  
&nbsp;- dark-side concrete cache monad: extends common AMonad; _processItem does LOAD (BizTreeCacheLoader)  
&nbsp;   and message apply via the event hub (CLEAR / CHECKSUM stubbed); _processItemCancellable returns a  
&nbsp;   "DUMMY" digest for now; REST reads gated on LOADED  
access.taijitu.BizTreeDirectorTaijitu  (new)  
&nbsp;- dark-side director: extends common ATaijituRig, implements IBizTreeDirector; ctor takes two Monads  
&nbsp;   ("monad" + "danom"); reads route to the current serving monad ((Monad) yang())  
**access.BizTreeDirectorConfig**  
&nbsp;- wired the "taijitu" case (two Monads -> BizTreeDirectorTaijitu); removed the "yang" case and the  
&nbsp;   MonadY / BizTreeDirectorYang imports; director options now legacy | taijitu  
**access.IBizTreeDirector**  
&nbsp;- javadoc: implementations are BizTreeDirectorTaijitu + legacy  
taijitu.MonadY  (removed)  
access.yang.BizTreeDirectorYang  (removed)  

**05/22/2026** mir0n  v1.2.5 Taijitu -- director lifecycle (bootstrap -> start) + IMonad rename caller  
**access.BizTreeBootstrapRunner**  
&nbsp;- fires the active director's start() (was bootstrap())  
**access.legacy.BizTreeDirectorLegacy**  
&nbsp;- bootstrap() renamed start() (ITaijituRig lifecycle)  
**taijitu.MonadY**  
&nbsp;- requireLoaded() calls id() (was monadId(), renamed on IMonad)  

**05/21/2026** mir0n  v1.2.5 Taijitu Step 3 generalization -- monad framework extracted to common  
**taijitu.MonadY**  
&nbsp;- rewritten as a thin extension of common pro.mir0n.utils.taijitu.AMonadY: implements the single  
&nbsp;   _processItem(QueueItem) hook (LOAD -> BizTreeCacheLoader.load(); message -> parse text + apply  
&nbsp;   via the event hub with MDC) plus the gated REST reads; own worker/queue/gates/status removed  
**access.yang.BizTreeDirectorYang**  
&nbsp;- now extends common ATaijituRigY; supplies the MonadY it controls and the REST reads;  
&nbsp;   bootstrap / shutdown / onEntityBroadcast / onStarted / onResult inherited  
**access.IBizTreeDirector**  
&nbsp;- now extends common ITaijituRig (bootstrap / shutdown / onEntityBroadcast); keeps only the  
&nbsp;   bizTree REST reads; inline lifecycle + JsonNode onEntityBroadcast removed  
**access.legacy.BizTreeDirectorLegacy**  
&nbsp;- onEntityBroadcast takes the 7 raw fields and parses textJson inline (no worker); added  
&nbsp;   ObjectMapper ctor arg + no-op shutdown()  
**access.BizTreeDirectorConfig**  
&nbsp;- @Bean takes ObjectMapper; builds MonadY with instance id "monad" (not the role "yang") +  
&nbsp;   cacheLoader / eventHub / readBackend / ObjectMapper; passes ObjectMapper to the legacy impl  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- forwards the RAW body (messageEncoding + textJson) + requestId / correlationId via the 7-arg  
&nbsp;   director.onEntityBroadcast(); ObjectMapper field + readTree block removed (director parses)  
taijitu.MonadCmdHub, IMonad, IMonadCommand, IQueueItem, ICacheLoad, IErrorListener,  
ICancelable, ICmdResponseListener, LoggingErrorListener, MonadStatus  (removed)  
&nbsp;- dissolved into the common generalization: command/event flattened to common QueueItem +  
&nbsp;   MonadCmd; ICancelable / ICmdResponseListener / MonadStatus moved to common pro.mir0n.utils.taijitu;  
&nbsp;   queue + error-listener machinery moved to common pro.mir0n.utils.concurrent (IQueueRig / BoundedQueueRig)  

**05/20/2026** mir0n  v1.2.5 Taijitu cache refactor (Steps 1+2) + per-monad precomposed SQL  
access.IBizTreeDirector  (new)  
&nbsp;- cache-access contract: bootstrap() + reads (esquire/esquirePath/esquireEntityNode/  
&nbsp;   esquireSubtree) + onEntityBroadcast()  
access.BizTreeDirectorConfig  (new)  
&nbsp;- @Bean wiring; biztree.director property selects legacy | yang | taijitu; choice logged at startup  
access.BizTreeBootstrapRunner  (new)  
&nbsp;- ApplicationReadyEvent listener; calls the active director's bootstrap()  
access.MessageHandlerHub  (new)  
&nbsp;- per-kind handler dispatch HandlerKey(eventType,kindBits) -> IBizTreeEventHandler;  
&nbsp;   extracted from EsqEntityBroadcastConsumer  
access.CacheNotReadyException  (new)  
&nbsp;- RuntimeException thrown by a monad read before status LOADED  
access.legacy.BizTreeDirectorLegacy  (new)  
&nbsp;- reads -> IBizTreeService; events -> MessageHandlerHub; bootstrap() -> cacheLoader.load()  
access.yang.BizTreeDirectorYang  (new)  
&nbsp;- role-router over one MonadY; bootstrap() starts the monad, enables queue, submits INIT  
taijitu.MonadY  (new)  
&nbsp;- active (yang) monad: API front + single worker, one FIFO queue, two gates  
&nbsp;   (queueEnabled/processingEnabled); commands -> MonadCmdHub, events -> eventHub, reads ->  
&nbsp;   IBizTreeService; implements IMonad; worker catches Exception, lets Error propagate  
taijitu.MonadCmdHub  (new)  
&nbsp;- handles INIT / CLEAN / CHECKSUM; drives MonadY status + gates  
taijitu.IMonad  (new)  
&nbsp;- monad contract: lifecycle, submit/offer, setQueueEnabled/status/queueDepth, reads, listeners  
taijitu.IMonadCommand  (new)  
&nbsp;- sealed: Init | Clean | Checksum  
taijitu.IQueueItem  (new)  
&nbsp;- sealed: Cmd(IMonadCommand) | Event(eventType, entityId, entityKind, JsonNode)  
taijitu.ICacheLoad, IEventSink, IErrorListener, ICancelable  (new)  
&nbsp;- functional interfaces: load() / apply() / onError() / cancel()  
taijitu.ICmdResponseListener  (new)  
&nbsp;- onStarted(command, cancelable) + onResult(command, status); NOOP default  
taijitu.LoggingErrorListener  (new)  
&nbsp;- IErrorListener default; logs to console + develop  
taijitu.MonadStatus  (new)  
&nbsp;- enum IDLE / LOADING / LOADED / FAILED  
cache.CacheSqlSet  (new)  
&nbsp;- precomposed table-bound SQL record; forTable(BizTreeCacheSql, table) substitutes {table}  
&nbsp;   and joins the read fragments once  
**controller.BizTreeController**  
&nbsp;- reads forwarded to IBizTreeDirector (was IBizTreeService)  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- pass-through to IBizTreeDirector; handler dispatch moved to MessageHandlerHub; parses  
&nbsp;   textJson once, forwards director.onEntityBroadcast()  
**cache.BizTreeCacheLoader**  
&nbsp;- no longer ApplicationReadyEvent listener; exposes load(); consumes CacheSqlSet  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- consumes CacheSqlSet; reads run ready statements (no selectCols()+where per call)  
**h2.BizTreeH2Config**  
&nbsp;- cacheSqlSet bean via CacheSqlSet.forTable (biztree.cache.table, default ESQ_TREE);  
&nbsp;   DDL executed from the set  
**src/main/resources/META-INF/h2-cache-sql.properties**  
&nbsp;- table name, index names, PK constraint parameterized with {table} token  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


### common/src/main/java/pro/mir0n/esquire/common/changes.txt


### common/src/main/java/pro/mir0n/utils/changes.txt

mir0n java common frameworks -- pro.mir0n.utils  

**05/23/2026** mir0n  v1.2.5 Taijitu night-watch -- periodic sweep, mismatch reactions, readiness gate  
taijitu.MismatchAction  (new)  
&nbsp;- enum LOG | SWAP | TERMINATE: the night-watch reaction when the two monads' checksums disagree  
**taijitu.ATaijituRig**  
&nbsp;- night-watch scheduler: a single daemon ScheduledExecutorService (nightWatchExec) re-arms each  
&nbsp;   sweep sweepIntervalMs AFTER the previous one ends; a sweeping AtomicBoolean allows one at a time  
&nbsp;- sweep(): load the shadow fresh, post CHECKSUM to both legs, collect each within sweepTimeoutMs  
&nbsp;   (resultCommand), screen FAILED via checksumFailed() (inconclusive -> abandon), compare digests,  
&nbsp;   react per onMismatch (LOG keep serving / SWAP swapYinYang / TERMINATE System.exit), finally  
&nbsp;   clearMonad(yin()) back to idle  
&nbsp;- sweepAsync(): dispatch sweepGuarded onto nightWatchExec and return at once (REST force-sweep);  
&nbsp;   sweepGuarded() swallows any throw so a fault cannot cancel the periodic schedule  
&nbsp;- configurable cadence/policy: sweepIntervalMs (10s), sweepTimeoutMs (10s), onMismatch (LOG) +  
&nbsp;   setSweepIntervalMs / setSweepTimeoutMs / setOnMismatch  
&nbsp;- checksumFailed(String): true for null or FAILED -- the only inconclusive outcome to screen out  
**taijitu.AMonadY**  
&nbsp;- submit(commandId) -> submitCommand(commandId, boolean enableQueue): clears the gate, posts the  
&nbsp;   command, opens the accept-gate when enableQueue  
&nbsp;- doCommand split: doCommand = submitCommand + resultCommand; new resultCommand(timeoutMs) blocks  
&nbsp;   on the gate, cancels the registered cancelable + grace-waits on a positive timeout  
&nbsp;- removed dead NOOP_CANCEL field; unknown-command log demoted devLog.warn -> devLog.debug  
**taijitu.IMonad**  
&nbsp;- added submitCommand(String commandId, boolean enableQueue) and resultCommand(long timeoutMs)  
**taijitu.ITaijituRig**  
&nbsp;- added isReady() (readiness gate: true once loaded + serving) and the sweepAsync() default no-op  
**taijitu.ATaijituRigY**  
&nbsp;- added isReady(): true once the serving monad is LOADED  
**taijitu.MonadStatus**  
&nbsp;- dropped the String code field/constructor/code() accessor -- plain enum (IDLE/LOADING/LOADED/FAILED)  
**taijitu.AMonad**  
&nbsp;- removed unused Logger/LoggerFactory imports; javadoc: the timed-out-checksum cancel seam is wired  
**taijitu.MonadCmd**  
&nbsp;- javadoc: submit -> submitCommand; CHECKSUM is the off-queue order-independent hash  
**concurrent.IQueueRig**  
&nbsp;- dropped redundant public modifiers from the interface members  

**05/22/2026** mir0n  v1.2.5 Taijitu dark side -- two-monad director + off-queue CHECKSUM  
taijitu.AMonad  (new)  
&nbsp;- dark-side monad: extends AMonadY; handleCommand intercepts CHECKSUM and runs it on a separate  
&nbsp;   thread (checksumExec, not the queue worker) via the abstract _processItemCancellable(listener,  
&nbsp;   item); the digest is reported through the 3-arg onResult. shutdown() also stops checksumExec.  
taijitu.ATaijituRig  (new)  
&nbsp;- dark-side director: extends ATaijituRigY; holds a second monad yinMonad (AtomicReference)  
&nbsp;+ swapYinYang() (pointer flip); start() sets the shadow's gateFor listener and starts it, then  
&nbsp;   super.start() brings up + loads the serving monad; shutdown() also stops the shadow;  
&nbsp;   onEntityBroadcast fans events to both monads.  
**taijitu.AMonadY**  
&nbsp;- onResult + CommandGate carry a result String (doCommand returns it; notifyComplete uses the  
&nbsp;   result, else status.name()); _processItem returns String; handleCommand + commandGate + log/devLog  
&nbsp;   made protected; CHECKSUM stub branch removed from handleCommand  
**taijitu.ATaijituRigY**  
&nbsp;- no longer implements ICmdResponseListener; added gateFor(IMonad) building a per-monad listener,  
&nbsp;   registered in start() (was the ctor self-registration); onResult 3-arg; log/devLog protected  
**taijitu.ICmdResponseListener**  
&nbsp;- onResult(String commandId, MonadStatus status, String result) -- added the result arg  

**05/22/2026** mir0n  v1.2.5 Taijitu -- synchronous command model + IMonad control contract  
taijitu.IMonad  (new)  
&nbsp;- the monad CONTROL contract a director drives: start/shutdown, offer, setQueueEnabled/  
&nbsp;   setProcessingEnabled/queueClear, status/queueSize/id, setCmdResponseListener,  
&nbsp;   doCommand(String cmd, boolean enableQueue, long timeoutMs). AMonadY implements it  
**taijitu.AMonadY**  
&nbsp;- implements IMonad; public control methods renamed to the IMonad names: stop()->shutdown(),  
&nbsp;   clearQueue()->queueClear(), queueDepth()->queueSize(), monadId()->id() (submit() stays, used  
&nbsp;   internally by doCommand -- not on the interface)  
&nbsp;- added doCommand(cmd, enableQueue, timeoutMs): posts the command, opens the queue right after  
&nbsp;   (when enableQueue), BLOCKS on an inner CommandGate monitor until the worker signals; timeoutMs<=0  
&nbsp;   waits indefinitely, a positive timeout cancels the registered cancelable + grace-waits; returns  
&nbsp;   the result string (status name / "TIMEDOUT" / "INTERRUPTED")  
&nbsp;- new inner CommandGate implements ICmdResponseListener: holds result + cancelable; the worker  
&nbsp;   notifies it (onStarted/onResult), it forwards to the rig listener and notifies doCommand  
&nbsp;- handleCommand notifies via the gate; CLEAR wrapped in try/finally -> always IDLE + onResult  
&nbsp;- listener field renamed cmdResponseListener -> rigCmdResponseListener  
&nbsp;- instance loggers via getClass() (was static AMonadY.class)  
**taijitu.ATaijituRigY**  
&nbsp;- bootstrap() renamed start(); active monad held as AtomicReference  
&nbsp;- ctor registers the director as the monad's ICmdResponseListener (yang.setCmdResponseListener(this))  
&nbsp;- start() is a synchronous retry loop: clearMonad(active) then doCommand(LOAD, true, 0) until  
&nbsp;   LOADED, else sleep(retryDelayMs) + retry  
&nbsp;- added clearMonad(IMonad): setQueueEnabled(false) + queueClear() + setProcessingEnabled(true) +  
&nbsp;   doCommand(CLEAR, false, 0)  
&nbsp;- onStarted/onResult drive the per-command gate-flag policy (LOAD: processing off during load,  
&nbsp;   on at LOADED; FAILED / CLEAR: queue + processing off)  
&nbsp;- instance loggers via getClass()  
**taijitu.ITaijituRig**  
&nbsp;- bootstrap() renamed start()  
**taijitu.MonadStatus**  
&nbsp;- each constant carries a String code; added code() accessor  

**05/21/2026** mir0n  v1.2.5 Taijitu Step 3 generalization -- queue rig + monad framework lifted from bizTree  
concurrent.IQueueRig  (new)  
&nbsp;- generic active-object queue contract : bounded FIFO drained by one worker (IQueueWorker);  
&nbsp;   processing gate (setProcessing -- when false the worker leaves the queue UNTOUCHED, no dequeue);  
&nbsp;   put / size / clear; nested IErrorListener seam  
concurrent.BoundedQueueRig  (new)  
&nbsp;- implements IQueueRig: ArrayBlockingQueue + single daemon worker; gate parks before dequeue;  
&nbsp;   recoverable Throwable -> IErrorListener and the worker keeps running; Error propagates;  
&nbsp;   InterruptedException = shutdown; clear() bulk-drops. Lifted from bizTree MonadY  
taijitu.QueueItem  (new)  
&nbsp;- flat record(eventType, entityId, entityKind, requestId, correlationId, messageEncoding, text);  
&nbsp;   commands and events share the one record; a command is eventType==MonadCmd.CMD with  
&nbsp;   entityId=the command id -- no sealed hierarchy  
taijitu.MonadCmd  (new)  
&nbsp;- final class; interned String command vocabulary: CMD (eventType marker), LOAD, CLEAR, CHECKSUM  
taijitu.MonadStatus  (new)  
&nbsp;- enum IDLE / LOADING / LOADED / FAILED  
taijitu.AMonadY  (new)  
&nbsp;- abstract cache monad: owns a BoundedQueueRig over QueueItem, the status machine, two gates,  
&nbsp;   and command EXECUTION (LOAD/CLEAR/CHECKSUM); single abstract hook _processItem(QueueItem);  
&nbsp;   fires onStarted/onResult; only LOAD/CLEAR change status, message/CHECKSUM faults traced by the rig  
taijitu.ATaijituRigY  (new)  
&nbsp;- abstract director: implements ITaijituRig + ICmdResponseListener; holds the active AMonadY;  
&nbsp;   bootstrap (submit LOAD, enable queue + processing); DRIVES the processing gate off the monad  
&nbsp;   callbacks (onStarted LOAD -> processing off; onResult LOADED -> on; FAILED -> setQueueEnabled(false)+clearQueue)  
taijitu.ITaijituRig  (new)  
&nbsp;- director contract: bootstrap() / onEntityBroadcast(7 raw fields) / shutdown(); bean-blind, REST-free  
taijitu.ICmdResponseListener  (new)  
&nbsp;- onStarted(commandId, ICancelable) + onResult(commandId, MonadStatus); NOOP default  
taijitu.ICancelable  (new)  
&nbsp;- in-flight command abort handle: cancel()  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**05/23/2026** mir0n  v1.2.5 Taijitu night-watch -- /esq-sweep route  
**resources/application.yml**  
&nbsp;- biztree-route Path: added /esq-sweep (force-sweep endpoint; route already allows GET + POST)  

---

## Commits

```

-- 2026-05-24 | commit: e1e2e33 | mir0n.the.programmer | finalizing --
M	README.md
M	doc/DatabaseDictionary.md
M	doc/Esquire.BizTree.md
M	doc/Esquire.TestingStack.md
M	doc/H2BizTree.md
M	doc/keyCloak-gateway.JWE.md
A	doc/media/BizTreeModel.png
M	doc/media/ComponentModel.png
A	doc/media/dblTree.32.png
M	doc/model/ComponentModel.vsdx
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/gateway.yaml
 15 files changed, 159 insertions(+), 90 deletions(-)

-- 2026-05-24 | commit: dcb423c | mir0n.the.programmer | v1.2.5 bizTree sprint -- Taijitu night-watch (anti-entropy sweep) --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CacheSqlSet.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CancelableStatement.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/health/CacheReadinessHealthIndicator.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeJmsConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/main/resources/application.yml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/CancelQueryTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/ChecksumSqlTest.java
M	common/src/main/java/pro/mir0n/utils/changes.txt
M	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
A	common/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
M	common/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
M	common/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
A	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
M	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
M	compose/compose.yaml
M	doc/Esquire.BizTree.md
M	doc/release_notes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
 47 files changed, 1257 insertions(+), 664 deletions(-)

-- 2026-05-22 | commit: 880511e | mir0n.the.programmer | v1.2.5 bizTree sprint -- Taijitu dark side (two-monad director) --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/yang/BizTreeDirectorYang.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
R067	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadY.java	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	common/src/main/java/pro/mir0n/utils/changes.txt
A	common/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
M	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
M	doc/release_notes.txt
 14 files changed, 394 insertions(+), 167 deletions(-)

-- 2026-05-22 | commit: 7dd2211 | mir0n.the.programmer | v1.2.5 bizTree sprint -- Taijitu synchronous command model --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeBootstrapRunner.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadY.java
M	common/src/main/java/pro/mir0n/utils/changes.txt
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
A	common/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
M	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
M	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
M	doc/release_notes.txt
 12 files changed, 406 insertions(+), 99 deletions(-)

-- 2026-05-21 | commit: 0117bcb | mir0n.the.programmer | v1.2.5 bizTree sprint -- Taijitu Step 3 generalization --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/yang/BizTreeDirectorYang.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/ICacheLoad.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/ICancelable.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/ICmdResponseListener.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IErrorListener.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IMonad.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IMonadCommand.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IQueueItem.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/LoggingErrorListener.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadCmdHub.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadStatus.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadY.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
D	bizTree/src/test/java/pro/mir0n/esquire/bizTree/taijitu/CacheLoadRaceComparisonTest.java
D	bizTree/src/test/java/pro/mir0n/esquire/bizTree/taijitu/MonadRaceTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
A	common/src/main/java/pro/mir0n/utils/changes.txt
A	common/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
A	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
A	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
A	common/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
A	common/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
A	common/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
A	common/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
A	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
M	doc/release_notes.txt
 35 files changed, 1190 insertions(+), 979 deletions(-)

-- 2026-05-20 | commit: d20efc5 | mir0n.the.programmer | v1.2.5 bizTree sprint -- Taijitu cache refactor (Steps 1+2) --
M	README.md
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeBootstrapRunner.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/CacheNotReadyException.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/yang/BizTreeDirectorYang.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CacheSqlSet.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/ICacheLoad.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/ICancelable.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/ICmdResponseListener.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IErrorListener.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IEventSink.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IMonad.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IMonadCommand.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IQueueItem.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/LoggingErrorListener.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadCmdHub.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadStatus.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/MonadY.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/taijitu/CacheLoadRaceComparisonTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/taijitu/MonadRaceTest.java
M	compose/compose.yaml
A	doc/Esquire.BizTree.md
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
M	pom.xml
 38 files changed, 2530 insertions(+), 270 deletions(-)

-- 2026-05-18 | commit: 8e2ca1f | mir0n.the.programmer | Create report_v1.2.4.md --
A	doc/reports/report_v1.2.4.md
 1 file changed, 444 insertions(+)

```

---

## Files Modified

```
M	README.md
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeBootstrapRunner.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/CacheNotReadyException.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CacheSqlSet.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CancelableStatement.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/health/CacheReadinessHealthIndicator.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeJmsConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IEventSink.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/main/resources/application.yml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/CancelQueryTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/ChecksumSqlTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
A	common/src/main/java/pro/mir0n/utils/changes.txt
A	common/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
A	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
A	common/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
A	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
A	common/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
A	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
A	common/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
A	common/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
A	common/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
A	common/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
A	common/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
A	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
A	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
M	compose/compose.yaml
M	doc/DatabaseDictionary.md
A	doc/Esquire.BizTree.md
M	doc/Esquire.TestingStack.md
M	doc/H2BizTree.md
M	doc/keyCloak-gateway.JWE.md
A	doc/media/BizTreeModel.png
M	doc/media/ComponentModel.png
A	doc/media/dblTree.32.png
M	doc/model/ComponentModel.vsdx
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
A	doc/reports/report_v1.2.4.md
M	doc/v1.2.x.Planning.md
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/resources/application.yml
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/gateway.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	pom.xml
 78 files changed, 4500 insertions(+), 389 deletions(-)
```

---

*From `v1.2.4` till `v1.2.5`*

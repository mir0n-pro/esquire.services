# Release Report: v1.2.5 → v1.2.6

**Repo:** `esquire.services/develop`  
**Top commit:** `bc3f797`

---

## Release Notes

### doc/release_notes.txt


**v1.2.6-2606.0220**  v1.2.6 -- bizTree work-batching + Oracle / pacMan fixes  
&nbsp;: Feature:     bizTree applies a backlog of cache-update events in ONE transaction once the monad queue  
&nbsp;                 passes a threshold -- about 3x broadcast-apply throughput under a move-cascade flood  
&nbsp;: Feature:     queue rig gains a bulk-worker contract (IQueueListWorker + signaler + list error listener):  
&nbsp;                 a worker can process a whole batch in one call instead of item-by-item  
&nbsp;: Fix:         Oracle /esq-cmd-tree -- positional ORDER BY in the subtree queries (Oracle rejected the  
&nbsp;                 aliased column in a UNION ALL ORDER BY)  
&nbsp;: Fix:         pacMan Postgres datasource now honors DB_PACMAN_PORT (was a mixed-case typo, silently ignored)  
&nbsp;: Doc:         services.configuring.md -- every service parameter grouped by service, plus logging + gateway routes  
&nbsp;: Config:      bizTree event-batch threshold (BIZTREE_QUEUE_BULK_THRESHOLD, default 10)  
&nbsp;   Components:   common,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan  

**v1.2.6-2606.0212**  v1.2.6 enyMan sprint -- move-command race fixes (race-8b / race-8c); async move queue  
&nbsp;: Refactoring: /esq-move processed asynchronously on an in-process single-worker move queue; the  
&nbsp;                 command returns 202 Accepted at submit time instead of 200 OK after processing  
&nbsp;: Fix:         race-8c -- a user created during an ancestor move no longer keeps a stale KC rootpath;  
&nbsp;                 kcMaster parks the post-move path in a recovery buffer and flushes it onto the new user  
&nbsp;: Fix:         race-8b -- a CREATE published during a move reconciles its entity path once the queued  
&nbsp;                 moves ahead of it have drained, reissuing the path broadcast on drift  
&nbsp;: Fix:         move-cascade path broadcast ordered parents-first so the bizTree cache cannot pick up a  
&nbsp;                 stale parent path for a descendant  
&nbsp;: Config:      enyMan move-queue capacity + validate-create-during-move toggle; kcMaster path-buffer  
&nbsp;                 ttl / prune interval  
&nbsp;: Config:      kcMaster chart -- KC admin baseUrl carries the /kc-auth prefix; confidential admin  
&nbsp;                 client (esq-kcMaster) replaces the public admin-cli  
&nbsp;   Components:   common,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 kcMaster,  
&nbsp;                 keySmith,  
&nbsp;                 k8s/charts/esquire-kcmaster  

**v1.2.6-2606.0110**  v1.2.6 enyMan sprint -- account CREATE moves to enyMan; instance-aware entity-id minting  
&nbsp;: Refactoring: account CREATE moved from pacMan to enyMan; pacMan keeps account UPDATE / DELETE / balance ops  
&nbsp;: Feature:     entity-id minter moved to enyMan + made instance-aware -- shape ms*10000 + instanceNo*1000 + (seq % 1000); single BIGINT  
&nbsp;: Feature:     EsqUtils.instanceNo() in common -- reads ESQUIRE_INSTANCE_NO / POD_INDEX / POD_NAME ordinal / sysprop / 0  
&nbsp;: Config:      enyMan chart Deployment -> StatefulSet with POD_INDEX / POD_NAME downward API; replicaCount cap 1..10; per-pod JMS clientId  
&nbsp;: Config:      esquire.version 1.2.5 -> 1.2.6; Chart.yaml appVersion brought current to 1.2.6 across all seven service charts  
&nbsp;: Doc:         README.md sprint header  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 gateway,  
&nbsp;                 k8s/charts/esquire-enyman  

---

## Code Changes

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**06/02/2026** mir0n  v1.2.6 -- batched cache transaction for the monad event drain  
**taijitu.Monad**  
&nbsp;- _processItems(List) override: wraps the event batch in ONE cache transaction via an injected  
&nbsp;   TransactionTemplate (cacheTx); a null cacheTx falls back to super (one-by-one). ctor takes the TransactionTemplate  
**h2.BizTreeH2Config**  
&nbsp;- cacheTransactionTemplate bean: DataSourceTransactionManager over the cacheJdbcTemplate DataSource  
**access.BizTreeDirectorConfig**  
&nbsp;- cacheTransactionTemplate injected into both Monads; biztree.queue.bulk-threshold @Value (default 10) applied  
&nbsp;   to each monad via setBulkThreshold  

**06/02/2026** mir0n  v1.2.6 Goal 3 -- broadcast skip-path made visible  
**access.MessageHandlerHub**  
&nbsp;- dispatch(): no-handler and null-textNode skips split into two guarded branches, each logged via  
&nbsp;   devLog.warn (eventType / entityKind / kindBits / entityId), instead of a single silent no-op  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**06/02/2026** mir0n  v1.2.6 Goal 3 -- instanceNo() lazy-cached  
EsqUtils  
&nbsp;- instanceNo(): result cached in a volatile Integer; resolved once per JVM lifetime, env / sysprop  
&nbsp;   walk amortised to a single resolution  
&nbsp;- resetInstanceNoCacheForTests() added (package-private): nulls the cache so a test re-resolves  

**06/01/2026** mir0n  v1.2.6 -- entity-id minter moved out of common; instanceNo() added  
EsqUtils  
&nbsp;- generateEntityId() removed (moved to enyMan.service.EntityIdGenerator); esquireEpoch field removed with it  
&nbsp;- instanceNo() added: int return; reads ESQUIRE_INSTANCE_NO env, then POD_INDEX env (k8s 1.28+  
&nbsp;   pod-index downward API label), then POD_NAME env (StatefulSet ordinal parsed via helper),  
&nbsp;   then esquire.instance.no system property, default 0  

### common/src/main/java/pro/mir0n/utils/changes.txt


**06/02/2026** mir0n  v1.2.6 -- bulk processing on the queue rig + monad  
**concurrent.IQueueRig**  
&nbsp;- IQueueListWorker (extends IQueueWorker): List process(ArrayList, ISignaler) returning the unprocessed remainder  
&nbsp;- ISignaler: isRunning / isProcessing / shouldContinue (read-only run/process view)  
&nbsp;- IListErrorListener (extends IErrorListener): List onError(Throwable, ArrayList) -- null = stop the bulk  
&nbsp;- put(Collection) and setBulkThreshold(int) default methods added  
**concurrent.BoundedQueueRig**  
&nbsp;- bulk drain: when the worker is an IQueueListWorker and backlog > bulkThreshold (default 10), the whole deque  
&nbsp;   is snapshot into one ArrayList and passed to process(ArrayList, signaler); the returned remainder is re-queued  
&nbsp;   to the front; a thrown bulk is routed to the IListErrorListener (null/empty = stop, else re-run the continuation)  
&nbsp;- run / process state read through an ISignaler (processing made volatile); setBulkThreshold(int);  
&nbsp;   LoggingErrorListener now implements IListErrorListener  
**taijitu.AMonadY**  
&nbsp;- inner MonadWorker (IQueueListWorker) replaces the method-ref worker; processBatch accumulates consecutive  
&nbsp;   events and flushes _processItems(List) before any command (arrival order preserved), capped at eventBatchMax  
&nbsp;- _processItems(List) default loops _processItem; setEventBatchMax / setBulkThreshold added  

**06/02/2026** mir0n  v1.2.6 Goal 3 -- non-blocking tryPut on the queue rig  
**concurrent.IQueueRig**  
&nbsp;- tryPut(E) default method enabled (was commented out); delegates to put(E) and returns true  
**concurrent.BoundedQueueRig**  
&nbsp;- tryPut(E) override: offers under the lock; returns false when stopped or at capacity (size >= capacity),  
&nbsp;   signals the worker on success -- no wait, no silent drop  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**06/02/2026** mir0n  v1.2.6 Goal 3 -- /esq-move async-ack via in-process move queue; CREATE-during-move reconcile  
queue.MoveQueueItem  (new)  
&nbsp;- sealed interface, permits MoveCommandItem | CreateReconcileItem  
queue.MoveCommandItem  (new)  
&nbsp;- record (kind, id, distId, rootPath, uid, roles, requestId, correlationId): queued move payload  
queue.CreateReconcileItem  (new)  
&nbsp;- record (entityId, kind, parentId, pathAtPublish): post-publish path-check task  
queue.PathRule  (new)  
&nbsp;- expectedFor(kind, parentPath, ownId): ep_path composition rule -- parentPath when isPathParentOnly(),  
&nbsp;   else parentPath + ownId + "."  
queue.MoveQueueManager  (new)  
&nbsp;- @Component implementing IQueueRig.IQueueWorker; owns a BoundedQueueRig + a  
&nbsp;   "move in progress" AtomicInteger; constructs its own OrgService / UsrService  
&nbsp;- submitMove(): counter++ then tryPut; on full rolls back counter and logs error (DROPPED)  
&nbsp;- submitReconcile(): tryPut; warn + drop on full  
&nbsp;- inMove() = counter > 0; queueSize(); start()/stop() lifecycle on the rig  
&nbsp;- process() dispatches MoveCommandItem -> processMove, CreateReconcileItem -> processReconcile  
&nbsp;- processMove(): sets MDC, calls org/usr esquireCommandMove, publishMoveEvent + publishKcMoveRequest,  
&nbsp;   counter-- in finally  
&nbsp;- processReconcile(): re-reads parent path, recomputes PathRule.expectedFor; on drift updates ep_path and  
&nbsp;   reissues EVENT_UPDATE_PATH (inherits the move's CID/RID from MDC)  
&nbsp;- publishMoveEvent() / publishKcMoveRequest() lifted here from EnyManService  
jpa.EntityPathLookup  (new)  
&nbsp;- native-query repository: pathFor(id) reads ep_path; updatePath(id, path) writes it  
**service.impl.EnyManService**  
&nbsp;- MoveQueueManager injected (replaces the KcRequestPublisher field); validateCreateDuringMove @Value toggle  
&nbsp;   (enyman.move-queue.validate-create-during-move, default true)  
&nbsp;- esquireCommandMove(): pre-checks stay on the request thread, then submitMove(MoveCommandItem), returns null  
&nbsp;- submitReconcileIfInMove(): after each CREATE broadcast, enqueue a CreateReconcileItem when inMove() and toggle on  
&nbsp;- publishMoveEvent() / publishKcMoveRequest() removed (moved to MoveQueueManager)  
**controller.EnyManController**  
&nbsp;- esquireCommandMove(): returns 202 Accepted (ResponseEntity.accepted()) -- move queued, async; OpenAPI 200 -> 202  
**service.IEnyManService**  
&nbsp;- esquireCommandMove() javadoc: EnyManService impl returns null (async-ack); per-kind impls still return records  
src/main/resources/META-INF/postgres-entity.xml, oracle-entity.xml  
&nbsp;- EsqOrgJpa.listMovedPaths, EsqUsrJpa.listMovedPaths: ORDER BY length(ep_path) so ancestor rows broadcast  
&nbsp;   before descendant rows (parents-first), keeping bizTree's per-node move handlers from picking up a stale parent path  
src/main/resources/META-INF/postgres-acct.xml, oracle-acct.xml  
&nbsp;- EsqAcctJpa.pathFor, EsqAcctJpa.updatePath native queries added (move-queue worker ep_path read / write)  
**src/main/resources/META-INF/oracle-entity.xml**  
&nbsp;- EsqSubtreeRow.subtreeFromOrg / subtreeFromUsr: ORDER BY switched to positional (7 DESC, 6 NULLS LAST) --  
&nbsp;   Oracle rejects the aliased column in a UNION ALL ORDER BY (ORA-00904 LVL); fixes /esq-cmd-tree on Oracle  
**src/main/resources/application.yml**  
&nbsp;- enyman.move-queue.capacity (ENYMAN_MOVE_QUEUE_CAPACITY, default 16384);  
&nbsp;   enyman.move-queue.validate-create-during-move (ENYMAN_VALIDATE_CREATE_DURING_MOVE, default true)  

**06/01/2026** mir0n  v1.2.6 -- account CREATE moved into enyMan; entity-id minter consolidated here  
service.EntityIdGenerator  (new)  
&nbsp;- static utility owning entity-id minting; moved from common.EsqUtils.  
&nbsp;- generateEntityId(): (System.currentTimeMillis() - esquireEpoch) * 10000  
&nbsp;+ EsqUtils.instanceNo() * 1000 + (sequence.getAndIncrement() % 1000); single BIGINT.  
&nbsp;- esquireEpoch = "26 Jun 2025 13:20 EDT" (kept verbatim from v1.2.5).  
&nbsp;- AtomicInteger sequence -- thread-safe, monotonic; mint-time IllegalStateException  
&nbsp;   if instanceNo outside [0, 9].  
service.impl.AcctService  (new)  
&nbsp;- account CREATE service  
jpa.EsqAcctRepository  (new)  
&nbsp;- CREATE-only Spring Data repository for esq_account; native queries acctPath, insertAcctPath, insertAcct.  
src/main/resources/META-INF/postgres-acct.xml, oracle-acct.xml  (new)  
&nbsp;- named native queries for EsqAcctJpa.acctPath, EsqAcctJpa.insertAcctPath, EsqAcctJpa.insertAcct  
&nbsp;   (per dialect); CREATE subset of pacMan's acct.xml.  
**service.impl.EnyManService**  
&nbsp;- EsqAcctRepository injected; acctService field constructed alongside orgService / usrService.  
&nbsp;- esquireCommandNew() applicability widened from (isOrg || isUsr) to (isOrg || isUsr || isAcct);  
&nbsp;   isAcct branch routes to acctService and publishes EVENT_CREATE.  
&nbsp;- publishEntityEvent() now forwards TEXT_STATUS (parity with the pacMan publisher this branch replaces).  
**service.impl.OrgService**  
&nbsp;- id minting call retargeted: EsqUtils.generateEntityId() -> EntityIdGenerator.generateEntityId().  
**service.impl.UsrService**  
&nbsp;- id minting call retargeted: EsqUtils.generateEntityId() -> EntityIdGenerator.generateEntityId().  
**src/main/resources/application.yml**  
&nbsp;- spring.jms.client-id and enyman.messaging.client-id both composed as  
&nbsp;   ${ENYMAN_MESSAGING_CLIENT_ID:enyman}-${POD_INDEX:0} -- per-pod clientId for multi-instance JMS.  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**06/01/2026** mir0n  v1.2.6 -- account CREATE rerouted to enyMan  
**resources/application.yml**  
&nbsp;- pacman-new-route deleted (was Path=/esq-cmd-new + Method=POST + EntityKind=isAcct -> pacMan).  
&nbsp;- /esq-cmd-new + EntityKind=isAcct now falls through to enyman-new-route.  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**06/02/2026** mir0n  v1.2.6 Goal 3 -- race-8c KC-path recovery buffer  
buffer.KcPathBuffer  (new)  
&nbsp;- @Component; ConcurrentHashMap + single-thread scheduled prune (ttl default 60s)  
&nbsp;- store() overwrites (latest path wins); consume() removes-and-returns, null if absent or older than ttl;  
&nbsp;   size(); prune() drops expired entries  
messaging.KcEntityBroadcastConsumer  (new)  
&nbsp;- non-durable @JmsListener on esquire.entity.broadcast (jmsTopicListenerFactory); selector on entity bus / msg-type  
&nbsp;- msg-audit receipt logged before the application filter; keeps EVENT_UPDATE_PATH ("X") only  
&nbsp;- if the KC user is missing, KcPathBuffer.store(entityId, path); if it exists, no-op (the URQ handler owns the update)  
**service.impl.KcIdentityService**  
&nbsp;- KcPathBuffer injected; createUser() flushes the buffer: consume(entityId), then applyBufferedPath()  
&nbsp;   writes esq_rootpath when it differs from the user's current value  
&nbsp;- updateEntityPath() no-KC-user branch: request side no longer buffers (skips; the X topic message is the buffer source)  
**messaging.KcMasterJmsConfig**  
&nbsp;- entity-broadcast topic subscription DURABLE -> NON-DURABLE; clientId + CachingConnectionFactory wiring removed;  
&nbsp;   jmsDurableTopicListenerFactory renamed jmsTopicListenerFactory  
**src/main/resources/application.yml**  
&nbsp;- kcmaster.path-buffer.ttl-ms (KCMASTER_PATH_BUFFER_TTL_MS, default 10000);  
&nbsp;   kcmaster.path-buffer.prune-interval-ms (KCMASTER_PATH_BUFFER_PRUNE_MS, default 30000)  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**06/02/2026** mir0n  v1.2.6 Goal 3 -- race-8c reproduction test hook  
**service.impl.KeySmithService**  
&nbsp;- esquireKeySave(): KEYSMITH_TEST_CONNECT_HOLD_MS env hook -- optional Thread.sleep between the committed  
&nbsp;   path read and the activation URQ publish; default 0 = disabled, never set in production  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**06/02/2026** mir0n  v1.2.6 -- Postgres datasource port typo fixed  
**src/main/resources/application.yml**  
&nbsp;- dev-postgres datasource url now reads ${DB_PACMAN_PORT:5432} (was the mixed-case ${DB_pacMAN_PORT:5432},  
&nbsp;   a silent no-op that pinned the port at the default); now matches the dev-oracle profile  

**06/01/2026** mir0n  v1.2.6 -- account CREATE removed from pacMan (moved to enyMan)  
**controller.PacManController**  
&nbsp;- esquireCommandNew() handler removed.  
**service.IPacManService**  
&nbsp;- esquireCommandNew(int, String, String, Map, String, String, List) removed from interface.  
**service.impl.PacManService**  
&nbsp;- esquireCommandNew() and private createAcct() helper removed.  
**jpa.EsqAcctRepository**  
&nbsp;- insertAcct() and insertAcctPath() Spring Data methods removed (CREATE moved to enyMan).  
src/main/resources/META-INF/postgres-acct.xml, oracle-acct.xml  
&nbsp;- EsqAcctJpa.insertAcct and EsqAcctJpa.insertAcctPath named native queries removed.  
&nbsp;- EsqAcctJpa.acctPath retained.  

---

## Commits

```

-- 2026-06-02 | commit: bc3f797 | mir0n.the.programmer |  v1.2.6 -- bizTree work-batching + Oracle / pacMan fixes --
M	README.md
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	common/src/main/java/pro/mir0n/utils/changes.txt
M	common/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
M	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
A	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	common/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
A	common/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
A	compose/compose.oracle.yaml
M	compose/compose.yaml
M	doc/Esquire.TestingStack.md
M	doc/release_notes.txt
A	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EntityPathLookup.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/CreateReconcileItem.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveCommandItem.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueItem.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/PathRule.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EntityIdGeneratorTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
 45 files changed, 2150 insertions(+), 372 deletions(-)

-- 2026-06-02 | commit: 974523e | mir0n.the.programmer | v1.2.6 enyMan sprint -- move-command race fixes (race-8b / race-8c); async move queue --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/java/pro/mir0n/utils/changes.txt
M	common/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
M	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBuffer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcMasterJmsConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBufferTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
 24 files changed, 747 insertions(+), 31 deletions(-)

-- 2026-06-02 | commit: 8ddc209 | mir0n.the.programmer | v1.2.6 enyMan sprint -- account CREATE moves to enyMan; instance-aware entity-id minting --
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqAcctRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/EntityIdGenerator.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
A	enyMan/src/main/resources/META-INF/oracle-acct.xml
A	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-backend/Chart.yaml
M	k8s/charts/esquire-biztree/Chart.yaml
M	k8s/charts/esquire-enyman/Chart.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/templates/service.yaml
A	k8s/charts/esquire-enyman/values.schema.json
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/Chart.yaml
M	k8s/charts/esquire-kcmaster/Chart.yaml
M	k8s/charts/esquire-keysmith/Chart.yaml
M	k8s/charts/esquire-pacman/Chart.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/pacman.yaml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml


-- 2026-05-24 | commit: b38037d | mir0n.the.programmer | v1.2.6 pending note --
M	README.md
 40 files changed, 610 insertions(+), 341 deletions(-)


 1 file changed, 1 insertion(+), 1 deletion(-)

-- 2026-05-25 | commit: 7a1848f | mir0n.the.programmer | img path fix --
M	doc/Esquire.BizTree.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.TestingStack.md


-- 2026-05-24 | commit: b38037d | mir0n.the.programmer | v1.2.6 pending note --
M	README.md
 3 files changed, 10 insertions(+), 10 deletions(-)

 1 file changed, 1 insertion(+), 1 deletion(-)

-- 2026-05-24 | commit: b38037d | mir0n.the.programmer | v1.2.6 pending note --
M	README.md
 1 file changed, 1 insertion(+), 1 deletion(-)

-- 2026-05-24 | commit: 48aa623 | mir0n.the.programmer | Create report_v1.2.5.md --
A	doc/reports/report_v1.2.5.md
 1 file changed, 634 insertions(+)

```

---

## Files Modified

```
M	README.md
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/java/pro/mir0n/utils/changes.txt
M	common/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
M	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
M	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
A	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	common/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
A	common/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
A	compose/compose.oracle.yaml
M	compose/compose.yaml
M	doc/Esquire.BizTree.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.TestingStack.md
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.5.md
A	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EntityPathLookup.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqAcctRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/CreateReconcileItem.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveCommandItem.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueItem.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/PathRule.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/EntityIdGenerator.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
A	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
A	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EntityIdGeneratorTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/resources/application.yml
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-backend/Chart.yaml
M	k8s/charts/esquire-biztree/Chart.yaml
M	k8s/charts/esquire-enyman/Chart.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/templates/service.yaml
A	k8s/charts/esquire-enyman/values.schema.json
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/Chart.yaml
M	k8s/charts/esquire-kcmaster/Chart.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/Chart.yaml
M	k8s/charts/esquire-pacman/Chart.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBuffer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcMasterJmsConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBufferTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
 97 files changed, 4145 insertions(+), 748 deletions(-)
```

---

*From `v1.2.5` till `v1.2.6`*

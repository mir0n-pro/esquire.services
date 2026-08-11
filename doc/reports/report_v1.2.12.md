# Release Report: v1.2.11 → v1.2.12

**Repo:** `esquire.services/develop`  
**Top commit:** `76b8b69`

---

## Release Notes

### doc/release_notes.txt


**v1.2.12-2608.1114**  v1.2.12 -- Entity change number  
&nbsp;: Feature:     every office, user, account, sign-in record, personal-details record, address and custom  
&nbsp;                 parameter row carries a change number that goes up by one each time the row is written  
&nbsp;: Feature:     where an entity sits in the tree is counted separately from the entity itself, so moving a  
&nbsp;                 branch is told apart from changing the records under it  
&nbsp;: Feature:     the change number travels on the message that announces a change (ChangeNo, FIX tag 50015)  
&nbsp;                 and is written into the audit trail  
&nbsp;: Feature:     bizTree applies an announced change only when its number is newer than the one the cache  
&nbsp;                 already holds, so a repeat or a late arrival is skipped instead of being repaired afterwards  
&nbsp;: Feature:     the audit trail's optional repeat protection is keyed on the record and its change number  
&nbsp;                 rather than on the request that caused the change, so the database-trigger and the  
&nbsp;                 messaging routes agree and can be used together  
&nbsp;: Feature:     a money movement records which version of the account it produced, so the ledger and the  
&nbsp;                 account history can be checked against each other by number  
&nbsp;: Fix:         kcMaster's create-while-move path buffer keeps the newest path rather than the last one to  
&nbsp;                 arrive  
&nbsp;: Refactoring: the expiring hand-off cache behind that buffer moved out of kcMaster into the shared  
&nbsp;                 mir0n-utils library  
&nbsp;: Refactoring: the unused bank-details table and its access code removed  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 mir0n-utils,  
&nbsp;                 dataKeep,  
&nbsp;                 audit,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 pacMan  
&nbsp;   Doc:          doc\EntityDictionary.md  
&nbsp;                 doc\DatabaseDictionary.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\Esquire.MessagingBus.MessageStructure.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Esquire.Auth.md  
&nbsp;                 doc\Esquire.Q&A.md  
&nbsp;                 doc\Esquire.ContinuingDev.md  
&nbsp;                 doc\Esquire.Vision.md  

---

## Code Changes

### audit/src/main/java/pro/mir0n/esquire/audit/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
AuditBusBridge  
&nbsp;- post() carries the row's change number onto the event header: the IMappable overload reads it from the  
&nbsp;   source (passed on a DELETE too, where the body is dropped), and a new overload takes it with a  
&nbsp;   pre-mapped body  
**resources/META-INF/audit/postgres.xml**  
&nbsp;- all eight *_log INSERTs carry *_change_no from :changeNo; the ON CONFLICT target stays off so the  
&nbsp;   statement follows whatever dedup index is installed  
**resources/META-INF/audit/oracle.xml**  
&nbsp;- all eight *_log MERGEs carry *_change_no from :changeNo, and their ON clauses are rekeyed to the row  
&nbsp;   plus its change number, with the correlation id out of the key  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
**access.IBizTreeDirector**  
&nbsp;- onRodEvent passes e.changeNo() to onEntityBroadcast, whose signature gains the parameter  
**access.MessageHandlerHub**  
&nbsp;- dispatch() takes the event's change number and guards on it: a path event against the node's stored  
&nbsp;   path number, every other event against its entity number; unknown or unseen applies unguarded, and the  
&nbsp;   applied number is stamped back. Takes IBizTreeCacheRepository for those reads  
**access.legacy.BizTreeDirectorLegacy**  
&nbsp;- onEntityBroadcast takes the change number and passes it to handlerHub.dispatch  
**cache.IBizTreeCacheRepository**  
&nbsp;- findChangeNumbers() returns the node's [entityChangeNo, pathChangeNo] (null when the entity is not  
&nbsp;   cached); stampEntityChangeNo() and stampPathChangeNo() write an applied number onto every row of the  
&nbsp;   entity  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- findChangeNumbers, stampEntityChangeNo and stampPathChangeNo implemented; the node mapper reads the two  
&nbsp;   new columns  
**cache.BizTreeCacheLoader**  
&nbsp;- the loaded node rows carry the entity and path change numbers read from the database; folder rows get  
&nbsp;   null for both  
**cache.BizTreeCacheSql**  
&nbsp;- findChangeNumbers, stampEntityChangeNo and stampPathChangeNo added to the SQL set  
**cache.CacheSqlSet**  
&nbsp;- the three change-number statements added to the per-table substitution set  
**h2.BizTreeH2Config**  
&nbsp;- the three change-number statements resolved from the properties into the SQL set  
**taijitu.IEventSink**  
&nbsp;- apply() takes the event's change number; null means the producer sent none and the event applies  
&nbsp;   unguarded  
**taijitu.Monad**  
&nbsp;- the queued item's change number is passed to eventHub.apply  
**resources/META-INF/h2-cache-sql.properties**  
&nbsp;- the node table gains TREE_ENTITY_CHANGE_NO and TREE_PATH_CHANGE_NO, both in the checksum; new  
&nbsp;   find-change-numbers read and stamp-entity-change-no / stamp-path-change-no updates  
**resources/META-INF/postgres-entity.xml**  
&nbsp;- the three findAllForTree reads select org/usr/acc_change_no and ep_change_no, mapped to changeNo and  
&nbsp;   pathChangeNo, so a cache load starts from the database values  
**resources/META-INF/oracle-entity.xml**  
&nbsp;- the three findAllForTree reads select the entity and path change numbers, matching the postgres dialect  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
**dto.EsqEntity**  
&nbsp;- changeNo field added, carried from the JPA row; @JsonIgnore + @Schema(hidden) keep it out of the REST  
&nbsp;   contract  
**jpa.EsqEntityJpa**  
&nbsp;- changeNo and pathChangeNo fields added (two separate counters) plus bumpChangeNo(), the one place a  
&nbsp;   change number moves; neither is emitted by fillMap()  
**jpa.EsqNameValueJpa**  
&nbsp;- changeNo field and bumpChangeNo() added  
**jpa.IMappable**  
&nbsp;- getChangeNo() default added; read separately from fillMap() because the number rides the x-Rod header  
**jpa.entity.EsqParRow**  
&nbsp;- changeNo field and bumpChangeNo() added, its own copy: the class is outside the EsqEntityJpa hierarchy  

### dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
**keep.RodEventDbWriter**  
&nbsp;- PARAM_CHANGE_NO added and bound from the event in the header parameter map  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
**jpa.EntityPathLookup**  
&nbsp;- updatePath raises ep_change_no inline (the path table is written under one global lock, not read for  
&nbsp;   update per row); pathChangeNoFor() reads the raised number back  
**jpa.EsqMoveRecord**  
&nbsp;- changeNo component added: the PATH row's number, the only number a descendant's move can carry  
**jpa.EsqOrgRepository**  
&nbsp;- updateOrg, moveOrgParent and the org path statements take a changeNo @Param  
**jpa.EsqUsrRepository**  
&nbsp;- the user, person, address and custom-parameter update statements take a changeNo @Param;  
&nbsp;   deletePersonBankInfo removed with the bank-info table  
**messaging.EntityBusAdapter**  
&nbsp;- publish() takes the change number onto the event header and prints it on the UE line; the parameter doc  
&nbsp;   states which counter each event type carries  
**messaging.KcBusAdapter**  
&nbsp;- the RodEvent constructor call carries a null change number: a KeyCloak request leg reports none  
**queue.MoveQueueManager**  
&nbsp;- the move broadcast carries the PATH change number: taken from the move record, and read back with  
&nbsp;   pathChangeNoFor after a reconcile repair so the reissue is not skipped by the receiver's guard  
**service.IEnyManService**  
&nbsp;- esquireCommandDelete returns the delete's change number for the caller's broadcast  
**service.impl.EnyManService**  
&nbsp;- esquireCommandDelete returns the number the delete raised and publishDeleteEvent carries it; the entity  
&nbsp;   broadcast carries the change number on create, save and move  
**service.impl.OrgService**  
&nbsp;- the org row's change number is raised before every update and passed to the statement; delete bumps  
&nbsp;   once and returns the number, which the delete event and the audit record share  
**service.impl.UsrService**  
&nbsp;- the user, person, address and custom-parameter rows raise their change numbers before their updates;  
&nbsp;   delete bumps once and returns the number, which the delete event and the audit record share  
**service.impl.AcctService**  
&nbsp;- esquireCommandDelete returns the delete's change number; a created account is stamped with 1, the  
&nbsp;   column default, for its CREATE event  
**resources/META-INF/postgres-entity.xml**  
&nbsp;- the org, user, person and address reads select their *_change_no and their updates set it from  
&nbsp;: changeNo; the move statements and the path update raise ep_change_no inline, and the moved-path reads  
&nbsp;   return it; EsqUsrJpa.deletePersonBankInfo removed  
**resources/META-INF/oracle-entity.xml**  
&nbsp;- the same change-number reads, updates and inline path raises, matching the postgres dialect;  
&nbsp;   EsqUsrJpa.deletePersonBankInfo removed  
**resources/META-INF/postgres-acct.xml**  
&nbsp;- EsqAcctJpa.updatePath raises ep_change_no inline, and the new EsqAcctJpa.pathChangeNoFor reads it back  
**resources/META-INF/oracle-acct.xml**  
&nbsp;- EsqAcctJpa.updatePath raises ep_change_no inline and EsqAcctJpa.pathChangeNoFor reads it back, matching  
&nbsp;   the postgres dialect  
**resources/META-INF/postgres-custom-field.xml**  
&nbsp;- the org and user parameter reads select *_change_no as changeNo and their updates set it from :changeNo  
**resources/META-INF/oracle-custom-field.xml**  
&nbsp;- the same parameter change-number reads and updates, matching the postgres dialect  
**resources/META-INF/audit/postgres-par.xml**  
&nbsp;- the parameter re-SELECTs carry *_change_no as changeNo onto the audit rows  
**resources/META-INF/audit/oracle-par.xml**  
&nbsp;- the parameter re-SELECTs carry *_change_no as changeNo, matching the postgres dialect  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
messaging.ParkedPath  (new)  
&nbsp;- a moved entity's new path parked with the PATH change number that produced it, so the race-8c buffer  
&nbsp;   keeps the newest arrival rather than the last one. Orders itself by that number (Comparable), which is  
&nbsp;   all ExpiringCache.storeIfGreater needs; an absent number never displaces a numbered path  
**messaging.EntityBusAdapter**  
&nbsp;- the path park moved to the shared ExpiringCache and now holds a ParkedPath: storeIfGreater keeps the  
&nbsp;   newest path by its path change number instead of the last arrival, in one atomic step  
**messaging.KcBusAdapter**  
&nbsp;- the RodEvent constructor call carries a null change number: a KeyCloak request leg reports none  
**service.impl.KcIdentityService**  
&nbsp;- createUser consumes a ParkedPath from the shared ExpiringCache and applies its path  
**config.KeycloakConfig**  
&nbsp;- the race-8c path buffer is declared here as an ExpiringCache bean, shared by the  
&nbsp;   topic adapter and createUser, and logs its effective ttl and prune interval at startup  
buffer.KcPathBuffer  (removed)  
&nbsp;- the map, timestamps, ttl, prune thread and lazy expiry moved to mir0n-utils ExpiringCache  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
**jpa.EsqAccessProfileRepository**  
&nbsp;- updateAccess takes a changeNo @Param  
**messaging.KcBusAdapter**  
&nbsp;- the RodEvent constructor call carries a null change number: a KeyCloak request leg reports none  
**service.impl.KeySmithService**  
&nbsp;- saveAccessProfile raises the auth row's change number and passes it to updateAccess; the audit copy is  
&nbsp;   stamped with the raised value  
**resources/META-INF/postgres-access-profile.xml**  
&nbsp;- the access reads select au_change_no, updateAccess sets it from :changeNo, and confirmPendingFlags  
&nbsp;   raises it inline in its own UPDATE  
**resources/META-INF/oracle-access-profile.xml**  
&nbsp;- the same auth change-number read, update and inline raise, matching the postgres dialect  

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
BusConstants  
&nbsp;- FIELD_CHANGE_NO added (ChangeNo, FIX 50015), the per-row change number supplied by the producer; the  
&nbsp;   declared exception is stated at the field: C/U/D carry the entity row's number, X the path row's,  
&nbsp;   never comparable  
RodEvent  
&nbsp;- changeNo component added to the identity group and to the producer constructor as a normal argument;  
&nbsp;   the record is documented as identity / header / payload / engine-stamped tail  
**xrod.RodEventCodec**  
&nbsp;- changeNo written to and read from the wire properties; new longOrNull() keeps an absent number null  
&nbsp;   instead of 0  
**xrod.impl.MsgAudit**  
&nbsp;- changeNo added to the TX/RX leg trace, printed as "-" when the producer supplied none  

### mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
concurrent.ExpiringCache  (new)  
&nbsp;- generic hand-off cache whose entries expire by age -- a ConcurrentHashMap with a timestamp per entry,  
&nbsp;   one daemon prune thread and a lazy age check on read, so an un-started cache is still correct.  
&nbsp;   store() parks a value, consume() takes it away, storeIfGreater() parks only when the arrival compares  
&nbsp;   greater than what is there, in one atomic merge. The value type is Comparable, so the cache needs no  
&nbsp;   comparator and never learns what its callers order by  
**taijitu.ITaijituRig**  
&nbsp;- onEntityBroadcast gains a changeNo parameter, with an unnumbered default overload for producers that  
&nbsp;   report none  
**taijitu.ATaijituRig**  
&nbsp;- onEntityBroadcast takes the change number and puts it on the QueueItem  
**taijitu.ATaijituRigY**  
&nbsp;- onEntityBroadcast takes the change number and puts it on the QueueItem  
**taijitu.QueueItem**  
&nbsp;- changeNo component added, plus a traced-but-unnumbered constructor; the no-trace constructor passes  
&nbsp;   null for both  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**08/11/2026** mir0n  v1.2.12 -- entity change number  
**acct.jpa.EsqAcctTransactionJpa**  
&nbsp;- accChangeNo field added: the ACCOUNT change number the transaction produced, nullable  
**acct.jpa.EsqAcctTransactionRepository**  
&nbsp;- insertAcctTransaction takes an accChangeNo @Param  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- the account's change number is raised before the insert, so the ledger line and the balance update  
&nbsp;   carry the same value and the transaction row points at the account history record it causes  
**jpa.EsqAcctRepository**  
&nbsp;- updateAcct and updateAcctBalance take a changeNo @Param  
**messaging.EntityBusAdapter**  
&nbsp;- publish() takes the change number onto the event header and prints it on the UE line  
**service.IPacManService**  
&nbsp;- esquireCommandDelete returns the delete's change number  
**service.impl.PacManService**  
&nbsp;- the account row raises its change number before every update, the entity broadcast carries it on  
&nbsp;   create, save and delete, and esquireCommandDelete returns the number the delete raised  
**resources/META-INF/postgres-acct.xml**  
&nbsp;- the account reads select acc_change_no, and updateAcct / updateAcctBalance set it from :changeNo  
**resources/META-INF/oracle-acct.xml**  
&nbsp;- the same account change-number read and updates, matching the postgres dialect  
**resources/META-INF/postgres-acct-transaction.xml**  
&nbsp;- the ledger INSERT carries atr_acc_change_no from :accChangeNo, and the read maps it to accChangeNo  
**resources/META-INF/oracle-acct-transaction.xml**  
&nbsp;- the same ledger column and mapping, matching the postgres dialect  

---

## Commits

```

-- 2026-08-11 | commit: 76b8b69 | mir0n.the.programmer | Update Esquire.TestingStack.md --
M	doc/Esquire.TestingStack.md
 1 file changed, 17 insertions(+), 16 deletions(-)


-- 2026-08-11 | commit: d6f8014 | mir0n.the.programmer |  v1.2.12 -- Entity change number --
M	README.md
M	Releases.md
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	auKeep/src/test/resources/it-account-log.sql
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/main/resources/META-INF/audit/oracle.xml
M	audit/src/main/resources/META-INF/audit/postgres.xml
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditKindsTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CacheSqlSet.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IEventSink.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/main/resources/META-INF/oracle-entity.xml
M	bizTree/src/main/resources/META-INF/postgres-entity.xml
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/MappingXmlWellFormedTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/ChecksumSqlTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/InsertNodeArityTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqNameValueJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/IMappable.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqParRow.java
A	common/src/test/java/pro/mir0n/esquire/backend/MappingXmlWellFormedTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqW3cIdConformanceTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
M	doc/DatabaseDictionary.md
M	doc/EntityDictionary.md
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.MessageStructure.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.Vision.md
M	doc/img/auth-move.svg
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EntityPathLookup.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqMoveRecord.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/audit/oracle-par.xml
M	enyMan/src/main/resources/META-INF/audit/postgres-par.xml
M	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-custom-field.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-custom-field.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/MappingXmlWellFormedTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DictionaryCompletionConcurrencyTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBuffer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPath.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBufferTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPathTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
A	keySmith/src/test/java/pro/mir0n/esquire/keySmith/MappingXmlWellFormedTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
A	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
M	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionJpa.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/MappingXmlWellFormedTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/jpa/AcctTransactionSqlTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
 150 files changed, 4187 insertions(+), 978 deletions(-)

-- 2026-07-28 | commit: 35ccd4b | mir0n.the.programmer | file heading update: md img rendering issue --
M	README.md
M	Releases.md
M	doc/DatabaseDictionary.md
M	doc/EntityDictionary.md
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.TokenPatterns.md
M	doc/Esquire.Auth.keySmithRoutine.md
M	doc/Esquire.Auth.md
M	doc/Esquire.BizTree.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.GrafanaGuide.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.Guides.md
M	doc/Esquire.MessagingBus.MessageStructure.md
M	doc/Esquire.MessagingBus.Q&A.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.ObservabilityStack.Logging.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/install/Docker.md
M	doc/install/LocalK8s.md
A	doc/logo/angular.png
A	doc/logo/node.js.png
A	doc/media/tempo_logo.png
M	doc/services.configuring.md
M	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
 35 files changed, 639 insertions(+), 219 deletions(-)

-- 2026-07-27 | commit: 4a72c27 | mir0n.the.programmer | Create report_v1.2.11.md --
A	doc/reports/report_v1.2.11.md
 1 file changed, 2944 insertions(+)
```

---

## Files Modified

```
M	README.md
M	Releases.md
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	auKeep/src/test/resources/it-account-log.sql
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/main/resources/META-INF/audit/oracle.xml
M	audit/src/main/resources/META-INF/audit/postgres.xml
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditKindsTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CacheSqlSet.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/IEventSink.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/main/resources/META-INF/oracle-entity.xml
M	bizTree/src/main/resources/META-INF/postgres-entity.xml
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/MappingXmlWellFormedTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/ChecksumSqlTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/InsertNodeArityTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqNameValueJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/IMappable.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqParRow.java
A	common/src/test/java/pro/mir0n/esquire/backend/MappingXmlWellFormedTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqW3cIdConformanceTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
M	doc/DatabaseDictionary.md
M	doc/EntityDictionary.md
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.TokenPatterns.md
M	doc/Esquire.Auth.keySmithRoutine.md
M	doc/Esquire.Auth.md
M	doc/Esquire.BizTree.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.GrafanaGuide.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.Guides.md
M	doc/Esquire.MessagingBus.MessageStructure.md
M	doc/Esquire.MessagingBus.Q&A.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.ObservabilityStack.Logging.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/img/auth-move.svg
M	doc/install/Docker.md
M	doc/install/LocalK8s.md
A	doc/logo/angular.png
A	doc/logo/node.js.png
A	doc/media/tempo_logo.png
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
A	doc/reports/report_v1.2.11.md
M	doc/services.configuring.md
M	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EntityPathLookup.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqMoveRecord.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/audit/oracle-par.xml
M	enyMan/src/main/resources/META-INF/audit/postgres-par.xml
M	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-custom-field.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-custom-field.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/MappingXmlWellFormedTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DictionaryCompletionConcurrencyTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBuffer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPath.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/buffer/KcPathBufferTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPathTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
A	keySmith/src/test/java/pro/mir0n/esquire/keySmith/MappingXmlWellFormedTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
A	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
M	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionJpa.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/MappingXmlWellFormedTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/jpa/AcctTransactionSqlTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
 174 files changed, 7782 insertions(+), 1208 deletions(-)
```

---

*From `v1.2.11` till `v1.2.12`*

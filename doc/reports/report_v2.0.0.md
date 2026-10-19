# Release Report: v1.2.16 → HEAD

**Repo:** `esquire.services/develop2`  
**Top commit:** `94ed4ce`

---

## Release Notes

### doc/release_notes.txt


**v2.0.0-2610.1720**  v2.0.0 -- the shared libraries are products of their own  
&nbsp;: Refactoring: the utilities, the recoverable cache and the load-test harness come from Armoury:  
&nbsp;                 armoury-utils, armoury-taijitu, armoury-hauberk  
&nbsp;: Refactoring: the messaging bus, its transports and its event store come from Herald:  
&nbsp;                 herald-messaging, herald-tp-*, herald-datakeep  
&nbsp;: Refactoring: hauberk holds the Esquire load tests, built on the Armoury harness  
&nbsp;: Config:      Armoury and Herald are taken from GitHub Packages, in the CI and on every machine  
&nbsp;: Doc:         README.md  
&nbsp;                 doc\v2.0.x.Goals.md (new)  
&nbsp;                 doc\v2.0.x.Planning.md (new)  
&nbsp;                 doc\Esquire.MessagingBus*.md moved to Herald  
&nbsp;                 doc\Esquire.DevProcess.md  
&nbsp;                 doc\Esquire.DevSetup.md  
&nbsp;   Components:   all services,  
&nbsp;                 hauberk  

---

## Code Changes

### auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt


**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  

### audit/src/main/java/pro/mir0n/esquire/audit/changes.txt


**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqBizMeters / EsqTraceMark -> pro.mir0n.o11y.BizMeters / TraceMark,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
&nbsp;- pro.mir0n.utils.taijitu -> pro.mir0n.taijitu, from armoury-taijitu; the classes are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  
&nbsp;- pro.mir0n:armoury-taijitu, new: the taijitu cache is a jar of its own in Armoury  
&nbsp;- moveUsrNode(): admin-aware orgPk extraction -- admin ep_path last segment is org pk  
&nbsp;- folderKindForUsr(): skip FOLDER_SYS_ADMIN -- admin users no longer mis-routed into root-org sys-admin folder  


### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
o11y.EsqBizMeters, o11y.EsqTraceMark, o11y.EsqGauge  (removed)  
&nbsp;- moved to Armoury as pro.mir0n.o11y.BizMeters / TraceMark / Gauges and deleted here. All three imported  
&nbsp;   Micrometer and the JDK only, so they sat below the domain, not in it. They had to move: the keep writer  
&nbsp;   called two of them and the keep travels with the bus, which made the Esquire domain a dependency of  
&nbsp;   Herald. The meter and span NAMES are unchanged, so no dashboard moves  
**o11y.ObservabilityConfig**  
&nbsp;- registers the facades in their new package; it still owns the startup hand-off of both registries  
**o11y.EsqRodObserver**  
&nbsp;- the gauge seam it hands off to is pro.mir0n.o11y.Gauges  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqBizMeters / EsqGauge / EsqTraceMark -> pro.mir0n.o11y.BizMeters / Gauges / TraceMark,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  
&nbsp;- applyFields(jpa, fields): simple overload -- delegates with personal=false, subLayer=0, writables=null  
&nbsp;- enforceDefaults() removed -- moved to EntityFieldUtils; BeanWrapper/ArrayList imports removed  
&nbsp;- deleted @Schema example corrected: "false" -> "N"  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  

### dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt

**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqBizMeters -> pro.mir0n.o11y.BizMeters,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  
&nbsp;- applyFields() removed -- moved to EntityFieldUtils; BeanWrapper/PropertyDescriptor/ValidatorFactory imports removed  

### gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt

**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqTraceMark -> pro.mir0n.o11y.TraceMark,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt

**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqBizMeters -> pro.mir0n.o11y.BizMeters,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  
&nbsp;- pacman-new/del-route, enyman-new/del-route: Path /esq-new->/esq-cmd-new, /esq-del->/esq-cmd-del  

### hauberk/changes.txt

hauberk changes  

**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
hauberk  (new project, esquire-hauberk)  
&nbsp;- the Esquire test routines move in from explorer/hauberk: chain (36 operation classes), simulations (22  
&nbsp;   plus LoadScenarios), reconcile.KcRecover, cli.KcReconcileCommand, config.EntityKinds, the per-target  
&nbsp;   properties and the perf / smoke scripts. The harness they ran on is Armoury's armoury-hauberk now  
config.EsqHauberkConfig  (new)  
&nbsp;- the Esquire half of the old HauberkConfig: gw.base, bff.base, enyman.base, playground.parent.id and the  
&nbsp;   prepare / move / super shape knobs, read through Armoury's accessors. Every property NAME is unchanged  
simulations.AEsqSimulation  (new)  
&nbsp;- one line over Armoury's AHauberkSimulation: it supplies the gateway base URL. The 22 Simulations extend  
&nbsp;   this instead of HauberkSimulation; nothing else in them changed  
cli.EsqRunCommand  (new)  
&nbsp;- the seventeen Esquire shape knobs (--read / --update / --create / --move / --tx / --tree / --readacct /  
&nbsp;- -treesmall / --cache / --cachesmall, --prep-*, --move-*), added back onto Armoury's run subcommand by  
&nbsp;   extension. picocli reads inherited options, so the command line is unchanged from v1.2.16  
cli.EsqHauberkCli  (new)  
&nbsp;- the Main-Class of the shaded jar: the Esquire run subcommand plus Armoury's list / summary / diff and  
&nbsp;   the Esquire kc-reconcile. finalName stays "hauberk", so hauberk.cmd and every script run as before  
**hauberk.properties**  
&nbsp;- the settings the harness itself now needs told: simulations.package, the three health.probe.* /  
&nbsp;   health.auth.probe URLs that used to be hard-coded, the five perf.header.* names that used to be  
&nbsp;   compiled-in mirrors of EsqConstants, perf.run.label, and kc.admin.client.id  
&nbsp;- cmd.recreate-biztree runs the services2 compose file, not the 1.2 one  
**hauberk-compact.properties**  
&nbsp;- cmd.recreate-biztree runs the services2 compose-compact file, not the 1.2 one  
**pom.xml**  
&nbsp;- : armoury from GitHub Packages (maven.pkg.github.com/mir0n-pro/armoury). The project has  
&nbsp;   no parent, so it declares its own  
**perf-pairs.ps1**  
&nbsp;- the default -OutDir is output\perf-pairs inside this project  
hauberk-aws.properties  (removed)  
&nbsp;- no aws target on the 2.0 line  
(the tree)  
&nbsp;- the projects that left: mir0n-utils, messaging, dataKeep and tp-* x5. What stays is the Esquire domain  
&nbsp;   and its services, plus the new hauberk project  
&nbsp;- anything aws left with them: compose-aws, k8s-aws, k8s-aws-compact, test/aws. The 2.0 line has no AWS  
&nbsp;   path until one is built; the 1.2 overlays are frozen and untouched  
&nbsp;- doc/ is out: the doc set is refined separately, and the rendered 1.2 reports at the root went with it  
**pom.xml**  
&nbsp;- version 2.0.0 (the parent too); the module list is common, bizTree, gateway, enyMan, pacMan, keySmith,  
&nbsp;   kcMaster, mesnie, gateWard, audit, auKeep, hauberk. The AWS SDK BOM import is gone with the tp-*  
&nbsp;   providers that used it; ${armoury.version} and ${herald.version} are new  
&nbsp;- : armoury and herald from GitHub Packages (maven.pkg.github.com/mir0n-pro/...)  
.github/workflows/ci.yml  
&nbsp;- a step writes the Maven settings for GitHub Packages: servers armoury and herald, token GHCR_TOKEN  
**build.all.bat**  
&nbsp;- the mir0n-utils, messaging and dataKeep steps are gone (Armoury and Herald build in their own trees);  
&nbsp;   the frontend and backend images build from ..\explorer2  
**build.services.bat**  
&nbsp;- the comment names the reactor as it now is, and says where the bus and the base libraries come from  
compose/compose.yaml, compose-compact/compose.yaml  
&nbsp;- the backend and frontend build contexts point at ../../explorer2, so a 2.0 stack cannot bake the 1.2 SPA  
**test/audit-smoke/run.sh**  
&nbsp;- hauberk is found at ${SVCS}/hauberk, inside this tree, instead of ../explorer/hauberk  
enyMan, keySmith, mesnie, pacMan application.yml  
&nbsp;- rod-class: pro.mir0n.dataKeep.keep.XRodInProcessKeep -- the keep pod is named reflectively in config,  
&nbsp;   so the package move lands here too  


### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqBizMeters -> pro.mir0n.o11y.BizMeters,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  
x removed messaging.KcEntityBroadcastConsumer -- path sync routed through KC request queue  
&nbsp;- publishSuccess/publishFailure: entityKind param added -- echoes actual entity kind  
&nbsp;- updateUser(): removed changed-flag guard -- attributes always merged and applied  
&nbsp;- CREATE/UPDATE/DELETE dispatch; tfaMethod->requireTotp/removeTotp; pwdChangeForced;  
&nbsp;   null roles->empty list; unknown command->IllegalArgumentException  
&nbsp;- kcAudit->msgLog/devLog; applMsgId read; dual-mode URQ audit; MDC set/clear; dual error pattern  
&nbsp;- kcAudit->msgLog/devLog; mid extracted before props map; dual-mode URS and URR audit; dual error  
&nbsp;- requestId/correlationId reads; MDC set/clear; devLog; log.debug->devLog.debug; dual error  
&nbsp;- kcAudit->devLog; KC state events (STARTED/SUCCESS) promoted to log.info (console observability)  
&nbsp;- all log.debug->devLog.debug; unused imports removed  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt

**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqBizMeters -> pro.mir0n.o11y.BizMeters,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt

### mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt

**10/17/2026** mir0n  v2.0.0 -- the 2.0 line: the bus, the keep and the base libraries moved out  
(every file that imported them)  
&nbsp;- pro.mir0n.esquire.messaging -> pro.mir0n.messaging, pro.mir0n.esquire.dataKeep -> pro.mir0n.dataKeep,  
&nbsp;   pro.mir0n.esquire.tp -> pro.mir0n.tp. The types and the method signatures are unchanged; only the  
&nbsp;   package root moved, because the bus is the Herald Messaging Framework now  
&nbsp;- o11y.EsqBizMeters / EsqTraceMark -> pro.mir0n.o11y.BizMeters / TraceMark,  
&nbsp;   from armoury-utils; the meter and span names are unchanged  
**pom.xml**  
&nbsp;- esquire-messaging -> pro.mir0n:herald-messaging, esquire-dataKeep -> pro.mir0n:herald-datakeep,  
&nbsp;   esquire-tp-* -> pro.mir0n:herald-tp-*, mir0n-utils -> pro.mir0n:armoury-utils. Versions come from  
&nbsp;   ${herald.version} and ${armoury.version} in the parent  


### tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt


### tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt


### tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt


### tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt


### tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt


### tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt


---

## Commits

```

-- 2026-10-18 | commit: 94ed4ce | mir0n.the.programmer | v2.0.0 finalization --
M	.github/workflows/ci.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.Messaging.md
M	doc/Esquire.Scrum.Process.md
M	doc/Esquire.TestingStack.md
M	doc/services.configuring.md
M	doc/v2.0.x.Planning.md
M	make-javadoc.bat


-- 2026-10-18 | commit: 4f20dc9 | mir0n.the.programmer | v2.0 Initial --
M	.github/workflows/ci.yml
M	README.md
M	Releases.md
M	auKeep/pom.xml
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/pom.xml
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditKeepDirector.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditSqlTest.java
M	bizTree/Dockerfile.lx
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/BizTreeConstantsTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	build.all.bat
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesService.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityFieldTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfileTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/InvalidValueExceptionTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/NoRawGaugeBuilderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EntityFieldUtilsTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/storage/EsqRolesStorageTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/GenericValidatorTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	compose-compact/compose.yaml
M	compose-compact/o11y-test.bat
M	compose/compose.yaml
M	compose/o11y-test.bat
D	dataKeep/pom.xml
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/director/IKeepDirector.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepSqlStore.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
D	dataKeep/src/main/resources/spring.properties
D	dataKeep/src/test/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriterTest.java
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
D	doc/Esquire.MessagingBus.ContinuingDev.md
D	doc/Esquire.MessagingBus.Guides.md
D	doc/Esquire.MessagingBus.MessageStructure.md
D	doc/Esquire.MessagingBus.Q&A.md
D	doc/Esquire.MessagingBus.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.Scrum.Process.md
M	doc/Esquire.Vision.md
D	doc/img/messaging-bus-architecture.svg
D	doc/img/messaging-bus-classes.svg
D	doc/img/messaging-bus-params.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
D	doc/v1.2.x.Planning.md
R077	doc/v1.2.x.Goal.md	doc/v2.0.x.Goals.md
A	doc/v2.0.x.Planning.md
M	enyMan/Dockerfile.lx
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/main/resources/logback-spring.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateWard/pom.xml
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateWard/src/main/resources/logback-spring.xml
M	gateway/Dockerfile.lx
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactory.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	hauberk/README.md
A	hauberk/changes.txt
A	hauberk/hauberk-M.properties
A	hauberk/hauberk-S.properties
A	hauberk/hauberk-compact.properties
A	hauberk/hauberk-k8s-compact.properties
A	hauberk/hauberk-k8s.properties
A	hauberk/hauberk-oke.properties
A	hauberk/hauberk.cmd
A	hauberk/hauberk.properties
A	hauberk/k8s-smoke.bat
A	hauberk/oke-perf-matrix.bat
A	hauberk/oke-perf-matrix.ps1
A	hauberk/perf-matrix-report.py
A	hauberk/perf-matrix.bat
A	hauberk/perf-matrix.ps1
A	hauberk/perf-pairs.ps1
A	hauberk/pom.xml
A	hauberk/smoke-5.bat
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupKcOrphans.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupOfficeByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CloseAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CompareTrees.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ConnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateSubOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DeleteEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Deposit.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DisconnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/EnsureOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ForceSweep.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/KcAdminAuth.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LoginViaBff.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupAccessProfile.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupOfficeIdByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/MoveEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PickRandomFromPool.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAcctDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetails.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Transfer.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/UpdateUserAddress.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitBizTreeDown.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitCacheReady.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Withdrawal.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqHauberkCli.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqRunCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EntityKinds.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EsqHauberkConfig.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/AEsqSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CleanHouseSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CompareTreesSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CreateLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/EntitySmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/HaTimeoutSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcIntegrationSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/LoadScenarios.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossTerminateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/PrepareForAnythingSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceCacheLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSingleShotSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ReadLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ResidueCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SuperLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/TxLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/UpdateLoadSimulation.java
A	hauberk/src/test/java/pro/mir0n/esquire/hauberk/simulations/SimulationCatalogContractTest.java
M	k8s-compact/charts/esquire-aukeep/Chart.yaml
M	k8s-compact/charts/esquire-gateward/Chart.yaml
M	k8s-compact/charts/esquire-mesnie/Chart.yaml
M	k8s-compact/charts/esquire-pacman/Chart.yaml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/oke-o11y-test.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci/README.md
M	k8s-oci/grafana/esquire-logging.json
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-o11y-test.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/Chart.yaml
M	k8s/charts/esquire-biztree/Chart.yaml
M	k8s/charts/esquire-enyman/Chart.yaml
M	k8s/charts/esquire-gateway/Chart.yaml
M	k8s/charts/esquire-kcmaster/Chart.yaml
M	k8s/charts/esquire-keysmith/Chart.yaml
M	k8s/charts/esquire-pacman/Chart.yaml
M	k8s/k8s-rebuild.bat
M	k8s/o11y-test.bat
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
M	kcMaster/Dockerfile.lx
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/logback-spring.xml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGatewayOutcomeTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/Dockerfile.lx
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/themes/README-CUSTOMIZATION.md
M	keycloak/themes/README.md
M	keycloak/themes/esquire-explorer/login/resources/css/styles.css
M	mesnie/pom.xml
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/main/resources/logback-spring.xml
D	messaging/pom.xml
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IRodEventRepo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEventRepoRegistry.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/TransportHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusNode.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusRef.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusSlot.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusTransport.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalog.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/OwnExcluding.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/SelectingReceiver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportHealth.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/ISessionSublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInProcess.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SessionSublayerFactory.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/EncodeFailingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/TransportHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/TopologyDriftGuardTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/OwnExcludingTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/SelectingReceiverTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportHealthTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSendFailureTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
D	mir0n-utils/pom.xml
D	mir0n-utils/src/main/java/pro/mir0n/utils/HostId.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/HostIdTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYOutcomeTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
D	mir0n-utils/src/test/resources/logback-test.xml
M	pacMan/Dockerfile.lx
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/main/resources/logback-spring.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	test/freshness-guard/run.sh
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
M	test/o11y/o11y-aspects.py
M	test/o11y/o11y-inventory.py
D	tp-activemq/pom.xml
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TpActiveMqAutoConfigFilter.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
D	tp-activemq/src/main/resources/META-INF/spring.factories
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/CredentialParamsIntegrationTest.java
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
D	tp-kafka/pom.xml
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TpKafkaAutoConfigFilter.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
D	tp-kafka/src/main/resources/META-INF/spring.factories
D	tp-kafka/src/test/java/pro/mir0n/esquire/tp/kafka/TransportProviderTest.java
D	tp-kinesis/doc/tp-kinesis.md
D	tp-kinesis/pom.xml
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/KinesisConsumer.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
D	tp-kinesis/src/test/java/pro/mir0n/esquire/tp/kinesis/StreamModeParamsTest.java
D	tp-redis/pom.xml
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TpRedisAutoConfigFilter.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
D	tp-redis/src/main/resources/META-INF/spring.factories
D	tp-redis/src/test/java/pro/mir0n/esquire/tp/redis/TransportProviderTest.java
D	tp-sqns/doc/tp-sqns.md
D	tp-sqns/pom.xml
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sns/TransportProvider.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SnsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsConsumer.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqs/TransportProvider.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamGroupTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamsTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportTest.java
 16 files changed, 205 insertions(+), 160 deletions(-)



 506 files changed, 9831 insertions(+), 23451 deletions(-)

-- 2026-10-18 | commit: 2c4fc11 | mir0n.the.programmer | v2.0.0 CI/CD --
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-oke.yml
M	k8s-oci-compact/ghcr-push.bat
M	k8s-oci/ghcr-push.bat
M	k8s-oci/publish.bat
 5 files changed, 109 insertions(+), 40 deletions(-)

-- 2026-10-18 | commit: dfddaf3 | mir0n.the.programmer | v2.0 Initial --
M	.github/scripts/deploy-compose.cmd
M	.github/scripts/deploy-local.cmd
M	.github/workflows/ci.yml
M	.github/workflows/deploy-local.yml
M	README.md
M	Releases.md
M	auKeep/pom.xml
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/pom.xml
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditKeepDirector.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditSqlTest.java
M	bizTree/Dockerfile.lx
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/BizTreeConstantsTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	build.all.bat
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesService.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityFieldTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfileTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/InvalidValueExceptionTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/NoRawGaugeBuilderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EntityFieldUtilsTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/storage/EsqRolesStorageTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/GenericValidatorTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	compose-compact/compose.yaml
M	compose-compact/o11y-test.bat
M	compose/compose.yaml
M	compose/o11y-test.bat
D	dataKeep/pom.xml
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/director/IKeepDirector.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepSqlStore.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
D	dataKeep/src/main/resources/spring.properties
D	dataKeep/src/test/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriterTest.java
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
D	doc/Esquire.MessagingBus.ContinuingDev.md
D	doc/Esquire.MessagingBus.Guides.md
D	doc/Esquire.MessagingBus.MessageStructure.md
D	doc/Esquire.MessagingBus.Q&A.md
D	doc/Esquire.MessagingBus.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.Scrum.Process.md
M	doc/Esquire.Vision.md
D	doc/img/messaging-bus-architecture.svg
D	doc/img/messaging-bus-classes.svg
D	doc/img/messaging-bus-params.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
D	doc/v1.2.x.Planning.md
R077	doc/v1.2.x.Goal.md	doc/v2.0.x.Goals.md
A	doc/v2.0.x.Planning.md
M	enyMan/Dockerfile.lx
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/main/resources/logback-spring.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateWard/pom.xml
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateWard/src/main/resources/logback-spring.xml
M	gateway/Dockerfile.lx
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactory.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	hauberk/README.md
A	hauberk/changes.txt
A	hauberk/hauberk-M.properties
A	hauberk/hauberk-S.properties
A	hauberk/hauberk-compact.properties
A	hauberk/hauberk-k8s-compact.properties
A	hauberk/hauberk-k8s.properties
A	hauberk/hauberk-oke.properties
A	hauberk/hauberk.cmd
A	hauberk/hauberk.properties
A	hauberk/k8s-smoke.bat
A	hauberk/oke-perf-matrix.bat
A	hauberk/oke-perf-matrix.ps1
A	hauberk/perf-matrix-report.py
A	hauberk/perf-matrix.bat
A	hauberk/perf-matrix.ps1
A	hauberk/perf-pairs.ps1
A	hauberk/pom.xml
A	hauberk/smoke-5.bat
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupKcOrphans.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupOfficeByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CloseAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CompareTrees.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ConnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateSubOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DeleteEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Deposit.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DisconnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/EnsureOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ForceSweep.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/KcAdminAuth.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LoginViaBff.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupAccessProfile.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupOfficeIdByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/MoveEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PickRandomFromPool.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAcctDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetails.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Transfer.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/UpdateUserAddress.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitBizTreeDown.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitCacheReady.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Withdrawal.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqHauberkCli.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqRunCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EntityKinds.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EsqHauberkConfig.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/AEsqSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CleanHouseSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CompareTreesSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CreateLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/EntitySmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/HaTimeoutSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcIntegrationSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/LoadScenarios.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossTerminateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/PrepareForAnythingSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceCacheLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSingleShotSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ReadLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ResidueCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SuperLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/TxLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/UpdateLoadSimulation.java
A	hauberk/src/test/java/pro/mir0n/esquire/hauberk/simulations/SimulationCatalogContractTest.java
M	k8s-compact/charts/esquire-aukeep/Chart.yaml
M	k8s-compact/charts/esquire-gateward/Chart.yaml
M	k8s-compact/charts/esquire-mesnie/Chart.yaml
M	k8s-compact/charts/esquire-pacman/Chart.yaml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/oke-o11y-test.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci/README.md
M	k8s-oci/grafana/esquire-logging.json
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-o11y-test.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/Chart.yaml
M	k8s/charts/esquire-biztree/Chart.yaml
M	k8s/charts/esquire-enyman/Chart.yaml
M	k8s/charts/esquire-gateway/Chart.yaml
M	k8s/charts/esquire-kcmaster/Chart.yaml
M	k8s/charts/esquire-keysmith/Chart.yaml
M	k8s/charts/esquire-pacman/Chart.yaml
M	k8s/k8s-rebuild.bat
M	k8s/o11y-test.bat
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
M	kcMaster/Dockerfile.lx
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/logback-spring.xml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGatewayOutcomeTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/Dockerfile.lx
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/themes/README-CUSTOMIZATION.md
M	keycloak/themes/README.md
M	keycloak/themes/esquire-explorer/login/resources/css/styles.css
M	mesnie/pom.xml
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/main/resources/logback-spring.xml
D	messaging/pom.xml
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IRodEventRepo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEventRepoRegistry.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/TransportHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusNode.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusRef.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusSlot.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusTransport.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalog.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/OwnExcluding.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/SelectingReceiver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportHealth.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/ISessionSublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInProcess.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SessionSublayerFactory.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/EncodeFailingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/TransportHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/TopologyDriftGuardTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/OwnExcludingTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/SelectingReceiverTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportHealthTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSendFailureTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
D	mir0n-utils/pom.xml
D	mir0n-utils/src/main/java/pro/mir0n/utils/HostId.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/HostIdTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYOutcomeTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
D	mir0n-utils/src/test/resources/logback-test.xml
M	pacMan/Dockerfile.lx
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/main/resources/logback-spring.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	test/freshness-guard/run.sh
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
M	test/o11y/o11y-aspects.py
M	test/o11y/o11y-inventory.py
D	tp-activemq/pom.xml
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TpActiveMqAutoConfigFilter.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
D	tp-activemq/src/main/resources/META-INF/spring.factories
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/CredentialParamsIntegrationTest.java
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
D	tp-kafka/pom.xml
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TpKafkaAutoConfigFilter.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
D	tp-kafka/src/main/resources/META-INF/spring.factories
D	tp-kafka/src/test/java/pro/mir0n/esquire/tp/kafka/TransportProviderTest.java
D	tp-kinesis/doc/tp-kinesis.md
D	tp-kinesis/pom.xml
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/KinesisConsumer.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
D	tp-kinesis/src/test/java/pro/mir0n/esquire/tp/kinesis/StreamModeParamsTest.java
D	tp-redis/pom.xml
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TpRedisAutoConfigFilter.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
D	tp-redis/src/main/resources/META-INF/spring.factories
D	tp-redis/src/test/java/pro/mir0n/esquire/tp/redis/TransportProviderTest.java
D	tp-sqns/doc/tp-sqns.md
D	tp-sqns/pom.xml
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sns/TransportProvider.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SnsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsConsumer.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqs/TransportProvider.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamGroupTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamsTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportTest.java


-- 2026-10-18 | commit: 4f20dc9 | mir0n.the.programmer | v2.0 Initial --
M	.github/workflows/ci.yml
M	README.md
M	Releases.md
M	auKeep/pom.xml
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/pom.xml
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditKeepDirector.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditSqlTest.java
M	bizTree/Dockerfile.lx
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/BizTreeConstantsTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	build.all.bat
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesService.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityFieldTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfileTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/InvalidValueExceptionTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/NoRawGaugeBuilderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EntityFieldUtilsTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/storage/EsqRolesStorageTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/GenericValidatorTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	compose-compact/compose.yaml
M	compose-compact/o11y-test.bat
M	compose/compose.yaml
M	compose/o11y-test.bat
D	dataKeep/pom.xml
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/director/IKeepDirector.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepSqlStore.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
D	dataKeep/src/main/resources/spring.properties
D	dataKeep/src/test/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriterTest.java
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
D	doc/Esquire.MessagingBus.ContinuingDev.md
D	doc/Esquire.MessagingBus.Guides.md
D	doc/Esquire.MessagingBus.MessageStructure.md
D	doc/Esquire.MessagingBus.Q&A.md
D	doc/Esquire.MessagingBus.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.Scrum.Process.md
M	doc/Esquire.Vision.md
D	doc/img/messaging-bus-architecture.svg
D	doc/img/messaging-bus-classes.svg
D	doc/img/messaging-bus-params.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
D	doc/v1.2.x.Planning.md
R077	doc/v1.2.x.Goal.md	doc/v2.0.x.Goals.md
A	doc/v2.0.x.Planning.md
M	enyMan/Dockerfile.lx
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/main/resources/logback-spring.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateWard/pom.xml
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateWard/src/main/resources/logback-spring.xml
M	gateway/Dockerfile.lx
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactory.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	hauberk/README.md
A	hauberk/changes.txt
A	hauberk/hauberk-M.properties
A	hauberk/hauberk-S.properties
A	hauberk/hauberk-compact.properties
A	hauberk/hauberk-k8s-compact.properties
A	hauberk/hauberk-k8s.properties
A	hauberk/hauberk-oke.properties
A	hauberk/hauberk.cmd
A	hauberk/hauberk.properties
A	hauberk/k8s-smoke.bat
A	hauberk/oke-perf-matrix.bat
A	hauberk/oke-perf-matrix.ps1
A	hauberk/perf-matrix-report.py
A	hauberk/perf-matrix.bat
A	hauberk/perf-matrix.ps1
A	hauberk/perf-pairs.ps1
A	hauberk/pom.xml
A	hauberk/smoke-5.bat
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupKcOrphans.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupOfficeByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CloseAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CompareTrees.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ConnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateSubOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DeleteEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Deposit.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DisconnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/EnsureOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ForceSweep.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/KcAdminAuth.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LoginViaBff.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupAccessProfile.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupOfficeIdByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/MoveEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PickRandomFromPool.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAcctDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetails.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Transfer.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/UpdateUserAddress.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitBizTreeDown.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitCacheReady.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Withdrawal.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqHauberkCli.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqRunCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EntityKinds.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EsqHauberkConfig.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/AEsqSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CleanHouseSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CompareTreesSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CreateLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/EntitySmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/HaTimeoutSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcIntegrationSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/LoadScenarios.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossTerminateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/PrepareForAnythingSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceCacheLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSingleShotSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ReadLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ResidueCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SuperLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/TxLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/UpdateLoadSimulation.java
A	hauberk/src/test/java/pro/mir0n/esquire/hauberk/simulations/SimulationCatalogContractTest.java
M	k8s-compact/charts/esquire-aukeep/Chart.yaml
M	k8s-compact/charts/esquire-gateward/Chart.yaml
M	k8s-compact/charts/esquire-mesnie/Chart.yaml
M	k8s-compact/charts/esquire-pacman/Chart.yaml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/oke-o11y-test.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci/README.md
M	k8s-oci/grafana/esquire-logging.json
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-o11y-test.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/Chart.yaml
M	k8s/charts/esquire-biztree/Chart.yaml
M	k8s/charts/esquire-enyman/Chart.yaml
M	k8s/charts/esquire-gateway/Chart.yaml
M	k8s/charts/esquire-kcmaster/Chart.yaml
M	k8s/charts/esquire-keysmith/Chart.yaml
M	k8s/charts/esquire-pacman/Chart.yaml
M	k8s/k8s-rebuild.bat
M	k8s/o11y-test.bat
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
M	kcMaster/Dockerfile.lx
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/logback-spring.xml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGatewayOutcomeTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/Dockerfile.lx
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/themes/README-CUSTOMIZATION.md
M	keycloak/themes/README.md
M	keycloak/themes/esquire-explorer/login/resources/css/styles.css
M	mesnie/pom.xml
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/main/resources/logback-spring.xml
D	messaging/pom.xml
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IRodEventRepo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEventRepoRegistry.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/TransportHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusNode.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusRef.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusSlot.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusTransport.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalog.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/OwnExcluding.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/SelectingReceiver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportHealth.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/ISessionSublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInProcess.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SessionSublayerFactory.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/EncodeFailingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/TransportHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/TopologyDriftGuardTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/OwnExcludingTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/SelectingReceiverTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportHealthTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSendFailureTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
D	mir0n-utils/pom.xml
D	mir0n-utils/src/main/java/pro/mir0n/utils/HostId.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/HostIdTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYOutcomeTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
D	mir0n-utils/src/test/resources/logback-test.xml
M	pacMan/Dockerfile.lx
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/main/resources/logback-spring.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	test/freshness-guard/run.sh
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
M	test/o11y/o11y-aspects.py
M	test/o11y/o11y-inventory.py
D	tp-activemq/pom.xml
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TpActiveMqAutoConfigFilter.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
D	tp-activemq/src/main/resources/META-INF/spring.factories
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/CredentialParamsIntegrationTest.java
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
D	tp-kafka/pom.xml
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TpKafkaAutoConfigFilter.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
D	tp-kafka/src/main/resources/META-INF/spring.factories
D	tp-kafka/src/test/java/pro/mir0n/esquire/tp/kafka/TransportProviderTest.java
D	tp-kinesis/doc/tp-kinesis.md
D	tp-kinesis/pom.xml
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/KinesisConsumer.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
D	tp-kinesis/src/test/java/pro/mir0n/esquire/tp/kinesis/StreamModeParamsTest.java
D	tp-redis/pom.xml
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TpRedisAutoConfigFilter.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
D	tp-redis/src/main/resources/META-INF/spring.factories
D	tp-redis/src/test/java/pro/mir0n/esquire/tp/redis/TransportProviderTest.java
D	tp-sqns/doc/tp-sqns.md
D	tp-sqns/pom.xml
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sns/TransportProvider.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SnsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsConsumer.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqs/TransportProvider.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamGroupTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamsTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportTest.java
 509 files changed, 9916 insertions(+), 23470 deletions(-)

 506 files changed, 9831 insertions(+), 23451 deletions(-)

-- 2026-10-18 | commit: 4f20dc9 | mir0n.the.programmer | v2.0 Initial --
M	.github/workflows/ci.yml
M	README.md
M	Releases.md
M	auKeep/pom.xml
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/pom.xml
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditKeepDirector.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditSqlTest.java
M	bizTree/Dockerfile.lx
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/BizTreeConstantsTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	build.all.bat
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesService.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityFieldTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfileTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/InvalidValueExceptionTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/NoRawGaugeBuilderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EntityFieldUtilsTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/storage/EsqRolesStorageTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/GenericValidatorTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	compose-compact/compose.yaml
M	compose-compact/o11y-test.bat
M	compose/compose.yaml
M	compose/o11y-test.bat
D	dataKeep/pom.xml
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/director/IKeepDirector.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepSqlStore.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
D	dataKeep/src/main/resources/spring.properties
D	dataKeep/src/test/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriterTest.java
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
D	doc/Esquire.MessagingBus.ContinuingDev.md
D	doc/Esquire.MessagingBus.Guides.md
D	doc/Esquire.MessagingBus.MessageStructure.md
D	doc/Esquire.MessagingBus.Q&A.md
D	doc/Esquire.MessagingBus.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.Scrum.Process.md
M	doc/Esquire.Vision.md
D	doc/img/messaging-bus-architecture.svg
D	doc/img/messaging-bus-classes.svg
D	doc/img/messaging-bus-params.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
D	doc/v1.2.x.Planning.md
R077	doc/v1.2.x.Goal.md	doc/v2.0.x.Goals.md
A	doc/v2.0.x.Planning.md
M	enyMan/Dockerfile.lx
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/main/resources/logback-spring.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateWard/pom.xml
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateWard/src/main/resources/logback-spring.xml
M	gateway/Dockerfile.lx
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactory.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	hauberk/README.md
A	hauberk/changes.txt
A	hauberk/hauberk-M.properties
A	hauberk/hauberk-S.properties
A	hauberk/hauberk-compact.properties
A	hauberk/hauberk-k8s-compact.properties
A	hauberk/hauberk-k8s.properties
A	hauberk/hauberk-oke.properties
A	hauberk/hauberk.cmd
A	hauberk/hauberk.properties
A	hauberk/k8s-smoke.bat
A	hauberk/oke-perf-matrix.bat
A	hauberk/oke-perf-matrix.ps1
A	hauberk/perf-matrix-report.py
A	hauberk/perf-matrix.bat
A	hauberk/perf-matrix.ps1
A	hauberk/perf-pairs.ps1
A	hauberk/pom.xml
A	hauberk/smoke-5.bat
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupKcOrphans.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupOfficeByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CloseAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CompareTrees.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ConnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateSubOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DeleteEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Deposit.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DisconnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/EnsureOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ForceSweep.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/KcAdminAuth.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LoginViaBff.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupAccessProfile.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupOfficeIdByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/MoveEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PickRandomFromPool.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAcctDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetails.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Transfer.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/UpdateUserAddress.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitBizTreeDown.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitCacheReady.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Withdrawal.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqHauberkCli.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqRunCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EntityKinds.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EsqHauberkConfig.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/AEsqSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CleanHouseSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CompareTreesSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CreateLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/EntitySmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/HaTimeoutSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcIntegrationSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/LoadScenarios.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossTerminateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/PrepareForAnythingSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceCacheLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSingleShotSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ReadLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ResidueCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SuperLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/TxLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/UpdateLoadSimulation.java
A	hauberk/src/test/java/pro/mir0n/esquire/hauberk/simulations/SimulationCatalogContractTest.java
M	k8s-compact/charts/esquire-aukeep/Chart.yaml
M	k8s-compact/charts/esquire-gateward/Chart.yaml
M	k8s-compact/charts/esquire-mesnie/Chart.yaml
M	k8s-compact/charts/esquire-pacman/Chart.yaml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/oke-o11y-test.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci/README.md
M	k8s-oci/grafana/esquire-logging.json
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-o11y-test.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/Chart.yaml
M	k8s/charts/esquire-biztree/Chart.yaml
M	k8s/charts/esquire-enyman/Chart.yaml
M	k8s/charts/esquire-gateway/Chart.yaml
M	k8s/charts/esquire-kcmaster/Chart.yaml
M	k8s/charts/esquire-keysmith/Chart.yaml
M	k8s/charts/esquire-pacman/Chart.yaml
M	k8s/k8s-rebuild.bat
M	k8s/o11y-test.bat
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
M	kcMaster/Dockerfile.lx
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/logback-spring.xml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGatewayOutcomeTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/Dockerfile.lx
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/themes/README-CUSTOMIZATION.md
M	keycloak/themes/README.md
M	keycloak/themes/esquire-explorer/login/resources/css/styles.css
M	mesnie/pom.xml
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/main/resources/logback-spring.xml
D	messaging/pom.xml
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IRodEventRepo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEventRepoRegistry.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/TransportHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusNode.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusRef.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusSlot.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusTransport.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalog.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/OwnExcluding.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/SelectingReceiver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportHealth.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/ISessionSublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInProcess.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SessionSublayerFactory.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/EncodeFailingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/TransportHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/TopologyDriftGuardTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/OwnExcludingTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/SelectingReceiverTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportHealthTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSendFailureTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
D	mir0n-utils/pom.xml
D	mir0n-utils/src/main/java/pro/mir0n/utils/HostId.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/HostIdTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYOutcomeTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
D	mir0n-utils/src/test/resources/logback-test.xml
M	pacMan/Dockerfile.lx
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/main/resources/logback-spring.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	test/freshness-guard/run.sh
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
M	test/o11y/o11y-aspects.py
M	test/o11y/o11y-inventory.py
D	tp-activemq/pom.xml
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TpActiveMqAutoConfigFilter.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
D	tp-activemq/src/main/resources/META-INF/spring.factories
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/CredentialParamsIntegrationTest.java
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
D	tp-kafka/pom.xml
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TpKafkaAutoConfigFilter.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
D	tp-kafka/src/main/resources/META-INF/spring.factories
D	tp-kafka/src/test/java/pro/mir0n/esquire/tp/kafka/TransportProviderTest.java
D	tp-kinesis/doc/tp-kinesis.md
D	tp-kinesis/pom.xml
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/KinesisConsumer.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
D	tp-kinesis/src/test/java/pro/mir0n/esquire/tp/kinesis/StreamModeParamsTest.java
D	tp-redis/pom.xml
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TpRedisAutoConfigFilter.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
D	tp-redis/src/main/resources/META-INF/spring.factories
D	tp-redis/src/test/java/pro/mir0n/esquire/tp/redis/TransportProviderTest.java
D	tp-sqns/doc/tp-sqns.md
D	tp-sqns/pom.xml
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sns/TransportProvider.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SnsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsConsumer.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqs/TransportProvider.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamGroupTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamsTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportTest.java
 506 files changed, 9831 insertions(+), 23451 deletions(-)
```

---

## Files Modified

```
M	.github/scripts/deploy-compose.cmd
M	.github/scripts/deploy-local.cmd
M	.github/scripts/oke-build-push.sh
M	.github/workflows/ci.yml
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	auKeep/pom.xml
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/pom.xml
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditBusBridge.java
M	audit/src/main/java/pro/mir0n/esquire/audit/AuditKeepDirector.java
M	audit/src/main/java/pro/mir0n/esquire/audit/changes.txt
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditSqlTest.java
M	bizTree/Dockerfile.lx
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/BizTreeDirectorConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/taijitu/BizTreeDirectorTaijitu.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/BizTreeConstantsTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	build.all.bat
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
D	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesService.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityFieldTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityLayerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfileTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/InvalidValueExceptionTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
D	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/NoRawGaugeBuilderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EntityFieldUtilsTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/storage/EsqRolesStorageTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/GenericValidatorTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	compose-compact/compose.yaml
M	compose-compact/o11y-test.bat
M	compose/compose.yaml
M	compose/o11y-test.bat
D	dataKeep/pom.xml
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/director/IKeepDirector.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepDataSourceParams.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepSqlStore.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
D	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/XRodInProcessKeep.java
D	dataKeep/src/main/resources/spring.properties
D	dataKeep/src/test/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriterTest.java
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
D	doc/Esquire.MessagingBus.ContinuingDev.md
D	doc/Esquire.MessagingBus.Guides.md
D	doc/Esquire.MessagingBus.MessageStructure.md
D	doc/Esquire.MessagingBus.Q&A.md
D	doc/Esquire.MessagingBus.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.Scrum.Process.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
D	doc/img/messaging-bus-architecture.svg
D	doc/img/messaging-bus-classes.svg
D	doc/img/messaging-bus-params.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
D	doc/v1.2.x.Planning.md
R077	doc/v1.2.x.Goal.md	doc/v2.0.x.Goals.md
A	doc/v2.0.x.Planning.md
M	enyMan/Dockerfile.lx
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/audit/AuditConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/main/resources/logback-spring.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateWard/pom.xml
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateWard/src/main/resources/logback-spring.xml
M	gateway/Dockerfile.lx
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactory.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	hauberk/README.md
A	hauberk/changes.txt
A	hauberk/hauberk-M.properties
A	hauberk/hauberk-S.properties
A	hauberk/hauberk-compact.properties
A	hauberk/hauberk-k8s-compact.properties
A	hauberk/hauberk-k8s.properties
A	hauberk/hauberk-oke.properties
A	hauberk/hauberk.cmd
A	hauberk/hauberk.properties
A	hauberk/k8s-smoke.bat
A	hauberk/oke-perf-matrix.bat
A	hauberk/oke-perf-matrix.ps1
A	hauberk/perf-matrix-report.py
A	hauberk/perf-matrix.bat
A	hauberk/perf-matrix.ps1
A	hauberk/perf-pairs.ps1
A	hauberk/pom.xml
A	hauberk/smoke-5.bat
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupKcOrphans.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CleanupOfficeByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CloseAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CompareTrees.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ConnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateAccount.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateSubOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/CreateUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DeleteEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Deposit.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/DisconnectUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/EnsureOffice.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ForceSweep.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/KcAdminAuth.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LoginViaBff.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupAccessProfile.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/LookupOfficeIdByName.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/MoveEntity.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PickRandomFromPool.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/PoolFetchUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAccounts.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadAcctDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadCacheUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeRoot.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeUser.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetail.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUserDetails.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadUsers.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Transfer.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/UpdateUserAddress.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitBizTreeDown.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/WaitCacheReady.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/Withdrawal.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqHauberkCli.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/EsqRunCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EntityKinds.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EsqHauberkConfig.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/AEsqSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CleanHouseSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CompareTreesSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/CreateLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/EntitySmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/HaTimeoutSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcIntegrationSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/LoadScenarios.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MessageLossTerminateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/MoveSmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/PrepareForAnythingSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceCacheLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSingleShotSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ReadLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/ResidueCleanupSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SmokeSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/SuperLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/TxLoadSimulation.java
A	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/UpdateLoadSimulation.java
A	hauberk/src/test/java/pro/mir0n/esquire/hauberk/simulations/SimulationCatalogContractTest.java
M	k8s-compact/charts/esquire-aukeep/Chart.yaml
M	k8s-compact/charts/esquire-gateward/Chart.yaml
M	k8s-compact/charts/esquire-mesnie/Chart.yaml
M	k8s-compact/charts/esquire-pacman/Chart.yaml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/ghcr-push.bat
M	k8s-oci-compact/oke-o11y-test.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci/README.md
M	k8s-oci/ghcr-push.bat
M	k8s-oci/grafana/esquire-logging.json
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-o11y-test.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/publish.bat
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/Chart.yaml
M	k8s/charts/esquire-biztree/Chart.yaml
M	k8s/charts/esquire-enyman/Chart.yaml
M	k8s/charts/esquire-gateway/Chart.yaml
M	k8s/charts/esquire-kcmaster/Chart.yaml
M	k8s/charts/esquire-keysmith/Chart.yaml
M	k8s/charts/esquire-pacman/Chart.yaml
M	k8s/k8s-rebuild.bat
M	k8s/o11y-test.bat
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
M	kcMaster/Dockerfile.lx
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/logback-spring.xml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGatewayOutcomeTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/Dockerfile.lx
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/audit/AuditConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/themes/README-CUSTOMIZATION.md
M	keycloak/themes/README.md
M	keycloak/themes/esquire-explorer/login/resources/css/styles.css
M	make-javadoc.bat
M	mesnie/pom.xml
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/main/resources/logback-spring.xml
D	messaging/pom.xml
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IRodEventRepo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEventRepoRegistry.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/TransportHealthIndicator.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusNode.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusRef.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusSlot.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/BusTransport.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBus.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalog.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/Role.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/BusIdentity.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ConsumeSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/ITransportProvider.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/OwnExcluding.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/PublishSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/SelectingReceiver.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportConsumer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportHealth.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportProviders.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportSettings.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodPublisher.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/ISessionSublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/MsgAudit.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabled.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInProcess.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfo.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoParams.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SessionSublayerFactory.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BrokerDownTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/BusRefBindTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/CapturingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/EncodeFailingTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/FakeTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/MessagingBusTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/ProducerOnlyTransportProvider.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/TransportHealthIndicatorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/XRodParamsTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/MessagingBusCatalogTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/TopologyDriftGuardTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/BusIdentityTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/OwnExcludingTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/SelectingReceiverTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportHealthTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/TransportProvidersTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventRepoRegistryTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapterTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodBrokerDownTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodRoleSupportTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSendFailureTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodValidateTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodDisabledTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/XRodInfoTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayerTest.java
D	mir0n-utils/pom.xml
D	mir0n-utils/src/main/java/pro/mir0n/utils/HostId.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
D	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/HostIdTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYOutcomeTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
D	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
D	mir0n-utils/src/test/resources/logback-test.xml
M	pacMan/Dockerfile.lx
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/audit/AuditConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EntityBusAdapter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/main/resources/logback-spring.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	test/freshness-guard/run.sh
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
M	test/o11y/o11y-aspects.py
M	test/o11y/o11y-inventory.py
D	tp-activemq/pom.xml
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/changes.txt
D	tp-activemq/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TpActiveMqAutoConfigFilter.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
D	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
D	tp-activemq/src/main/resources/META-INF/spring.factories
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/CredentialParamsIntegrationTest.java
D	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
D	tp-kafka/pom.xml
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TpKafkaAutoConfigFilter.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
D	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
D	tp-kafka/src/main/resources/META-INF/spring.factories
D	tp-kafka/src/test/java/pro/mir0n/esquire/tp/kafka/TransportProviderTest.java
D	tp-kinesis/doc/tp-kinesis.md
D	tp-kinesis/pom.xml
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/KinesisConsumer.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
D	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
D	tp-kinesis/src/test/java/pro/mir0n/esquire/tp/kinesis/StreamModeParamsTest.java
D	tp-redis/pom.xml
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TpRedisAutoConfigFilter.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
D	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
D	tp-redis/src/main/resources/META-INF/spring.factories
D	tp-redis/src/test/java/pro/mir0n/esquire/tp/redis/TransportProviderTest.java
D	tp-sqns/doc/tp-sqns.md
D	tp-sqns/pom.xml
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sns/TransportProvider.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SnsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsConsumer.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsSupport.java
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt
D	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqs/TransportProvider.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamGroupTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamsTest.java
D	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportTest.java
 517 files changed, 10226 insertions(+), 23666 deletions(-)
```

---

*From `v1.2.16` till `HEAD`*

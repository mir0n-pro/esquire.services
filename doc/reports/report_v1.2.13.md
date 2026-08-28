# Release Report: v1.2.12 → v1.2.13

**Repo:** `esquire.services/develop`  
**Top commit:** `aadbe27`

---

## Release Notes

### doc/release_notes.txt


**v1.2.13-2608.2722**  v1.2.13 -- Finalization  
&nbsp;: Doc:         doc\Esquire.TestingStack.md  
&nbsp;                 doc\Esquire.ContinuingDev.md  
&nbsp;                 doc\Esquire.DevProcess.md  
&nbsp;                 doc\v1.2.x.Goal  
&nbsp;                 doc\v1.2.x.Planning  

**v1.2.13-2608.2718**  v1.2.13 -- Refresh the cloud: the sprint carried to OKE  
&nbsp;: Doc:         doc\Esquire.GitHubActions.md  
&nbsp;   Components:  k8s-oci-compact,  

**v1.2.13-2608.2620**  v1.2.13 -- Hardening: the solution read back, and the issues it raised  
&nbsp;- the tree cache is fed in the order the events were published  
&nbsp;- a move tells the cache both of the numbers it changed, and every save of an entity is announced  
&nbsp;- a path is resolved only inside the caller own part of the tree  
&nbsp;- asking for a password change, or for two-factor, can be taken back again  
&nbsp;- a deletion the identity server refuses is no longer reported as done  
&nbsp;- a message that cannot be encoded leaves a record instead of vanishing  
&nbsp;- a queue tells its owner about both outcomes, what it handled and what it failed  
&nbsp;- a retry ladder that cannot be read refuses the connection instead of inventing one  
&nbsp;- a connection reports that it is unproven until something proves it, and that state now reaches the boards  
&nbsp;- request timing is measured only when monitoring is switched on  
&nbsp;- a refused command answers as refused, not as accepted  
&nbsp;- an event the system does not recognise is no longer treated as a deletion  
&nbsp;- money moves only when both accounts are there, and only for the kind of account named  
&nbsp;- the deployment scripts report the failures they used to pass over  
&nbsp;- an image records which upstream it was built from  
&nbsp;: Doc:         doc\Esquire.BizTree.md  
&nbsp;                 doc\Esquire.Messaging.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.Guides.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Esquire.ContinuingDev.md  
&nbsp;                 doc\Esquire.Auth.md  
&nbsp;                 doc\Esquire.Auth.keySmithRoutine.md  
&nbsp;                 doc\Esquire.Auth.TokenPatterns.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\Esquire.ObservabilityStack.md  
&nbsp;                 doc\Esquire.ObservabilityStack.Logging.md  
&nbsp;                 doc\Esquire.ObservabilityStack.Inventory.csv  
&nbsp;                 doc\Esquire.ObservabilityStack.Inventory.Compact.csv  
&nbsp;                 doc\Esquire.ObservabilityStack.Inventory.SuperCompact.csv  
&nbsp;                 doc\Esquire.GrafanaGuide.md  
&nbsp;                 doc\Esquire.GitHubActions.md  
&nbsp;                 doc\Esquire.DevProcess.md  
&nbsp;                 doc\Esquire.DevSetup.md  
&nbsp;                 doc\EntityDictionary.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 mir0n-utils,  
&nbsp;                 tp-activemq,  
&nbsp;                 tp-kafka,  
&nbsp;                 tp-redis,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 kcMaster,  
&nbsp;                 keySmith,  
&nbsp;                 auKeep,  
&nbsp;                 dataKeep,  
&nbsp;                 gateway,  
&nbsp;                 gateWard,  
&nbsp;                 mesnie,  
&nbsp;                 compose,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s-oci-compact,  
&nbsp;                 keycloak,  
&nbsp;                 postgres,  

**v1.2.13-2608.2119**  v1.2.13 -- KeyCloak 26.6.0, and the sprint documentation  
&nbsp;: Config:      the identity server is built on KeyCloak 26.6.0 for the docker stacks  
&nbsp;: Config:      the load-test client that must not read its own token is handed the stripped one again  
&nbsp;: Doc:         doc\Esquire.Vision.md  
&nbsp;                 doc\Esquire.Auth.TokenPatterns.md  
&nbsp;                 doc\Esquire.TestingStack.md  
&nbsp;                 doc\install\Docker.md  
&nbsp;                 doc\install\LocalK8s.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\Esquire.DevSetup.md  
&nbsp;                 doc\Esquire.DevProcess.md  
&nbsp;                 doc\Esquire.GitHubActions.md  
&nbsp;                 doc\Esquire.GrafanaGuide.md  
&nbsp;                 doc\Esquire.HighAvailability.md  
&nbsp;                 doc\Esquire.BizTree.md  
&nbsp;                 doc\Esquire.Auth.md  
&nbsp;                 doc\Esquire.Auth.keySmithRoutine.md  
&nbsp;                 doc\Esquire.Messaging.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\Esquire.ObservabilityStack.md  
&nbsp;                 doc\model\ComponentModel.vsdx  
&nbsp;   Components:   compose,  
&nbsp;                 compose-compact,  
&nbsp;                 keycloak,  
&nbsp;                 doc  

**v1.2.13-2608.2017**  v1.2.13 -- the composed services on the cloud  
&nbsp;: Feature:     the cloud runs the composed setup: four programs instead of eight, with the change history  
&nbsp;                 written by the database itself  
&nbsp;: Feature:     the automated cloud deploy ships that setup -- seven images instead of ten  
&nbsp;: Doc:         the component model drawing is updated  
&nbsp;   Components:   .github (CI/CD),  
&nbsp;                 activemq,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s-oci-compact,  
&nbsp;                 o11y  

**v1.2.13-2608.1823**  v1.2.13 -- observability for the composed services  
&nbsp;: Feature:     keySmith reports its own work -- its reads and saves by outcome, and what it asks the  
&nbsp;                 identity provider to do  
&nbsp;: Feature:     the compact stacks have their own monitoring boards and their own picture of what runs, with  
&nbsp;                 a composed service drawn as the services it holds  
&nbsp;: Feature:     Mesnie and gateWard have their own drawn marks, and the marks of the gate, the tree cache,  
&nbsp;                 the audit writer, the entity manager and the message broker are re-drawn  
&nbsp;: Config:      the audit writer is deployed on the compact stacks again, and the audit trail travels the  
&nbsp;                 message bus there as it does on the classic ones  
&nbsp;: Doc:         doc\Esquire.ObservabilityStack.md  
&nbsp;                 doc\Esquire.ObservabilityStack.Inventory.csv  
&nbsp;                 doc\Esquire.ObservabilityStack.Inventory.Compact.csv  
&nbsp;   Components:   common,  
&nbsp;                 keySmith,  
&nbsp;                 mesnie,  
&nbsp;                 gateWard,  
&nbsp;                 compose,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact  

**v1.2.13-2608.1521**  v1.2.13 -- gateWard  
&nbsp;: Feature:     gateWard: one service that runs the gate and the tree cache together in a single process,  
&nbsp;                 in place of the two separate services  
&nbsp;: Feature:     a request for the tree is answered inside the gate from its own copy, with no call out to  
&nbsp;                 another service  
&nbsp;: Fix:         asking for something that is not there is answered "not found" instead of "bad request",  
&nbsp;                 in every service  
&nbsp;: Fix:         the gate reports itself unavailable while the message broker is down, so it stops being  
&nbsp;                 sent work  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 bizTree,  
&nbsp;                 gateway,  
&nbsp;                 gateWard,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s-compact  

**v1.2.13-2608.1321**  v1.2.13 -- Mesnie CI/CD  
&nbsp;: Config:      the automatic local build-and-deploy now deploys whichever of the two stacks the machine is  
&nbsp;                 already running, and shuts the other one down before it starts. The two answer on the same  
&nbsp;                 addresses, so with the other one still up the deploy stopped part way and nothing said why  
&nbsp;: Fix:         taking the compact stack down on kubernetes now removes Mesnie as well, so the two stacks  
&nbsp;                 cannot end up running side by side  
&nbsp;   Components:   .github (CI/CD),  
&nbsp;                 k8s-compact  

**v1.2.13-2608.1316**  v1.2.13 -- Mesnie  
&nbsp;: Feature:     Mesnie: one service that runs the entity, sign-in and identity work together in a single  
&nbsp;                 process, in place of the three separate services  
&nbsp;: Feature:     a service asks for identity work through one way in, and what answers it -- another service  
&nbsp;                 over the message bus, or the same process -- is decided where the service is wired  
&nbsp;: Feature:     a compact stack that runs Mesnie, the gate, the tree cache, the money service and the  
&nbsp;                 browser tier, with its own docker and local-kubernetes folders kept apart from the classic  
&nbsp;                 ones  
&nbsp;: Feature:     a moved entity's new position is announced to every copy of a process, so whichever copy  
&nbsp;                 later creates that sign-in identity still gives it the position it ended up at  
&nbsp;: Refactoring: the identity work has one home: the message-bus ends of kcMaster now only carry messages in  
&nbsp;                 and answers out  
&nbsp;: Refactoring: the sign-in request that travels to the identity provider is one shared class, filled by the  
&nbsp;                 caller and read by the provider  
&nbsp;: Config:      the audit trail on the compact stack is written by the service itself, so no separate audit  
&nbsp;                 writer is deployed  
&nbsp;: Config:      the compact stack's deploy scripts build and run it from its own folder alone -- what each  
&nbsp;                 image is built from, what is watched, and which services are started are all named there,  
&nbsp;                 so building or starting either stack never reaches into the other  
&nbsp;: Doc:         doc\Esquire.Auth.md  
&nbsp;                 doc\Esquire.ContinuingDev.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 mesnie,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s-compact  

---

## Code Changes

### auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt


**08/26/2026** mir0n  v1.2.13 -- a disabled audit bus that names itself  
**messaging.AuditConsumerConfig**  
&nbsp;- a disabled audit bus names the property that disabled it (AUDIT_BUS_ID) in the refusal  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the path lookup scoped, and the two change numbers  
**access.MessageHandlerHub**  
&nbsp;- dispatch reads pathChangeNo from the body: an X is guarded on the PATH number and stamps BOTH columns,  
&nbsp;   every other event on the entity number; the stamp follows the handler, and the dispatch outcome starts  
&nbsp;   at error so only a completed apply counts as handled  
**cache.BizTreeCacheSql**  
&nbsp;- findPathScoped added to the repo SQL record  
**cache.CacheSqlSet**  
&nbsp;- forSet carries findPathScoped through the per-table substitution  
**cache.IBizTreeCacheRepository**  
&nbsp;- findPathScoped(id, rootPath) added -- the path lookup bounded by the caller root path  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- findPathScoped implemented: the path is returned only when the row sits under rootPath  
**controller.BizTreeController**  
&nbsp;- the develop log lines pass the value itself instead of String.valueOf, so nothing is formatted when the  
&nbsp;   level is off  
**h2.BizTreeH2Config**  
&nbsp;- reads biztree.cache.sql.repo.find-path-scoped into the SQL set  
**service.impl.BizTreeService**  
&nbsp;- esquirePath resolves through findPathScoped(id, rootPath) -- a path outside the caller root path no  
&nbsp;   longer answers  
**resources/META-INF/h2-cache-sql.properties**  
&nbsp;- find-path-scoped added: the path of a row read only when its entity path is under the given prefix  
**resources/application.yml**  
&nbsp;- the entity-broadcast receiver pool defaults to 1  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

**08/15/2026** mir0n  v1.2.13 -- gateWard  
BizTreeCacheConfig  (new)  
&nbsp;- created: the CACHE half of bizTree as a set of beans -- the one place its packages are named. Excludes  
&nbsp;   BizTreeApplication; leaves the web layer (bizTree.controller) and the servlet security (backend.security)  
&nbsp;   to the standalone process  
BizTreeApplication  
&nbsp;- @SpringBootApplication(scanBasePackages) split into @SpringBootConfiguration + @EnableAutoConfiguration +  
&nbsp;   @Import(BizTreeCacheConfig) + a @ComponentScan naming only the PROCESS packages (bizTree.controller,  
&nbsp;   backend.security, backend.exception, backend.service); @EntityScan / @EnableJpaRepositories moved to  
&nbsp;   BizTreeCacheConfig  
**pom.xml**  
&nbsp;- maven-jar-plugin classes-jar execution attaches a plain classes jar (classifier "classes"), excluding  
&nbsp;   application.yml and logback-spring.xml  
Dockerfile  
&nbsp;- COPY target/esquire-biz-tree.jar app.jar  
**Dockerfile.win**  
&nbsp;- COPY target/esquire-biz-tree.jar app.jar  
**Dockerfile.lx**  
&nbsp;- COPY --from=builder /build/bizTree/target/esquire-biz-tree.jar app.jar  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**08/26/2026** mir0n  v1.2.13 -- a refusal that says so, and metrics that ask first  
**error.GenericExceptionHandler**  
&nbsp;- CommandNotAcceptedException answers 503 Service Unavailable  
**o11y.EsqRodObserver**  
&nbsp;- registerTransportUp registers messaging.transport.up as an EsqGauge, tagged bus-id  
**security.SecurityConfiguration**  
&nbsp;- an authentication entry point answers a ProblemDetail JSON instead of an empty 401; the filter is  
&nbsp;   JwtClaimsExtractionFilter, and /esq-kinds joins the permitAll list  
**service.MdcFilter**  
&nbsp;- the meter registry is taken only when esquire.observability.metrics.enabled is true, so a registry  
&nbsp;   present for other reasons no longer turns request metering on  
**service.PerformanceAspect**  
&nbsp;- observabilityOn requires esquire.observability.metrics.enabled as well as a registry  

**08/17/2026** mir0n  v1.2.13 -- observability for the composed services  
o11y.IMeterOwner  (new)  
&nbsp;- created: a composed service names the Esquire service behind each meter, so enyMan, keySmith and kcMaster  
&nbsp;   keep their metric identity inside one process  
o11y.EsqServiceTagFilter  (new)  
&nbsp;- created: stamps service= beside application=, from the IMeterOwner the running  
&nbsp;   service contributes; no owner means service==application  
**o11y.ObservabilityConfig**  
&nbsp;- esqServiceTag() @Bean added (metrics.enabled): an EsqServiceTagFilter stamping service=  
&nbsp;   beside application=, taking the running service's IMeterOwner through ObjectProvider. No owner ->  
&nbsp;   service == application, which is what every classic service reports. The owner is asked with the meter ID  
&nbsp;   and nothing else: a MeterFilter runs at REGISTRATION, so a per-request value would freeze whichever service  
&nbsp;   touched the meter first  

**08/15/2026** mir0n  v1.2.13 -- gateWard  
**error.GenericExceptionHandler**  
&nbsp;- ResourceNotFoundException gets its own branch: 404 NOT_FOUND, title "Not Found" (was the 400 BAD_REQUEST  
&nbsp;   branch it shared with InvalidValueException)  

**08/12/2026** mir0n  v1.2.13 -- Mesnie  
identity.IIdentityGateway  (new)  
&nbsp;- created: the way into an identity provider -- start / stop, postRequest(RodEvent), postMessage(RodEvent)  
&nbsp;   for PATH broadcasts and setResultHandler(Consumer); the implementation is named in each  
&nbsp;   process's wiring  
identity.AuthSyncRequest  (new)  
&nbsp;- created: moved from kcMaster.messaging.KcSyncRequest and renamed. The body of an identity command  
&nbsp;   (id, kind, loginId, newLoginId, email, pwdChangeForced, tfaMethod, connectFlg, path, roles) plus toMap(),  
&nbsp;   which writes the RodEvent body in declaration order and omits what was not set  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**08/27/2026** mir0n  v1.2.13 -- one writer for the error body  
**error.ProblemDetailWriter**  
&nbsp;- added: the one place a ProblemDetail reaches a servlet response -- a mapper carrying JavaTimeModule  
&nbsp;   and ISO dates, and the body serialized to bytes BEFORE the status, the content type and the write, so  
&nbsp;   a value with no serializer can no longer leave a half-body on a response already committed  
**security.SecurityConfiguration**  
&nbsp;- unauthenticated() hands the problem to ProblemDetailWriter; the class keeps no ObjectMapper  
**security.JwtClaimsExtractionFilter**  
&nbsp;- sendErrorResponse() hands the problem to ProblemDetailWriter; the per-call ObjectMapper is gone  

**08/26/2026** mir0n  v1.2.13 -- the path change number on the wire  
EsqConstants  
&nbsp;- TEXT_PATH_CHANGE_NO added -- the path change number carried in a broadcast body  

### dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt


**08/26/2026** mir0n  v1.2.13 -- an event the keep cannot record  
**keep.RodEventDbWriter**  
&nbsp;- an op the action mapping does not cover is ignored with a named warning and counted outcome=ignored,  
&nbsp;   instead of falling through to a delete; action() answers null for it  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the move carries both change numbers, and every save broadcasts  
**controller.EnyManController**  
&nbsp;- the develop log lines pass the value itself instead of String.valueOf; the move endpoint documents 503  
&nbsp;   as a response code  
**jpa.EntityPathLookup**  
&nbsp;- entityChangeNoFor(id) added -- the entity change number for the header of a reissued path broadcast  
**jpa.EsqMoveRecord**  
&nbsp;- changeNo split in two: pathChangeNo (EP_CHANGE_NO) and entityChangeNo, read back from the same row  
&nbsp;   after the move raised them  
**queue.MoveQueueManager**  
&nbsp;- implements ISuccessListener and IErrorListener and registers both on the rig, so a move outcome is  
&nbsp;   counted on either side; submitMove answers false when the queue refuses the item  
&nbsp;- the move broadcast carries pathChangeNo in the body and the entity number in the header, and the  
&nbsp;   reconcile reissue reads both back  
**service.impl.EnyManService**  
&nbsp;- every esquireCommandSave publishes an UPDATE -- isBroadcastableUpdate is gone, so the field list that  
&nbsp;   raises the change number and the one that broadcasts can no longer differ  
&nbsp;- a refused move raises CommandNotAcceptedException  
**service.impl.OrgService**  
&nbsp;- saveOrg / deleteOrg / moveOrg take the kind; moveOrg reads the moved paths AFTER the parent write, so  
&nbsp;   the broadcast carries the raised entity number rather than the previous one  
**service.impl.UsrService**  
&nbsp;- saveUsr / deleteUsr / moveUsr take the kind; moveUsr reads the moved paths AFTER the parent write  
&nbsp;- a person-driven rename puts the derived name into the broadcast fields  
**resources/META-INF/postgres-entity.xml**  
&nbsp;- the three move queries return ep_change_no and the entity change number together, joining the row  
&nbsp;   to whichever of esq_org / esq_user / esq_account holds it  
**resources/META-INF/oracle-entity.xml**  
&nbsp;- the three move queries return ep_change_no and the entity change number together, joining the row  
&nbsp;   to whichever of esq_org / esq_user / esq_account holds it  
**resources/META-INF/postgres-acct.xml**  
&nbsp;- EsqAcctJpa.entityChangeNoFor added -- the entity change number for one id, from whichever table holds it  
**resources/META-INF/oracle-acct.xml**  
&nbsp;- EsqAcctJpa.entityChangeNoFor added -- the entity change number for one id, from whichever table holds it  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

**08/12/2026** mir0n  v1.2.13 -- Mesnie  
EnyManApplication  
&nbsp;- the package list moved to EnyManConfig (@Import); @Bean IIdentityGateway identityGateway() declared here  
&nbsp;   with initMethod start / destroyMethod stop -- one gateway per PROCESS  
EnyManConfig  (new)  
&nbsp;- created: enyMan's @ComponentScan / @EntityScan / @EnableJpaRepositories, the one place its packages are  
&nbsp;   named; excludes EnyManApplication and AuditConfig from the scan  
**messaging.EntityBusAdapter**  
&nbsp;- IIdentityGateway injected; publish() and the receive worker hand a PATH broadcast to postMessage, which is  
&nbsp;   all that arm takes; one worker over subscription EventType IN ('C','X')  
**messaging.KcBusAdapter**  
&nbsp;- implements IIdentityGateway: postRequest(RodEvent) transmits, postMessage is skipped, start()/stop() take  
&nbsp;   the kc leg (was the constructor)  
**queue.MoveQueueManager**  
&nbsp;- field/ctor param KcBusAdapter -> IIdentityGateway; publishKcMoveRequest builds an AuthSyncRequest +  
&nbsp;   RodEvent and calls postRequest, carrying the PATH change number  
**pom.xml**  
&nbsp;- maven-jar-plugin execution attaches a plain classes jar (classifier "classes"), excluding application.yml  
&nbsp;   and logback-spring.xml  
Dockerfile, Dockerfile.win, Dockerfile.lx  
&nbsp;- COPY names esquire-eny-man.jar instead of target/*.jar  

### gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt

Esquire gateWard Microservice  

**08/26/2026** mir0n  v1.2.13 -- the tree reads traced, and metrics that ask first  
BizTreeCacheController  
&nbsp;- each tree read is wrapped in its own EsqTraceMark span -- read tree, read subtree, read path  
TreeRouteTimingFilter  
&nbsp;- the meter registry is taken only when the metrics switch is on; the trace ids reach MDC  
**resources/application.yml**  
&nbsp;- the keycloak.exchange block (base-url, realm, client-id, client-secret) and the entity-broadcast  
&nbsp;   receiver pool defaulting to 1  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

**08/17/2026** mir0n  v1.2.13 -- observability for the composed services  
GateWardMeterOwner  (new)  
&nbsp;- created: whether a meter is the gate's or the tree cache's, read off the meter id: its name, the route it  
&nbsp;   was served on, or the bus it travelled. PROCESS_OWNED is checked FIRST and stops the lookup -- reactor-netty  
&nbsp;   tags the edge server with a coarse uri that matches a cache route, which would credit the whole edge to  
&nbsp;   bizTree. http.client.requests is matched by name for the same reason: the gate's outbound leg carries a  
&nbsp;   downstream uri  
GateWardApplication  
&nbsp;- @Bean IMeterOwner meterOwner(entityBusId) declared here: a GateWardMeterOwner, handed the entity bus id  
&nbsp;   from the property the bus itself reads  

**08/15/2026** mir0n  v1.2.13 -- gateWard  
GateWardApplication  (new)  
&nbsp;- created: the gateway and the bizTree CACHE in ONE process. Imports GatewayConfig and BizTreeCacheConfig,  
&nbsp;   scans gateWard's own handlers and read scheduler, loads the object kinds at start-up, and runs one  
&nbsp;   MessagingBusLifecycleRegistrar over the entity bus. No web starter comes in with the cache, so the  
&nbsp;   process stays WebFlux  
BizTreeCacheController  (new)  
&nbsp;- created: the five tree routes answered IN PROCESS from the cache instead of proxied to bizTree -- the  
&nbsp;   same director calls the bizTree controller makes, each handed to the cache-read scheduler so no blocking  
&nbsp;   JDBC lands on an event-loop thread, and each carrying the caller's rootPath / uid across that thread hop  
CacheReadScheduler  (new)  
&nbsp;- created: the one scheduler the cache reads run on, a bounded elastic named gateward-cache-read, sized  
&nbsp;   from gateward.cache-read.pool-size against biztree.h2.pool.maximum-pool-size  
TreeRouteTimingFilter  (new)  
&nbsp;- created: a WebFilter recording esq.gw.outer / esq.gw.inner / esq.srv.outer / esq.srv.inner (route tag  
&nbsp;   biztree-local) for a locally answered tree route, writing the four capture headers, echoing X-Request-ID  
&nbsp;   and X-Correlation-ID and logging the OUTGOING line. gateInner / wardOuter take the two stamps from  
&nbsp;   BizTreeCacheController; all four bands are recorded together or not at all  
BizTreeCacheErrorAdvice  (new)  
&nbsp;- created: @RestControllerAdvice(assignableTypes = BizTreeCacheController) mapping GenericRuntimeException  
&nbsp;   to the statuses backend.error.GenericExceptionHandler gives it -- 403 / 409 / 404 / 400 -- rendered  
&nbsp;   through the gateway's reactive ProblemDetailMill and logged on both tiers  
application.yml  (new)  
&nbsp;- created: the union of the gateway's and bizTree's settings under spring.application.name gateward --  
&nbsp;   twelve proxy routes with no biztree-route, the reactive security chain and token relay, DB_BIZTREE_*  
&nbsp;   datasource, the H2 cache and taijitu knobs, the entity bus ref, and a readiness group of  
&nbsp;   readinessState, cacheReadiness and messagingBus  
logback-spring.xml  (new)  
&nbsp;- created: the develop and msg channels writing logs/gateWard-develop.log and logs/gateWard-msg.log  
pom.xml  (new)  
&nbsp;- created: depends on the gateway and bizTree plain classes jars (classifier "classes"), excluding  
&nbsp;   spring-boot-starter-web and springdoc-openapi-starter-webmvc-ui from bizTree; spring-boot-maven-plugin  
&nbsp;   builds esquire-gate-ward.jar  
Dockerfile  (new)  
&nbsp;- created: COPY target/esquire-gate-ward.jar app.jar  
compose.yaml  (new)  
&nbsp;- created: builds the esquire.gateward image from this module  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the decoder validators, and metrics that ask first  
**config.SecurityConfig**  
&nbsp;- the JWT decoder carries the default validators through DelegatingOAuth2TokenValidator, and the  
&nbsp;   JWE-aware decoder and token-relay wiring leave this class  
**filters.InnerTimerFilter**  
&nbsp;- runs at @Order(1), and the meter registry is taken only when the metrics switch is on  
**filters.ResponseTraceFilter**  
&nbsp;- the meter registry is taken only when the metrics switch is on; the trace ids reach MDC  
**resources/application.yml**  
&nbsp;- the keycloak.exchange block: base-url, realm, client-id, client-secret  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

**08/15/2026** mir0n  v1.2.13 -- gateWard  
GatewayConfig  (new)  
&nbsp;- created: the gateway's @ComponentScan and @ConfigurationPropertiesScan, the one place its packages are  
&nbsp;   named; excludes GatewayApplication  
GatewayApplication  
&nbsp;- @SpringBootApplication + @ConfigurationPropertiesScan split into @SpringBootConfiguration +  
&nbsp;   @EnableAutoConfiguration + @Import(GatewayConfig), which now carries both scans  
**pom.xml**  
&nbsp;- maven-jar-plugin classes-jar execution attaches a plain classes jar (classifier "classes"), excluding  
&nbsp;   application.yml and logback-spring.xml  
Dockerfile  
&nbsp;- COPY target/esquire-gateway.jar app.jar  
**Dockerfile.win**  
&nbsp;- COPY target/esquire-gateway.jar app.jar  
**Dockerfile.lx**  
&nbsp;- COPY --from=builder /build/gateway/target/esquire-gateway.jar app.jar  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**08/26/2026** mir0n  v1.2.13 -- credential state held both ways, and the admin client moved  
**config.KeycloakConfig**  
&nbsp;- reduced to the kc-async task executor bean; the Keycloak admin client and its settings move to  
&nbsp;   KcIdentityGateway, built from the shared KcConnectionSettings  
**identity.KcIdentityGateway**  
&nbsp;- builds the Keycloak admin client itself from KcConnectionSettings -- its own JAX-RS Client with  
&nbsp;   JacksonProvider and connect / read timeouts -- and implements IQueueRig.IErrorListener so a worker  
&nbsp;   throw is recorded rather than lost  
**messaging.KcRequestHandler**  
&nbsp;- the updateAccess call drops the password and enabled arguments, neither of which the messaging path  
&nbsp;   manages  
**service.IKcIdentityService**  
&nbsp;- updateAccess drops the password and enabled parameters; forcePasswordChange becomes a Boolean and  
&nbsp;   removeTotp joins it, so each required action can be withdrawn as well as set  
**service.impl.KcIdentityService**  
&nbsp;- updateAccess reads the user existing required actions and holds each one both ways via holdAction:  
&nbsp;   UPDATE_PASSWORD follows forcePasswordChange, CONFIGURE_TOTP is set on request and removed on  
&nbsp;   removeTotp, and the write happens only when something changed  
&nbsp;- deleteUser checks and closes the JAX-RS Response, so a refused delete is not reported SUCCESS  
**resources/application.yml**  
&nbsp;- the admin connect / read timeouts take KC_ADMIN_CONNECT_TIMEOUT_MS and KC_ADMIN_READ_TIMEOUT_MS  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

**08/12/2026** mir0n  v1.2.13 -- Mesnie  
KcMasterApplication  
&nbsp;- the package list moved to KcMasterConfig (@Import)  
KcMasterConfig  (new)  
&nbsp;- created: kcMaster's @ComponentScan, the one place its packages are named; excludes KcMasterApplication;  
&nbsp;   declares @Bean KcIdentityGateway identityGateway(Environment) with initMethod start / destroyMethod stop  
identity.KcIdentityGateway  (new)  
&nbsp;- created: THE identity workflow and the only copy of it. Builds its own KeyCloak admin client, path park,  
&nbsp;   KcIdentityService and KcRequestHandler from keycloak.admin.* and kcmaster.path-buffer.*; serve(RodEvent)  
&nbsp;   routes by msgType -- a request to the handler, a broadcast to the park; postRequest / postMessage queue  
&nbsp;   onto a BoundedQueueRig (one worker, FIFO); start()/stop() drive the park pruner and the queue gate  
**config.KeycloakConfig**  
&nbsp;- the keycloak() and kcPathBuffer() beans removed: KcIdentityGateway builds the admin client and the path  
&nbsp;   park itself; the I39 note moved with the client  
**messaging.EntityBusAdapter**  
&nbsp;- transport only: the receive worker is KcIdentityGateway.serve; the park decision, the KC user lookup and  
&nbsp;   the path extraction moved to the gateway  
**messaging.KcBusAdapter**  
&nbsp;- transport only: the receive worker is KcIdentityGateway.serve and the gateway's answers transmit back on  
&nbsp;   the rod; the dispatch and the URS/URR building moved to the gateway  
**messaging.KcRequestHandler**  
&nbsp;- KcSyncRequest -> AuthSyncRequest (moved to common backend.identity); @Component dropped -- the handler is  
&nbsp;   built by KcIdentityGateway  
**messaging.KcSyncRequest**  
&nbsp;- moved to common backend.identity as AuthSyncRequest  
**service.impl.KcIdentityService**  
&nbsp;- @Service dropped -- built by KcIdentityGateway; KcSyncRequest -> AuthSyncRequest  
**pom.xml**  
&nbsp;- maven-jar-plugin execution attaches a plain classes jar (classifier "classes"), excluding application.yml  
&nbsp;   and logback-spring.xml  
Dockerfile, Dockerfile.win, Dockerfile.lx  
&nbsp;- COPY names esquire-kc-master.jar instead of target/*.jar  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**08/26/2026** mir0n  v1.2.13 -- develop logging  
**controller.KeySmithController**  
&nbsp;- the develop log lines pass the value itself instead of String.valueOf  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

**08/17/2026** mir0n  v1.2.13 -- observability for the composed services  
**service.impl.KeySmithService**  
&nbsp;- business meters added: meterKeyOp() records esq.biz.key.ops.total (op=read|save, outcome=ok|error) in a  
&nbsp;   finally around each of the two operations, and esq.biz.key.identity.total (op=) is counted after  
&nbsp;   the identity request is posted. esquireKeySave() body extracted to saveAndSync(), so the meter wraps it with  
&nbsp;   one exit. Both meter names are spelled at the call: the o11y inventory scan reads the tree for the literal,  
&nbsp;   so a name held in a constant never reaches the sheet  

**08/12/2026** mir0n  v1.2.13 -- Mesnie  
KeySmithApplication  
&nbsp;- the package list moved to KeySmithConfig (@Import); @Bean IIdentityGateway identityGateway() declared here  
&nbsp;   with initMethod start / destroyMethod stop -- one gateway per PROCESS  
KeySmithConfig  (new)  
&nbsp;- created: keySmith's @ComponentScan / @EntityScan / @EnableJpaRepositories, the one place its packages are  
&nbsp;   named; excludes KeySmithApplication and AuditConfig from the scan  
**messaging.KcBusAdapter**  
&nbsp;- implements IIdentityGateway: postRequest(RodEvent) transmits, postMessage is skipped, start()/stop() take  
&nbsp;   the kc leg (was the constructor)  
**service.impl.KeySmithService**  
&nbsp;- field KcBusAdapter -> IIdentityGateway; identityCommand() picks C/U/D from the connect flag and  
&nbsp;   identityEvent() builds the AuthSyncRequest + RodEvent posted to it  
**pom.xml**  
&nbsp;- maven-jar-plugin execution attaches a plain classes jar (classifier "classes"), excluding application.yml  
&nbsp;   and logback-spring.xml  
Dockerfile, Dockerfile.win, Dockerfile.lx  
&nbsp;- COPY names esquire-key-smith.jar instead of target/*.jar  

### mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt

Esquire Mesnie Microservice  

**08/26/2026** mir0n  v1.2.13 -- configuration and develop logging  
**resources/application.yml**  
&nbsp;- the admin connect / read timeouts take KC_ADMIN_CONNECT_TIMEOUT_MS and KC_ADMIN_READ_TIMEOUT_MS  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

**08/17/2026** mir0n  v1.2.13 -- observability for the composed services  
MesnieMeterOwner  (new)  
&nbsp;- created: which of the three the household's meters belong to, read off the meter id: its name, the route  
&nbsp;   it was served on, or the bus it travelled  
MesnieApplication  
&nbsp;- @Bean IMeterOwner meterOwner(entityBusId) declared here: a MesnieMeterOwner, handed the entity bus id from  
&nbsp;   the property the bus itself reads  

**08/12/2026** mir0n  v1.2.13 -- Mesnie  
MesnieApplication  (new)  
&nbsp;- created: enyMan and keySmith in ONE process, each imported as its own @Configuration, with kcMaster's  
&nbsp;   identity work served in that same process. Declares what a PROCESS owns -- the roles repository, one  
&nbsp;   AuditBusBridge, one IIdentityGateway (KcIdentityGateway, named and handed the Environment), the startup  
&nbsp;   storages and one MessagingBusLifecycleRegistrar over the entity and audit buses. No kc rod is built.  
application.yml  (new)  
&nbsp;- created: the union of enyMan's and keySmith's settings under spring.application.name mesnie -- DB_MESNIE_*  
&nbsp;   datasource, the seven mapping resources per dialect, the enyman.move-queue block bound by its literal  
&nbsp;   prefix, keycloak.admin.* and kcmaster.path-buffer.* read by the identity gateway, and bus refs for entity  
&nbsp;   and audit only  
logback-spring.xml  (new)  
&nbsp;- created: the develop and msg channels writing logs/mesnie-develop.log and logs/mesnie-msg.log  
pom.xml  (new)  
&nbsp;- created: depends on the enyMan, keySmith and kcMaster plain classes jars (classifier "classes");  
&nbsp;   spring-boot-maven-plugin builds esquire-mesnie.jar  
Dockerfile  (new)  
&nbsp;- created: COPY target/esquire-mesnie.jar app.jar  
compose.yaml  (new)  
&nbsp;- created: builds the esquire.mesnie image from this module  

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt


**08/27/2026** mir0n  v1.2.13 -- one alphabet for the create op  
BusConstants  
&nbsp;- EVENT_CREATE is "I" -- the wire event code and the *_log action column carry the same letters  
RodEvent  
&nbsp;- dbAction() -- the action code the keep writes; null for UPDATE_PATH and UNKNOWN, which no *_log holds  

**08/26/2026** mir0n  v1.2.13 -- the transport-state gauge, and failures that leave a record  
BusHealthIndicator  
&nbsp;- registerTransportGauges publishes each bus transport state as messaging.transport.up (1 connected,  
&nbsp;   0 not), so the state reaches Prometheus and not only /actuator/health  
**o11y.IRodMeters**  
&nbsp;- registerTransportUp(busId, IntSupplier) added -- the transport-state gauge, registered once per bus;  
&nbsp;   NOOP implementation alongside  
**o11y.IRodObserver**  
&nbsp;- registerTransportUp forwarded to the meters, and to IRodMeters.NOOP when unobserved  
RodEvent  
&nbsp;- Op gains UNKNOWN: opOf falls back to it instead of DELETE, and opCode() answers null for it  
**xrod.impl.AXRod**  
&nbsp;- implements IQueueRig.IErrorListener and registers itself on the feed, so whatever the send worker  
&nbsp;   throws is recorded as TX-ERR plus the error meter; encode THROWS instead of returning null, which was  
&nbsp;   the one send failure that left no record  
**xrod.impl.sublayer.AliveSessionRR**  
&nbsp;- an R&R SERVER emits no unsolicited keep-alive -- keepAliveEvent returns null for that role  
**xrod.impl.sublayer.SendRetrySublayer**  
&nbsp;- parseBackoff takes the BusIdentity and REFUSES the leg on a step that does not parse, naming it; a  
&nbsp;   blank spec still yields the single 1s step  

**08/15/2026** mir0n  v1.2.13 -- gateWard  
BusHealthIndicator  
&nbsp;- register() now registers into BOTH health registries: the blocking HealthContributorRegistry as before,  
&nbsp;   and the ReactiveHealthContributorRegistry via ReactiveHealthContributor.adapt() in registerReactive(),  
&nbsp;   which a WebFlux service reads  

### mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the queue-rig outcome seam, and the night-watch reaction  
**concurrent.IQueueRig**  
&nbsp;- outcome seam made symmetric: ISuccessListener + setSuccessListener; single-item only (a bulk worker  
&nbsp;   returns what it did not handle). NOOP on both listener contracts  
**concurrent.BoundedQueueRig**  
&nbsp;- the outcome seam made symmetric: an ISuccessListener fired per handled item (setSuccessListener, NOOP  
&nbsp;   default); setErrorListener(null) restores the LOGGING default, not NOOP  
**taijitu.AMonadY**  
&nbsp;- implements IQueueRig.IListErrorListener and registers itself on the rig; inFlightCommand carries the  
&nbsp;   command the worker is running so onError notifies its gate. RESULT_TIMEDOUT / RESULT_INTERRUPTED  
&nbsp;   replace the two literals resultCommand writes  
**taijitu.ATaijituRig**  
&nbsp;- the sweep splits a non-digest three ways: TIMEDOUT promotes the freshly loaded shadow, FAILED and  
&nbsp;   INTERRUPTED report and leave both legs, and shuttingDown (set by shutdown()) stops any promotion once  
&nbsp;   the process is going down. notADigest / unusable replace checksumFailed  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the kind checked, both accounts read, every save broadcast  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- assertKindMatches refuses an operation whose request kind differs from the account row kind;  
&nbsp;   entityRepository made protected for the transfer processor  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- the transfer reads BOTH accounts before it moves anything and raises ResourceNotFoundException when  
&nbsp;   either is absent  
**controller.PacManController**  
&nbsp;- the develop log lines pass the value itself instead of String.valueOf  
**service.impl.PacManService**  
&nbsp;- every esquireCommandSave publishes an UPDATE -- isBroadcastableUpdate removed  
**resources/logback-spring.xml**  
&nbsp;- the develop pattern drops the "develop." prefix from the logger name  

### tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the connection health seed  
**tp.activemq.TransportProvider**  
&nbsp;- the connection health seeds UNKNOWN, not UP -- nothing has proved the connection at open  

### tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the connection health seed  
**tp.kafka.TransportProvider**  
&nbsp;- the connection health seeds UNKNOWN, not UP -- nothing has proved the connection at open  

### tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt


**08/26/2026** mir0n  v1.2.13 -- the connection health seed  
**tp.redis.TransportProvider**  
&nbsp;- the connection health seeds UNKNOWN, not UP -- nothing has proved the connection at open  

---

## Commits

```

-- 2026-08-28 | commit: aadbe27 | mir0n.the.programmer | v1.2.13 -- Finalization --
M	README.md
M	Releases.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.TestingStack.md
M	doc/release_notes.txt
M	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
 8 files changed, 169 insertions(+), 420 deletions(-)

-- 2026-08-27 | commit: aa8a0b8 | mir0n.the.programmer | v1.2.13 -- Refresh the cloud: the sprint carried to OKE --
M	.github/workflows/deploy-oke.yml
M	doc/Esquire.GitHubActions.md
M	doc/release_notes.txt
D	k8s-oci-compact/fix.bat
M	k8s-oci-compact/oke-o11y-off.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci-compact/values/gateward.yaml
 8 files changed, 98 insertions(+), 35 deletions(-)

-- 2026-08-27 | commit: fb124ab | mir0n.the.programmer | v1.2.13 -- Hardening: the solution read back, and the issues it raised --
M	.github/scripts/deploy-local.cmd
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	activemq/Dockerfile
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/logback-spring.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CacheSqlSet.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/main/resources/application.yml
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/MoveNodeOrderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/error/CommandNotAcceptedException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailWriter.java
A	common/src/main/java/pro/mir0n/esquire/backend/identity/KcConnectionSettings.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
R077	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/error/ProblemDetailWriterTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/identity/KcConnectionSettingsTest.java
R082	common/src/test/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilterTest.java	common/src/test/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilterTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/PerformanceAspectTest.java
M	compose-compact/compose-rebuild.bat
M	compose-compact/compose.yaml
M	compose-compact/docker-compose-down.bat
M	compose-compact/docker-compose-up.bat
A	compose-compact/o11y-log-off.bat
A	compose-compact/o11y-log-on.bat
M	compose-compact/o11y-off.bat
M	compose-compact/o11y-on.bat
A	compose-compact/o11y-test.bat
M	compose-compact/o11y-verify.bat
M	compose-compact/o11y/alloy-config.alloy
M	compose-compact/o11y/grafana/gen-dashboard.py
M	compose-compact/o11y/grafana/gen-datasources.py
M	compose-compact/o11y/grafana/gen-topology.py
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose-compact/o11y/grafana/provisioning/datasources/loki.yaml
M	compose-compact/o11y/grafana/provisioning/datasources/prometheus.yaml
M	compose-compact/o11y/grafana/provisioning/datasources/tempo.yaml
M	compose-compact/o11y/loki-config.yaml
M	compose-compact/o11y/otel-collector-config.yaml
M	compose-compact/o11y/rules.yml
M	compose-compact/o11y/tempo-config.yaml
M	compose-compact/topology/esquire-topology.yml
M	compose/compose-rebuild.bat
M	compose/compose.yaml
D	compose/data/postgres/-placeholder-
M	compose/docker-compose-down.bat
M	compose/docker-compose-up.bat
M	compose/o11y-test.bat
M	compose/o11y-verify.bat
M	compose/o11y/alloy-config.alloy
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-datasources.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/o11y/loki-config.yaml
M	compose/o11y/otel-collector-config.yaml
M	compose/o11y/rules.yml
M	compose/o11y/tempo-config.yaml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
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
M	doc/Esquire.Messaging.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.Guides.md
M	doc/Esquire.MessagingBus.MessageStructure.md
M	doc/Esquire.MessagingBus.Q&A.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.ObservabilityStack.Inventory.Compact.csv
A	doc/Esquire.ObservabilityStack.Inventory.SuperCompact.csv
M	doc/Esquire.ObservabilityStack.Inventory.csv
M	doc/Esquire.ObservabilityStack.Logging.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EntityPathLookup.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqMoveRecord.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/logback-spring.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/MoveDestinationGuardTest.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/TreeRouteTimingFilter.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateWard/src/main/resources/logback-spring.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/InnerTimerFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
R091	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JweAwareJwtDecoder.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JweAwareJwtDecoder.java
R098	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwksController.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JwksController.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/package-info.java
R092	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ExpiringJwt.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ExpiringJwt.java
R095	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayClient.java
R096	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayVariant.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayVariant.java
R096	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/KcTokenRequest.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/KcTokenRequest.java
R098	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/PhantomTokenRelay.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelay.java
R092	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayCache.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
R080	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayFilter.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilter.java
R098	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VanillaTokenRelay.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelay.java
R095	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VariantAction.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VariantAction.java
R099	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/WebClientTokenRelayClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/TokenRelayWiringGuardTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelayTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/RelayTestTokens.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCacheTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilterTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayVariantContractTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelayTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
M	k8s-compact/charts/esquire-aukeep/templates/deployment.yaml
D	k8s-compact/charts/esquire-biztree/Chart.yaml
D	k8s-compact/charts/esquire-biztree/templates/configmap.yaml
D	k8s-compact/charts/esquire-biztree/templates/deployment.yaml
D	k8s-compact/charts/esquire-biztree/templates/secret.yaml
D	k8s-compact/charts/esquire-biztree/templates/service.yaml
D	k8s-compact/charts/esquire-biztree/values.yaml
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
D	k8s-compact/charts/esquire-gateway/Chart.yaml
D	k8s-compact/charts/esquire-gateway/templates/configmap.yaml
D	k8s-compact/charts/esquire-gateway/templates/deployment.yaml
D	k8s-compact/charts/esquire-gateway/templates/secret.yaml
D	k8s-compact/charts/esquire-gateway/templates/service.yaml
D	k8s-compact/charts/esquire-gateway/values.yaml
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-compact/charts/infra/activemq/Chart.yaml
M	k8s-compact/charts/infra/alloy/templates/configmap.yaml
M	k8s-compact/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s-compact/charts/infra/grafana/templates/configmap-dashboards.yaml
M	k8s-compact/charts/infra/grafana/templates/configmap-datasource.yaml
M	k8s-compact/charts/infra/grafana/templates/configmap-icons.yaml
M	k8s-compact/charts/infra/grafana/templates/ingress.yaml
M	k8s-compact/charts/infra/grafana/values.yaml
D	k8s-compact/charts/infra/kafka/Chart.yaml
D	k8s-compact/charts/infra/kafka/templates/deployment.yaml
D	k8s-compact/charts/infra/kafka/templates/service.yaml
D	k8s-compact/charts/infra/kafka/values.yaml
M	k8s-compact/charts/infra/keycloak/Chart.yaml
M	k8s-compact/charts/infra/keycloak/templates/statefulset.yaml
M	k8s-compact/charts/infra/loki/templates/configmap.yaml
M	k8s-compact/charts/infra/otel-collector/templates/configmap.yaml
M	k8s-compact/charts/infra/postgres/Chart.yaml
M	k8s-compact/charts/infra/postgres/templates/statefulset.yaml
M	k8s-compact/charts/infra/prometheus/rules.yml
M	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
M	k8s-compact/charts/infra/redis/Chart.yaml
M	k8s-compact/charts/infra/tempo/templates/configmap.yaml
M	k8s-compact/k8s-down.bat
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/k8s-up.bat
M	k8s-compact/o11y-forward-stop.bat
M	k8s-compact/o11y-full-on.bat
M	k8s-compact/o11y-log-off.bat
M	k8s-compact/o11y-log-on.bat
M	k8s-compact/o11y-off.bat
M	k8s-compact/o11y-on.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/o11y-verify.bat
M	k8s-compact/values/activemq.yaml
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
D	k8s-compact/values/biztree.yaml
M	k8s-compact/values/gateward.yaml
D	k8s-compact/values/gateway.yaml
M	k8s-compact/values/keycloak.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-compact/values/postgres.yaml
M	k8s-oci-compact/ghcr-push.bat
A	k8s-oci-compact/grafana/esquire-logging.json
A	k8s-oci-compact/grafana/esquire-services.json
M	k8s-oci-compact/grafana/esquire-topology.json
M	k8s-oci-compact/oke-o11y-off.bat
M	k8s-oci-compact/oke-o11y-on.bat
M	k8s-oci-compact/oke-o11y-test.bat
M	k8s-oci-compact/oke-o11y-verify.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci-compact/values/gateward.yaml
M	k8s-oci/README.md
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/infra/activemq/Chart.yaml
M	k8s/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/kafka/Chart.yaml
M	k8s/charts/infra/keycloak/Chart.yaml
M	k8s/charts/infra/loki/templates/configmap.yaml
M	k8s/charts/infra/otel-collector/templates/configmap.yaml
M	k8s/charts/infra/postgres/Chart.yaml
M	k8s/charts/infra/prometheus/rules.yml
M	k8s/charts/infra/redis/Chart.yaml
M	k8s/charts/infra/tempo/templates/configmap.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
M	k8s/o11y-forward-stop.bat
M	k8s/o11y-full-on.bat
M	k8s/o11y-log-off.bat
M	k8s/o11y-log-on.bat
M	k8s/o11y-off.bat
M	k8s/o11y-on.bat
M	k8s/o11y-test.bat
M	k8s/o11y-verify.bat
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/main/resources/logback-spring.xml
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGatewayOutcomeTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keycloak/Dockerfile.keycloak
M	keycloak/compose.yaml
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/main/resources/logback-spring.xml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/EncodeFailingTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSendFailureTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
M	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYOutcomeTest.java
M	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/logback-spring.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	postgres/Dockerfile
M	test/audit-smoke/run.sh
M	test/o11y/fleet-compact-k8s.bat
M	test/o11y/fleet-supercompact-k8s.bat
A	test/o11y/o11y-aspects.py
M	test/o11y/o11y-drive.py
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
 345 files changed, 14365 insertions(+), 2871 deletions(-)

-- 2026-08-21 | commit: 41e5aef | mir0n.the.programmer | v1.2.13 -- KeyCloak 26.6.0, and the sprint documentation --
M	README.md
M	compose-compact/compose.yaml
M	compose/compose.yaml
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
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/install/Docker.md
M	doc/install/LocalK8s.md
A	doc/media/ComponentModel.Compact.png
M	doc/media/ComponentModel.png
M	doc/media/token-exchange-v1v2.svg
M	doc/media/topology-screenshot.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
M	keycloak/Dockerfile.keycloak
M	keycloak/compose.yaml
M	keycloak/import/esquire.json
 30 files changed, 726 insertions(+), 71 deletions(-)

-- 2026-08-20 | commit: be8a6b4 | mir0n.the.programmer |  v1.2.13 -- the composed services on the cloud --
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-oke.yml
M	activemq/conf/activemq.xml
M	activemq/esq-entrypoint.sh
M	compose-compact/compose.yaml
A	compose-compact/o11y-off.bat
A	compose-compact/o11y-on.bat
A	compose-compact/o11y-verify.bat
M	compose-compact/o11y/grafana/gen-topology.py
M	doc/logo/keySmith.svg
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	k8s-compact/charts/esquire-aukeep/templates/configmap.yaml
M	k8s-compact/charts/esquire-aukeep/templates/deployment.yaml
M	k8s-compact/charts/esquire-aukeep/templates/secret.yaml
M	k8s-compact/charts/esquire-aukeep/templates/service.yaml
M	k8s-compact/charts/esquire-backend/templates/configmap.yaml
M	k8s-compact/charts/esquire-backend/templates/deployment.yaml
M	k8s-compact/charts/esquire-backend/templates/secret.yaml
M	k8s-compact/charts/esquire-backend/templates/service.yaml
M	k8s-compact/charts/esquire-backend/templates/spa-config.yaml
M	k8s-compact/charts/esquire-backend/values.yaml
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/templates/deployment.yaml
M	k8s-compact/charts/esquire-gateward/templates/secret.yaml
M	k8s-compact/charts/esquire-gateward/templates/service.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
M	k8s-compact/charts/esquire-gateway/values.yaml
M	k8s-compact/charts/esquire-mesnie/templates/configmap.yaml
M	k8s-compact/charts/esquire-mesnie/templates/deployment.yaml
M	k8s-compact/charts/esquire-mesnie/templates/secret.yaml
M	k8s-compact/charts/esquire-mesnie/templates/service.yaml
M	k8s-compact/charts/esquire-pacman/templates/configmap.yaml
M	k8s-compact/charts/esquire-pacman/templates/deployment.yaml
M	k8s-compact/charts/esquire-pacman/templates/secret.yaml
M	k8s-compact/charts/esquire-pacman/templates/service.yaml
M	k8s-compact/charts/infra/activemq/templates/statefulset.yaml
M	k8s-compact/charts/infra/activemq/values.yaml
M	k8s-compact/charts/infra/grafana/templates/configmap-dashboards.yaml
M	k8s-compact/charts/infra/grafana/templates/deployment.yaml
M	k8s-compact/charts/infra/grafana/values.yaml
M	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
M	k8s-compact/charts/infra/redis/templates/deployment.yaml
M	k8s-compact/charts/infra/redis/values.yaml
M	k8s-compact/cluster/ingress.yaml
M	k8s-compact/k8s-down.bat
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/k8s-up.bat
D	k8s-compact/logs/-placeholder-
M	k8s-compact/o11y-full-on.bat
M	k8s-compact/o11y-log-off.bat
M	k8s-compact/o11y-log-on.bat
M	k8s-compact/o11y-off.bat
M	k8s-compact/o11y-on.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/o11y-verify.bat
M	k8s-compact/values/activemq.yaml
M	k8s-compact/values/postgres.yaml
A	k8s-oci-compact/cluster/ingress.yaml
A	k8s-oci-compact/esquire-topology.yml
A	k8s-oci-compact/fix.bat
A	k8s-oci-compact/ghcr-push.bat
A	k8s-oci-compact/grafana/esquire-topology.json
A	k8s-oci-compact/oke-config-parity.bat
A	k8s-oci-compact/oke-down.bat
A	k8s-oci-compact/oke-grafana-forward.bat
A	k8s-oci-compact/oke-login.bat
A	k8s-oci-compact/oke-o11y-off.bat
A	k8s-oci-compact/oke-o11y-on.bat
A	k8s-oci-compact/oke-o11y-test.bat
A	k8s-oci-compact/oke-o11y-verify.bat
A	k8s-oci-compact/oke-pg-forward.bat
A	k8s-oci-compact/oke-rebuild.bat
A	k8s-oci-compact/oke-up.bat
A	k8s-oci-compact/values/activemq.yaml
A	k8s-oci-compact/values/backend.yaml
A	k8s-oci-compact/values/gateward.yaml
A	k8s-oci-compact/values/keycloak.yaml
A	k8s-oci-compact/values/mesnie.yaml
A	k8s-oci-compact/values/pacman.yaml
A	k8s-oci-compact/values/postgres.yaml
A	k8s-oci-compact/values/redis.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
M	k8s/o11y-full-on.bat
M	k8s/o11y-log-off.bat
M	k8s/o11y-log-on.bat
M	k8s/o11y-off.bat
M	k8s/o11y-on.bat
M	test/audit-smoke/run.sh
M	test/config-parity/config-parity.py
A	test/o11y/fleet-compact-k8s.bat
A	test/o11y/fleet-compact.bat
A	test/o11y/fleet-supercompact-k8s.bat
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
 99 files changed, 8431 insertions(+), 366 deletions(-)

-- 2026-08-20 | commit: 518c67c | mir0n.the.programmer | v1.2.13 -- observability for the composed services --
M	README.md
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqServiceTagFilter.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/IMeterOwner.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqServiceTagFilterTest.java
M	compose-compact/compose-rebuild.bat
M	compose-compact/compose.yaml
M	compose-compact/docker-compose-down.bat
M	compose-compact/docker-compose-start.bat
M	compose-compact/docker-compose-stop.bat
M	compose-compact/docker-compose-up.bat
A	compose-compact/logs/-placeholder-
M	compose-compact/o11y/grafana/gen-dashboard.py
M	compose-compact/o11y/grafana/gen-topology.py
M	compose-compact/o11y/grafana/icons/activemq.svg
M	compose-compact/o11y/grafana/icons/aukeep.svg
M	compose-compact/o11y/grafana/icons/biztree.svg
M	compose-compact/o11y/grafana/icons/enyman.svg
M	compose-compact/o11y/grafana/icons/explorer.svg
A	compose-compact/o11y/grafana/icons/gateward.svg
M	compose-compact/o11y/grafana/icons/gateway.svg
M	compose-compact/o11y/grafana/icons/kcmaster.svg
M	compose-compact/o11y/grafana/icons/keycloak.svg
M	compose-compact/o11y/grafana/icons/keysmith.svg
A	compose-compact/o11y/grafana/icons/mesnie.svg
M	compose-compact/o11y/grafana/icons/pacman.svg
M	compose-compact/o11y/grafana/icons/postgres.svg
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose-compact/o11y/prometheus.yml
M	compose/docker-compose-down.bat
M	compose/docker-compose-up.bat
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/icons/activemq.svg
M	compose/o11y/grafana/icons/aukeep.svg
M	compose/o11y/grafana/icons/biztree.svg
M	compose/o11y/grafana/icons/enyman.svg
M	compose/o11y/grafana/icons/explorer.svg
M	compose/o11y/grafana/icons/gateway.svg
M	compose/o11y/grafana/icons/kcmaster.svg
M	compose/o11y/grafana/icons/keycloak.svg
M	compose/o11y/grafana/icons/keysmith.svg
M	compose/o11y/grafana/icons/pacman.svg
M	compose/o11y/grafana/icons/postgres.svg
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	doc/Esquire.BizTree.md
M	doc/Esquire.Haubergeon.md
A	doc/Esquire.ObservabilityStack.Inventory.Compact.csv
M	doc/Esquire.ObservabilityStack.Inventory.csv
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.TestingStack.md
D	doc/logo/activemq.png
A	doc/logo/activemq.svg
D	doc/logo/angular.png
M	doc/logo/angular.svg
D	doc/logo/bizTree.png
A	doc/logo/bizTree.svg
D	doc/logo/enyMan.3.png
A	doc/logo/enyMan.svg
A	doc/logo/esquire.svg
A	doc/logo/gateward.svg
M	doc/logo/gateway.svg
R100	doc/media/grafana_icon.svg	doc/logo/grafana_icon.svg
M	doc/logo/hauberk.svg
R100	doc/media/jacoco.png	doc/logo/jacoco.png
R100	doc/media/jasmine.svg	doc/logo/jasmine.svg
R100	doc/media/junit.svg	doc/logo/junit.svg
R100	doc/media/karma.svg	doc/logo/karma.svg
D	doc/logo/kcMaster.png
A	doc/logo/kcMaster.svg
M	doc/logo/keep.svg
D	doc/logo/keySmith.3.png
A	doc/logo/keySmith.svg
D	doc/logo/keycloak.png
A	doc/logo/keycloak.svg
R100	doc/media/loki_icon.svg	doc/logo/loki_icon.svg
A	doc/logo/mesnie.svg
D	doc/logo/node.js.png
M	doc/logo/node.js.svg
M	doc/logo/pac-man.2.svg
M	doc/logo/pac-man.svg
R100	doc/media/playwrite.svg	doc/logo/playwrite.svg
M	doc/logo/postgres.svg
R100	doc/media/prometheus_logo.svg	doc/logo/prometheus_logo.svg
A	doc/logo/tempo_logo.svg
R100	doc/media/vitest.svg	doc/logo/vitest.svg
D	doc/media/dblTree.32.png
D	doc/media/gatling.svg
D	doc/media/hauberk.svg
D	doc/media/tempo_logo.png
D	doc/media/tempo_logo.svg
M	doc/release_notes.txt
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardMeterOwner.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
A	gateWard/src/test/java/pro/mir0n/esquire/gateWard/GateWardMeterOwnerTest.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
A	k8s-compact/charts/esquire-aukeep/Chart.yaml
A	k8s-compact/charts/esquire-aukeep/templates/configmap.yaml
A	k8s-compact/charts/esquire-aukeep/templates/deployment.yaml
A	k8s-compact/charts/esquire-aukeep/templates/secret.yaml
A	k8s-compact/charts/esquire-aukeep/templates/service.yaml
A	k8s-compact/charts/esquire-aukeep/values.yaml
M	k8s-compact/charts/esquire-mesnie/values.yaml
M	k8s-compact/charts/esquire-pacman/values.yaml
M	k8s-compact/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s-compact/charts/infra/grafana/icons/activemq.svg
M	k8s-compact/charts/infra/grafana/icons/aukeep.svg
M	k8s-compact/charts/infra/grafana/icons/biztree.svg
M	k8s-compact/charts/infra/grafana/icons/enyman.svg
M	k8s-compact/charts/infra/grafana/icons/explorer.svg
A	k8s-compact/charts/infra/grafana/icons/gateward.svg
M	k8s-compact/charts/infra/grafana/icons/gateway.svg
M	k8s-compact/charts/infra/grafana/icons/kcmaster.svg
M	k8s-compact/charts/infra/grafana/icons/keycloak.svg
M	k8s-compact/charts/infra/grafana/icons/keysmith.svg
A	k8s-compact/charts/infra/grafana/icons/mesnie.svg
M	k8s-compact/charts/infra/grafana/icons/pacman.svg
M	k8s-compact/charts/infra/grafana/icons/postgres.svg
M	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/k8s-up.bat
A	k8s-compact/logs/-placeholder-
M	k8s-compact/o11y-full-on.bat
M	k8s-compact/o11y-log-off.bat
M	k8s-compact/o11y-log-on.bat
M	k8s-compact/o11y-off.bat
M	k8s-compact/o11y-on.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/o11y-verify.bat
M	k8s-compact/values/activemq.yaml
A	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/grafana/icons/activemq.svg
M	k8s/charts/infra/grafana/icons/aukeep.svg
M	k8s/charts/infra/grafana/icons/biztree.svg
M	k8s/charts/infra/grafana/icons/enyman.svg
M	k8s/charts/infra/grafana/icons/explorer.svg
M	k8s/charts/infra/grafana/icons/gateway.svg
M	k8s/charts/infra/grafana/icons/kcmaster.svg
M	k8s/charts/infra/grafana/icons/keycloak.svg
M	k8s/charts/infra/grafana/icons/keysmith.svg
M	k8s/charts/infra/grafana/icons/pacman.svg
M	k8s/charts/infra/grafana/icons/postgres.svg
A	k8s/logs/-placeholder-
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
A	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieMeterOwner.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
A	mesnie/src/test/java/pro/mir0n/esquire/mesnie/MesnieMeterOwnerTest.java
M	test/audit-smoke/run.sh
M	test/health-smoke/run.sh
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
 168 files changed, 6422 insertions(+), 6660 deletions(-)

-- 2026-08-15 | commit: e622e85 | mir0n.the.programmer | v1.2.13 -- gateWard --
M	.github/scripts/deploy-local.cmd
M	bizTree/Dockerfile
M	bizTree/Dockerfile.lx
M	bizTree/Dockerfile.win
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeCacheConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
M	compose-compact/compose-rebuild.bat
M	compose-compact/compose.yaml
M	compose-compact/o11y/prometheus.yml
M	doc/release_notes.txt
A	gateWard/Dockerfile
A	gateWard/compose.yaml
A	gateWard/pom.xml
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheErrorAdvice.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/CacheReadScheduler.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/TreeRouteTimingFilter.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
A	gateWard/src/main/resources/application.yml
A	gateWard/src/main/resources/logback-spring.xml
M	gateway/Dockerfile
M	gateway/Dockerfile.lx
M	gateway/Dockerfile.win
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	k8s-compact/charts/esquire-backend/values.yaml
A	k8s-compact/charts/esquire-gateward/Chart.yaml
A	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
A	k8s-compact/charts/esquire-gateward/templates/deployment.yaml
A	k8s-compact/charts/esquire-gateward/templates/secret.yaml
A	k8s-compact/charts/esquire-gateward/templates/service.yaml
A	k8s-compact/charts/esquire-gateward/values.yaml
M	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
M	k8s-compact/cluster/ingress.yaml
M	k8s-compact/k8s-down.bat
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/k8s-up.bat
M	k8s-compact/o11y-full-on.bat
M	k8s-compact/o11y-log-off.bat
M	k8s-compact/o11y-log-on.bat
M	k8s-compact/o11y-off.bat
M	k8s-compact/o11y-on.bat
M	k8s-compact/o11y-test.bat
M	k8s-compact/o11y-verify.bat
M	k8s-compact/values/activemq.yaml
M	k8s-compact/values/backend.yaml
A	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	pom.xml
A	test/freshness-guard/run.sh
M	test/health-smoke/run.sh
 62 files changed, 2932 insertions(+), 215 deletions(-)

-- 2026-08-13 | commit: fcb06c4 | mir0n.the.programmer | v1.2.13 -- Mesnie CI/CD --
M	.github/scripts/deploy-compose.cmd
M	.github/scripts/deploy-local.cmd
M	doc/release_notes.txt
M	k8s-compact/k8s-down.bat
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
 13 files changed, 131 insertions(+), 23 deletions(-)

-- 2026-08-13 | commit: e34bfa8 | mir0n.the.programmer | v1.2.13 -- Mesnie --
M	README.md
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
A	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
A	compose-compact/compose-rebuild.bat
A	compose-compact/compose.yaml
A	compose-compact/docker-compose-down.bat
A	compose-compact/docker-compose-start.bat
A	compose-compact/docker-compose-stop.bat
A	compose-compact/docker-compose-up.bat
A	compose-compact/o11y/alloy-config.alloy
A	compose-compact/o11y/grafana/gen-dashboard.py
A	compose-compact/o11y/grafana/gen-datasources.py
A	compose-compact/o11y/grafana/gen-topology.py
A	compose-compact/o11y/grafana/icons/activemq.svg
A	compose-compact/o11y/grafana/icons/aukeep.svg
A	compose-compact/o11y/grafana/icons/biztree.svg
A	compose-compact/o11y/grafana/icons/enyman.svg
A	compose-compact/o11y/grafana/icons/explorer.svg
A	compose-compact/o11y/grafana/icons/gateway.svg
A	compose-compact/o11y/grafana/icons/kcmaster.svg
A	compose-compact/o11y/grafana/icons/keycloak.svg
A	compose-compact/o11y/grafana/icons/keysmith.svg
A	compose-compact/o11y/grafana/icons/pacman.svg
A	compose-compact/o11y/grafana/icons/postgres.svg
A	compose-compact/o11y/grafana/provisioning/dashboards/dashboards.yaml
A	compose-compact/o11y/grafana/provisioning/dashboards/esquire-logging.json
A	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
A	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
A	compose-compact/o11y/grafana/provisioning/datasources/loki.yaml
A	compose-compact/o11y/grafana/provisioning/datasources/prometheus.yaml
A	compose-compact/o11y/grafana/provisioning/datasources/tempo.yaml
A	compose-compact/o11y/loki-config.yaml
A	compose-compact/o11y/otel-collector-config.yaml
A	compose-compact/o11y/prometheus.yml
A	compose-compact/o11y/rules.yml
A	compose-compact/o11y/tempo-config.yaml
A	compose-compact/topology/esquire-topology.yml
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/release_notes.txt
M	enyMan/Dockerfile
M	enyMan/Dockerfile.lx
M	enyMan/Dockerfile.win
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
A	k8s-compact/addIngressNginx.bat
A	k8s-compact/addMetalLB.bat
A	k8s-compact/charts/esquire-backend/Chart.yaml
A	k8s-compact/charts/esquire-backend/templates/configmap.yaml
A	k8s-compact/charts/esquire-backend/templates/deployment.yaml
A	k8s-compact/charts/esquire-backend/templates/secret.yaml
A	k8s-compact/charts/esquire-backend/templates/service.yaml
A	k8s-compact/charts/esquire-backend/templates/spa-config.yaml
A	k8s-compact/charts/esquire-backend/values.yaml
A	k8s-compact/charts/esquire-biztree/Chart.yaml
A	k8s-compact/charts/esquire-biztree/templates/configmap.yaml
A	k8s-compact/charts/esquire-biztree/templates/deployment.yaml
A	k8s-compact/charts/esquire-biztree/templates/secret.yaml
A	k8s-compact/charts/esquire-biztree/templates/service.yaml
A	k8s-compact/charts/esquire-biztree/values.yaml
A	k8s-compact/charts/esquire-gateway/Chart.yaml
A	k8s-compact/charts/esquire-gateway/templates/configmap.yaml
A	k8s-compact/charts/esquire-gateway/templates/deployment.yaml
A	k8s-compact/charts/esquire-gateway/templates/secret.yaml
A	k8s-compact/charts/esquire-gateway/templates/service.yaml
A	k8s-compact/charts/esquire-gateway/values.yaml
A	k8s-compact/charts/esquire-mesnie/Chart.yaml
A	k8s-compact/charts/esquire-mesnie/templates/configmap.yaml
A	k8s-compact/charts/esquire-mesnie/templates/deployment.yaml
A	k8s-compact/charts/esquire-mesnie/templates/secret.yaml
A	k8s-compact/charts/esquire-mesnie/templates/service.yaml
A	k8s-compact/charts/esquire-mesnie/values.schema.json
A	k8s-compact/charts/esquire-mesnie/values.yaml
A	k8s-compact/charts/esquire-pacman/Chart.yaml
A	k8s-compact/charts/esquire-pacman/templates/configmap.yaml
A	k8s-compact/charts/esquire-pacman/templates/deployment.yaml
A	k8s-compact/charts/esquire-pacman/templates/secret.yaml
A	k8s-compact/charts/esquire-pacman/templates/service.yaml
A	k8s-compact/charts/esquire-pacman/values.yaml
A	k8s-compact/charts/esquire-topology/Chart.yaml
A	k8s-compact/charts/esquire-topology/esquire-topology.yml
A	k8s-compact/charts/esquire-topology/templates/configmap.yaml
A	k8s-compact/charts/esquire-topology/values.yaml
A	k8s-compact/charts/infra/activemq/Chart.yaml
A	k8s-compact/charts/infra/activemq/templates/service.yaml
A	k8s-compact/charts/infra/activemq/templates/statefulset.yaml
A	k8s-compact/charts/infra/activemq/values.yaml
A	k8s-compact/charts/infra/alloy/Chart.yaml
A	k8s-compact/charts/infra/alloy/templates/configmap.yaml
A	k8s-compact/charts/infra/alloy/templates/deployment.yaml
A	k8s-compact/charts/infra/alloy/templates/pvc.yaml
A	k8s-compact/charts/infra/alloy/templates/rbac.yaml
A	k8s-compact/charts/infra/alloy/values.yaml
A	k8s-compact/charts/infra/grafana/Chart.yaml
A	k8s-compact/charts/infra/grafana/dashboards/esquire-logging.json
A	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
A	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
A	k8s-compact/charts/infra/grafana/icons/activemq.svg
A	k8s-compact/charts/infra/grafana/icons/aukeep.svg
A	k8s-compact/charts/infra/grafana/icons/biztree.svg
A	k8s-compact/charts/infra/grafana/icons/enyman.svg
A	k8s-compact/charts/infra/grafana/icons/explorer.svg
A	k8s-compact/charts/infra/grafana/icons/gateway.svg
A	k8s-compact/charts/infra/grafana/icons/kcmaster.svg
A	k8s-compact/charts/infra/grafana/icons/keycloak.svg
A	k8s-compact/charts/infra/grafana/icons/keysmith.svg
A	k8s-compact/charts/infra/grafana/icons/pacman.svg
A	k8s-compact/charts/infra/grafana/icons/postgres.svg
A	k8s-compact/charts/infra/grafana/templates/configmap-dashboards.yaml
A	k8s-compact/charts/infra/grafana/templates/configmap-datasource.yaml
A	k8s-compact/charts/infra/grafana/templates/configmap-icons.yaml
A	k8s-compact/charts/infra/grafana/templates/deployment.yaml
A	k8s-compact/charts/infra/grafana/templates/ingress.yaml
A	k8s-compact/charts/infra/grafana/templates/pvc.yaml
A	k8s-compact/charts/infra/grafana/templates/service.yaml
A	k8s-compact/charts/infra/grafana/values.yaml
A	k8s-compact/charts/infra/kafka/Chart.yaml
A	k8s-compact/charts/infra/kafka/templates/deployment.yaml
A	k8s-compact/charts/infra/kafka/templates/service.yaml
A	k8s-compact/charts/infra/kafka/values.yaml
A	k8s-compact/charts/infra/keycloak/Chart.yaml
A	k8s-compact/charts/infra/keycloak/templates/secret.yaml
A	k8s-compact/charts/infra/keycloak/templates/service.yaml
A	k8s-compact/charts/infra/keycloak/templates/statefulset.yaml
A	k8s-compact/charts/infra/keycloak/values.yaml
A	k8s-compact/charts/infra/loki/Chart.yaml
A	k8s-compact/charts/infra/loki/templates/configmap.yaml
A	k8s-compact/charts/infra/loki/templates/deployment.yaml
A	k8s-compact/charts/infra/loki/templates/pvc.yaml
A	k8s-compact/charts/infra/loki/templates/service.yaml
A	k8s-compact/charts/infra/loki/values.yaml
A	k8s-compact/charts/infra/otel-collector/Chart.yaml
A	k8s-compact/charts/infra/otel-collector/templates/configmap.yaml
A	k8s-compact/charts/infra/otel-collector/templates/deployment.yaml
A	k8s-compact/charts/infra/otel-collector/templates/service.yaml
A	k8s-compact/charts/infra/otel-collector/values.yaml
A	k8s-compact/charts/infra/postgres-exporter/Chart.yaml
A	k8s-compact/charts/infra/postgres-exporter/templates/deployment.yaml
A	k8s-compact/charts/infra/postgres-exporter/templates/service.yaml
A	k8s-compact/charts/infra/postgres-exporter/values.yaml
A	k8s-compact/charts/infra/postgres/Chart.yaml
A	k8s-compact/charts/infra/postgres/templates/secret.yaml
A	k8s-compact/charts/infra/postgres/templates/service.yaml
A	k8s-compact/charts/infra/postgres/templates/statefulset.yaml
A	k8s-compact/charts/infra/postgres/values.yaml
A	k8s-compact/charts/infra/prometheus/Chart.yaml
A	k8s-compact/charts/infra/prometheus/rules.yml
A	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
A	k8s-compact/charts/infra/prometheus/templates/deployment.yaml
A	k8s-compact/charts/infra/prometheus/templates/pvc.yaml
A	k8s-compact/charts/infra/prometheus/templates/rbac.yaml
A	k8s-compact/charts/infra/prometheus/templates/service.yaml
A	k8s-compact/charts/infra/prometheus/values.yaml
A	k8s-compact/charts/infra/redis/Chart.yaml
A	k8s-compact/charts/infra/redis/templates/deployment.yaml
A	k8s-compact/charts/infra/redis/templates/service.yaml
A	k8s-compact/charts/infra/redis/values.yaml
A	k8s-compact/charts/infra/tempo/Chart.yaml
A	k8s-compact/charts/infra/tempo/templates/configmap.yaml
A	k8s-compact/charts/infra/tempo/templates/deployment.yaml
A	k8s-compact/charts/infra/tempo/templates/pvc.yaml
A	k8s-compact/charts/infra/tempo/templates/service.yaml
A	k8s-compact/charts/infra/tempo/values.yaml
A	k8s-compact/cluster/ingress.yaml
A	k8s-compact/k8s-down.bat
A	k8s-compact/k8s-rebuild.bat
A	k8s-compact/k8s-up.bat
A	k8s-compact/metallb-config.yaml
A	k8s-compact/o11y-forward-stop.bat
A	k8s-compact/o11y-forward.bat
A	k8s-compact/o11y-full-on.bat
A	k8s-compact/o11y-log-off.bat
A	k8s-compact/o11y-log-on.bat
A	k8s-compact/o11y-off.bat
A	k8s-compact/o11y-on.bat
A	k8s-compact/o11y-test.bat
A	k8s-compact/o11y-verify.bat
A	k8s-compact/show.them.all.bat
A	k8s-compact/values/activemq.yaml
A	k8s-compact/values/backend.yaml
A	k8s-compact/values/biztree.yaml
A	k8s-compact/values/gateway.yaml
A	k8s-compact/values/keycloak.yaml
A	k8s-compact/values/mesnie.yaml
A	k8s-compact/values/pacman.yaml
A	k8s-compact/values/postgres.yaml
M	kcMaster/Dockerfile
M	kcMaster/Dockerfile.lx
M	kcMaster/Dockerfile.win
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcSyncRequest.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	keySmith/Dockerfile
M	keySmith/Dockerfile.lx
M	keySmith/Dockerfile.win
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
A	mesnie/Dockerfile
A	mesnie/compose.yaml
A	mesnie/pom.xml
A	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
A	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
A	mesnie/src/main/resources/application.yml
A	mesnie/src/main/resources/logback-spring.xml
M	pom.xml
M	test/audit-smoke/run.sh
M	test/health-smoke/run.sh
 229 files changed, 33080 insertions(+), 550 deletions(-)

-- 2026-08-11 | commit: 12a0b4f | mir0n.the.programmer | Create report_v1.2.12.md --
A	doc/reports/report_v1.2.12.md
 1 file changed, 691 insertions(+)
```

---

## Files Modified

```
M	.github/scripts/deploy-compose.cmd
M	.github/scripts/deploy-local.cmd
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	activemq/Dockerfile
M	activemq/conf/activemq.xml
M	activemq/esq-entrypoint.sh
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/logback-spring.xml
M	bizTree/Dockerfile
M	bizTree/Dockerfile.lx
M	bizTree/Dockerfile.win
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeCacheConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/CacheSqlSet.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/main/resources/application.yml
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHubGuardTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/MoveNodeOrderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/error/CommandNotAcceptedException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailWriter.java
A	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
A	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
A	common/src/main/java/pro/mir0n/esquire/backend/identity/KcConnectionSettings.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqServiceTagFilter.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/IMeterOwner.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
R077	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/error/ProblemDetailWriterTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/identity/KcConnectionSettingsTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqServiceTagFilterTest.java
R082	common/src/test/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilterTest.java	common/src/test/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilterTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/PerformanceAspectTest.java
A	compose-compact/compose-rebuild.bat
A	compose-compact/compose.yaml
A	compose-compact/docker-compose-down.bat
A	compose-compact/docker-compose-start.bat
A	compose-compact/docker-compose-stop.bat
A	compose-compact/docker-compose-up.bat
R100	compose/data/postgres/-placeholder-	compose-compact/logs/-placeholder-
A	compose-compact/o11y-log-off.bat
A	compose-compact/o11y-log-on.bat
A	compose-compact/o11y-off.bat
A	compose-compact/o11y-on.bat
A	compose-compact/o11y-test.bat
A	compose-compact/o11y-verify.bat
A	compose-compact/o11y/alloy-config.alloy
A	compose-compact/o11y/grafana/gen-dashboard.py
A	compose-compact/o11y/grafana/gen-datasources.py
A	compose-compact/o11y/grafana/gen-topology.py
A	compose-compact/o11y/grafana/icons/activemq.svg
A	compose-compact/o11y/grafana/icons/aukeep.svg
A	compose-compact/o11y/grafana/icons/biztree.svg
A	compose-compact/o11y/grafana/icons/enyman.svg
A	compose-compact/o11y/grafana/icons/explorer.svg
A	compose-compact/o11y/grafana/icons/gateward.svg
A	compose-compact/o11y/grafana/icons/gateway.svg
A	compose-compact/o11y/grafana/icons/kcmaster.svg
A	compose-compact/o11y/grafana/icons/keycloak.svg
A	compose-compact/o11y/grafana/icons/keysmith.svg
A	compose-compact/o11y/grafana/icons/mesnie.svg
A	compose-compact/o11y/grafana/icons/pacman.svg
A	compose-compact/o11y/grafana/icons/postgres.svg
A	compose-compact/o11y/grafana/provisioning/dashboards/dashboards.yaml
A	compose-compact/o11y/grafana/provisioning/dashboards/esquire-logging.json
A	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
A	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
A	compose-compact/o11y/grafana/provisioning/datasources/loki.yaml
A	compose-compact/o11y/grafana/provisioning/datasources/prometheus.yaml
A	compose-compact/o11y/grafana/provisioning/datasources/tempo.yaml
A	compose-compact/o11y/loki-config.yaml
A	compose-compact/o11y/otel-collector-config.yaml
A	compose-compact/o11y/prometheus.yml
A	compose-compact/o11y/rules.yml
A	compose-compact/o11y/tempo-config.yaml
A	compose-compact/topology/esquire-topology.yml
M	compose/compose-rebuild.bat
M	compose/compose.yaml
M	compose/docker-compose-down.bat
M	compose/docker-compose-up.bat
M	compose/o11y-test.bat
M	compose/o11y-verify.bat
M	compose/o11y/alloy-config.alloy
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-datasources.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/icons/activemq.svg
M	compose/o11y/grafana/icons/aukeep.svg
M	compose/o11y/grafana/icons/biztree.svg
M	compose/o11y/grafana/icons/enyman.svg
M	compose/o11y/grafana/icons/explorer.svg
M	compose/o11y/grafana/icons/gateway.svg
M	compose/o11y/grafana/icons/kcmaster.svg
M	compose/o11y/grafana/icons/keycloak.svg
M	compose/o11y/grafana/icons/keysmith.svg
M	compose/o11y/grafana/icons/pacman.svg
M	compose/o11y/grafana/icons/postgres.svg
M	compose/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/o11y/loki-config.yaml
M	compose/o11y/otel-collector-config.yaml
M	compose/o11y/rules.yml
M	compose/o11y/tempo-config.yaml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
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
A	doc/Esquire.ObservabilityStack.Inventory.Compact.csv
A	doc/Esquire.ObservabilityStack.Inventory.SuperCompact.csv
M	doc/Esquire.ObservabilityStack.Inventory.csv
M	doc/Esquire.ObservabilityStack.Logging.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/install/Docker.md
M	doc/install/LocalK8s.md
D	doc/logo/activemq.png
A	doc/logo/activemq.svg
D	doc/logo/angular.png
M	doc/logo/angular.svg
D	doc/logo/bizTree.png
A	doc/logo/bizTree.svg
D	doc/logo/enyMan.3.png
A	doc/logo/enyMan.svg
A	doc/logo/esquire.svg
A	doc/logo/gateward.svg
M	doc/logo/gateway.svg
R100	doc/media/grafana_icon.svg	doc/logo/grafana_icon.svg
M	doc/logo/hauberk.svg
R100	doc/media/jacoco.png	doc/logo/jacoco.png
R100	doc/media/jasmine.svg	doc/logo/jasmine.svg
R100	doc/media/junit.svg	doc/logo/junit.svg
R100	doc/media/karma.svg	doc/logo/karma.svg
D	doc/logo/kcMaster.png
A	doc/logo/kcMaster.svg
M	doc/logo/keep.svg
D	doc/logo/keySmith.3.png
A	doc/logo/keySmith.svg
D	doc/logo/keycloak.png
A	doc/logo/keycloak.svg
R100	doc/media/loki_icon.svg	doc/logo/loki_icon.svg
A	doc/logo/mesnie.svg
D	doc/logo/node.js.png
M	doc/logo/node.js.svg
M	doc/logo/pac-man.2.svg
M	doc/logo/pac-man.svg
R100	doc/media/playwrite.svg	doc/logo/playwrite.svg
M	doc/logo/postgres.svg
R100	doc/media/prometheus_logo.svg	doc/logo/prometheus_logo.svg
A	doc/logo/tempo_logo.svg
R100	doc/media/vitest.svg	doc/logo/vitest.svg
A	doc/media/ComponentModel.Compact.png
M	doc/media/ComponentModel.png
D	doc/media/dblTree.32.png
D	doc/media/gatling.svg
D	doc/media/hauberk.svg
D	doc/media/tempo_logo.png
D	doc/media/tempo_logo.svg
M	doc/media/token-exchange-v1v2.svg
M	doc/media/topology-screenshot.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.12.md
M	doc/services.configuring.md
M	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
M	enyMan/Dockerfile
M	enyMan/Dockerfile.lx
M	enyMan/Dockerfile.win
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EntityPathLookup.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqMoveRecord.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/logback-spring.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/MoveDestinationGuardTest.java
A	gateWard/Dockerfile
A	gateWard/compose.yaml
A	gateWard/pom.xml
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheErrorAdvice.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/CacheReadScheduler.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardMeterOwner.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/TreeRouteTimingFilter.java
A	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
A	gateWard/src/main/resources/application.yml
A	gateWard/src/main/resources/logback-spring.xml
A	gateWard/src/test/java/pro/mir0n/esquire/gateWard/GateWardMeterOwnerTest.java
M	gateway/Dockerfile
M	gateway/Dockerfile.lx
M	gateway/Dockerfile.win
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/InnerTimerFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
R091	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JweAwareJwtDecoder.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JweAwareJwtDecoder.java
R098	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwksController.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JwksController.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/package-info.java
R092	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ExpiringJwt.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ExpiringJwt.java
R095	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayClient.java
R096	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayVariant.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayVariant.java
R096	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/KcTokenRequest.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/KcTokenRequest.java
R098	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/PhantomTokenRelay.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelay.java
R092	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayCache.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
R080	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayFilter.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilter.java
R098	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VanillaTokenRelay.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelay.java
R095	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VariantAction.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VariantAction.java
R099	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/WebClientTokenRelayClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/TokenRelayWiringGuardTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelayTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/RelayTestTokens.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCacheTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilterTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayVariantContractTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelayTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
A	k8s-compact/addIngressNginx.bat
A	k8s-compact/addMetalLB.bat
A	k8s-compact/charts/esquire-aukeep/Chart.yaml
A	k8s-compact/charts/esquire-aukeep/templates/configmap.yaml
A	k8s-compact/charts/esquire-aukeep/templates/deployment.yaml
A	k8s-compact/charts/esquire-aukeep/templates/secret.yaml
A	k8s-compact/charts/esquire-aukeep/templates/service.yaml
A	k8s-compact/charts/esquire-aukeep/values.yaml
A	k8s-compact/charts/esquire-backend/Chart.yaml
A	k8s-compact/charts/esquire-backend/templates/configmap.yaml
A	k8s-compact/charts/esquire-backend/templates/deployment.yaml
A	k8s-compact/charts/esquire-backend/templates/secret.yaml
A	k8s-compact/charts/esquire-backend/templates/service.yaml
A	k8s-compact/charts/esquire-backend/templates/spa-config.yaml
A	k8s-compact/charts/esquire-backend/values.yaml
A	k8s-compact/charts/esquire-gateward/Chart.yaml
A	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
A	k8s-compact/charts/esquire-gateward/templates/deployment.yaml
A	k8s-compact/charts/esquire-gateward/templates/secret.yaml
A	k8s-compact/charts/esquire-gateward/templates/service.yaml
A	k8s-compact/charts/esquire-gateward/values.yaml
A	k8s-compact/charts/esquire-mesnie/Chart.yaml
A	k8s-compact/charts/esquire-mesnie/templates/configmap.yaml
A	k8s-compact/charts/esquire-mesnie/templates/deployment.yaml
A	k8s-compact/charts/esquire-mesnie/templates/secret.yaml
A	k8s-compact/charts/esquire-mesnie/templates/service.yaml
A	k8s-compact/charts/esquire-mesnie/values.schema.json
A	k8s-compact/charts/esquire-mesnie/values.yaml
A	k8s-compact/charts/esquire-pacman/Chart.yaml
A	k8s-compact/charts/esquire-pacman/templates/configmap.yaml
A	k8s-compact/charts/esquire-pacman/templates/deployment.yaml
A	k8s-compact/charts/esquire-pacman/templates/secret.yaml
A	k8s-compact/charts/esquire-pacman/templates/service.yaml
A	k8s-compact/charts/esquire-pacman/values.yaml
A	k8s-compact/charts/esquire-topology/Chart.yaml
A	k8s-compact/charts/esquire-topology/esquire-topology.yml
A	k8s-compact/charts/esquire-topology/templates/configmap.yaml
A	k8s-compact/charts/esquire-topology/values.yaml
A	k8s-compact/charts/infra/activemq/Chart.yaml
A	k8s-compact/charts/infra/activemq/templates/service.yaml
A	k8s-compact/charts/infra/activemq/templates/statefulset.yaml
A	k8s-compact/charts/infra/activemq/values.yaml
A	k8s-compact/charts/infra/alloy/Chart.yaml
A	k8s-compact/charts/infra/alloy/templates/configmap.yaml
A	k8s-compact/charts/infra/alloy/templates/deployment.yaml
A	k8s-compact/charts/infra/alloy/templates/pvc.yaml
A	k8s-compact/charts/infra/alloy/templates/rbac.yaml
A	k8s-compact/charts/infra/alloy/values.yaml
A	k8s-compact/charts/infra/grafana/Chart.yaml
A	k8s-compact/charts/infra/grafana/dashboards/esquire-logging.json
A	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
A	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
A	k8s-compact/charts/infra/grafana/icons/activemq.svg
A	k8s-compact/charts/infra/grafana/icons/aukeep.svg
A	k8s-compact/charts/infra/grafana/icons/biztree.svg
A	k8s-compact/charts/infra/grafana/icons/enyman.svg
A	k8s-compact/charts/infra/grafana/icons/explorer.svg
A	k8s-compact/charts/infra/grafana/icons/gateward.svg
A	k8s-compact/charts/infra/grafana/icons/gateway.svg
A	k8s-compact/charts/infra/grafana/icons/kcmaster.svg
A	k8s-compact/charts/infra/grafana/icons/keycloak.svg
A	k8s-compact/charts/infra/grafana/icons/keysmith.svg
A	k8s-compact/charts/infra/grafana/icons/mesnie.svg
A	k8s-compact/charts/infra/grafana/icons/pacman.svg
A	k8s-compact/charts/infra/grafana/icons/postgres.svg
A	k8s-compact/charts/infra/grafana/templates/configmap-dashboards.yaml
A	k8s-compact/charts/infra/grafana/templates/configmap-datasource.yaml
A	k8s-compact/charts/infra/grafana/templates/configmap-icons.yaml
A	k8s-compact/charts/infra/grafana/templates/deployment.yaml
A	k8s-compact/charts/infra/grafana/templates/ingress.yaml
A	k8s-compact/charts/infra/grafana/templates/pvc.yaml
A	k8s-compact/charts/infra/grafana/templates/service.yaml
A	k8s-compact/charts/infra/grafana/values.yaml
A	k8s-compact/charts/infra/keycloak/Chart.yaml
A	k8s-compact/charts/infra/keycloak/templates/secret.yaml
A	k8s-compact/charts/infra/keycloak/templates/service.yaml
A	k8s-compact/charts/infra/keycloak/templates/statefulset.yaml
A	k8s-compact/charts/infra/keycloak/values.yaml
A	k8s-compact/charts/infra/loki/Chart.yaml
A	k8s-compact/charts/infra/loki/templates/configmap.yaml
A	k8s-compact/charts/infra/loki/templates/deployment.yaml
A	k8s-compact/charts/infra/loki/templates/pvc.yaml
A	k8s-compact/charts/infra/loki/templates/service.yaml
A	k8s-compact/charts/infra/loki/values.yaml
A	k8s-compact/charts/infra/otel-collector/Chart.yaml
A	k8s-compact/charts/infra/otel-collector/templates/configmap.yaml
A	k8s-compact/charts/infra/otel-collector/templates/deployment.yaml
A	k8s-compact/charts/infra/otel-collector/templates/service.yaml
A	k8s-compact/charts/infra/otel-collector/values.yaml
A	k8s-compact/charts/infra/postgres-exporter/Chart.yaml
A	k8s-compact/charts/infra/postgres-exporter/templates/deployment.yaml
A	k8s-compact/charts/infra/postgres-exporter/templates/service.yaml
A	k8s-compact/charts/infra/postgres-exporter/values.yaml
A	k8s-compact/charts/infra/postgres/Chart.yaml
A	k8s-compact/charts/infra/postgres/templates/secret.yaml
A	k8s-compact/charts/infra/postgres/templates/service.yaml
A	k8s-compact/charts/infra/postgres/templates/statefulset.yaml
A	k8s-compact/charts/infra/postgres/values.yaml
A	k8s-compact/charts/infra/prometheus/Chart.yaml
A	k8s-compact/charts/infra/prometheus/rules.yml
A	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
A	k8s-compact/charts/infra/prometheus/templates/deployment.yaml
A	k8s-compact/charts/infra/prometheus/templates/pvc.yaml
A	k8s-compact/charts/infra/prometheus/templates/rbac.yaml
A	k8s-compact/charts/infra/prometheus/templates/service.yaml
A	k8s-compact/charts/infra/prometheus/values.yaml
A	k8s-compact/charts/infra/redis/Chart.yaml
A	k8s-compact/charts/infra/redis/templates/deployment.yaml
A	k8s-compact/charts/infra/redis/templates/service.yaml
A	k8s-compact/charts/infra/redis/values.yaml
A	k8s-compact/charts/infra/tempo/Chart.yaml
A	k8s-compact/charts/infra/tempo/templates/configmap.yaml
A	k8s-compact/charts/infra/tempo/templates/deployment.yaml
A	k8s-compact/charts/infra/tempo/templates/pvc.yaml
A	k8s-compact/charts/infra/tempo/templates/service.yaml
A	k8s-compact/charts/infra/tempo/values.yaml
A	k8s-compact/cluster/ingress.yaml
A	k8s-compact/k8s-down.bat
A	k8s-compact/k8s-rebuild.bat
A	k8s-compact/k8s-up.bat
A	k8s-compact/metallb-config.yaml
A	k8s-compact/o11y-forward-stop.bat
A	k8s-compact/o11y-forward.bat
A	k8s-compact/o11y-full-on.bat
A	k8s-compact/o11y-log-off.bat
A	k8s-compact/o11y-log-on.bat
A	k8s-compact/o11y-off.bat
A	k8s-compact/o11y-on.bat
A	k8s-compact/o11y-test.bat
A	k8s-compact/o11y-verify.bat
A	k8s-compact/show.them.all.bat
A	k8s-compact/values/activemq.yaml
A	k8s-compact/values/aukeep.yaml
A	k8s-compact/values/backend.yaml
A	k8s-compact/values/gateward.yaml
A	k8s-compact/values/keycloak.yaml
A	k8s-compact/values/mesnie.yaml
A	k8s-compact/values/pacman.yaml
A	k8s-compact/values/postgres.yaml
A	k8s-oci-compact/cluster/ingress.yaml
A	k8s-oci-compact/esquire-topology.yml
A	k8s-oci-compact/ghcr-push.bat
A	k8s-oci-compact/grafana/esquire-logging.json
A	k8s-oci-compact/grafana/esquire-services.json
A	k8s-oci-compact/grafana/esquire-topology.json
A	k8s-oci-compact/oke-config-parity.bat
A	k8s-oci-compact/oke-down.bat
A	k8s-oci-compact/oke-grafana-forward.bat
A	k8s-oci-compact/oke-login.bat
A	k8s-oci-compact/oke-o11y-off.bat
A	k8s-oci-compact/oke-o11y-on.bat
A	k8s-oci-compact/oke-o11y-test.bat
A	k8s-oci-compact/oke-o11y-verify.bat
A	k8s-oci-compact/oke-pg-forward.bat
A	k8s-oci-compact/oke-rebuild.bat
A	k8s-oci-compact/oke-up.bat
A	k8s-oci-compact/values/activemq.yaml
A	k8s-oci-compact/values/backend.yaml
A	k8s-oci-compact/values/gateward.yaml
A	k8s-oci-compact/values/keycloak.yaml
A	k8s-oci-compact/values/mesnie.yaml
A	k8s-oci-compact/values/pacman.yaml
A	k8s-oci-compact/values/postgres.yaml
A	k8s-oci-compact/values/redis.yaml
M	k8s-oci/README.md
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/infra/activemq/Chart.yaml
M	k8s/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/grafana/icons/activemq.svg
M	k8s/charts/infra/grafana/icons/aukeep.svg
M	k8s/charts/infra/grafana/icons/biztree.svg
M	k8s/charts/infra/grafana/icons/enyman.svg
M	k8s/charts/infra/grafana/icons/explorer.svg
M	k8s/charts/infra/grafana/icons/gateway.svg
M	k8s/charts/infra/grafana/icons/kcmaster.svg
M	k8s/charts/infra/grafana/icons/keycloak.svg
M	k8s/charts/infra/grafana/icons/keysmith.svg
M	k8s/charts/infra/grafana/icons/pacman.svg
M	k8s/charts/infra/grafana/icons/postgres.svg
M	k8s/charts/infra/kafka/Chart.yaml
M	k8s/charts/infra/keycloak/Chart.yaml
M	k8s/charts/infra/loki/templates/configmap.yaml
M	k8s/charts/infra/otel-collector/templates/configmap.yaml
M	k8s/charts/infra/postgres/Chart.yaml
M	k8s/charts/infra/prometheus/rules.yml
M	k8s/charts/infra/redis/Chart.yaml
M	k8s/charts/infra/tempo/templates/configmap.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
A	k8s/logs/-placeholder-
M	k8s/o11y-forward-stop.bat
M	k8s/o11y-full-on.bat
M	k8s/o11y-log-off.bat
M	k8s/o11y-log-on.bat
M	k8s/o11y-off.bat
M	k8s/o11y-on.bat
M	k8s/o11y-test.bat
M	k8s/o11y-verify.bat
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	kcMaster/Dockerfile
M	kcMaster/Dockerfile.lx
M	kcMaster/Dockerfile.win
M	kcMaster/pom.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcSyncRequest.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/main/resources/logback-spring.xml
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGatewayOutcomeTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/Dockerfile
M	keySmith/Dockerfile.lx
M	keySmith/Dockerfile.win
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/Dockerfile.keycloak
M	keycloak/compose.yaml
M	keycloak/import/esquire.json
A	mesnie/Dockerfile
A	mesnie/compose.yaml
A	mesnie/pom.xml
A	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
A	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieMeterOwner.java
A	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
A	mesnie/src/main/resources/application.yml
A	mesnie/src/main/resources/logback-spring.xml
A	mesnie/src/test/java/pro/mir0n/esquire/mesnie/MesnieMeterOwnerTest.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusHealthIndicator.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/IXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/TransportMessage.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodTransportAdapter.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/EncodeFailingTransportProvider.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSendFailureTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/XRodSubscriptionSelectorTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
M	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
M	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYOutcomeTest.java
M	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/logback-spring.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pom.xml
M	postgres/Dockerfile
M	test/audit-smoke/run.sh
M	test/config-parity/config-parity.py
A	test/freshness-guard/run.sh
M	test/health-smoke/run.sh
A	test/o11y/fleet-compact-k8s.bat
A	test/o11y/fleet-compact.bat
A	test/o11y/fleet-supercompact-k8s.bat
A	test/o11y/o11y-aspects.py
M	test/o11y/o11y-drive.py
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/NoLocalIntegrationTest.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/TransportProvider.java
M	tp-kafka/src/main/java/pro/mir0n/esquire/tp/kafka/changes.txt
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/TransportProvider.java
M	tp-redis/src/main/java/pro/mir0n/esquire/tp/redis/changes.txt
 600 files changed, 59463 insertions(+), 3629 deletions(-)
```

---

*From `v1.2.12` till `v1.2.13`*

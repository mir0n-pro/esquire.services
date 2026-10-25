# Release Report: v2.0.0 → HEAD

**Repo:** `esquire.services/develop2`  
**Top commit:** `d362f9a`

---

## Release Notes

### doc/release_notes.txt


**v2.0.1-2610.2421**  v2.0.1 -- paacMan: the account commands go over the message bus  
&nbsp;: Feature:     paacMan, a new service: pacMan's account commands, answered from the message bus  
&nbsp;: Feature:     the gateway sends the account routes to paacMan over the bus; one setting,  
&nbsp;                 PACMAN_SERVICE, sends them to pacMan over HTTP again  
&nbsp;: Feature:     an account command that waits in the broker longer than the gateway waits is  
&nbsp;                 dropped, so it never runs after the client got its timeout  
&nbsp;: Fix:         every gateway error answers with the gateway's own error body: its type, title  
&nbsp;                 and trace id  
&nbsp;: Refactoring: the services read the caller's roles from the request, not from the token, so one  
&nbsp;                 set of commands serves HTTP and the bus  
&nbsp;: Config:      the bus settings are read under herald, as Herald 2.0.1 names them; the keep meters  
&nbsp;                 are named herald.keep.*  
&nbsp;: Config:      paacMan runs on docker and local Kubernetes, classic and compact, and on OKE in  
&nbsp;                 place of pacMan; pacMan's image is still published with every release  
&nbsp;: Feature:     hauberk: the tx load cycle adds a transfer there and back between two accounts  
&nbsp;: Feature:     hauberk: the perf scripts measure one shape with the account routes on HTTP and on  
&nbsp;                 the bus  
&nbsp;: Doc:         doc\Esquire.Messaging.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\v2.0.x.Goals.md  
&nbsp;                 doc\v2.0.x.Planning.md  
&nbsp;                 doc\Esquire.ObservabilityStack.Inventory.csv  
&nbsp;                 doc\Esquire.ObservabilityStack.Inventory.Compact.csv  
&nbsp;                 k8s-oci\README.md  
&nbsp;   Components:   paacMan (new),  
&nbsp;                 pacMan,  
&nbsp;                 gateway,  
&nbsp;                 gateWard,  
&nbsp;                 enyMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 mesnie,  
&nbsp;                 auKeep,  
&nbsp;                 bizTree,  
&nbsp;                 common,  
&nbsp;                 compose,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s-oci-compact,  
&nbsp;                 k8s-oci,  
&nbsp;                 hauberk  

---

## Code Changes

### auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the bus refs under herald  
**messaging.AuditConsumerConfig**  
&nbsp;- AUDIT_BUS_ID is built from BusConstants.CONFIG_ROOT  
**resources/application.yml**  
&nbsp;- the bus refs move under herald:  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the bus refs under herald  
**resources/application.yml**  
&nbsp;- the bus refs move under herald:  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**10/22/2026** mir0n  v2.0.1 -- one request scope for REST and the bus; the caller's roles in the request context  
messaging.IBusServer  (new)  
&nbsp;- created: the server end of a request-response bus: serve(RodEvent) answers one request, setResultHandler()  
&nbsp;   takes where the answer goes  
messaging.BusServerAdapter  (new)  
&nbsp;- created: one SERVER rod per bus key: start() points the rod's worker at the IBusServer and the server's  
&nbsp;   result handler at the same rod's transmit  
service.EsqRequestScope  (new)  
&nbsp;- created: the request work every front shares: claimsComplete(), bind() / unbind() of the EsqRequestContext  
&nbsp;   with the MDC uid, the INCOMING / OUTGOING lines (written with the caller's logger) and the esq.srv.outer /  
&nbsp;   esq.srv.inner meters  
service.PerformanceHolder  (new)  
&nbsp;- created: a ThreadLocal RequestPerformance for a worker thread with no HTTP request behind it (a bus  
&nbsp;   request): set / get / clear  
**service.EsqRequestContext**  
&nbsp;- roles (List) added: the caller's realm roles, beside uid and rootPath  
**service.RequestContextUtils**  
&nbsp;- getRoles(): the caller's roles from the bound context  
**service.MdcFilter**  
&nbsp;- the INCOMING / OUTGOING lines and the esq.srv.outer / esq.srv.inner meters go through EsqRequestScope; the  
&nbsp;   constructor takes EsqRequestScope in place of the MeterRegistry provider and the metrics switch  
**service.PerformanceAspect**  
&nbsp;- off a request thread the JPA time goes to the PerformanceHolder tracker, when one is bound  
**security.JwtClaimsExtractionFilter**  
&nbsp;- the realm roles go into the EsqRequestContext; the claim check is EsqRequestScope.claimsComplete(), and the  
&nbsp;   bind / unbind of the context and the MDC uid are EsqRequestScope.bind() / unbind()  
**error.GenericExceptionHandler**  
&nbsp;- statusOf(Throwable) / titleOf(Throwable): the one exception -> status and title mapping, for the REST  
&nbsp;   response and the bus answer; handlePermissionDeniedException removed (the 403 comes from the same mapping);  
&nbsp;   an exception that is not a GenericRuntimeException maps to 500  
**error.ProblemDetailMill**  
&nbsp;- createProblemDetail(instance, correlationId, requestId, captureAsked, status, title, msg, ex): the same  
&nbsp;   problem detail with no HTTP request behind it; the request form calls it  
**o11y.ObservabilityConfig**  
&nbsp;- the histogram switch and the trace-mark switch take the herald. meter names too (herald.keep.write.duration,  
&nbsp;   herald.keep.apply)  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the biz bus keys  
EsqConstants  
&nbsp;- BUS_KEY_BIZ ("biz-bus"); the biz bus operations BIZ_OP_ACCT / BIZ_OP_CMD / BIZ_OP_CMD_SAVE / BIZ_OP_CMD_DEL  
&nbsp;   and the command and answer keys BIZ_KEY_*; the bus-key comment names herald..messaging-bus  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the roles from the request context; the bus refs under herald  
**controller.EnyManController**  
&nbsp;- the @AuthenticationPrincipal Claims parameters and the roles read from the token removed  
**service.IEnyManService**  
&nbsp;- esquireCommandSave / esquireCommandNew / esquireCommandDelete / esquireCommandMove: the roles parameter  
&nbsp;   removed  
**service.impl.EnyManService**  
&nbsp;- esquireCommandSave / esquireCommandNew / esquireCommandDelete / esquireCommandMove: the roles parameter  
&nbsp;   removed; the admin permissions and the queued move's roles come from RequestContextUtils.getRoles()  
**service.impl.OrgService**  
&nbsp;- esquireCommandSave / esquireCommandNew / esquireCommandDelete / esquireCommandMove: the roles parameter  
&nbsp;   removed  
**service.impl.UsrService**  
&nbsp;- esquireCommandSave / esquireCommandNew / esquireCommandDelete / esquireCommandMove: the roles parameter  
&nbsp;   removed  
**service.impl.AcctService**  
&nbsp;- esquireCommandSave / esquireCommandNew / esquireCommandDelete / esquireCommandMove: the roles parameter  
&nbsp;   removed  
**queue.MoveQueueManager**  
&nbsp;- the queued move binds the item's roles into the EsqRequestContext; the org / usr move calls drop the roles  
&nbsp;   argument  
**resources/application.yml**  
&nbsp;- the bus refs move under herald:; the overlay comment names herald.messaging-bus  

### gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the account routes over the biz bus to paacMan  
GateWardApplication  
&nbsp;- the bus registrar builds the biz bus beside the entity bus; the meter owner reads the entity bus id under  
&nbsp;   BusConstants.CONFIG_ROOT  
BizTreeCacheController  
&nbsp;- the request context carries the caller's realm roles from the token  
**resources/application.yml**  
&nbsp;- esq.gateway.biz-bus (timeout-seconds, max-pending, sweep-ms) and esq.gateway.pacman (service, default  
&nbsp;   paacman; services pacman / paacman); the four pacman routes take their uri from it; the herald.biz-bus  
&nbsp;   CLIENT ref (bus-id esquire.biz, the answer and publisher pools, feed-await-ms, send-retry 1,2,5 x3);  
&nbsp;   spring.webflux.problemdetails.enabled false; the entity bus ref under herald:  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the account routes over the biz bus to paacMan  
bus.BizBusClient  (new)  
&nbsp;- created: the CLIENT end of the biz bus: send() posts a command and returns a Mono that completes with the  
&nbsp;   answer of the same correlation id; 503 when the leg is DOWN or max-pending commands wait, 409 for a  
&nbsp;   correlation id already waiting, a timeout past timeout-seconds (a sweep every sweep-ms)  
bus.BizBusRoutingFilter  (new)  
&nbsp;- created: a GlobalFilter for the routes whose uri is bus://biz-bus: builds the command from the request (the  
&nbsp;   operation from the path, kind / id / cmd from the query, the fields from the body, uid / rootPath / roles  
&nbsp;   from the token), sends it with BizBusClient and sets the status and headers from the answer; a body that is  
&nbsp;   not a JSON object, or no numeric kind, answers 400  
bus.BizBusWriteResponseFilter  (new)  
&nbsp;- created: writes the answer body BizBusRoutingFilter kept on the exchange, in the post-phase at the order of  
&nbsp;   NettyWriteResponseFilter  
GatewayApplication  
&nbsp;- MessagingBusLifecycleRegistrar, registered last: builds the biz bus at environment-prepared, starts it and  
&nbsp;   registers the bus health indicator at ready, closes it at context close  
**security.JwtClaimPeek**  
&nbsp;- peekClaims(String): all the claims of the token, unverified; empty when there is no token or it cannot be  
&nbsp;   parsed  
**error.GatewayErrorWebExceptionHandler**  
&nbsp;- a 504 status is a Network Timeout like a TimeoutException; a ResponseStatusException with a reason gives the  
&nbsp;   reason phrase as the title and the reason as the detail  
**pom.xml**  
&nbsp;- herald-messaging and herald-tp-activemq, the biz bus client  
**resources/application.yml**  
&nbsp;- esq.gateway.biz-bus (timeout-seconds, max-pending, sweep-ms) and esq.gateway.pacman (service, default  
&nbsp;   paacman; services pacman / paacman); the four pacman routes take their uri from it; the herald.biz-bus  
&nbsp;   CLIENT ref (bus-id esquire.biz, the answer and publisher pools, feed-await-ms, send-retry 1,2,5 x3);  
&nbsp;   spring.webflux.problemdetails.enabled false; the topology import; the org.springframework.jms /  
&nbsp;   org.apache.activemq / msg log levels and msg.log-path  
**resources/logback-spring.xml**  
&nbsp;- the msg logger with its MSG_FILE / MSG_CONSOLE appenders; the org.springframework.jms and  
&nbsp;   org.apache.activemq levels  

### hauberk/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the tx cycle with transfers; the REST / bus pair  
**simulations.LoadScenarios**  
&nbsp;- the tx scenario on two random accounts: deposit A, transfer A->B, transfer B->A, withdraw A (net-zero; the  
&nbsp;   transfers skipped when A and B are the same)  
**simulations.TxLoadSimulation**  
&nbsp;- the javadoc and @SimulationInfo name the new tx cycle  
**perf-matrix.ps1**  
&nbsp;- -GatewayTag: the classic gateway image for both cells; the k8s-x2-OFF-BUS cell (classic, the account routes  
&nbsp;   on the biz bus); the gateway upgraded before the o11y arm; $SVCS points at services2  
**perf-pairs.ps1**  
&nbsp;- -Shapes: the two cells of a pair, in run order; -GatewayTag passed to perf-matrix  
**oke-perf-matrix.ps1**  
&nbsp;- $SVCS points at services2  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the bus refs under herald  
**identity.KcIdentityGateway**  
&nbsp;- the trace comment names herald.keep.apply and herald.keep.write.*  
**resources/application.yml**  
&nbsp;- the bus refs move under herald:  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the roles from the request context; the bus refs under herald  
**controller.KeySmithController**  
&nbsp;- the @AuthenticationPrincipal Claims parameters and the roles read from the token removed  
**service.IKeySmithService**  
&nbsp;- esquireKeySave: the roles parameter removed  
**service.impl.KeySmithService**  
&nbsp;- esquireKeySave / saveAndSync / saveAccess: the roles parameter removed; the admin permissions come from  
&nbsp;   RequestContextUtils.getRoles()  
**resources/application.yml**  
&nbsp;- the bus refs move under herald:; the overlay comment names herald.messaging-bus  

### mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt


**10/22/2026** mir0n  v2.0.1 -- the bus refs under herald  
MesnieApplication  
&nbsp;- the meter owner reads the entity bus id under BusConstants.CONFIG_ROOT  
**resources/application.yml**  
&nbsp;- the bus refs move under herald:; the overlay comment names herald.messaging-bus  

### paacMan/src/main/java/pro/mir0n/esquire/paacMan/changes.txt

Esquire paacMan Microservice  

**10/22/2026** mir0n  v2.0.1 -- paacMan: pacMan's commands served over the biz bus  
paacMan  (new project, esquire-paac-man)  
&nbsp;- pacMan's code, from its classes jar, with a bus face and no REST face  
PaacManApplication  (new)  
&nbsp;- created: the paacMan process: pacMan's code without PacManController and PacManApplication in the scan, the  
&nbsp;   bizBus BusServerAdapter on BizRequestHandler, and the buses entity, audit and biz  
messaging.BizRequestHandler  (new)  
&nbsp;- created: serves one biz request: the caller's claims from the message into the request context  
&nbsp;   (EsqRequestScope), the JPA timing, the INCOMING / OUTGOING lines and the esq.srv.* meters, then the  
&nbsp;   PacManCommands operation the request names (esq-acct, esq-cmd, esq-cmd-save, esq-cmd-del); the answer is URS  
&nbsp;   on a 2xx, URR with the problem detail otherwise  
pom.xml  (new)  
&nbsp;- esquire-pac-man (classifier classes), esquire-common, esquire-audit, herald-messaging and the herald-tp-*  
&nbsp;   transports  
resources/application.yml  (new)  
&nbsp;- pacMan's settings, and the herald.biz-bus SERVER ref (bus-id esquire.biz) beside the entity and audit refs  
resources/logback-spring.xml  (new)  
&nbsp;- pacMan's logging, the develop and msg logs at logs/paacMan-*.log  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**10/22/2026** mir0n  v2.0.1 -- one set of commands for REST and the biz bus; the roles from the request context  
service.PacManCommands  (new)  
&nbsp;- created: the pacMan commands for every front: esquireCommandSave / esquireCommandDelete / esquireCommandAcct  
&nbsp;   / esquireCommand over IPacManService and AcctTransactionService, with the develop log lines; the caller is  
&nbsp;   read from the request context  
**controller.PacManController**  
&nbsp;- calls PacManCommands; the @AuthenticationPrincipal Claims parameters and the roles read from the token  
&nbsp;   removed  
**service.IPacManService**  
&nbsp;- esquireCommandSave / esquireCommandDelete: the roles parameter removed  
**service.impl.PacManService**  
&nbsp;- esquireCommandSave / esquireCommandDelete: the roles parameter removed; the admin permissions come from  
&nbsp;   RequestContextUtils.getRoles()  
**acct.IAcctTransactionProcessor**  
&nbsp;- esquireCommandAcct: the roles parameter removed  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- esquireCommandAcct / validatePermissions: the roles parameter removed; the admin permissions come from  
&nbsp;   RequestContextUtils.getRoles()  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- esquireCommandAcct / esquireCommandTransfer: the roles parameter removed  
**acct.service.AcctTransactionService**  
&nbsp;- esquireCommandAcct: the roles parameter removed  
PacManApplication  
&nbsp;- MessagingBusLifecycleRegistrar.busKeys(): the buses the process builds (entity, audit), protected so a  
&nbsp;   service built on pacMan adds its own  
**pom.xml**  
&nbsp;- a second artifact, classifier classes: the plain classes jar without application.yml and logback-spring.xml,  
&nbsp;   for paacMan to build on  
**resources/application.yml**  
&nbsp;- the bus refs move under herald:; the overlay comment names herald.messaging-bus  

---

## Commits

```

-- 2026-10-24 | commit: d362f9a | mir0n.the.programmer | v2.0.1 Finalization --
M	Releases.md
M	doc/Esquire.Messaging.md
M	doc/Esquire.TestingStack.md
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v2.0.x.Planning.md
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/paacman.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci/README.md
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/paacman.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
 25 files changed, 141 insertions(+), 55 deletions(-)

-- 2026-10-23 | commit: 40db6f8 | mir0n.the.programmer | Update deploy-oke.sh --
M	.github/scripts/deploy-oke.sh
 1 file changed, 6 insertions(+), 1 deletion(-)


-- 2026-10-23 | commit: 100973f | mir0n.the.programmer | v2.0.1 Finalization --
M	README.md
M	Releases.md
 2 files changed, 29 insertions(+), 23 deletions(-)

-- 2026-10-23 | commit: 26368c2 | mir0n.the.programmer | v2.0.1 paacMan: the account commands go over the message bus --
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-oke.yml
M	README.md
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/application.yml
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailMill.java
A	common/src/main/java/pro/mir0n/esquire/backend/messaging/BusServerAdapter.java
A	common/src/main/java/pro/mir0n/esquire/backend/messaging/IBusServer.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestScope.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/EsqRequestScopeTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/RequestContextUtilsTest.java
M	compose-compact/compose-rebuild.bat
M	compose-compact/compose.yaml
M	compose-compact/o11y-log-off.bat
M	compose-compact/o11y-log-on.bat
M	compose-compact/o11y-off.bat
M	compose-compact/o11y-on.bat
M	compose-compact/o11y/grafana/gen-dashboard.py
M	compose-compact/o11y/grafana/gen-topology.py
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose-compact/o11y/prometheus.yml
M	compose-compact/topology/esquire-topology.yml
M	compose/compose-rebuild.bat
M	compose/compose.yaml
M	compose/o11y-log-off.bat
M	compose/o11y-log-on.bat
M	compose/o11y-off.bat
M	compose/o11y-on.bat
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/o11y/prometheus.yml
M	compose/topology/esquire-topology.yml
M	doc/Esquire.ObservabilityStack.Inventory.Compact.csv
M	doc/Esquire.ObservabilityStack.Inventory.csv
A	doc/logo/herald.3.svg
A	doc/logo/herald.5.png
A	doc/logo/herald.5.svg
A	doc/logo/kinesis.svg
M	doc/media/ComponentModel.Compact.png
A	doc/media/ComponentModel.Compact.svg
M	doc/media/ComponentModel.png
A	doc/media/ComponentModel.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v2.0.x.Goals.md
M	doc/v2.0.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DictionaryCompletionConcurrencyTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/MoveDestinationGuardTest.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusWriteResponseFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/GatewayErrorWebExceptionHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwtClaimPeek.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusClientTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilterTest.java
M	hauberk/changes.txt
M	hauberk/oke-perf-matrix.ps1
M	hauberk/perf-matrix.ps1
M	hauberk/perf-pairs.ps1
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/LoadScenarios.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/TxLoadSimulation.java
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
A	k8s-compact/charts/esquire-paacman/Chart.yaml
A	k8s-compact/charts/esquire-paacman/templates/configmap.yaml
A	k8s-compact/charts/esquire-paacman/templates/deployment.yaml
A	k8s-compact/charts/esquire-paacman/templates/secret.yaml
A	k8s-compact/charts/esquire-paacman/templates/service.yaml
A	k8s-compact/charts/esquire-paacman/values.yaml
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-compact/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
M	k8s-compact/k8s-down.bat
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/k8s-up.bat
M	k8s-compact/o11y-full-on.bat
M	k8s-compact/o11y-log-off.bat
M	k8s-compact/o11y-log-on.bat
M	k8s-compact/o11y-off.bat
M	k8s-compact/o11y-on.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
A	k8s-compact/values/paacman.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/esquire-topology.yml
M	k8s-oci-compact/ghcr-push.bat
M	k8s-oci-compact/grafana/esquire-logging.json
M	k8s-oci-compact/grafana/esquire-services.json
M	k8s-oci-compact/grafana/esquire-topology.json
M	k8s-oci-compact/oke-down.bat
M	k8s-oci-compact/oke-o11y-off.bat
M	k8s-oci-compact/oke-o11y-on.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci-compact/values/backend.yaml
M	k8s-oci-compact/values/gateward.yaml
A	k8s-oci-compact/values/paacman.yaml
M	k8s-oci/esquire-topology.yml
M	k8s-oci/ghcr-push.bat
M	k8s-oci/grafana/esquire-logging.json
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-down.bat
M	k8s-oci/oke-o11y-off.bat
M	k8s-oci/oke-o11y-on.bat
M	k8s-oci/oke-o11y-test.bat
M	k8s-oci/oke-o11y-verify.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/publish.bat
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/gateway.yaml
A	k8s-oci/values/paacman.yaml
A	k8s-oci/values/redis.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/values.yaml
A	k8s/charts/esquire-paacman/Chart.yaml
A	k8s/charts/esquire-paacman/templates/configmap.yaml
A	k8s/charts/esquire-paacman/templates/deployment.yaml
A	k8s/charts/esquire-paacman/templates/secret.yaml
A	k8s/charts/esquire-paacman/templates/service.yaml
A	k8s/charts/esquire-paacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/prometheus/templates/configmap.yaml
M	k8s/charts/infra/redis/templates/deployment.yaml
M	k8s/charts/infra/redis/values.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
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
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
A	k8s/values/paacman.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/test/java/pro/mir0n/esquire/mesnie/MesnieMeterOwnerTest.java
A	paacMan/Dockerfile
A	paacMan/Dockerfile.lx
A	paacMan/Dockerfile.win
A	paacMan/compose.yaml
A	paacMan/docker-compose-build.bat
A	paacMan/pom.xml
A	paacMan/src/main/java/pro/mir0n/esquire/paacMan/PaacManApplication.java
A	paacMan/src/main/java/pro/mir0n/esquire/paacMan/changes.txt
A	paacMan/src/main/java/pro/mir0n/esquire/paacMan/messaging/BizRequestHandler.java
A	paacMan/src/main/resources/application.yml
A	paacMan/src/main/resources/logback-spring.xml
A	paacMan/src/test/java/pro/mir0n/esquire/paacMan/messaging/BizRequestHandlerTest.java
A	paacMan/src/test/resources/logback-test.xml
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/PacManCommands.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/o11y/fleet-supercompact-k8s.bat
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
 249 files changed, 48778 insertions(+), 1642 deletions(-)
```

---

## Files Modified

```
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/application.yml
M	auKeep/src/test/java/pro/mir0n/esquire/auKeep/RodBusIntegrationTest.java
M	audit/src/test/java/pro/mir0n/esquire/audit/AuditBusBridgeTest.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailMill.java
A	common/src/main/java/pro/mir0n/esquire/backend/messaging/BusServerAdapter.java
A	common/src/main/java/pro/mir0n/esquire/backend/messaging/IBusServer.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestContext.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestScope.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/EsqRequestScopeTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/RequestContextUtilsTest.java
M	compose-compact/compose-rebuild.bat
M	compose-compact/compose.yaml
M	compose-compact/o11y-log-off.bat
M	compose-compact/o11y-log-on.bat
M	compose-compact/o11y-off.bat
M	compose-compact/o11y-on.bat
M	compose-compact/o11y/grafana/gen-dashboard.py
M	compose-compact/o11y/grafana/gen-topology.py
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose-compact/o11y/prometheus.yml
M	compose-compact/topology/esquire-topology.yml
M	compose/compose-rebuild.bat
M	compose/compose.yaml
M	compose/o11y-log-off.bat
M	compose/o11y-log-on.bat
M	compose/o11y-off.bat
M	compose/o11y-on.bat
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/o11y/prometheus.yml
M	compose/topology/esquire-topology.yml
M	doc/Esquire.Messaging.md
M	doc/Esquire.ObservabilityStack.Inventory.Compact.csv
M	doc/Esquire.ObservabilityStack.Inventory.csv
M	doc/Esquire.TestingStack.md
A	doc/logo/herald.3.svg
A	doc/logo/herald.5.png
A	doc/logo/herald.5.svg
A	doc/logo/kinesis.svg
M	doc/media/ComponentModel.Compact.png
A	doc/media/ComponentModel.Compact.svg
M	doc/media/ComponentModel.png
A	doc/media/ComponentModel.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v2.0.x.Goals.md
M	doc/v2.0.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DenialStatusRuleTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/DictionaryCompletionConcurrencyTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/MoveDestinationGuardTest.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusWriteResponseFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/GatewayErrorWebExceptionHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwtClaimPeek.java
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusClientTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilterTest.java
M	hauberk/changes.txt
M	hauberk/oke-perf-matrix.ps1
M	hauberk/perf-matrix.ps1
M	hauberk/perf-pairs.ps1
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/LoadScenarios.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/TxLoadSimulation.java
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
A	k8s-compact/charts/esquire-paacman/Chart.yaml
A	k8s-compact/charts/esquire-paacman/templates/configmap.yaml
A	k8s-compact/charts/esquire-paacman/templates/deployment.yaml
A	k8s-compact/charts/esquire-paacman/templates/secret.yaml
A	k8s-compact/charts/esquire-paacman/templates/service.yaml
A	k8s-compact/charts/esquire-paacman/values.yaml
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-compact/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s-compact/charts/infra/prometheus/templates/configmap.yaml
M	k8s-compact/k8s-down.bat
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/k8s-up.bat
M	k8s-compact/o11y-full-on.bat
M	k8s-compact/o11y-log-off.bat
M	k8s-compact/o11y-log-on.bat
M	k8s-compact/o11y-off.bat
M	k8s-compact/o11y-on.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
A	k8s-compact/values/paacman.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/esquire-topology.yml
M	k8s-oci-compact/ghcr-push.bat
M	k8s-oci-compact/grafana/esquire-logging.json
M	k8s-oci-compact/grafana/esquire-services.json
M	k8s-oci-compact/grafana/esquire-topology.json
M	k8s-oci-compact/oke-down.bat
M	k8s-oci-compact/oke-o11y-off.bat
M	k8s-oci-compact/oke-o11y-on.bat
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci-compact/values/backend.yaml
M	k8s-oci-compact/values/gateward.yaml
A	k8s-oci-compact/values/paacman.yaml
M	k8s-oci/README.md
M	k8s-oci/esquire-topology.yml
M	k8s-oci/ghcr-push.bat
M	k8s-oci/grafana/esquire-logging.json
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-down.bat
M	k8s-oci/oke-o11y-off.bat
M	k8s-oci/oke-o11y-on.bat
M	k8s-oci/oke-o11y-test.bat
M	k8s-oci/oke-o11y-verify.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/publish.bat
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/gateway.yaml
A	k8s-oci/values/paacman.yaml
A	k8s-oci/values/redis.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/values.yaml
A	k8s/charts/esquire-paacman/Chart.yaml
A	k8s/charts/esquire-paacman/templates/configmap.yaml
A	k8s/charts/esquire-paacman/templates/deployment.yaml
A	k8s/charts/esquire-paacman/templates/secret.yaml
A	k8s/charts/esquire-paacman/templates/service.yaml
A	k8s/charts/esquire-paacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/prometheus/templates/configmap.yaml
M	k8s/charts/infra/redis/templates/deployment.yaml
M	k8s/charts/infra/redis/values.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
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
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
A	k8s/values/paacman.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	mesnie/src/test/java/pro/mir0n/esquire/mesnie/MesnieMeterOwnerTest.java
A	paacMan/Dockerfile
A	paacMan/Dockerfile.lx
A	paacMan/Dockerfile.win
A	paacMan/compose.yaml
A	paacMan/docker-compose-build.bat
A	paacMan/pom.xml
A	paacMan/src/main/java/pro/mir0n/esquire/paacMan/PaacManApplication.java
A	paacMan/src/main/java/pro/mir0n/esquire/paacMan/changes.txt
A	paacMan/src/main/java/pro/mir0n/esquire/paacMan/messaging/BizRequestHandler.java
A	paacMan/src/main/resources/application.yml
A	paacMan/src/main/resources/logback-spring.xml
A	paacMan/src/test/java/pro/mir0n/esquire/paacMan/messaging/BizRequestHandlerTest.java
A	paacMan/src/test/resources/logback-test.xml
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/PacManCommands.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
M	test/o11y/fleet-supercompact-k8s.bat
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
 253 files changed, 48919 insertions(+), 1686 deletions(-)
```

---

*From `v2.0.0` till `HEAD`*

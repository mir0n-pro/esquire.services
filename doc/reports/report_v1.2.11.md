# Release Report: v1.2.10 → v1.2.11

**Repo:** `esquire.services/develop`  
**Top commit:** `3effe6a`

---

## Release Notes

### doc/release_notes.txt


**v1.2.11-2607.2714**  v1.2.11 -- Finalization  
&nbsp;: Fix:         the on-demand cloud observability routine now completes in both directions -- switching  
&nbsp;                 the full stack on also enables the message-broker and identity metrics, and switching it  
&nbsp;                 off again also removes the viewing tools  
&nbsp;   Components:   k8s  

**v1.2.11-2607.2614**  v1.2.11 -- T13: sprint documentation finalization  
&nbsp;: Fix:         the paper-client-account entity-kind title corrected to "Paper Client Account" (and its description)  
&nbsp;: Doc:         doc\Esquire.Auth.md  (new -- authentication and authorization: the token and its claims  
&nbsp;                 and roles, the personal self-service flag, user creation, two-factor, entity synchronization)  
&nbsp;                 doc\Esquire.Auth.TokenPatterns.md  (new)  
&nbsp;                 doc\Esquire.Auth.keySmithRoutine.md  (new -- was keySmithCredentialRoutine.md)  
&nbsp;                 doc\install\Docker.md  (new -- step-by-step install and run, Docker sandbox)  
&nbsp;                 doc\install\LocalK8s.md  (new -- step-by-step install and run, local Kubernetes)  
&nbsp;                 doc\v1.2.x.Goal.md  (new -- the goal, and the fifteen-factor scorecard)  
&nbsp;                 doc\Esquire.GrafanaGuide.md  (new)  
&nbsp;                 doc\Esquire.ObservabilityStack.Logging.md  (new -- was Logging.md)  
&nbsp;                 Releases.md  (new -- the release history)  
&nbsp;                 README.md  (installation section, refreshed component model and observability stack,  
&nbsp;                 release history, documentation links)  
&nbsp;                 doc\Esquire.Vision.md  (the mature-framework value proposition)  
&nbsp;                 doc\Esquire.ObservabilityStack.md  
&nbsp;                 doc\entity.path.semantics.md  (removed -- folded into Esquire.Auth.md)  
&nbsp;   Components:   common,  
&nbsp;                 doc  

**v1.2.11-2607.2320**  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
&nbsp;- while a branch is being moved, path changes are applied to parent entities before their  
&nbsp;     children, so the tree the cache serves can never briefly show a child ahead of its parent  
&nbsp;- a new account's parent is looked up only within the signed-in tenant's own branch  
&nbsp;- an entity created at the very end of a move is now repaired onto the moved path, closing a  
&nbsp;     brief window where it could keep the old one  
&nbsp;- the entity-id counter can no longer wrap into a negative value and corrupt an id after very  
&nbsp;     high volume  
&nbsp;- money amounts and balances are rounded to the ledger's scale before any check or storage, so  
&nbsp;     tiny floating-point remainders never reach the books  
&nbsp;- when a service's outgoing message queue is full it now waits rather than dropping an identity  
&nbsp;     sync or an entity change  
&nbsp;- on the cloud cluster a message-broker restart now reconnects on its own, instead of a brief  
&nbsp;     broker outage turning into a lasting service outage  
&nbsp;- the monitoring and health endpoints are served on a separate internal-only port, unreachable  
&nbsp;     from the internet  
&nbsp;- more log lines now carry the request's own id -- the first line of each request, and the  
&nbsp;     automatic path-repair message keeps the id of the create it repairs  
&nbsp;- the monitoring dashboard's "Service" list shows the services again, and its all-targets count  
&nbsp;     is relabeled so it is not misread as counting only the Esquire services  
&nbsp;- the cloud monitoring deploy no longer points the message broker at an image that was never  
&nbsp;     published, which had blocked the rollout  
&nbsp;   Components:   gateway,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 bizTree,  
&nbsp;                 common,  
&nbsp;                 messaging,  
&nbsp;                 auKeep,  
&nbsp;                 kcMaster,  
&nbsp;                 keySmith,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.11-2607.1910**  v1.2.11 -- T12: on-demand monitoring on the OKE cloud cluster  
&nbsp;: Feature:     monitoring can be switched on for the OKE cloud cluster on demand -- logs only, or the full  
&nbsp;                 logs + traces + metrics stack -- and switched back off again  
&nbsp;: Config:      the monitoring stack (its collectors and dashboards) runs on its own dedicated cluster nodes,  
&nbsp;                 apart from the services, so bringing it up or down does not compete with a running service  
&nbsp;: Config:      the OKE services log at ERROR by default -- quiet until monitoring is switched on  
&nbsp;   Components:   gateway,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster  

**v1.2.11-2607.1714**  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
&nbsp;- a business measurement with a missing label could throw and hide the real error  
&nbsp;- a mislabeled measurement could flood the metrics store with unbounded labels  
&nbsp;- the trace-id format was defined in two places and could drift  
&nbsp;- there were no ready-made alerts for the obvious failures: a service gone dark, a dropped  
&nbsp;     message, a tripped safety switch, a database with no spare connections  
&nbsp;- tracing and metrics could not be switched on or off independently of one another  
&nbsp;- the message-queue metrics were still listed as planned although they ship and are on the dashboards  
&nbsp;   Components:   common,  
&nbsp;                 gateway,  
&nbsp;                 enyMan,  
&nbsp;                 bizTree,  
&nbsp;                 pacMan,  
&nbsp;                 kcMaster,  
&nbsp;                 keySmith,  
&nbsp;                 auKeep,  
&nbsp;                 messaging,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.11-2607.1513**  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
&nbsp;- gateway liveness and readiness were not separated from the aggregate health check  
&nbsp;- messages handled off the bus were not correlated in the logs  
&nbsp;- a dashboard panel read "No data" for a counter that had not yet fired  
&nbsp;- a configuration change did not restart the observability tools that depend on it  
&nbsp;- the log collector was ingesting its own stack's logs  
&nbsp;- the browser tier stood outside the single observability switch  
&nbsp;- heartbeat tracing differed between environments  
&nbsp;- the observability tools ran without a fixed history window or resource limits  
&nbsp;- the metrics store used an unnamed data volume  
&nbsp;   Components:   gateway,  
&nbsp;                 common,  
&nbsp;                 messaging,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 kcMaster,  
&nbsp;                 keySmith,  
&nbsp;                 auKeep,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.11-2607.1413**  v1.2.11 -- circuit breaker: its parameters set up and verified under a heavy load test  
&nbsp;: Fix:         under a heavy burst the gateway was refusing requests it could have served. A limit nobody had  
&nbsp;                 set -- 25 calls at once, a default that came with the library -- was rejecting the overflow, and  
&nbsp;                 those rejections were then counted against the service as if IT had failed, so the gateway  
&nbsp;                 stopped calling a service that was perfectly healthy. The limit is now set deliberately, from  
&nbsp;                 the size of the connection pool it protects, and a request turned away for arriving in a crowd  
&nbsp;                 is no longer held against the service it was going to  
&nbsp;: Fix:         a fresh install could not build its database. The setup script inside the Postgres image had  
&nbsp;                 Windows line endings, so the container could not run it and stopped at start-up. It went  
&nbsp;                 unnoticed for months because that script only runs on an EMPTY database -- which is exactly what  
&nbsp;                 a new machine, or anyone rebuilding from scratch, has  
&nbsp;: Feature:     a message that must not be lost can now wait instead of being thrown away: when the outgoing  
&nbsp;                 queue is full the sender holds until there is room, rather than discarding the message after ten  
&nbsp;                 seconds. Switched on for the login-server sync, where every change has to arrive eventually and  
&nbsp;                 how long it takes does not matter  
&nbsp;: Feature:     each message queue is now given its own share of the broker's memory, so one queue that falls  
&nbsp;                 behind can no longer starve the others  
&nbsp;: Feature:     two new views on the dashboard: what the gateway's safety switches are doing (and which service  
&nbsp;                 they are turning away), and how many of the machine's processor cores the system is actually  
&nbsp;                 using -- including what each service THINKS it has been given, which is not always what the  
&nbsp;                 machine has  
&nbsp;: Doc:         doc/review/Esquire.PerfMatrix-07-14.md -- the performance matrix: every environment measured on  
&nbsp;                 the same hardware, what a second copy of each service buys, and what watching the system costs it  
&nbsp;   Components:   gateway,  
&nbsp;                 messaging,  
&nbsp;                 mir0n-utils,  
&nbsp;                 keySmith,  
&nbsp;                 activemq,  
&nbsp;                 postgres  

**v1.2.11-2607.1222**  v1.2.11 -- observability: the message broker reports, the three signals link up, and the system draws itself  
&nbsp;: Feature:     the message broker now reports on itself -- what is waiting in each queue, whether anyone is  
&nbsp;                 listening to it, how full its memory is, and how many clients are connected  
&nbsp;: Feature:     a reading on a chart, a request trace, and a log line are now linked to one another: from a slow  
&nbsp;                 point on a graph you reach the request that caused it, from that request its log lines, and from  
&nbsp;                 a log line back to the request  
&nbsp;: Feature:     a new picture of the running system -- every component, the three message buses, and the calls  
&nbsp;                 between them, each box carrying its own live readings and turning red when it stops reporting  
&nbsp;: Feature:     on the cluster the picture shows each parallel copy of a service as its own box with its own  
&nbsp;                 readings, and warns when one copy sits idle while its twin does all the work  
&nbsp;: Feature:     a new logging view: how much each service is saying, what it is complaining about, and every log  
&nbsp;                 line of a single request across every service it touched  
&nbsp;: Fix:         switching the detailed timing breakdowns on is what makes the chart-to-trace link work at all;  
&nbsp;                 they were being switched on by an environment variable someone had typed by hand, so the link  
&nbsp;                 worked on the developer machine and silently did not exist on the cluster  
&nbsp;: Fix:         messages were being sent in the mode that makes the sender wait for the broker to confirm every  
&nbsp;                 one of them, although the broker keeps nothing on disk and there was nothing to confirm; sending  
&nbsp;                 the same 2000 messages now takes 27ms instead of 233ms  
&nbsp;: Fix:         the gateway is the first thing every request meets, and it was the one service whose log lines  
&nbsp;                 could not be found when tracing that request  
&nbsp;: Config:      the broker's reporting rides the same observability switch as everything else, and is off unless  
&nbsp;                 it is turned on  
&nbsp;   Components:   gateway,  
&nbsp;                 tp-activemq,  
&nbsp;                 activemq,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.11-2607.1120**  v1.2.11 -- observability: what each service DOES, not just how it runs  
&nbsp;: Feature:     every service now reports on its own work, not only its health -- entities created, moved and  
&nbsp;                 deleted, money moved, identities synced with the login server, tree updates applied, audit  
&nbsp;                 records written, and permission decisions allowed or refused  
&nbsp;: Feature:     the login gateway reports how often it can answer a request from its own token cache instead of  
&nbsp;                 going to the login server, and how long that server takes when it has to  
&nbsp;: Feature:     work that finishes after the caller has been answered -- a queued move, an audit write -- now  
&nbsp;                 reports whether it actually succeeded; until now nothing did  
&nbsp;: Feature:     four new dashboard rows carry all of it, arranged so the line worth looking at is always the one  
&nbsp;                 that is not "ok"  
&nbsp;: Fix:         the detailed timing breakdowns were being collected for every web request whether or not anyone  
&nbsp;                 had asked for them, at a cost of a fifth of everything the system records; they are now  
&nbsp;                 collected only when switched on, and the ordinary averages still work without them  
&nbsp;: Fix:         several dashboard readings were wrong in a way that looked plausible -- one showed a tenth of a  
&nbsp;                 millisecond for an operation that really took a hundred and thirty, and a cache that was working  
&nbsp;                 99% of the time was drawn as 42%  
&nbsp;: Fix:         readings that mix different units (a percentage and a duration, a queue length and a rate) no  
&nbsp;                 longer share one scale, where the larger number flattened the smaller one out of sight  
&nbsp;: Config:      the new per-service reporting has its own switch under the observability switch, on by default;  
&nbsp;                 turning it off removes it entirely at no cost to anything else  
&nbsp;   Components:   common,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 bizTree,  
&nbsp;                 dataKeep,  
&nbsp;                 kcMaster,  
&nbsp;                 gateway,  
&nbsp;                 auKeep,  
&nbsp;                 keySmith,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.11-2607.1115**  v1.2.11 -- observability: four ways of getting the measurements wrong are now impossible  
&nbsp;: Fix:         a measurement that reads a live value (queue depth, messages held) could stop reporting after a  
&nbsp;                 while and show nothing at all; it now keeps reporting for as long as the service runs  
&nbsp;: Fix:         the request-time breakdown could drop one of its four parts entirely -- showing nothing rather  
&nbsp;                 than zero -- whenever that part had no traffic yet; each part now reads zero and the four  
&nbsp;                 still add up to the total  
&nbsp;: Fix:         the database part of the request-time breakdown was described as needing the load-test switch;  
&nbsp;                 it is measured on every request while observability is on, and the dashboard now says so  
&nbsp;: Refactoring: a live-value measurement can now only be created one way, and the build fails if it is created  
&nbsp;                 any other way  
&nbsp;: Refactoring: the timing of database work no longer relies on catching an error to find out whether it is  
&nbsp;                 running inside a request; it asks directly  
&nbsp;: Config:      new scripts to reach the local cluster's observability tools from the desktop, on ports that  
&nbsp;                 cannot be confused with the ones the docker sandbox already uses  
&nbsp;   Components:   common, compose, k8s  

**v1.2.11-2607.1111**  v1.2.11 -- observability: the messaging bus, the time breakdown and the traffic volume are measured  
&nbsp;: Feature:     the messaging bus now reports its own measurements -- messages sent and received, how long a  
&nbsp;                 send takes, send errors, how deep the outgoing queue is, and how many messages are held or  
&nbsp;                 dropped by the send retry  
&nbsp;: Feature:     the time a request takes is now broken down into the four stretches that make it up -- the  
&nbsp;                 entry point, the gateway's own work, the service's own work, and the database -- per route, so  
&nbsp;                 a slow stretch is visible instead of a single total  
&nbsp;: Feature:     the traffic volume in and out of each service, and at the entry point, is measured  
&nbsp;: Feature:     the dashboard gains rows for the messaging bus, the time breakdown and the traffic volume  
&nbsp;: Config:      all observability settings now sit under one name, esquire.observability -- the request-timeline  
&nbsp;                 settings moved under it, and the new measurement settings joined them there  
&nbsp;: Config:      two settings under the one observability switch: the fine-grained timing detail is off by  
&nbsp;                 default (it is what costs), the traffic-volume counters are on  
&nbsp;: Refactoring: the bus hook that carries request timelines across the bus now carries measurements as well --  
&nbsp;                 one bus observer instead of two: RodTracerHolder and EsqRodTracer are renamed RodObserverHolder  
&nbsp;                 and EsqRodObserver  
&nbsp;   Components:   common,  
&nbsp;                 messaging,  
&nbsp;                 gateway,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 auKeep,  
&nbsp;                 compose,  
&nbsp;                 k8s  

**v1.2.11-2607.1019**  v1.2.11 -- observability: live measurements for every service on one dashboard  
&nbsp;: Feature:     every service now publishes its own live measurements -- how many requests it handles and how  
&nbsp;                 long they take, its memory and thread use, and how busy its database connections are -- gathered  
&nbsp;                 onto a single metrics dashboard  
&nbsp;: Feature:     the login gateway, the identity server, and the database each report their own measurements to  
&nbsp;                 the same dashboard, so the whole running stack is visible in one place  
&nbsp;: Feature:     the audit writer's own database connections are measured too, not only the services' shared pools  
&nbsp;: Config:      measurements are off by default and turn on with the one switch per service that also turns on  
&nbsp;                 request timelines -- no cost until asked for  
&nbsp;: Config:      the local sandbox now runs its database as a container (matching the cluster) with room for more  
&nbsp;                 connections  
&nbsp;   Components:   common, bizTree, enyMan, pacMan, keySmith, kcMaster, gateway, auKeep, dataKeep, compose, k8s  

**v1.2.11-2607.0922**  v1.2.11 -- observability: request timelines cross the messaging bus  
&nbsp;: Feature:     a request's timeline now continues across the messaging bus -- the service that sends a  
&nbsp;                 message and the service that receives it appear as one timeline, drawn as a send-and-receive  
&nbsp;                 pair  
&nbsp;: Feature:     work handed to a background worker stays on the same timeline as the request that queued it --  
&nbsp;                 the queued move and the tree-cache update included  
&nbsp;: Feature:     every request carries one id, whether or not the caller supplied one, and that id names both  
&nbsp;                 the request's timeline and its log lines  
&nbsp;: Feature:     every recorded step is labelled with the copy of the service that ran it  
&nbsp;: Feature:     the request/response bus can have its liveness check timed end to end -- the probe and the  
&nbsp;                 reply it draws form one timeline, so the bus itself can be watched  
&nbsp;: Feature:     the KeyCloak path update is recorded as its own step  
&nbsp;: Config:      the liveness-check timing added to every service, off by default; switched on for the docker  
&nbsp;                 stack  
&nbsp;: Refactoring: the shared base library (queue rig, worker pool, taijitu, host identity) moved into its own  
&nbsp;                 module, mir0n-utils; the messaging bus now builds on it alone and carries nothing Esquire  
&nbsp;   Components:   mir0n-utils,  
&nbsp;                 messaging,  
&nbsp;                 common,  
&nbsp;                 gateway,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 auKeep  

**v1.2.11-2607.0813**  v1.2.11 -- observability: request tracing across the backend services  
&nbsp;- each service now records the steps a request takes through it and how long each one lasts, so a  
&nbsp;     single request can be followed from the gateway through every service it reaches -- drawn as one  
&nbsp;     timeline  
&nbsp;- the recorded steps: read, save, create, delete, move, account transaction, sign-in account  
&nbsp;     changes, audit write  
&nbsp;- one id names a request's timeline and tags its log lines, so the log viewer and the timeline  
&nbsp;     point at the same request  
&nbsp;- the timeline stays clean: the security framework's own internal steps are left out, and the  
&nbsp;     platform's every-few-seconds health checks make no timeline at all  
&nbsp;- it is opt-in and off by default, switched on together with the log-viewing stack  
&nbsp;: Feature:     request tracing across the gateway and every backend service, drawn as one timeline  
&nbsp;: Feature:     the gateway settles one id per request that names both its timeline and its log lines  
&nbsp;: Config:      the trace collector and store added to the docker stack and the local Kubernetes  
&nbsp;                 charts, both opt-in and off by default  
&nbsp;: Config:      tracing settings added to every service and to every service chart, off by default  
&nbsp;: Fix:         the aspect weaver now ships with the common library, so every service records its  
&nbsp;                 steps from the first start  
&nbsp;   Components:   common,  
&nbsp;                 gateway,  
&nbsp;                 biztree,  
&nbsp;                 enyMan,  
&nbsp;                 pacMan,  
&nbsp;                 keySmith,  
&nbsp;                 kcMaster,  
&nbsp;                 auKeep,  
&nbsp;                 dataKeep,  
&nbsp;                 k8s,  
&nbsp;                 compose  

**v1.2.11-2607.0701**  v1.2.11 -- observability: search every service's logs in one place  
&nbsp;- a ready-to-run log-viewing stack (a log viewer over a log store, fed by a log collector) can now be  
&nbsp;     started alongside the local docker stack and the local Kubernetes cluster  
&nbsp;- each service's logs are gathered automatically and searched from one screen; a single request can be  
&nbsp;     followed across services by its request / correlation id  
&nbsp;- it is opt-in and off by default -- it does not start with the everyday stack (a docker "o11y" profile,  
&nbsp;     and a separate start script on Kubernetes) -- so the normal stack stays light  
&nbsp;: Feature:     one screen to search all the services' logs, with per-request follow-through  
&nbsp;: Config:      the log-viewing stack added to the docker compose stack and as local Kubernetes charts,  
&nbsp;                 both opt-in and off by default  

---

## Code Changes

### auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**resources/application.yml**  
&nbsp;- actuator moved to a separate internal-only management port 8090 (MANAGEMENT_SERVER_PORT), off the public  
&nbsp;   server.port  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the tracing and metrics switches each read their own env var (ESQ_TRACING_ENABLED / ESQ_METRICS_ENABLED),  
&nbsp;   defaulting to the ESQ_OBSERVABILITY_ENABLED umbrella, so either pillar can be turned off on its own (I41)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**messaging.AuditConsumerConfig**  
&nbsp;- wraps the keep applier so the audit consumer stamps MDC via EsqContextHolder.applyMessage(event) before  
&nbsp;   applying and clears in a finally, correlating its log lines to the audited message (I10)  

**07/11/2026** mir0n  v1.2.11 -- the business-meter sub-switch (O1/T8)  
**resources/application.yml**  
&nbsp;- esquire.observability.metrics.business-enabled: ${ESQ_METRICS_BUSINESS:true} added -- the sub-switch gating  
&nbsp;   the esq.biz.* domain tier under the observability master  

**07/11/2026** mir0n  v1.2.11 -- observability config namespace + the metrics sub-switches (O1/T5)  
**resources/application.yml**  
&nbsp;- the tracing keys move under the umbrella: esquire.tracing.* -> esquire.observability.tracing.*  
&nbsp;   (otlp-endpoint, sampling-ratio, marks-enabled, excluded-paths, msg-bus-alive-trace);  
&nbsp;   esquire.observability.metrics.* sub-switches added -- histograms-enabled (ESQ_METRICS_HISTOGRAMS,  
&nbsp;   default false) and bandwidth-enabled (ESQ_METRICS_BANDWIDTH, default true)  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): keep-pool meters + the observability umbrella switch  
**messaging.AuditConsumerConfig**  
&nbsp;- injects ObjectProvider and hands getIfAvailable() to the KeepApplier, so the keep pool's  
&nbsp;   hikaricp_* meters report when observability is enabled  
**resources/application.yml**  
&nbsp;- esquire.observability.enabled (ESQ_OBSERVABILITY_ENABLED) now gates BOTH tracing and metrics (replaces the  
&nbsp;   tracing-only switch); management.prometheus.metrics.export.enabled + endpoints.web.exposure.include prometheus added  

**07/09/2026** mir0n  v1.2.11 -- the RR liveness round-trip trace knob  
**resources/application.yml**  
&nbsp;- esquire.tracing.msg-bus-alive-trace: ${ESQ_MSG_BUS_ALIVE_TRACE:false} added  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): tracing wired into auKeep  
AuKeepApplication  
&nbsp;- @Import(TracingConfig.class): the common distributed-tracing wiring  
**resources/application.yml**  
&nbsp;- esquire.tracing block (enabled / otlp-endpoint / sampling-ratio / marks-enabled / excluded-paths) with  
&nbsp;   the ESQ_TRACING_* env overrides; management.tracing.enabled mirrors esquire.tracing.enabled  
k8s chart esquire-aukeep/values.yaml  
&nbsp;- tracing block (enabled "false" / otlpEndpoint / samplingRatio / marksEnabled / excludedPaths) --  
&nbsp;   opt-in, off by default  
k8s chart esquire-aukeep/templates/configmap.yaml  
&nbsp;- ESQ_TRACING_ENABLED / ESQ_OTLP_ENDPOINT / ESQ_TRACING_SAMPLING_RATIO / ESQ_TRACING_MARKS_ENABLED /  
&nbsp;   ESQ_TRACING_EXCLUDED_PATHS rendered from .Values.tracing  

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**taijitu.Monad**  
&nbsp;- javadoc: a handler exception in MessageHandlerHub.dispatch is swallowed (logged + outcome=failed) and the batch  
&nbsp;   commits, not rolled back -- a should-not-happen condition; the night-watch SWAP heals any resulting cache/DB drift  
**resources/application.yml**  
&nbsp;- actuator moved to a separate internal-only management port 8090 (MANAGEMENT_SERVER_PORT), off the public  
&nbsp;   server.port  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the tracing and metrics switches each read their own env var (ESQ_TRACING_ENABLED / ESQ_METRICS_ENABLED),  
&nbsp;   defaulting to the ESQ_OBSERVABILITY_ENABLED umbrella, so either pillar can be turned off on its own (I41)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**messaging.EntityBusAdapter**  
&nbsp;- the entity-broadcast receive worker stamps MDC via EsqContextHolder.applyMessage(event) and clears in a  
&nbsp;   finally, so its INFO log line carries the message's correlationId / requestId (I10)  
**taijitu.Monad**  
&nbsp;- the cache-apply worker stamps MDC via EsqContextHolder.applyMessage(requestId, correlationId) and clears in a  
&nbsp;   finally, so its log lines carry the message ids (I10)  

**07/11/2026** mir0n  v1.2.11 -- business meters (O1/T8): what the cache did with each broadcast  
**access.MessageHandlerHub**  
&nbsp;- dispatch() counts esq.biz.tree.handler.dispatch.total (tags event, kind, outcome = handled|no-handler|  
&nbsp;   no-payload|failed) in a finally; the body is unchanged. The FAILED value is the point: the catch here SWALLOWS  
&nbsp;   the handler exception, so a handler that blows up leaves the cache silently stale while the bus still counts  
&nbsp;   the message as received  
**cache.BizTreeCacheLoader**  
&nbsp;- load() counts esq.biz.tree.rebuild.total (tag outcome) in a finally; it still throws so the caller can  
&nbsp;   transition its monad to FAILED  
**resources/application.yml**  
&nbsp;- esquire.observability.metrics.business-enabled: ${ESQ_METRICS_BUSINESS:true} added -- the sub-switch gating  
&nbsp;   the esq.biz.* domain tier under the observability master  

**07/11/2026** mir0n  v1.2.11 -- observability config namespace + the metrics sub-switches (O1/T5)  
**resources/application.yml**  
&nbsp;- the tracing keys move under the umbrella: esquire.tracing.* -> esquire.observability.tracing.*  
&nbsp;   (otlp-endpoint, sampling-ratio, marks-enabled, excluded-paths, msg-bus-alive-trace);  
&nbsp;   esquire.observability.metrics.* sub-switches added -- histograms-enabled (ESQ_METRICS_HISTOGRAMS,  
&nbsp;   default false) and bandwidth-enabled (ESQ_METRICS_BANDWIDTH, default true)  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): Prometheus export + the observability umbrella switch  
**resources/application.yml**  
&nbsp;- esquire.observability.enabled (ESQ_OBSERVABILITY_ENABLED) now gates BOTH tracing and metrics (replaces the  
&nbsp;   tracing-only switch); management.prometheus.metrics.export.enabled + endpoints.web.exposure.include prometheus added  

**07/09/2026** mir0n  v1.2.11 -- distributed tracing (O2/T3): the cache apply continues the producer's trace  
**access.IBizTreeDirector**  
&nbsp;- onRodEvent() captures the traceparent (EsqAsyncTrace.capture) and passes it on; onEntityBroadcast()  
&nbsp;   signature gains a traceparent parameter (last)  
**access.legacy.BizTreeDirectorLegacy**  
&nbsp;- onEntityBroadcast() signature gains a traceparent parameter (unused: the legacy apply is synchronous on the  
&nbsp;   receive thread); the H2 apply wrapped in EsqTraceMark.around("esq.svc.cache", "cache apply", ...)  
**taijitu.Monad**  
&nbsp;- the H2 apply runs inside EsqAsyncTrace.continueIn(item.traceparent(), item.correlationId(),  
&nbsp;   "cache apply", ...)  
**resources/application.yml**  
&nbsp;- esquire.tracing.msg-bus-alive-trace: ${ESQ_MSG_BUS_ALIVE_TRACE:false} added  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): trace marks on the tree reads  
**controller.BizTreeController**  
&nbsp;- @EsqTraced on the four GET reads (esq.svc.tree / node / subtree / path) -- marked here, at the REST entry  
&nbsp;   point, so a cache-served read is traced whichever director is wired; POST /esq-sweep is not marked  
**service.impl.BizTreeService**  
&nbsp;- @EsqTraced on esquire / esquireEntityNode / esquirePath / esquireSubtree (esq.svc.tree / node / path / subtree)  
BizTreeApplication  
&nbsp;- @Import(TracingConfig.class): the common distributed-tracing wiring  
**resources/application.yml**  
&nbsp;- esquire.tracing block (enabled / otlp-endpoint / sampling-ratio / marks-enabled / excluded-paths) with  
&nbsp;   the ESQ_TRACING_* env overrides; management.tracing.enabled mirrors esquire.tracing.enabled  
k8s chart esquire-biztree/values.yaml  
&nbsp;- tracing block (enabled "false" / otlpEndpoint / samplingRatio / marksEnabled / excludedPaths) --  
&nbsp;   opt-in, off by default  
k8s chart esquire-biztree/templates/configmap.yaml  
&nbsp;- ESQ_TRACING_ENABLED / ESQ_OTLP_ENDPOINT / ESQ_TRACING_SAMPLING_RATIO / ESQ_TRACING_MARKS_ENABLED /  
&nbsp;   ESQ_TRACING_EXCLUDED_PATHS rendered from .Values.tracing  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**o11y.EsqBizMeters**  
&nbsp;- gauge(): defensive re-check after PENDING.add so a gauge is not stranded by a concurrent setRegistry() drain  
&nbsp;   (idempotent per meter id; zero-cost while off)  
**o11y.EsqGauge**  
&nbsp;- javadoc: register() is idempotent per meter id (re-registration keeps the FIRST supplier); safe because gauged  
&nbsp;   objects are process-lifetime singletons, never rebuilt in-process  
**o11y.EsqTagCardinalityCap**  
&nbsp;- javadoc: the once-per-id property relies on Micrometer's preFilterIdToMeterMap cache (>= 1.12), so map() is not  
&nbsp;   per-op; and the cap is a best-effort SAFETY BOUND, not an exact quota  
**service.MdcFilter**  
&nbsp;- the INCOMING log line is emitted AFTER MDC is populated, so it carries the correlationId / requestId fields  
&nbsp;   like every other line in the request  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**o11y.EsqBizMeters**  
&nbsp;- safeTags(): a null tag element is coerced to "null" centrally, so an esq.biz.* meter called from a finally  
&nbsp;   cannot throw over a label (I18); clones only when a null is present  
**o11y.EsqRodObserver**  
&nbsp;- no producer->consumer span LINKS by design -- fan-out is drawn consumer-side via the carried traceparent  
&nbsp;   (I36); alive-trace opt-in key under esquire.observability.tracing.*  
o11y.EsqTagCardinalityCap  (new)  
&nbsp;- a MeterFilter that CAPS the distinct values any esq.biz.* / messaging.* tag may take (I25); past the cap a  
&nbsp;   value collapses to a sentinel, so an unbounded tag is structurally impossible  
**o11y.EsqTraceMark**  
&nbsp;- observe() records the WHOLE Throwable hierarchy incl. Error -- the I33 "misses Error" review claim, checked  
&nbsp;   live, is FALSE  
**o11y.ObservabilityConfig**  
&nbsp;- the OTLP exporter is wrapped by Boot's default BatchSpanProcessor (bounded queue, drops on overflow, never  
&nbsp;   blocks the request thread) -- the o11y path is not a request-failure mode (I53)  
**o11y.W3CTraceContext**  
&nbsp;- isW3cTraceId delegates to common.EsqUtils so the trace-id shape cannot drift (I35); tracestate kept empty by  
&nbsp;   design (I37)  
**service.PerformanceAspect**  
&nbsp;- the request-thread test is extracted to isRequestThread() and made the FIRST && operand, so the @RequestScope  
&nbsp;   bean is read only on a request thread (no exception-as-detector)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**service.EsqContextHolder**  
&nbsp;- MDC control centralised here (I10): set() now also stamps correlationId / requestId into MDC (the priority  
&nbsp;   path), clear() removes them, and applyMessage(RodEvent) / applyMessage(requestId, correlationId) stamp MDC  
&nbsp;   ONLY -- for a bus worker that has no full context to set. Bus listener workers no longer touch MDC directly  

**07/11/2026** mir0n  v1.2.11 -- business meters (O1/T8): the esq.biz.* facility, the one cross-cutting meter, the business sub-switch, and ONE switch for every histogram bucket  
o11y.EsqBizMeters  (new)  
&nbsp;- the shared esq.biz.* meter facility -- the ONE entry point for every business meter, so per-service work stays  
&nbsp;   THIN (a meter name, its tags, and the call site at its own domain seam) and the machinery stays generic in  
&nbsp;   common (D6). count / time / gauge; gauge() delegates to EsqGauge so an esq.biz.* gauge is strongReference'd  
&nbsp;   by construction. STATIC + registrar-backed (the EsqTraceMark shape) -- the only shape that reaches BOTH Spring  
&nbsp;   beans and the new-ed, never-proxied objects (AcctTransactionProcessor*, the taijitu Monad, KeepSqlStore).  
&nbsp;   Registry null while the umbrella is off, so every call is a null check and nothing else. A gauge asked for  
&nbsp;   BEFORE the registry arrives is HELD and registered when it does: a bean's @PostConstruct can run before the  
&nbsp;   registrar, and a gauge() call at that moment would quietly do nothing and the gauge would never exist  
**o11y.ObservabilityConfig**  
&nbsp;- esqBizMetersRegistrar() @Bean hands the MeterRegistry to EsqBizMeters, gated by the NEW sub-switch  
&nbsp;   esquire.observability.metrics.business-enabled (default TRUE). Gating the REGISTRAR is what makes off free:  
&nbsp;   no registry reaches the facility, so every esq.biz.* call site in the fleet collapses to a null check.  
&nbsp;   esqLatencyHistograms(): esq.biz.* timers ride histograms-enabled BY PREFIX (a name list would need editing  
&nbsp;   every time a service gains a timer), and http.server.requests moves INTO the gated set -- it was special-cased  
&nbsp;   always-on so p95 would work without asking, and that one clause emitted 1,173 bucket series, 20.5% of the  
&nbsp;   entire scrape, whether or not anyone ever looked at a percentile. ONE switch now governs every bucket in the  
&nbsp;   fleet: off = 4,597 series, on = 10,686. Count/sum/max survive with it off, so the average panels stay  
&nbsp;   populated and only the percentile panels go dark  
**storage.EsqRolesStorage**  
&nbsp;- isAdminCmdPermitted() counts esq.biz.perm.check.total (tags cmd = the AdminCmd enum, result = allow|deny):  
&nbsp;   the authorization decision itself, at the one gate every service goes through. The gate sees allow and deny  
&nbsp;   ONLY -- a self-update BYPASSES it entirely (id.equals(uid) short-circuits at the caller), which is why there  
&nbsp;   is no third tag value  

**07/11/2026** mir0n  v1.2.11 -- trap removal (O1/T7): the weak-reference gauge and the request-scope probe resolved in code  
o11y.EsqGauge  (new)  
&nbsp;- the ONE place a Micrometer gauge is built. Micrometer holds a gauge's state object WEAKLY: when that state  
&nbsp;   object IS the supplier lambda and nothing else holds it, GC collects it and the gauge reports NaN -- a dead  
&nbsp;   panel, no error, no stack trace. register() always sets strongReference(true), so a caller cannot get it  
&nbsp;   wrong; callers hand over a name, an IntSupplier and tags, and never touch Gauge.builder  
**o11y.EsqRodObserver**  
&nbsp;- registerFeedDepth / registerRetryHeld no longer build their gauge: both hand off to EsqGauge.register(),  
&nbsp;   which owns Gauge.builder and always applies strongReference(true). The hand-written strongReference at each  
&nbsp;   call site is gone, and with it the last raw Gauge.builder in the codebase; the io.micrometer Gauge import  
&nbsp;   drops out  
**service.PerformanceAspect**  
&nbsp;- the exception is no longer the detector. The aspect asks RequestContextHolder.getRequestAttributes() != null  
&nbsp;   FIRST, and only then whether anyone wants the number; the try / catch (ScopeNotActiveException) is gone, and  
&nbsp;   the @RequestScope bean is never touched on a thread that has no request. The || that caused the 500 is now  
&nbsp;   harmless -- whichever side answers it, the thread is already known to be serving a request  

**07/11/2026** mir0n  v1.2.11 -- bus meters + latency bands + bandwidth (O1/T5): ONE bus observer, the esq.* latency timers, the HTTP byte counters, ONE config namespace  
o11y.EsqRodObserver  (new -- was EsqRodTracer)  
&nbsp;- renamed from EsqRodTracer and widened to the ONE bus observer: it now implements o11y.IRodObserver  
&nbsp;   (IRodTracer + IRodMeters), so the SAME object that traces the bus hop also meters it. The ctor takes the  
&nbsp;   Micrometer MeterRegistry alongside the OTel Tracer. The meter side emits messaging.send.total /  
&nbsp;   receive.total / error.total (counters, tagged bus-id / slot / msg-type; error also by leg),  
&nbsp;   messaging.send.duration (timer), messaging.retry.backoff / retry.dropped, and registers the  
&nbsp;   messaging.feed.depth / messaging.retry.held GAUGES. Both gauges are built .strongReference(true): the state  
&nbsp;   object is the supplier lambda itself and nothing else holds it, so Micrometer's default WEAK reference lets  
&nbsp;   it be GC'd and the gauge then reports NaN  
**o11y.ObservabilityConfig**  
&nbsp;- ONE config namespace: every key moves under esquire.observability.* (the five tracing @Values now read  
&nbsp;   esquire.observability.tracing.*; no sibling esquire.tracing.* root is left). SUB-SWITCHES under the master,  
&nbsp;   each defaulted so the master alone is enough: esqHttpLatencyHistogram() becomes esqLatencyHistograms(),  
&nbsp;   widened to the esq.* latency timers as well as http.server.requests and now gated by  
&nbsp;   esquire.observability.metrics.histograms-enabled (opt-in, default false); the nested TomcatByteMetrics and  
&nbsp;   NettyByteMetrics (@ConditionalOnClass, so a servlet service takes the first and the reactive gateway the  
&nbsp;   second) are gated by esquire.observability.metrics.bandwidth-enabled (default true): TomcatByteMetrics  
&nbsp;   contributes a LOWEST_PRECEDENCE WebServerFactoryCustomizer re-enabling the Tomcat MBean registry Boot  
&nbsp;   disables (without it tomcat.global.sent/received never exist), NettyByteMetrics a NettyServerCustomizer  
&nbsp;   turning on reactor-netty server metrics with a coarseUri mapper (first path segment only -- the raw uri  
&nbsp;   carries entity ids = unbounded cardinality). esqRodTraceRegistrar() becomes esqRodObserverRegistrar(): it  
&nbsp;   takes the MeterRegistry too and registers ONE EsqRodObserver (trace + meters) into RodObserverHolder  
**service.MdcFilter**  
&nbsp;- the two numbers behind the timing headers are now also recorded as Micrometer timers: esq.srv.outer (the  
&nbsp;   whole servlet wall time) and esq.srv.inner (the JPA/DB time PerformanceAspect accumulated for the request =  
&nbsp;   the DB band), tagged by the matched route pattern  
&nbsp;   (HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE). Explicit ctor (RequestPerformance,  
&nbsp;   ObjectProvider); the registry is absent when observability is off and the timers are then  
&nbsp;   not recorded. The X-Capture-Metrics response headers are untouched  
**service.PerformanceAspect**  
&nbsp;- the JPA timing is now also collected when observability is on, not only when the caller asked for it with  
&nbsp;   the X-Capture-Metrics header. @RequiredArgsConstructor dropped for an explicit ctor taking  
&nbsp;   (RequestPerformance, ObjectProvider) -- observability-on is resolved ONCE at construction.  
&nbsp;   The request-scope probe (RequestPerformance.isMetricsCaptured) is made FIRST and ALWAYS and the  
&nbsp;   ScopeNotActiveException it raises off-request keeps driving the skip; it must NOT be folded into  
&nbsp;   'observabilityOn || isMetricsCaptured()', because || short-circuits the probe away and the aspect then  
&nbsp;   calls addJpaTime() on an off-request thread (the taijitu cache loader runs JPA on a monad worker)  
**pom.xml**  
&nbsp;- reactor-netty-http (provided) added -- the NettyServerCustomizer / HttpServer types ObservabilityConfig  
&nbsp;   compiles against; only the gateway supplies it at runtime  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): free JVM/HTTP/pool meters under the observability umbrella  
**o11y.ObservabilityConfig**  
&nbsp;- metrics folded onto the tracing umbrella (class was TracingConfig): the Prometheus meter registry is  
&nbsp;   Boot-owned; this config contributes only the policy -- esqCommonMetricTags() (MeterFilter: common tag  
&nbsp;   application= on every meter) and esqHttpLatencyHistogram() (MeterFilter:  
&nbsp;   percentile-histogram on http.server.requests so p95 has _bucket series); gate widened to  
&nbsp;   esquire.observability.enabled (one switch for tracing AND metrics)  
**pom.xml**  
&nbsp;- micrometer-registry-prometheus (compile) added -- the Prometheus meter registry + the free jvm/http/pool  
&nbsp;   binders on common's classpath; Actuator exposes /actuator/prometheus once the registry bean exists  

**07/09/2026** mir0n  v1.2.11 -- distributed tracing (O2/T3): the OTel implementation of the bus-hop hook, the async-boundary primitive, the replica badge  
o11y.EsqRodTracer  (new)  
&nbsp;- the OTel implementation of the messaging-declared o11y.IRodTracer, built on the raw OTel Tracer (not the  
&nbsp;   ObservationRegistry) so each bus leg carries an explicit span kind: outbound() opens a PRODUCER span on the  
&nbsp;   send and returns a traceparent whose TRACE ID is the correlationId (authoritative) and whose span id is that  
&nbsp;   span; inbound() rebuilds the remote parent and runs the consumer worker inside a CONSUMER span, so the  
&nbsp;   worker's marks nest under the producer span in ONE trace. aliveOutbound() / aliveInbound() are the same pair  
&nbsp;   for the RR liveness round-trip (aliveOutbound opens a ROOT trace when the send has no current span -- a  
&nbsp;   CLIENT TestRequest off the heartbeat cadence); newTraceId() mints a fresh W3C-shaped correlation id;  
&nbsp;   aliveTrace() carries the esquire.tracing.msg-bus-alive-trace opt-in, passed to the constructor. Span names  
&nbsp;   carry no instance id -- the collector badges each span with its replica  
o11y.W3CTraceContext  (new)  
&nbsp;- the shared W3C trace-context helpers used by both the bus-hop tracer (EsqRodTracer) and the async-boundary  
&nbsp;   primitive (EsqAsyncTrace). The trace id is ALWAYS the correlationId (authoritative); a traceparent only  
&nbsp;   carries the parent span id  
o11y.EsqAsyncTrace  (new)  
&nbsp;- the async-boundary trace primitive. When work is HANDED OFF to another thread (a queue worker) the OTel span  
&nbsp;   does not follow -- only the correlationId travels. capture() grabs the current traceparent on the submitting  
&nbsp;   thread; continueIn() re-establishes it on the worker thread so the worker's spans nest in the SAME trace  
**o11y.TracingConfig**  
&nbsp;- esqOtelResource() @Bean added: the OTel resource carries service.name plus service.instance.id  
&nbsp;   (.); esqRodTraceRegistrar() @Bean registers the EsqRodTracer (carrying the  
&nbsp;   esquire.tracing.msg-bus-alive-trace opt-in) into the messaging o11y.RodTracerHolder hand-off;  
&nbsp;   esqAsyncTraceRegistrar() @Bean hands the ObservationRegistry to EsqAsyncTrace  
**o11y.EsqTraceMark**  
&nbsp;- contextualName(label): the span name no longer carries the instance id  
**o11y.EsqTracedAspect**  
&nbsp;- contextualName(label): the span name no longer carries the instance id  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): the Esquire trace-mark facility  
o11y.EsqTraced  (new)  
&nbsp;- the Esquire trace-mark annotation: put it on a Spring-managed service method and the method becomes its  
&nbsp;   own span (a child of the request span) via EsqTracedAspect. name = the low-cardinality observation name;  
&nbsp;   label = the span name in the trace. The programmatic twin for non-Spring / final code is  
&nbsp;   EsqTraceMark.around(); both ride the same ObservationRegistry, so ONE gate (TracingConfig's  
&nbsp;   ObservationPredicate) and the ESQ_TRACING_ENABLED master switch govern them together with the HTTP spans  
o11y.EsqTracedAspect  (new)  
&nbsp;- the aspect that turns @EsqTraced into a span. Around an annotated Spring-managed method it opens an  
&nbsp;   Observation named by the annotation; the shared ObservationRegistry's tracing handler renders it as a span  
&nbsp;   nested in the request trace, and TracingConfig's ObservationPredicate decides whether that span is  
&nbsp;   populated. Our own aspect (not Micrometer's ObservedAspect) so the annotation and its label are Esquire's  
o11y.EsqTraceMark  (new)  
&nbsp;- the programmatic half of the trace-mark facility: around(name, label, () -> ...) / aroundChecked() wrap a  
&nbsp;   processing step in an Observation the tracing handler renders as a span nested in the request trace. The  
&nbsp;   twin of @EsqTraced, for code Spring AOP cannot proxy (non-bean or final classes) -- the dataKeep  
&nbsp;   RodEventDbWriter apply, and the pacMan acct transaction / transfer processors. Both entry points share the  
&nbsp;   ObservationRegistry handed in by TracingConfig; when tracing is off the registry is NOOP, so the action  
&nbsp;   runs with zero span overhead  
o11y.TracingConfig  (new)  
&nbsp;- explicit distributed-tracing wiring: the OTLP span exporter (endpoint) and the head sampler declared as  
&nbsp;   explicit @Beans; the Micrometer-Tracing bridge assembles them onto the request observations.  
&nbsp;   esqTracedAspect backs @EsqTraced; esqTraceRegistrar hands the shared ObservationRegistry to EsqTraceMark.  
&nbsp;   Gated by esquire.tracing.enabled (off by default = zero cost); management.tracing.enabled mirrors it.  
&nbsp;   Imported per service  
&nbsp;- esqObservationGate is ONE ObservationPredicate deciding whether an observation is populated: it governs  
&nbsp;   the esq.* marks (esquire.tracing.marks-enabled) and refuses an http.* SERVER observation whose request  
&nbsp;   path sits under esquire.tracing.excluded-paths (default /actuator), so a health probe never builds a span  
&nbsp;- requestPath(Observation.Context) reads the path off micrometer's ReceiverContext carrier (HttpServletRequest  
&nbsp;   or reactive ServerHttpRequest), so neither Spring context type is named; isExcluded / parsePrefixes support it  
&nbsp;- nested SecurityObservationsOff (@Configuration, @ConditionalOnClass) contributes  
&nbsp;   SecurityObservationSettings.noObservations(): Spring Security's filter-chain / authentication /  
&nbsp;   authorization observations are off. Nested + conditional because kcMaster and auKeep carry no  
&nbsp;   spring-security-config, and a @Bean method on TracingConfig would force every importer to resolve that  
&nbsp;   return type  
**error.ProblemDetailMill**  
&nbsp;- createProblemDetail(): the incoming correlation id runs through EsqUtils.settleCorrelationId(); the settled  
&nbsp;   value is set on BOTH the traceId and the correlationId problem-detail properties (was: traceId only when no  
&nbsp;   correlation id came in)  
**pom.xml**  
&nbsp;- micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp added (compile); spring-boot-autoconfigure  
&nbsp;   added (provided) for the @ConditionalOnProperty on TracingConfig  
&nbsp;- aspectjweaver added (compile): EsqTracedAspect references org.aspectj.lang.ProceedingJoinPoint, and  
&nbsp;   spring-aspects is provided-only, so a service importing TracingConfig without the AOP starter on its own  
&nbsp;   classpath (gateway / kcMaster / auKeep) failed to create esqTracedAspect (NoClassDefFoundError)  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**07/26/2026** mir0n  v1.2.11 -- Paper Client Account kind label correction  
**esq-object-kinds.xml**  
&nbsp;- the paper-client-account shortcut kind  "Paper Client" -> "Paper Client Account";  
&nbsp;    double space removed  

**07/09/2026** mir0n  v1.2.11 -- the instance-identity rule moves down to mir0n-utils  
EsqUtils  
&nbsp;- instanceNo() / instanceHost() and the private parsePodNameOrdinal() moved to pro.mir0n.utils.HostId  
&nbsp;   (mir0n-utils); instanceNo() / instanceHost() and the test seams now delegate to it  

**07/08/2026** mir0n  v1.2.11 -- W3C trace-id settlement (O2)  
EsqUtils  
&nbsp;- generateCorrelationId() now emits 32 lowercase hex from 16 SecureRandom bytes, non-zero (was a UUID string)  
&nbsp;- isW3cTraceId(String), toW3cTraceId(String) (SHA-256, first 16 bytes -> 32 hex), settleCorrelationId(String)  
&nbsp;   (keep-if-W3C / convert / generate), buildTraceparent(String traceId), isValidTraceparent(String) and  
&nbsp;   traceIdFromTraceparent(String) added  
EsqConstants  
&nbsp;- TRACEPARENT ("traceparent") added: the W3C Trace Context header name  

### dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt


**07/11/2026** mir0n  v1.2.11 -- business meters (O1/T8): the DB write at the keep sink  
**keep.RodEventDbWriter**  
&nbsp;- applyEvent() counts esq.biz.keep.write.total and times esq.biz.keep.write.duration (tags op = the RodEvent op,  
&nbsp;   outcome = ok|error) around the DB write. This is the one thing the bus meters cannot see:  
&nbsp;   messaging.receive.total says the audit event ARRIVED, only this says whether the row was WRITTEN. The op tag  
&nbsp;   is null-safe -- these read from a finally, and a meter that throws there would REPLACE the real exception  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): the keep pool reports hikaricp_* meters  
**keep.KeepApplier**  
&nbsp;- 5-arg constructor + buildPool(p, metricRegistry): an optional metricRegistry (nullable Object, so dataKeep  
&nbsp;   stays Micrometer-free) is handed to HikariConfig.setMetricRegistry, so this OWN (non-Spring) keep pool  
&nbsp;   reports its hikaricp_* meters alongside the services' Spring-managed pools  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): trace mark on the keep apply  
**keep.RodEventDbWriter**  
&nbsp;- applyEvent() body wrapped in EsqTraceMark.around("esq.keep.apply", "keep audit log", ...) -- the writer is not a  
&nbsp;   Spring bean, so the programmatic mark stands in for @EsqTraced  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**jpa.EsqAcctRepository**  
&nbsp;- acctPath() gains a rootPath @Param (tenant scope, matching orgPath/usrPath)  
**messaging.EntityBusAdapter**  
&nbsp;- forwardPeerCreate carries the create's OWN cid/rid onto the CreateReconcileItem (the path-fix reissue stays  
&nbsp;   correlated to the create it repairs, no leftover-worker-MDC reliance)  
**queue.CreateReconcileItem**  
&nbsp;- gains correlationId/requestId components: the item now carries the originating create's ids, which the worker  
&nbsp;   stamps itself (no reliance on leftover worker MDC)  
**queue.MoveQueueManager**  
&nbsp;- "elastic end of move": inMove() stays true for a grace window (enyman.move-queue.in-move-grace-ms, default 200,  
&nbsp;   0 disables) after the last move drains, so a create event with a pre-move path is still reconciled  
**service.EntityIdGenerator**  
&nbsp;- the sequence digit is forced non-negative (AtomicInteger wraps negative after 2^31; a negative seq would borrow  
&nbsp;   into the instance/time digits and corrupt the id)  
**service.impl.AcctService**  
&nbsp;- createAcct reads rootPath (RequestContextUtils) and passes it to acctPath(parentId, rootPath) -- the parent  
&nbsp;   lookup is now tenant-scoped, like org/usr create  
**service.impl.EnyManService**  
&nbsp;- submitReconcileIfInMove passes the create's cid/rid onto the CreateReconcileItem  
**resources/META-INF/postgres-entity.xml**  
&nbsp;- EsqUsrJpa.listMovedPaths orders by ep_et_pk (entity-kind first) so a move broadcast emits parents before  
&nbsp;   children by construction  
**resources/META-INF/oracle-entity.xml**  
&nbsp;- EsqUsrJpa.listMovedPaths orders by ep_et_pk (entity-kind first), matching the postgres dialect  
**resources/META-INF/postgres-acct.xml**  
&nbsp;- EsqAcctRepository.acctPath adds AND ep_path LIKE :rootPath || '%' so the account parent lookup is tenant-scoped  
**resources/META-INF/oracle-acct.xml**  
&nbsp;- EsqAcctRepository.acctPath adds the rootPath LIKE filter, matching the postgres dialect  
**resources/application.yml**  
&nbsp;- move-queue.in-move-grace-ms added (ENYMAN_MOVE_QUEUE_IN_MOVE_GRACE_MS, default 200) -- the elastic-end-of-move  
&nbsp;   grace window  
&nbsp;- the KC leg and the entity-broadcast leg each get feed-await-ms:0 (wait forever) so a full feed HOLDS the  
&nbsp;   producer instead of dropping an identity sync / entity broadcast  
&nbsp;- actuator moved to a separate internal-only management port 8090 (MANAGEMENT_SERVER_PORT), off the public  
&nbsp;   server.port  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the tracing and metrics switches each read their own env var (ESQ_TRACING_ENABLED / ESQ_METRICS_ENABLED),  
&nbsp;   defaulting to the ESQ_OBSERVABILITY_ENABLED umbrella, so either pillar can be turned off on its own (I41)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**messaging.KcBusAdapter**  
&nbsp;- the kc-bus receive worker (onResponse) stamps MDC via EsqContextHolder.applyMessage(event) and clears in a  
&nbsp;   finally (I10)  
**queue.MoveQueueManager**  
&nbsp;- processMove() binds the worker context with EsqContextHolder.set(), which now stamps MDC itself; the separate,  
&nbsp;   now-redundant MDC apply was dropped (I10)  

**07/11/2026** mir0n  v1.2.11 -- business meters (O1/T8): entity operations, the move queue, dictionary lookups  
**service.impl.EnyManService**  
&nbsp;- esquireCommandNew / Delete / Move count esq.biz.entity.ops.total (tags op, kind, outcome = ok|denied|error)  
&nbsp;   via the private meterEntityOp(); each body is wrapped in try / catch (PermissionDeniedException, rethrown) /  
&nbsp;   finally, otherwise unchanged. For a MOVE this records that the command was ACCEPTED, not that the move  
&nbsp;   succeeded -- the work happens off-request on the queue worker  
**queue.MoveQueueManager**  
&nbsp;- start() registers the esq.biz.move.queue.depth gauge (queueSize); processMove() counts  
&nbsp;   esq.biz.move.processed.total / .failed.total (tag kind) from a boolean flag in the existing finally -- the  
&nbsp;   exception flow is untouched. The move's REAL outcome: /esq-move answers 202 at submit time, so a move that  
&nbsp;   fails on the worker is invisible to the caller and to every HTTP meter  
**controller.EnyManController**  
&nbsp;- esquireDictionary() counts esq.biz.dict.lookup.total (tag kind). Not a duplicate of http.server.requests:  
&nbsp;   that is tagged by URI TEMPLATE (/esq-dict) and the kind is a query param  
**resources/application.yml**  
&nbsp;- esquire.observability.metrics.business-enabled: ${ESQ_METRICS_BUSINESS:true} added -- the sub-switch gating  
&nbsp;   the esq.biz.* domain tier under the observability master  

**07/11/2026** mir0n  v1.2.11 -- observability config namespace + the metrics sub-switches (O1/T5)  
**resources/application.yml**  
&nbsp;- the tracing keys move under the umbrella: esquire.tracing.* -> esquire.observability.tracing.*  
&nbsp;   (otlp-endpoint, sampling-ratio, marks-enabled, excluded-paths, msg-bus-alive-trace);  
&nbsp;   esquire.observability.metrics.* sub-switches added -- histograms-enabled (ESQ_METRICS_HISTOGRAMS,  
&nbsp;   default false) and bandwidth-enabled (ESQ_METRICS_BANDWIDTH, default true)  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): Prometheus export + the observability umbrella switch  
**resources/application.yml**  
&nbsp;- esquire.observability.enabled (ESQ_OBSERVABILITY_ENABLED) now gates BOTH tracing and metrics (replaces the  
&nbsp;   tracing-only switch); management.prometheus.metrics.export.enabled + endpoints.web.exposure.include prometheus added  

**07/09/2026** mir0n  v1.2.11 -- distributed tracing (O2/T3): the async move continues the request's trace  
**service.impl.EnyManService**  
&nbsp;- esquireCommandMove() captures the traceparent (EsqAsyncTrace.capture) inside the traced move and passes it  
&nbsp;   to MoveCommandItem  
**queue.MoveCommandItem**  
&nbsp;- the record gains a traceparent component (last)  
**queue.MoveQueueManager**  
&nbsp;- processMove() runs inside EsqAsyncTrace.continueIn(item.traceparent(), item.correlationId(),  
&nbsp;   "move (async)", ...)  
**resources/application.yml**  
&nbsp;- esquire.tracing.msg-bus-alive-trace: ${ESQ_MSG_BUS_ALIVE_TRACE:false} added  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): trace marks on the entity commands  
**service.impl.EnyManService**  
&nbsp;- @EsqTraced on esquireCommand / Save / New / Delete / Move / Tree  
&nbsp;   (esq.svc.read / save / create / delete / move / tree)  
EnyManApplication  
&nbsp;- @Import(TracingConfig.class): the common distributed-tracing wiring  
**resources/application.yml**  
&nbsp;- esquire.tracing block (enabled / otlp-endpoint / sampling-ratio / marks-enabled / excluded-paths) with  
&nbsp;   the ESQ_TRACING_* env overrides; management.tracing.enabled mirrors esquire.tracing.enabled  
k8s chart esquire-enyman/values.yaml  
&nbsp;- tracing block (enabled "false" / otlpEndpoint / samplingRatio / marksEnabled / excludedPaths) --  
&nbsp;   opt-in, off by default  
k8s chart esquire-enyman/templates/configmap.yaml  
&nbsp;- ESQ_TRACING_ENABLED / ESQ_OTLP_ENDPOINT / ESQ_TRACING_SAMPLING_RATIO / ESQ_TRACING_MARKS_ENABLED /  
&nbsp;   ESQ_TRACING_EXCLUDED_PATHS rendered from .Values.tracing  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**config.CorrelationPropagatorConfig**  
&nbsp;- the propagator bean is @ConditionalOnProperty(esquire.observability.tracing.enabled, matchIfMissing=true) so it  
&nbsp;   does not load in a metrics-only observability config  
**config.KeycloakRoleConverter**  
&nbsp;- single-ret pattern: realm_access.roles -> ROLE_ collected into one ret list  
**config.SecurityConfig**  
&nbsp;- the "/esq*" authorization is a SINGLE hasRole("TREE") rule (implies authenticated AND the TREE realm role);  
&nbsp;   comment on why there must be exactly one /esq* rule (first-match-wins)  
**security.tokenrelay.TokenRelayCache**  
&nbsp;- note-at-switch: a cache HIT returns the stored JWT WITHOUT re-verifying the caller's credential (Vanilla =  
&nbsp;   client_id key, secret not re-checked; Phantom = jti read unvalidated)  
**resources/application.yml**  
&nbsp;- actuator moved to a separate internal-only management port 8090 (MANAGEMENT_SERVER_PORT), off the public  
&nbsp;   server.port  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the tracing and metrics switches each read their own env var (ESQ_TRACING_ENABLED / ESQ_METRICS_ENABLED),  
&nbsp;   defaulting to the ESQ_OBSERVABILITY_ENABLED umbrella, so either pillar can be turned off on its own (I41)  
**config.SecurityConfig**  
&nbsp;- the JWKS fetch to KeyCloak is left un-instrumented on purpose (I42/L3 accepted) -- no meter, its time falls  
&nbsp;   in the gw.outer-minus-gw.inner window; the cost lands on one request per key rotation (JWK set cached)  
**security.JweAwareJwtDecoder**  
&nbsp;- the JWE-path twin leaves the JWKS fetch un-instrumented for the same reason (I42/L3); the full note and the  
&nbsp;   instrument-it seam live at SecurityConfig.jwtDecoder()  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the management.endpoint block sat at column 0 (a sibling of management, not a child), so  
&nbsp;   endpoint.health.probes.enabled was silently ignored and /actuator/health/liveness + /readiness returned 404;  
&nbsp;   indented under management so the probe groups are exposed. Removed the dead management.health.db block (I1)  

**07/14/2026** mir0n  v1.2.11 -- the bulkhead is declared, sized from the pool, and no longer opens the breaker (T10)  
**config.ResilienceConfig**  
&nbsp;- the BULKHEAD is now declared and sized here. circuitBreakerConfig() adds ignoreExceptions(BulkheadFullException)  
&nbsp;- - a bulkhead rejection is "too many at once", not a backend fault, and must not open the breaker. New @Bean  
&nbsp;   bulkheadCustomizer() (Customizer) replaces the library default of 25  
&nbsp;   concurrent calls; bulkheadCap() reads max-concurrent-calls, and 0 (the default) DERIVES it from the pool it  
&nbsp;   backstops: spring.cloud.gateway.httpclient.pool.max-connections x queue-per-connection. slidingWindowType is  
&nbsp;   now a knob (TIME_BASED default, was hard-coded COUNT_BASED); half-open-calls default 5 -> 20  
**resources/application.yml**  
&nbsp;- esq.gateway.resilience.bulkhead block added: max-concurrent-calls (${GW_BULKHEAD_MAX_CONCURRENT:0}, 0 = derive  
&nbsp;   from the pool), queue-per-connection (${GW_BULKHEAD_QUEUE_PER_CONN:16}), max-wait-ms. circuit-breaker gains  
&nbsp;   sliding-window-type (${GW_CB_WINDOW_TYPE:TIME_BASED}); sliding-window 20 -> 30 (now SECONDS, not calls);  
&nbsp;   half-open-calls 5 -> 20  
**pom.xml**  
&nbsp;- io.github.resilience4j:resilience4j-bulkhead added -- the bulkhead classes were on the RUNTIME classpath but  
&nbsp;   the starter does not expose them for COMPILE, so ResilienceConfig could not name BulkheadConfig /  
&nbsp;   BulkheadFullException  

**07/12/2026** mir0n  v1.2.11 -- the gateway's request log line carries correlationId as a FIELD (O1/T9)  
**filters.RequestTraceFilter**  
&nbsp;- the INCOMING log line is wrapped in MDC.put/remove of PD_CORRELATION_ID and PD_REQUEST_ID, so correlationId is  
&nbsp;   emitted as a log FIELD and not only as message text. The gateway is reactive, so the servlet MdcFilter never  
&nbsp;   runs here; the put is safe because the log call is synchronous on this thread, and the remove is in a finally  
&nbsp;   because a reactor thread is pooled and a leaked entry would stamp the next request  

**07/11/2026** mir0n  v1.2.11 -- business meters (O1/T8): the token relay -- cache hit rate and the KeyCloak round-trip  
**security.tokenrelay.TokenRelayCache**  
&nbsp;- getOrAcquire() counts esq.biz.gw.tokenrelay.total (tag result = hit|miss). A GENUINE hit/miss: a hit serves  
&nbsp;   the request without touching KeyCloak, a miss is a live /token round-trip on the hot path -- so the hit RATE  
&nbsp;   is exactly how much of KeyCloak's latency the users are spared  
**security.tokenrelay.WebClientTokenRelayClient**  
&nbsp;- acquire() counts esq.biz.gw.tokenrelay.acquire.total and times esq.biz.gw.tokenrelay.duration (tag outcome =  
&nbsp;   ok|error|cancelled) around the KC /token call -- an EXTERNAL server on the hot path that nothing measured.  
&nbsp;   Wrapped in Mono.defer so the clock starts at SUBSCRIPTION, not assembly: a nanoTime() outside the chain is  
&nbsp;   captured when the Mono is BUILT and times the wrong window. doOnSuccess / doOnError / doOnCancel cover every  
&nbsp;   terminal signal, so a client that hangs up mid-relay does not vanish from the count  
**resources/application.yml**  
&nbsp;- esquire.observability.metrics.business-enabled: ${ESQ_METRICS_BUSINESS:true} added -- the sub-switch gating  
&nbsp;   the esq.biz.* domain tier under the observability master  

**07/11/2026** mir0n  v1.2.11 -- edge latency bands + edge bandwidth (O1/T5): the gateway's own timers; ONE config namespace  
**filters.ResponseTraceFilter**  
&nbsp;- the OUTER window is now also recorded as the esq.gw.outer Micrometer timer, tagged by the matched gateway  
&nbsp;   route id (ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR). Explicit ctor taking ObjectProvider:  
&nbsp;   absent when observability is off and the timer is then not recorded. Recording is INDEPENDENT of the  
&nbsp;   X-Capture-Metrics header instrument -- the header is still written only when the caller asks, the timer always  
**filters.InnerTimerFilter**  
&nbsp;- the downstream-call window is now also recorded as the esq.gw.inner Micrometer timer, tagged by the matched  
&nbsp;   gateway route id (ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR). Explicit ctor taking  
&nbsp;   ObjectProvider: absent when observability is off and the timer is then not recorded.  
&nbsp;   Recording is INDEPENDENT of the X-Capture-Metrics header instrument  
**resources/application.yml**  
&nbsp;- the tracing keys move under the umbrella: esquire.tracing.* -> esquire.observability.tracing.* (otlp-endpoint,  
&nbsp;   sampling-ratio, marks-enabled, excluded-paths, msg-bus-alive-trace); esquire.observability.metrics.*  
&nbsp;   sub-switches added -- histograms-enabled (ESQ_METRICS_HISTOGRAMS, default false) and bandwidth-enabled  
&nbsp;   (ESQ_METRICS_BANDWIDTH, default true)  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): Prometheus export + the observability umbrella switch  
**resources/application.yml**  
&nbsp;- esquire.observability.enabled (ESQ_OBSERVABILITY_ENABLED) now gates BOTH tracing and metrics (replaces the  
&nbsp;   tracing-only switch); management.prometheus.metrics.export.enabled + endpoints.web.exposure.include health, info, prometheus  

**07/09/2026** mir0n  v1.2.11 -- distributed tracing (O2/T3): the gateway seeds the trace with the settled correlation id  
config.CorrelationPropagatorConfig  (new)  
&nbsp;- the edge trace-context propagator: a @Primary Propagator whose extract() settles the correlation id  
&nbsp;   (Esq-Correlation-ID / X-Correlation-ID, kept when W3C-shaped, converted otherwise, else generated) and builds  
&nbsp;   the parent TraceContext with THAT id as the trace id, so traceId == correlationId for every client, whether  
&nbsp;   or not it sent a traceparent; inject() writes the traceparent alone, and fields() declares only that header  
&nbsp;   so the correlation headers are never cleared from the outgoing request  
**filters.RequestTraceFilter**  
&nbsp;- constructor takes ObjectProvider; settleTraceparent() and the downstream traceparent stamp removed  
&nbsp;   (the CorrelationPropagator injects it); currentTraceId(exchange) reads the trace id off the server request  
&nbsp;   observation; a one-shot WARN when tracing is on and a proxied request has no current span  
**resources/application.yml**  
&nbsp;- esquire.tracing.msg-bus-alive-trace: ${ESQ_MSG_BUS_ALIVE_TRACE:false} added  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): the gateway settles the trace id at the edge  
**filters.RequestTraceFilter**  
&nbsp;- obtainCorrelationId() returns a settled W3C-shaped id (EsqUtils.settleCorrelationId over  
&nbsp;   Esq-/X-Correlation-ID; X-Request-ID is no longer a seed); Esq-Correlation-ID is now stamped on EVERY  
&nbsp;   downstream request, not only when it was absent  
&nbsp;- settleTraceparent() added: keeps an incoming traceparent whose trace id equals the settled correlation id,  
&nbsp;   else mints a root one from it; the traceparent header is stamped downstream so span traceId == correlationId  
**error.ProblemDetailMill**  
&nbsp;- createProblemDetail(): the incoming correlation id runs through EsqUtils.settleCorrelationId(); the settled  
&nbsp;   value is set on BOTH the traceId and the correlationId problem-detail properties (was: traceId only when no  
&nbsp;   correlation id came in)  
GatewayApplication  
&nbsp;- @Import(TracingConfig.class): the common distributed-tracing wiring  
**resources/application.yml**  
&nbsp;- esquire.tracing block (enabled / otlp-endpoint / sampling-ratio / marks-enabled / excluded-paths) with  
&nbsp;   the ESQ_TRACING_* env overrides; management.tracing.enabled mirrors esquire.tracing.enabled  
k8s chart esquire-gateway/values.yaml  
&nbsp;- tracing block (enabled "false" / otlpEndpoint / samplingRatio / marksEnabled / excludedPaths) --  
&nbsp;   opt-in, off by default  
k8s chart esquire-gateway/templates/configmap.yaml  
&nbsp;- ESQ_TRACING_ENABLED / ESQ_OTLP_ENDPOINT / ESQ_TRACING_SAMPLING_RATIO / ESQ_TRACING_MARKS_ENABLED /  
&nbsp;   ESQ_TRACING_EXCLUDED_PATHS rendered from .Values.tracing  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**resources/application.yml**  
&nbsp;- actuator moved to a separate internal-only management port 8090 (MANAGEMENT_SERVER_PORT), off the public  
&nbsp;   server.port  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the tracing and metrics switches each read their own env var (ESQ_TRACING_ENABLED / ESQ_METRICS_ENABLED),  
&nbsp;   defaulting to the ESQ_OBSERVABILITY_ENABLED umbrella, so either pillar can be turned off on its own (I41)  
**config.KeycloakConfig**  
&nbsp;- the KC-admin client is un-instrumented at the wire on purpose (I39 covered) -- the KC-sync duration IS  
&nbsp;   measured at the operation grain (esq.biz.kc.sync.duration), only a per-call span is absent; copyright URL  
&nbsp;   mir0n.me -> mir0n.pro  
**messaging.KcRequestHandler**  
&nbsp;- esq.biz.kc.sync.duration is orthogonal to the bus-hop span pair -- the KC request/response hop IS traced  
&nbsp;   (PRODUCER/CONSUMER via AXRod), not a waterfall gap (I51)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**messaging.EntityBusAdapter**  
&nbsp;- the entity-broadcast receive worker stamps MDC via EsqContextHolder.applyMessage(event) and clears in a  
&nbsp;   finally (I10)  
**messaging.KcBusAdapter**  
&nbsp;- the kc-bus receive worker stamps MDC via EsqContextHolder.applyMessage(event) and clears in a finally (I10)  

**07/11/2026** mir0n  v1.2.11 -- business meters (O1/T8): the KeyCloak identity sync  
**messaging.KcRequestHandler**  
&nbsp;- handle() counts esq.biz.kc.sync.total and times esq.biz.kc.sync.duration (tags op = the BusConstants command,  
&nbsp;   outcome) in a finally; the switch is unchanged. A sync that arrives and then FAILS leaves Esquire and KeyCloak  
&nbsp;   disagreeing about who exists. The duration is the whole sync, not the admin client alone -- the KC round-trip  
&nbsp;   dominates it, and the name says what it measures rather than implying an isolation that was not built  
**resources/application.yml**  
&nbsp;- esquire.observability.metrics.business-enabled: ${ESQ_METRICS_BUSINESS:true} added -- the sub-switch gating  
&nbsp;   the esq.biz.* domain tier under the observability master  

**07/11/2026** mir0n  v1.2.11 -- observability config namespace + the metrics sub-switches (O1/T5)  
**resources/application.yml**  
&nbsp;- the tracing keys move under the umbrella: esquire.tracing.* -> esquire.observability.tracing.*  
&nbsp;   (otlp-endpoint, sampling-ratio, marks-enabled, excluded-paths, msg-bus-alive-trace);  
&nbsp;   esquire.observability.metrics.* sub-switches added -- histograms-enabled (ESQ_METRICS_HISTOGRAMS,  
&nbsp;   default false) and bandwidth-enabled (ESQ_METRICS_BANDWIDTH, default true)  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): Prometheus export + the observability umbrella switch  
**resources/application.yml**  
&nbsp;- esquire.observability.enabled (ESQ_OBSERVABILITY_ENABLED) now gates BOTH tracing and metrics (replaces the  
&nbsp;   tracing-only switch); management.prometheus.metrics.export.enabled + endpoints.web.exposure.include prometheus added  

**07/09/2026** mir0n  v1.2.11 -- distributed tracing (O2/T3): the KeyCloak path update is traced; span labels spelled out  
**service.impl.KcIdentityService**  
&nbsp;- @EsqTraced labels "KC ..." -> "Keycloak ..."; @EsqTraced on updateUserPath (esq.kc.update-path,  
&nbsp;   "Keycloak update path")  
**resources/application.yml**  
&nbsp;- esquire.tracing.msg-bus-alive-trace: ${ESQ_MSG_BUS_ALIVE_TRACE:false} added  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): trace marks on the KeyCloak identity calls  
**service.impl.KcIdentityService**  
&nbsp;- @EsqTraced on createUser / updateUserAuthState / deleteUser  
&nbsp;   (esq.kc.create-user / esq.kc.update-auth / esq.kc.delete-user)  
KcMasterApplication  
&nbsp;- @Import(TracingConfig.class): the common distributed-tracing wiring  
**resources/application.yml**  
&nbsp;- esquire.tracing block (enabled / otlp-endpoint / sampling-ratio / marks-enabled / excluded-paths) with  
&nbsp;   the ESQ_TRACING_* env overrides; management.tracing.enabled mirrors esquire.tracing.enabled  
k8s chart esquire-kcmaster/values.yaml  
&nbsp;- tracing block (enabled "false" / otlpEndpoint / samplingRatio / marksEnabled / excludedPaths) --  
&nbsp;   opt-in, off by default  
k8s chart esquire-kcmaster/templates/configmap.yaml  
&nbsp;- ESQ_TRACING_ENABLED / ESQ_OTLP_ENDPOINT / ESQ_TRACING_SAMPLING_RATIO / ESQ_TRACING_MARKS_ENABLED /  
&nbsp;   ESQ_TRACING_EXCLUDED_PATHS rendered from .Values.tracing  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**resources/application.yml**  
&nbsp;- the KC send-retry comment now states the honest limit: it covers a send that THROWS (broker down at send time),  
&nbsp;   NOT a request lost after landing on the non-persistent kc bus (reply tracking deferred)  
&nbsp;- actuator moved to a separate internal-only management port 8090 (MANAGEMENT_SERVER_PORT), off the public  
&nbsp;   server.port  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the tracing and metrics switches each read their own env var (ESQ_TRACING_ENABLED / ESQ_METRICS_ENABLED),  
&nbsp;   defaulting to the ESQ_OBSERVABILITY_ENABLED umbrella, so either pillar can be turned off on its own (I41)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**messaging.KcBusAdapter**  
&nbsp;- the kc-bus receive worker (onResponse) stamps MDC via EsqContextHolder.applyMessage(event) and clears in a  
&nbsp;   finally (I10)  

**07/14/2026** mir0n  v1.2.11 -- the KC request leg holds instead of dropping when its feed is full (T10)  
**resources/application.yml**  
&nbsp;- kc-bus x-rod: feed-await-ms: ${KEYSMITH_KC_FEED_AWAIT_MS:0} added -- 0 = wait forever, so a full feed holds  
&nbsp;   the producer instead of discarding the KC sync event (the default, 10000ms, DROPS it once the feed has been  
&nbsp;   full that long)  

**07/11/2026** mir0n  v1.2.11 -- the business-meter sub-switch (O1/T8)  
**resources/application.yml**  
&nbsp;- esquire.observability.metrics.business-enabled: ${ESQ_METRICS_BUSINESS:true} added -- the sub-switch gating  
&nbsp;   the esq.biz.* domain tier under the observability master  

**07/11/2026** mir0n  v1.2.11 -- observability config namespace + the metrics sub-switches (O1/T5)  
**resources/application.yml**  
&nbsp;- the tracing keys move under the umbrella: esquire.tracing.* -> esquire.observability.tracing.*  
&nbsp;   (otlp-endpoint, sampling-ratio, marks-enabled, excluded-paths, msg-bus-alive-trace);  
&nbsp;   esquire.observability.metrics.* sub-switches added -- histograms-enabled (ESQ_METRICS_HISTOGRAMS,  
&nbsp;   default false) and bandwidth-enabled (ESQ_METRICS_BANDWIDTH, default true)  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): Prometheus export + the observability umbrella switch  
**resources/application.yml**  
&nbsp;- esquire.observability.enabled (ESQ_OBSERVABILITY_ENABLED) now gates BOTH tracing and metrics (replaces the  
&nbsp;   tracing-only switch); management.prometheus.metrics.export.enabled + endpoints.web.exposure.include prometheus added  

**07/09/2026** mir0n  v1.2.11 -- the RR liveness round-trip trace knob  
**resources/application.yml**  
&nbsp;- esquire.tracing.msg-bus-alive-trace: ${ESQ_MSG_BUS_ALIVE_TRACE:false} added  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): trace marks on the access-profile commands  
**service.impl.KeySmithService**  
&nbsp;- @EsqTraced on esquireKey / esquireKeySave (esq.svc.key.read / esq.svc.key.save)  
KeySmithApplication  
&nbsp;- @Import(TracingConfig.class): the common distributed-tracing wiring  
**resources/application.yml**  
&nbsp;- esquire.tracing block (enabled / otlp-endpoint / sampling-ratio / marks-enabled / excluded-paths) with  
&nbsp;   the ESQ_TRACING_* env overrides; management.tracing.enabled mirrors esquire.tracing.enabled  
k8s chart esquire-keysmith/values.yaml  
&nbsp;- tracing block (enabled "false" / otlpEndpoint / samplingRatio / marksEnabled / excludedPaths) --  
&nbsp;   opt-in, off by default  
k8s chart esquire-keysmith/templates/configmap.yaml  
&nbsp;- ESQ_TRACING_ENABLED / ESQ_OTLP_ENDPOINT / ESQ_TRACING_SAMPLING_RATIO / ESQ_TRACING_MARKS_ENABLED /  
&nbsp;   ESQ_TRACING_EXCLUDED_PATHS rendered from .Values.tracing  

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**xrod.impl.XRod**  
&nbsp;- setWorker(subscription) comment: set the subscription BEFORE start(); the x-rod does not support changing a  
&nbsp;   broker subscription on the fly  
**xrod.impl.sublayer.AliveSession**  
&nbsp;- comment: the tick() heartbeat uses a BLOCKING put (not tryPut) -- it fires only when the leg is idle, so the  
&nbsp;   feed is drained/empty and put() returns at once; never drops a heartbeat  
**xrod.impl.sublayer.AliveSessionRR**  
&nbsp;- comment: the SERVER HeartBeat reply uses a BLOCKING put -- a reply can be needed while the leg is busy, and  
&nbsp;   dropping it would cause a false SERVER-DOWN at the client  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**o11y.IRodTracer**  
&nbsp;- the alive-trace opt-in key is namespaced under esquire.observability.tracing.msg-bus-alive-trace (was  
&nbsp;   esquire.tracing.*)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**o11y.RodObserverHolder**  
&nbsp;- registrar-vs-bus-start ordering tripwire (I11): a feedDepthAgainstNoop latch + a develop-channel logger.  
&nbsp;   noteFeedDepthAgainstNoop() latches when a feed-depth gauge is registered while the observer is still NOOP;  
&nbsp;   setObserver() logs an ERROR if a real observer is installed AFTER that. Silent when observability is off  
**xrod.impl.AXRod**  
&nbsp;- runEngine() calls RodObserverHolder.noteFeedDepthAgainstNoop() when the observer is still NOOP at feed-depth  
&nbsp;   registration, arming the ordering tripwire (I11)  

**07/14/2026** mir0n  v1.2.11 -- feed-await-ms: a producer leg can HOLD on a full feed instead of dropping the event (T10)  
**catalog.XRodParams**  
&nbsp;- feed-await-ms in SCALARS + feedAwaitMsOr(long) getter -- how long a producer waits on a FULL feed before the  
&nbsp;   event is DISCARDED; <= 0 = wait forever (backpressure, never drop). longOr(key, def) helper added alongside  
&nbsp;   intOr / boolOr  
**xrod.impl.AXRod**  
&nbsp;- feedAwaitMs read from params (feed-await-ms, default BoundedQueueRig.DEFAULT_AWAIT_TIMEOUT_MS) and applied to  
&nbsp;   the feed via setPutAwaitMs in buildEngine -- <= 0 holds the producer on a full feed instead of discarding the  
&nbsp;   event  

**07/11/2026** mir0n  v1.2.11 -- bus meters (O1/T5): the bus-hop meter hook, declared by the bus; trace + metrics unified into ONE bus observer  
o11y.IRodMeters  (new)  
&nbsp;- the bus-hop METER hook, DECLARED by the messaging bus. The mirror of IRodTracer for metrics: the bus deals  
&nbsp;   only in String / primitives / IntSupplier through it and imports no Micrometer (or anything above itself);  
&nbsp;   the Micrometer-backed implementation is the host application's, handed in via RodObserverHolder as part of  
&nbsp;   the ONE bus observer. sent / sendDuration / received / error / retryBackoff / retryDropped report events;  
&nbsp;   registerFeedDepth / registerRetryHeld hand over an IntSupplier the host reads as a gauge. NOOP = zero cost  
o11y.IRodObserver  (new)  
&nbsp;- the ONE bus-hop observer umbrella: joins IRodTracer + IRodMeters so a single host object covers both, is  
&nbsp;   held once in RodObserverHolder and is registered by one bean. of(IRodTracer, IRodMeters) combines two  
&nbsp;   separate hooks into one observer; NOOP. The interfaces stay separate -- only the object / holder /  
&nbsp;   registrar / umbrella switch are unified  
o11y.RodObserverHolder  (new -- was RodTracerHolder)  
&nbsp;- generalised from RodTracerHolder to the ONE bus observer umbrella: holds a single volatile IRodObserver  
&nbsp;   (default NOOP); setObserver() registers it, observer() / tracer() / meters() return it as each view. One  
&nbsp;   object, two views -- so a trace seam and a metric seam read the SAME registered observer  
**o11y.IRodTracer**  
&nbsp;- javadoc {@link} repointed RodTracerHolder -> RodObserverHolder  
**xrod.impl.AXRod**  
&nbsp;- the tracer holder is repointed to o11y.RodObserverHolder. The bus METERS are emitted from the same seams  
&nbsp;   the tracer already owns: onSendSuccess -> sent(), onSendError -> error(..,"send"), the receive worker ->  
&nbsp;   received() / error(..,"receive"), and send() times the whole dispatch into sendDuration() in a finally.  
&nbsp;   runEngine registers the feed-depth gauge (registerFeedDepth(meterBusId(), meterSlotId(), feed::size)).  
&nbsp;   Session (heartbeat) events are excluded from every meter. meterBusId() / meterSlotId() added (the identity's  
&nbsp;   bus / slot, or the rod name when it has no identity)  
**xrod.impl.sublayer.SendRetrySublayer**  
&nbsp;- retry meters over o11y.RodObserverHolder.meters(): start() registers the held-count gauge  
&nbsp;   (registerRetryHeld(.., this::heldCount)), a hold reports retryBackoff(busId, backoffMs) and drop() reports  
&nbsp;   retryDropped(busId, slotId); start() added as an ISessionSublayer override  
**xrod.impl.sublayer.AliveSessionRR**  
&nbsp;- RodTracerHolder -> RodObserverHolder at the four alive round-trip trace call sites  

**07/09/2026** mir0n  v1.2.11 -- distributed tracing (O2/T3): the bus-hop trace hook, declared by the bus; the RR liveness round-trip; the bus drops its Esquire dependency  
o11y.IRodTracer  (new)  
&nbsp;- the bus-hop trace hook, DECLARED by the messaging bus. The bus deals only in String + Runnable through it  
&nbsp;   and imports nothing above itself; the OTel-backed implementation is the host application's and is handed in  
&nbsp;   via RodTracerHolder. Legs: outbound() on the PRODUCER thread stamps the traceparent (parent span) onto the  
&nbsp;   event; inbound() on the CONSUMER (pool) thread runs the worker inside a span continuing the producer's trace;  
&nbsp;   aliveOutbound() / aliveInbound() are the same pair for the RR liveness round-trip; newTraceId() mints a trace  
&nbsp;   id in the tracer's own shape, so the bus never has to know what one looks like; aliveTrace() carries the  
&nbsp;   host's opt-in for the round-trip trace. traceId is ALWAYS the correlationId (authoritative); the traceparent  
&nbsp;   only carries the parent span id. NOOP when tracing is off  
o11y.RodTracerHolder  (new)  
&nbsp;- the single-slot hand-off for the bus-hop trace hook. The host application's tracing config sets its  
&nbsp;   IRodTracer once at startup (only when tracing is enabled); the x-rod engine reads tracer() at each bus hop.  
&nbsp;   Defaults to IRodTracer.NOOP so the bus pays nothing when tracing is off / never registered. Holds the tracer  
&nbsp;   and nothing else -- the RR liveness round-trip switch rides on the tracer (IRodTracer.aliveTrace())  
BusConstants  
&nbsp;- FIELD_TRACEPARENT ("TraceParent", FIX 50014) added  
RodEvent  
&nbsp;- the record gains a traceparent component (last); the applMsgId-shaped constructor leaves it null;  
&nbsp;   withTraceparent(String) copy added, withApplMsgId preserves it  
**xrod.RodEventCodec**  
&nbsp;- toProps() writes FIELD_TRACEPARENT on both the entity and the session branch when the event carries one;  
&nbsp;   fromProps() reads it back via withTraceparent()  
**xrod.impl.AXRod**  
&nbsp;- transmit() stamps the W3C traceparent on the caller's thread for non-session events  
&nbsp;   (o11y.RodTracerHolder.tracer().outbound()); the receive path runs the pool worker inside inbound() and, on a rod  
&nbsp;   that tracesAliveRoundTrip() with msg-bus-alive-trace on, wraps onReceiveSessn in aliveInbound();  
&nbsp;   tracesAliveRoundTrip() (false) and aliveMsgLabel(String) added  
**xrod.impl.XRodRR**  
&nbsp;- tracesAliveRoundTrip() overridden to true  
**xrod.impl.sublayer.AliveSessionRR**  
&nbsp;- when the registered tracer's aliveTrace() is on: a CLIENT keepAliveEvent() takes its correlation id from  
&nbsp;   o11y.IRodTracer.newTraceId() and opens a ROOT producer span (aliveOutbound asRoot=true), stamping the  
&nbsp;   traceparent on the TestRequest; a SERVER onReceiveSessn() stamps its HeartBeat reply from a nested producer  
&nbsp;   span (asRoot=false)  
MessagingBus  
&nbsp;- instanceId(): the per-instance token now reads pro.mir0n.utils.HostId.instanceNo() (was EsqUtils.instanceNo());  
&nbsp;   the bus no longer imports anything Esquire  

### mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt

mir0n java common frameworks -- pro.mir0n.utils  

**07/14/2026** mir0n  v1.2.11 -- BoundedQueueRig: the producer's wait on a FULL queue is its own knob, and can be endless (T10)  
**concurrent.BoundedQueueRig**  
&nbsp;- putAwaitMs + setPutAwaitMs(long) added -- the producer's own wait on a FULL queue, split off from  
&nbsp;   awaitTimeoutMs (which now only paces the worker's missed-signal re-check). <= 0 = NO TIMEOUT: put() awaits  
&nbsp;   notFull without a deadline and never discards; shutdown() / clear() already signal notFull, so a parked  
&nbsp;   producer still wakes. Default unchanged (10s, drops)  

**07/09/2026** mir0n  v1.2.11 -- pro.mir0n.utils becomes its own module (mir0n-utils); HostId  
HostId  (new)  
&nbsp;- this instance's host identity -- instanceHost() + the lazy-cached instanceNo(), moved down from esquire  
&nbsp;   common.EsqUtils (which now delegates) so the messaging bus can build its default rod-id without depending  
&nbsp;   on anything Esquire  
**concurrent.IQueueRig**  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.concurrent unchanged  
**concurrent.BoundedQueueRig**  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.concurrent unchanged  
**concurrent.WorkerPool**  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.concurrent unchanged  
**taijitu.***  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.taijitu unchanged  

**07/01/2026** mir0n  v1.2.10 -- a common bounded worker pool with three thread models (platform / virtual / virtual-per-task)  
concurrent.WorkerPool  (new)  
&nbsp;- a bounded worker pool with three thread models: PLATFORM / VIRTUAL = a FIXED pool of `size` reused workers  
&nbsp;   (OS threads, or `size` virtual threads); VIRTUAL_PER_TASK = one virtual thread per task, capped by a  
&nbsp;   Semaphore(size) (uncapped when size=0). create(name, size, mode); submit(Runnable) applies the bound  
&nbsp;   (acquire / execute / release); shutdown(awaitSeconds) drains; capacity(); Mode.of(String) parses the mode  

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

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**07/23/2026** mir0n  v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- round3(): the amount and the new balance are rounded to 3 decimals (the NUMERIC(16,3) scale, half away from  
&nbsp;   zero) before any check or store, so double FP dust never reaches the ledger  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- credit leg promotes the shared fields map (AMOUNT overwritten with the credit amount) and passes it straight  
&nbsp;   through -- per-request map, deliberately not cloned  
**service.BizValidatorFactory**  
&nbsp;- account validation guard refined with a balance != 0 condition  
**resources/application.yml**  
&nbsp;- the entity-broadcast leg gets feed-await-ms:0 (wait forever) so a full feed HOLDS the producer instead of  
&nbsp;   dropping an entity broadcast  
&nbsp;- actuator moved to a separate internal-only management port 8090 (MANAGEMENT_SERVER_PORT), off the public  
&nbsp;   server.port  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**resources/application.yml**  
&nbsp;- the tracing and metrics switches each read their own env var (ESQ_TRACING_ENABLED / ESQ_METRICS_ENABLED),  
&nbsp;   defaulting to the ESQ_OBSERVABILITY_ENABLED umbrella, so either pillar can be turned off on its own (I41)  

**07/11/2026** mir0n  v1.2.11 -- business meters (O1/T8): the money path  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- esquireCommandAcct() counts esq.biz.acct.tx.total and times esq.biz.acct.tx.duration (tags type =  
&nbsp;   AcctOperation.Code, outcome = ok|denied|error); _esquireCommandAcct() counts esq.biz.acct.fx.apply.total when  
&nbsp;   convRate is non-null (a conversion rate is present only on the cross-currency leg, so it IS the FX  
&nbsp;   application). operTag() added: the type tag is NULL-SAFE because these read from a finally, and a raw  
&nbsp;   oper.name() there throws an NPE that REPLACES the real exception on its way out  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- esquireCommandAcct() counts esq.biz.acct.tx.total and times esq.biz.acct.tx.duration (tags type, outcome) --  
&nbsp;   its OWN meters, because this override does NOT call super, so the meters on AcctTransactionProcessorSingle  
&nbsp;   never see a transfer and the whole transfer path would have been silently missing from the money panel  
**service.impl.PacManService**  
&nbsp;- deleteAcct() counts esq.biz.acct.close.total (tag purge = test-house|none), only once the delete has SUCCEEDED  
&nbsp;   past the three guards, so a refused delete never inflates it; the purge tag names the Test-House branch that  
&nbsp;   forces those guards open. The branch condition is lifted to a local flag  
**resources/application.yml**  
&nbsp;- esquire.observability.metrics.business-enabled: ${ESQ_METRICS_BUSINESS:true} added -- the sub-switch gating  
&nbsp;   the esq.biz.* domain tier under the observability master  

**07/11/2026** mir0n  v1.2.11 -- observability config namespace + the metrics sub-switches (O1/T5)  
**resources/application.yml**  
&nbsp;- the tracing keys move under the umbrella: esquire.tracing.* -> esquire.observability.tracing.*  
&nbsp;   (otlp-endpoint, sampling-ratio, marks-enabled, excluded-paths, msg-bus-alive-trace);  
&nbsp;   esquire.observability.metrics.* sub-switches added -- histograms-enabled (ESQ_METRICS_HISTOGRAMS,  
&nbsp;   default false) and bandwidth-enabled (ESQ_METRICS_BANDWIDTH, default true)  

**07/10/2026** mir0n  v1.2.11 -- metrics foundation (O1): Prometheus export + the observability umbrella switch  
**resources/application.yml**  
&nbsp;- esquire.observability.enabled (ESQ_OBSERVABILITY_ENABLED) now gates BOTH tracing and metrics (replaces the  
&nbsp;   tracing-only switch); management.prometheus.metrics.export.enabled + endpoints.web.exposure.include prometheus added  

**07/09/2026** mir0n  v1.2.11 -- the RR liveness round-trip trace knob  
**resources/application.yml**  
&nbsp;- esquire.tracing.msg-bus-alive-trace: ${ESQ_MSG_BUS_ALIVE_TRACE:false} added  

**07/08/2026** mir0n  v1.2.11 -- distributed tracing (O2): trace marks on the account commands  
**service.impl.PacManService**  
&nbsp;- @EsqTraced on esquireCommand / esquireCommandSave / esquireCommandDelete  
&nbsp;   (esq.svc.acct.read / save / delete)  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- esquireCommandAcct() body wrapped in EsqTraceMark.around("esq.svc.acct.tx", "account transaction", ...) -- this  
&nbsp;   processor is constructed with new() by AcctTransactionService, so Spring never proxies it and @EsqTraced  
&nbsp;   would not be advised  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- esquireCommandAcct() delegates to the new private esquireCommandTransfer(), wrapped in  
&nbsp;   EsqTraceMark.around("esq.svc.acct.tx", "account transfer", ...) -- this processor is constructed with new() by  
&nbsp;   AcctTransactionService, so Spring never proxies it and @EsqTraced would not be advised  
PacManApplication  
&nbsp;- @Import(TracingConfig.class): the common distributed-tracing wiring  
**resources/application.yml**  
&nbsp;- esquire.tracing block (enabled / otlp-endpoint / sampling-ratio / marks-enabled / excluded-paths) with  
&nbsp;   the ESQ_TRACING_* env overrides; management.tracing.enabled mirrors esquire.tracing.enabled  
k8s chart esquire-pacman/values.yaml  
&nbsp;- tracing block (enabled "false" / otlpEndpoint / samplingRatio / marksEnabled / excludedPaths) --  
&nbsp;   opt-in, off by default  
k8s chart esquire-pacman/templates/configmap.yaml  
&nbsp;- ESQ_TRACING_ENABLED / ESQ_OTLP_ENDPOINT / ESQ_TRACING_SAMPLING_RATIO / ESQ_TRACING_MARKS_ENABLED /  
&nbsp;   ESQ_TRACING_EXCLUDED_PATHS rendered from .Values.tracing  

### tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt


**07/12/2026** mir0n  v1.2.11 -- JMS delivery mode as a declared bus param (O1/T9)  
**tp.activemq.TransportProvider**  
&nbsp;- PARAM_PERSISTENT ("persistent") added: the JMS delivery mode read from transport.params.persistent (absent =  
&nbsp;   false = NON_PERSISTENT), applied on the JmsTemplate via setExplicitQosEnabled(true) + setDeliveryPersistent(...)  
&nbsp;- - without explicit QoS the delivery mode is silently ignored. Excluded from withParams (a setter, not a  
&nbsp;   broker-URI option); the publisher-opened devLog line now carries persistent=  

---

## Commits

```

-- 2026-07-27 | commit: 3effe6a | mir0n.the.programmer | v1.2.11 -- Finalization --
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-oke.yml
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
M	k8s-oci/oke-o11y-off.bat
M	k8s-oci/oke-o11y-on.bat
 6 files changed, 56 insertions(+), 19 deletions(-)


-- 2026-07-26 | commit: fd0a7af | mir0n.the.programmer | v1.2.11 -- T13: sprint documentation finalization --
M	README.md
A	Releases.md
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/resources/esq-object-kinds.xml
M	doc/DatabaseDictionary.md
D	doc/DefaultRule.md
A	doc/EntityDictionary.md
M	doc/Esquire.AuditLoggingStack.md
R090	doc/keyCloak-gateway.JWE.md	doc/Esquire.Auth.TokenPatterns.md
R083	doc/keySmithCredentialRoutine.md	doc/Esquire.Auth.keySmithRoutine.md
A	doc/Esquire.Auth.md
M	doc/Esquire.BizTree.md
A	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
A	doc/Esquire.GrafanaGuide.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
R091	doc/Messaging.md	doc/Esquire.Messaging.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.Guides.md
R083	doc/Message.Structure.md	doc/Esquire.MessagingBus.MessageStructure.md
A	doc/Esquire.MessagingBus.Q&A.md
M	doc/Esquire.MessagingBus.md
R084	doc/Logging.md	doc/Esquire.ObservabilityStack.Logging.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
D	doc/H2BizTree.md
D	doc/OCI.Pricing.md
D	doc/Object.Kind.enum.md
D	doc/WhereToGo.md
D	doc/entity.path.semantics.md
A	doc/img/auth-collaboration.svg
A	doc/img/auth-connect-down.svg
A	doc/img/auth-connect-up.svg
A	doc/img/auth-move.svg
A	doc/img/auth-person-props.svg
A	doc/img/auth-tfa-disable.svg
A	doc/img/auth-tfa-enable.svg
A	doc/img/keysmith-connect.svg
A	doc/img/keysmith-password.svg
A	doc/img/keysmith-totp.svg
A	doc/img/move-ordering.svg
A	doc/img/move-race.svg
A	doc/install/Docker.md
A	doc/install/LocalK8s.md
A	doc/logo/OTelCollector.png
A	doc/logo/alloy_icon.png
M	doc/media/ComponentModel.png
A	doc/media/ObservabilityStack.svg
A	doc/media/grafana_icon.svg
A	doc/media/logging-screenshot.png
A	doc/media/loki_icon.svg
A	doc/media/o11yStack.png
A	doc/media/prometheus_logo.svg
A	doc/media/req-latency-band-screenshot.png
A	doc/media/services-screenshot.png
A	doc/media/tempo_logo.svg
A	doc/media/token-detour.svg
A	doc/media/token-exchange-v1v2.svg
A	doc/media/token-formats.svg
A	doc/media/token-ideal.svg
A	doc/media/topology-screenshot.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
A	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
A	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/TopologyDriftGuardTest.java
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
 74 files changed, 5271 insertions(+), 2509 deletions(-)

-- 2026-07-23 | commit: c9004de | mir0n.the.programmer | v1.2.11 -- homework: fix/resolve issues found in the fresh-mind audit --
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	compose/compose.yaml
A	compose/o11y-log-off.bat
A	compose/o11y-log-on.bat
A	compose/o11y-test.bat
A	compose/o11y-verify.bat
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/prometheus.yml
M	doc/Esquire.Q&A.md
M	doc/release_notes.txt
M	doc/review/Esquire.PerfMatrix-07-17.md
R100	enyMan/Dockerfilel.lx	enyMan/Dockerfile.lx
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqAcctRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/CreateReconcileItem.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/EntityIdGenerator.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayCache.java
M	gateway/src/main/resources/application.yml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorConfigWiringTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverterTest.java
M	k8s-oci/esquire-topology.yml
M	k8s-oci/grafana/esquire-services.json
A	k8s-oci/oke-config-parity.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/deployment.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/templates/deployment.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/templates/secret.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/prometheus/templates/configmap.yaml
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
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
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	keycloak/import/esquire.json
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
A	test/config-parity/config-parity.py
 109 files changed, 1981 insertions(+), 264 deletions(-)

-- 2026-07-19 | commit: a87e0ab | mir0n.the.programmer | v1.2.11 -- T12: on-demand monitoring on the OKE cloud cluster --
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-topology.py
M	doc/release_notes.txt
A	k8s-oci/grafana/esquire-logging.json
A	k8s-oci/grafana/esquire-services.json
A	k8s-oci/grafana/esquire-topology.json
A	k8s-oci/oke-grafana-forward.bat
A	k8s-oci/oke-o11y-off.bat
A	k8s-oci/oke-o11y-on.bat
A	k8s-oci/oke-o11y-test.bat
A	k8s-oci/oke-o11y-verify.bat
A	k8s-oci/oke-pg-forward.bat
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/infra/alloy/templates/deployment.yaml
M	k8s/charts/infra/grafana/templates/deployment.yaml
M	k8s/charts/infra/loki/templates/deployment.yaml
M	k8s/charts/infra/otel-collector/templates/deployment.yaml
M	k8s/charts/infra/postgres-exporter/templates/deployment.yaml
M	k8s/charts/infra/prometheus/templates/deployment.yaml
M	k8s/charts/infra/tempo/templates/deployment.yaml
A	k8s/o11y-test.bat
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
 28 files changed, 11120 insertions(+), 45 deletions(-)

-- 2026-07-17 | commit: 647ee37 | mir0n.the.programmer | v1.2.11 -- T11 cleanup: fix/resolve issues noted during development (complete) --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/W3CTraceContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
M	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCapTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqW3cIdConformanceTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
A	common/src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension
A	common/src/test/resources/junit-platform.properties
M	compose/compose.yaml
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-datasources.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/o11y/otel-collector-config.yaml
M	compose/o11y/prometheus.yml
A	compose/o11y/rules.yml
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.MessagingBus.md
A	doc/Esquire.ObservabilityStack.Inventory.csv
M	doc/release_notes.txt
A	doc/review/Esquire.PerfMatrix-07-17.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JweAwareJwtDecoder.java
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/otel-collector/templates/configmap.yaml
A	k8s/charts/infra/prometheus/rules.yml
M	k8s/charts/infra/prometheus/templates/configmap.yaml
M	k8s/o11y-forward.bat
A	k8s/o11y-full-on.bat
A	k8s/o11y-log-off.bat
A	k8s/o11y-log-on.bat
A	k8s/o11y-verify.bat
M	k8s/values/backend.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
A	test/o11y/o11y-drive.py
A	test/o11y/o11y-inventory.py
A	test/o11y/o11y-verify.py
 83 files changed, 6659 insertions(+), 535 deletions(-)

-- 2026-07-15 | commit: 7814346 | mir0n.the.programmer | v1.2.11 -- T11 cleanup: fix/resolve issues noted during development --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
M	compose/compose.yaml
A	compose/docker-compose-down.bat
A	compose/docker-compose-up.bat
M	compose/o11y/alloy-config.alloy
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/loki-config.yaml
M	compose/o11y/tempo-config.yaml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/alloy/templates/configmap.yaml
M	k8s/charts/infra/alloy/templates/deployment.yaml
A	k8s/charts/infra/alloy/templates/pvc.yaml
M	k8s/charts/infra/alloy/values.yaml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/templates/deployment.yaml
A	k8s/charts/infra/grafana/templates/pvc.yaml
M	k8s/charts/infra/grafana/values.yaml
M	k8s/charts/infra/loki/templates/configmap.yaml
M	k8s/charts/infra/loki/templates/deployment.yaml
A	k8s/charts/infra/loki/templates/pvc.yaml
M	k8s/charts/infra/loki/values.yaml
M	k8s/charts/infra/otel-collector/templates/deployment.yaml
M	k8s/charts/infra/otel-collector/values.yaml
M	k8s/charts/infra/prometheus/templates/deployment.yaml
A	k8s/charts/infra/prometheus/templates/pvc.yaml
M	k8s/charts/infra/prometheus/values.yaml
M	k8s/charts/infra/tempo/templates/configmap.yaml
M	k8s/charts/infra/tempo/templates/deployment.yaml
A	k8s/charts/infra/tempo/templates/pvc.yaml
M	k8s/charts/infra/tempo/values.yaml
M	k8s/o11y-off.bat
M	k8s/o11y-on.bat
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
 76 files changed, 706 insertions(+), 91 deletions(-)

-- 2026-07-14 | commit: e1aef58 | mir0n.the.programmer | v1.2.11 -- circuit breaker: its parameters set up and verified under a heavy load test --
M	activemq/conf/activemq.xml
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	doc/release_notes.txt
A	doc/review/Esquire.PerfMatrix-07-14.md
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/ResilienceConfig.java
M	gateway/src/main/resources/application.yml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/values/activemq.yaml
M	k8s/values/backend.yaml
M	k8s/values/gateway.yaml
M	k8s/values/keysmith.yaml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
M	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
M	postgres/Dockerfile
 25 files changed, 1605 insertions(+), 26 deletions(-)

-- 2026-07-13 | commit: 43371f1 | mir0n.the.programmer |  v1.2.11 -- observability: the message broker reports, the three signals link up, and the system draws itself --
A	.gitattributes
M	activemq/Dockerfile
M	activemq/conf/activemq.xml
A	activemq/conf/jmx-exporter.yml
A	activemq/esq-entrypoint.sh
M	compose/compose-rebuild.bat
M	compose/compose.yaml
M	compose/o11y-off.bat
M	compose/o11y-on.bat
M	compose/o11y/grafana/gen-dashboard.py
A	compose/o11y/grafana/gen-datasources.py
A	compose/o11y/grafana/gen-topology.py
A	compose/o11y/grafana/icons/activemq.svg
A	compose/o11y/grafana/icons/aukeep.svg
A	compose/o11y/grafana/icons/biztree.svg
A	compose/o11y/grafana/icons/enyman.svg
A	compose/o11y/grafana/icons/explorer.svg
A	compose/o11y/grafana/icons/gateway.svg
A	compose/o11y/grafana/icons/kcmaster.svg
A	compose/o11y/grafana/icons/keycloak.svg
A	compose/o11y/grafana/icons/keysmith.svg
A	compose/o11y/grafana/icons/pacman.svg
A	compose/o11y/grafana/icons/postgres.svg
A	compose/o11y/grafana/provisioning/dashboards/esquire-logging.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
A	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/o11y/grafana/provisioning/datasources/loki.yaml
M	compose/o11y/grafana/provisioning/datasources/prometheus.yaml
M	compose/o11y/grafana/provisioning/datasources/tempo.yaml
M	compose/o11y/otel-collector-config.yaml
M	compose/o11y/prometheus.yml
M	compose/topology/esquire-topology.yml
M	doc/release_notes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/infra/activemq/templates/statefulset.yaml
M	k8s/charts/infra/activemq/values.yaml
A	k8s/charts/infra/grafana/dashboards/esquire-logging.json
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
A	k8s/charts/infra/grafana/dashboards/esquire-topology.json
A	k8s/charts/infra/grafana/icons/activemq.svg
A	k8s/charts/infra/grafana/icons/aukeep.svg
A	k8s/charts/infra/grafana/icons/biztree.svg
A	k8s/charts/infra/grafana/icons/enyman.svg
A	k8s/charts/infra/grafana/icons/explorer.svg
A	k8s/charts/infra/grafana/icons/gateway.svg
A	k8s/charts/infra/grafana/icons/kcmaster.svg
A	k8s/charts/infra/grafana/icons/keycloak.svg
A	k8s/charts/infra/grafana/icons/keysmith.svg
A	k8s/charts/infra/grafana/icons/pacman.svg
A	k8s/charts/infra/grafana/icons/postgres.svg
M	k8s/charts/infra/grafana/templates/configmap-dashboards.yaml
M	k8s/charts/infra/grafana/templates/configmap-datasource.yaml
A	k8s/charts/infra/grafana/templates/configmap-icons.yaml
M	k8s/charts/infra/grafana/templates/deployment.yaml
M	k8s/charts/infra/otel-collector/templates/configmap.yaml
M	k8s/charts/infra/otel-collector/templates/service.yaml
M	k8s/charts/infra/postgres-exporter/values.yaml
M	k8s/charts/infra/prometheus/templates/configmap.yaml
M	k8s/charts/infra/prometheus/templates/deployment.yaml
M	k8s/charts/infra/prometheus/values.yaml
M	k8s/k8s-rebuild.bat
M	k8s/o11y-off.bat
M	k8s/o11y-on.bat
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
 84 files changed, 17009 insertions(+), 296 deletions(-)

-- 2026-07-12 | commit: 2f8efcf | mir0n.the.programmer | v1.2.11 -- observability: what each service DOES, not just how it runs --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
M	compose/compose.yaml
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
 59 files changed, 2455 insertions(+), 269 deletions(-)

-- 2026-07-11 | commit: 0021309 | mir0n.the.programmer | v1.2.11 -- observability: four ways of getting the measurements wrong are now impossible --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/NoRawGaugeBuilderTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/PerformanceAspectTest.java
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	doc/release_notes.txt
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
A	k8s/o11y-forward-stop.bat
A	k8s/o11y-forward.bat
 12 files changed, 473 insertions(+), 54 deletions(-)

-- 2026-07-11 | commit: 4d369c5 | mir0n.the.programmer | v1.2.11 -- observability: the messaging bus, the time breakdown and the traffic volume are measured --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
R061	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodTracer.java	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
R069	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqRodTracerTest.java	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqRodObserverTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/PerformanceAspectTest.java
M	compose/compose.yaml
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/InnerTimerFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
D	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodTracerHolder.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
D	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodTracerHolderTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
 63 files changed, 2585 insertions(+), 291 deletions(-)

-- 2026-07-10 | commit: a8f1435 | mir0n.the.programmer | v1.2.11 -- observability: live measurements for every service on one dashboard --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/application.yml
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraced.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTracedAspect.java
R072	common/src/main/java/pro/mir0n/esquire/backend/o11y/TracingConfig.java	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
M	compose/compose.yaml
M	compose/o11y-off.bat
M	compose/o11y-on.bat
A	compose/o11y/grafana/gen-dashboard.py
A	compose/o11y/grafana/provisioning/dashboards/dashboards.yaml
A	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
A	compose/o11y/grafana/provisioning/datasources/prometheus.yaml
A	compose/o11y/prometheus.yml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorConfig.java
M	gateway/src/main/resources/application.yml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
A	k8s/charts/infra/grafana/dashboards/esquire-services.json
A	k8s/charts/infra/grafana/templates/configmap-dashboards.yaml
M	k8s/charts/infra/grafana/templates/configmap-datasource.yaml
M	k8s/charts/infra/grafana/templates/deployment.yaml
M	k8s/charts/infra/grafana/values.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/values.yaml
A	k8s/charts/infra/postgres-exporter/Chart.yaml
A	k8s/charts/infra/postgres-exporter/templates/deployment.yaml
A	k8s/charts/infra/postgres-exporter/templates/service.yaml
A	k8s/charts/infra/postgres-exporter/values.yaml
A	k8s/charts/infra/prometheus/Chart.yaml
A	k8s/charts/infra/prometheus/templates/configmap.yaml
A	k8s/charts/infra/prometheus/templates/deployment.yaml
A	k8s/charts/infra/prometheus/templates/rbac.yaml
A	k8s/charts/infra/prometheus/templates/service.yaml
A	k8s/charts/infra/prometheus/values.yaml
M	k8s/o11y-off.bat
M	k8s/o11y-on.bat
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
A	kcMaster/.mvn/wrapper/maven-wrapper.jar
A	kcMaster/.mvn/wrapper/maven-wrapper.properties
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/resources/application.yml
A	keySmith/doc/keyCloak.docx
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
 87 files changed, 3315 insertions(+), 148 deletions(-)

-- 2026-07-10 | commit: 3fc6330 | mir0n.the.programmer | v1.2.11 -- observability: request timelines cross the messaging bus --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/application.yml
M	build.all.bat
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodTracer.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTracedAspect.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/TracingConfig.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/W3CTraceContext.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTraceTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqRodTracerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/W3CTraceContextTest.java
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	compose/compose.yaml
M	compose/o11y/otel-collector-config.yaml
M	compose/topology/esquire-topology.yml
M	doc/Esquire.ObservabilityStack.md
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveCommandItem.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
A	gateway/src/main/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	gateway/src/main/resources/application.yml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilterTest.java
M	k8s/charts/infra/otel-collector/templates/configmap.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/application.yml
M	messaging/pom.xml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodTracerHolder.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodTracerHolderTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
A	mir0n-utils/pom.xml
A	mir0n-utils/src/main/java/pro/mir0n/utils/HostId.java
R093	common/src/main/java/pro/mir0n/utils/changes.txt	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
R100	common/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
R100	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
R100	common/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/AMonad.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
R098	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
R098	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/IMonad.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
R090	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
R077	common/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/HostIdTest.java
R100	common/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
R100	common/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
R100	common/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
R100	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
R099	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
A	mir0n-utils/src/test/resources/logback-test.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/application.yml
M	pom.xml
M	tp-activemq/pom.xml
M	tp-kafka/pom.xml
M	tp-redis/pom.xml
 98 files changed, 2291 insertions(+), 254 deletions(-)

-- 2026-07-08 | commit: 662cdb1 | mir0n.the.programmer | v1.2.11 -- observability: request tracing across the backend services --
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/resources/application.yml
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailMill.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraced.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTracedAspect.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/TracingConfig.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	compose/compose.yaml
A	compose/o11y-off.bat
A	compose/o11y-on.bat
A	compose/o11y/alloy-config.alloy
A	compose/o11y/grafana/provisioning/datasources/loki.yaml
A	compose/o11y/grafana/provisioning/datasources/tempo.yaml
A	compose/o11y/loki-config.yaml
A	compose/o11y/otel-collector-config.yaml
A	compose/o11y/tempo-config.yaml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
M	doc/Esquire.ObservabilityStack.md
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	gateway/src/main/resources/application.yml
M	gateway/src/test/java/pro/mir0n/esquire/gateway/error/ProblemDetailMillTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilterTest.java
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/grafana/templates/configmap-datasource.yaml
M	k8s/charts/infra/grafana/values.yaml
A	k8s/charts/infra/otel-collector/Chart.yaml
A	k8s/charts/infra/otel-collector/templates/configmap.yaml
A	k8s/charts/infra/otel-collector/templates/deployment.yaml
A	k8s/charts/infra/otel-collector/templates/service.yaml
A	k8s/charts/infra/otel-collector/values.yaml
A	k8s/charts/infra/tempo/Chart.yaml
A	k8s/charts/infra/tempo/templates/configmap.yaml
A	k8s/charts/infra/tempo/templates/deployment.yaml
A	k8s/charts/infra/tempo/templates/service.yaml
A	k8s/charts/infra/tempo/values.yaml
D	k8s/o11y-down.bat
A	k8s/o11y-off.bat
A	k8s/o11y-on.bat
D	k8s/o11y-up.bat
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
 97 files changed, 1993 insertions(+), 132 deletions(-)

-- 2026-07-07 | commit: 2362340 | mir0n.the.programmer | v1.2.11 -- observability: search every service's logs in one place --
M	compose/compose.yaml
M	doc/Esquire.ObservabilityStack.md
M	doc/release_notes.txt
A	k8s/charts/infra/alloy/Chart.yaml
A	k8s/charts/infra/alloy/templates/configmap.yaml
A	k8s/charts/infra/alloy/templates/deployment.yaml
A	k8s/charts/infra/alloy/templates/rbac.yaml
A	k8s/charts/infra/alloy/values.yaml
A	k8s/charts/infra/grafana/Chart.yaml
A	k8s/charts/infra/grafana/templates/configmap-datasource.yaml
A	k8s/charts/infra/grafana/templates/deployment.yaml
A	k8s/charts/infra/grafana/templates/ingress.yaml
A	k8s/charts/infra/grafana/templates/service.yaml
A	k8s/charts/infra/grafana/values.yaml
A	k8s/charts/infra/loki/Chart.yaml
A	k8s/charts/infra/loki/templates/configmap.yaml
A	k8s/charts/infra/loki/templates/deployment.yaml
A	k8s/charts/infra/loki/templates/service.yaml
A	k8s/charts/infra/loki/values.yaml
A	k8s/o11y-down.bat
A	k8s/o11y-up.bat
 21 files changed, 538 insertions(+), 1 deletion(-)

-- 2026-07-05 | commit: a4355cb | mir0n.the.programmer | v1.2.11 -- retag --
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
 8 files changed, 8 insertions(+), 8 deletions(-)

-- 2026-07-05 | commit: 1becbb7 | mir0n.the.programmer | v1.2.11 -- version bump --
M	README.md
M	pom.xml
 2 files changed, 6 insertions(+), 3 deletions(-)

-- 2026-07-05 | commit: e3a2a36 | mir0n.the.programmer | Create report_v1.2.10.md --
A	doc/reports/report_v1.2.10.md
 1 file changed, 1115 insertions(+)
```

---

## Files Modified

```
A	.gitattributes
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-oke.yml
M	README.md
A	Releases.md
M	activemq/Dockerfile
M	activemq/conf/activemq.xml
A	activemq/conf/jmx-exporter.yml
A	activemq/esq-entrypoint.sh
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/AuKeepApplication.java
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/changes.txt
M	auKeep/src/main/java/pro/mir0n/esquire/auKeep/messaging/AuditConsumerConfig.java
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/IBizTreeDirector.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/MessageHandlerHub.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/legacy/BizTreeDirectorLegacy.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EntityBusAdapter.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/taijitu/Monad.java
M	bizTree/src/main/resources/application.yml
M	build.all.bat
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailMill.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTrace.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqBizMeters.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqGauge.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqRodObserver.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraceMark.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTraced.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTracedAspect.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/ObservabilityConfig.java
A	common/src/main/java/pro/mir0n/esquire/backend/o11y/W3CTraceContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqContextHolder.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/resources/esq-object-kinds.xml
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqAsyncTraceTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqBizMetersTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryReset.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqO11yRegistryResetTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqRodObserverTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCapTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqTraceMarkTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/EsqW3cIdConformanceTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/NoRawGaugeBuilderTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/O11yMeterDriftTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/o11y/W3CTraceContextTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqContextHolderTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/PerformanceAspectTest.java
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
A	common/src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension
A	common/src/test/resources/junit-platform.properties
M	compose/compose-rebuild.bat
M	compose/compose.yaml
A	compose/docker-compose-down.bat
A	compose/docker-compose-up.bat
A	compose/o11y-log-off.bat
A	compose/o11y-log-on.bat
A	compose/o11y-off.bat
A	compose/o11y-on.bat
A	compose/o11y-test.bat
A	compose/o11y-verify.bat
A	compose/o11y/alloy-config.alloy
A	compose/o11y/grafana/gen-dashboard.py
A	compose/o11y/grafana/gen-datasources.py
A	compose/o11y/grafana/gen-topology.py
A	compose/o11y/grafana/icons/activemq.svg
A	compose/o11y/grafana/icons/aukeep.svg
A	compose/o11y/grafana/icons/biztree.svg
A	compose/o11y/grafana/icons/enyman.svg
A	compose/o11y/grafana/icons/explorer.svg
A	compose/o11y/grafana/icons/gateway.svg
A	compose/o11y/grafana/icons/kcmaster.svg
A	compose/o11y/grafana/icons/keycloak.svg
A	compose/o11y/grafana/icons/keysmith.svg
A	compose/o11y/grafana/icons/pacman.svg
A	compose/o11y/grafana/icons/postgres.svg
A	compose/o11y/grafana/provisioning/dashboards/dashboards.yaml
A	compose/o11y/grafana/provisioning/dashboards/esquire-logging.json
A	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
A	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
A	compose/o11y/grafana/provisioning/datasources/loki.yaml
A	compose/o11y/grafana/provisioning/datasources/prometheus.yaml
A	compose/o11y/grafana/provisioning/datasources/tempo.yaml
A	compose/o11y/loki-config.yaml
A	compose/o11y/otel-collector-config.yaml
A	compose/o11y/prometheus.yml
A	compose/o11y/rules.yml
A	compose/o11y/tempo-config.yaml
M	compose/topology/esquire-topology.yml
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/changes.txt
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/KeepApplier.java
M	dataKeep/src/main/java/pro/mir0n/esquire/dataKeep/keep/RodEventDbWriter.java
M	doc/DatabaseDictionary.md
D	doc/DefaultRule.md
A	doc/EntityDictionary.md
M	doc/Esquire.AuditLoggingStack.md
R090	doc/keyCloak-gateway.JWE.md	doc/Esquire.Auth.TokenPatterns.md
R083	doc/keySmithCredentialRoutine.md	doc/Esquire.Auth.keySmithRoutine.md
A	doc/Esquire.Auth.md
M	doc/Esquire.BizTree.md
A	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
A	doc/Esquire.GrafanaGuide.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.HighAvailability.md
R091	doc/Messaging.md	doc/Esquire.Messaging.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.Guides.md
R083	doc/Message.Structure.md	doc/Esquire.MessagingBus.MessageStructure.md
A	doc/Esquire.MessagingBus.Q&A.md
M	doc/Esquire.MessagingBus.md
A	doc/Esquire.ObservabilityStack.Inventory.csv
R084	doc/Logging.md	doc/Esquire.ObservabilityStack.Logging.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
D	doc/H2BizTree.md
D	doc/OCI.Pricing.md
D	doc/Object.Kind.enum.md
D	doc/WhereToGo.md
D	doc/entity.path.semantics.md
A	doc/img/auth-collaboration.svg
A	doc/img/auth-connect-down.svg
A	doc/img/auth-connect-up.svg
A	doc/img/auth-move.svg
A	doc/img/auth-person-props.svg
A	doc/img/auth-tfa-disable.svg
A	doc/img/auth-tfa-enable.svg
A	doc/img/keysmith-connect.svg
A	doc/img/keysmith-password.svg
A	doc/img/keysmith-totp.svg
A	doc/img/move-ordering.svg
A	doc/img/move-race.svg
A	doc/install/Docker.md
A	doc/install/LocalK8s.md
A	doc/logo/OTelCollector.png
A	doc/logo/alloy_icon.png
M	doc/media/ComponentModel.png
A	doc/media/ObservabilityStack.svg
A	doc/media/grafana_icon.svg
A	doc/media/logging-screenshot.png
A	doc/media/loki_icon.svg
A	doc/media/o11yStack.png
A	doc/media/prometheus_logo.svg
A	doc/media/req-latency-band-screenshot.png
A	doc/media/services-screenshot.png
A	doc/media/tempo_logo.svg
A	doc/media/token-detour.svg
A	doc/media/token-exchange-v1v2.svg
A	doc/media/token-formats.svg
A	doc/media/token-ideal.svg
A	doc/media/topology-screenshot.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.10.md
A	doc/review/Esquire.PerfMatrix-07-14.md
A	doc/review/Esquire.PerfMatrix-07-17.md
M	doc/services.configuring.md
A	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
R100	enyMan/Dockerfilel.lx	enyMan/Dockerfile.lx
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqAcctRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/CreateReconcileItem.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveCommandItem.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/EntityIdGenerator.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AcctService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/META-INF/oracle-acct.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-acct.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
A	gateway/src/main/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/ResilienceConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/InnerTimerFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JweAwareJwtDecoder.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorConfigWiringTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/CorrelationPropagatorTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverterTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/error/ProblemDetailMillTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilterTest.java
M	k8s-oci/esquire-topology.yml
A	k8s-oci/grafana/esquire-logging.json
A	k8s-oci/grafana/esquire-services.json
A	k8s-oci/grafana/esquire-topology.json
A	k8s-oci/oke-config-parity.bat
A	k8s-oci/oke-grafana-forward.bat
A	k8s-oci/oke-o11y-off.bat
A	k8s-oci/oke-o11y-on.bat
A	k8s-oci/oke-o11y-test.bat
A	k8s-oci/oke-o11y-verify.bat
A	k8s-oci/oke-pg-forward.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s/charts/esquire-aukeep/templates/configmap.yaml
M	k8s/charts/esquire-aukeep/templates/deployment.yaml
M	k8s/charts/esquire-aukeep/values.yaml
M	k8s/charts/esquire-backend/templates/configmap.yaml
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
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/templates/deployment.yaml
M	k8s/charts/esquire-kcmaster/templates/secret.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/deployment.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/deployment.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/infra/activemq/templates/statefulset.yaml
M	k8s/charts/infra/activemq/values.yaml
A	k8s/charts/infra/alloy/Chart.yaml
A	k8s/charts/infra/alloy/templates/configmap.yaml
A	k8s/charts/infra/alloy/templates/deployment.yaml
A	k8s/charts/infra/alloy/templates/pvc.yaml
A	k8s/charts/infra/alloy/templates/rbac.yaml
A	k8s/charts/infra/alloy/values.yaml
A	k8s/charts/infra/grafana/Chart.yaml
A	k8s/charts/infra/grafana/dashboards/esquire-logging.json
A	k8s/charts/infra/grafana/dashboards/esquire-services.json
A	k8s/charts/infra/grafana/dashboards/esquire-topology.json
A	k8s/charts/infra/grafana/icons/activemq.svg
A	k8s/charts/infra/grafana/icons/aukeep.svg
A	k8s/charts/infra/grafana/icons/biztree.svg
A	k8s/charts/infra/grafana/icons/enyman.svg
A	k8s/charts/infra/grafana/icons/explorer.svg
A	k8s/charts/infra/grafana/icons/gateway.svg
A	k8s/charts/infra/grafana/icons/kcmaster.svg
A	k8s/charts/infra/grafana/icons/keycloak.svg
A	k8s/charts/infra/grafana/icons/keysmith.svg
A	k8s/charts/infra/grafana/icons/pacman.svg
A	k8s/charts/infra/grafana/icons/postgres.svg
A	k8s/charts/infra/grafana/templates/configmap-dashboards.yaml
A	k8s/charts/infra/grafana/templates/configmap-datasource.yaml
A	k8s/charts/infra/grafana/templates/configmap-icons.yaml
A	k8s/charts/infra/grafana/templates/deployment.yaml
A	k8s/charts/infra/grafana/templates/ingress.yaml
A	k8s/charts/infra/grafana/templates/pvc.yaml
A	k8s/charts/infra/grafana/templates/service.yaml
A	k8s/charts/infra/grafana/values.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/values.yaml
A	k8s/charts/infra/loki/Chart.yaml
A	k8s/charts/infra/loki/templates/configmap.yaml
A	k8s/charts/infra/loki/templates/deployment.yaml
A	k8s/charts/infra/loki/templates/pvc.yaml
A	k8s/charts/infra/loki/templates/service.yaml
A	k8s/charts/infra/loki/values.yaml
A	k8s/charts/infra/otel-collector/Chart.yaml
A	k8s/charts/infra/otel-collector/templates/configmap.yaml
A	k8s/charts/infra/otel-collector/templates/deployment.yaml
A	k8s/charts/infra/otel-collector/templates/service.yaml
A	k8s/charts/infra/otel-collector/values.yaml
A	k8s/charts/infra/postgres-exporter/Chart.yaml
A	k8s/charts/infra/postgres-exporter/templates/deployment.yaml
A	k8s/charts/infra/postgres-exporter/templates/service.yaml
A	k8s/charts/infra/postgres-exporter/values.yaml
A	k8s/charts/infra/prometheus/Chart.yaml
A	k8s/charts/infra/prometheus/rules.yml
A	k8s/charts/infra/prometheus/templates/configmap.yaml
A	k8s/charts/infra/prometheus/templates/deployment.yaml
A	k8s/charts/infra/prometheus/templates/pvc.yaml
A	k8s/charts/infra/prometheus/templates/rbac.yaml
A	k8s/charts/infra/prometheus/templates/service.yaml
A	k8s/charts/infra/prometheus/values.yaml
A	k8s/charts/infra/tempo/Chart.yaml
A	k8s/charts/infra/tempo/templates/configmap.yaml
A	k8s/charts/infra/tempo/templates/deployment.yaml
A	k8s/charts/infra/tempo/templates/pvc.yaml
A	k8s/charts/infra/tempo/templates/service.yaml
A	k8s/charts/infra/tempo/values.yaml
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
A	k8s/o11y-forward-stop.bat
A	k8s/o11y-forward.bat
A	k8s/o11y-full-on.bat
A	k8s/o11y-log-off.bat
A	k8s/o11y-log-on.bat
A	k8s/o11y-off.bat
A	k8s/o11y-on.bat
A	k8s/o11y-test.bat
A	k8s/o11y-verify.bat
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
A	kcMaster/.mvn/wrapper/maven-wrapper.jar
A	kcMaster/.mvn/wrapper/maven-wrapper.properties
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
A	keySmith/doc/keyCloak.docx
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keycloak/import/esquire.json
M	messaging/pom.xml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/BusConstants.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/MessagingBus.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/RodEvent.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/catalog/XRodParams.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodMeters.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodObserver.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/IRodTracer.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolder.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/RodEventCodec.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/AXRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRod.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/XRodRR.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSession.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionRR.java
M	messaging/src/main/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/SendRetrySublayer.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/catalog/TopologyDriftGuardTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/o11y/RodObserverHolderTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/RodEventCodecTest.java
M	messaging/src/test/java/pro/mir0n/esquire/messaging/xrod/impl/sublayer/AliveSessionTest.java
A	mir0n-utils/pom.xml
A	mir0n-utils/src/main/java/pro/mir0n/utils/HostId.java
R089	common/src/main/java/pro/mir0n/utils/changes.txt	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
R085	common/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
R100	common/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
R100	common/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/AMonad.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonad.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/AMonadY.java
R098	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRig.java
R098	common/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ATaijituRigY.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICancelable.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ICmdResponseListener.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/IMonad.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/IMonad.java
R090	common/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/ITaijituRig.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MismatchAction.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadCmd.java
R100	common/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/MonadStatus.java
R077	common/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java	mir0n-utils/src/main/java/pro/mir0n/utils/taijitu/QueueItem.java
A	mir0n-utils/src/test/java/pro/mir0n/utils/HostIdTest.java
R100	common/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
R100	common/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
R100	common/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/AMonadYBulkTest.java
R100	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigTest.java
R099	common/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java	mir0n-utils/src/test/java/pro/mir0n/utils/taijitu/ATaijituRigYTest.java
A	mir0n-utils/src/test/resources/logback-test.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pom.xml
M	postgres/Dockerfile
A	test/config-parity/config-parity.py
M	test/health-smoke/README.md
M	test/health-smoke/run.sh
A	test/o11y/o11y-drive.py
A	test/o11y/o11y-inventory.py
A	test/o11y/o11y-verify.py
M	tp-activemq/pom.xml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-kafka/pom.xml
M	tp-redis/pom.xml
 424 files changed, 57525 insertions(+), 3188 deletions(-)
```

---

*From `v1.2.10` till `v1.2.11`*

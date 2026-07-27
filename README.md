
---

# Esquire Application Frameworks(tm) 2.0

<img src="./helm.svg" alt="Rod logo" align="left" width="256" height="256">

***Tree-shaped authorization. Write business logic only. The server defines the UI.***

Esquire is a **business entity framework** — the structural backbone for any backoffice system. It
organizes people, organizations, and resources in a single tree, and runs your business operations against it.

Authorization comes from the tree itself, in two dimensions at once: **role-based rules** for what a user
may *do*, and **tree-based scope** for what a user may *see*. A user's position resolves both — no permission
filter written by hand.

And you write **only business logic.** Place an entity on the tree and describe what it *means* — its fields,
its rules, its commands — and the browser **renders that description at runtime**, with no field layout coded
in the frontend. How the entity is stored, synchronized across services, secured, and audited is **inherited,
not coded** — persistence, messaging, identity, and deployment are the framework's job, not yours.

To show it all works together, Esquire ships a complete backoffice — onboarding, profile maintenance,
permissions, and a working accounting domain. Accounting is the deliberately familiar example, so the
framework speaks for itself; any other hierarchy sits on the same backbone.

Esquire takes the **widely used stack** (a relational database, Spring Boot, Node.js with Angular,
Grafana/Prometheus) and drives it **to the end of what the stack can do**: ordinary parts taken as far as
they go — a hybrid REST-and-event engine, vendor-neutral messaging, high availability that shrugs off losing
a machine, and full, switch-on-anywhere observability. **Released by pipeline and live in production,**
everything the framework promises, it does — in a running deployment, on three targets, open for anyone to
check.

**See it live — [esquire.mir0n.pro](https://esquire.mir0n.pro).** Sign in, browse the tree, run the operations.

---
> 
> **v1.2.11 — complete. The [goal](doc/v1.2.x.Goal.md) is met.** The final **v1.2.x** sprint,
> **Observability**, is the capstone of the whole line. Seeing what the running system is doing arrives not as
> a bolted-on afterthought but as a **common layer built into the framework** — one consistent way to watch
> health, timing, and traffic, provided once for every service instead of wired up service by service.
> 
> **One pane over three pillars.** Metrics, traces, and logs are tied together by a single **correlation id**,
> so a log line, its trace, and its numbers are one click apart in a single **Grafana** view — and a single
> request is followed across services *and* across message-bus hops. It is **off by default** and switched on
> on demand — on a laptop, on the test cluster, or briefly on the cloud — so the everyday stack carries no
> weight when nobody is watching. Business counters, ready-made alerts for the obvious failures, and
> purpose-built dashboards all ship with it.
> 
> The sprint also brought a run of **hardening fixes** from a fresh, top-to-bottom review — safer entity moves,
> money rounded to the ledger's precision before it is stored, a cloud broker that reconnects on its own after
> a blip, and health and metrics moved onto an internal-only port — and a **full documentation refresh**,
> including the framework's first **step-by-step install guides** for Docker and local Kubernetes.
>


## Installation

Esquire runs as a whole framework on your own machine — the services, the browser tier, the
messaging bus, a seeded demo database, and identity, all together. Two step-by-step routines bring up
the same application, each with an optional one-click view of what the running system is doing:

- **[Install & Run — Docker sandbox](doc/install/Docker.md)** — the fast single-instance stack: build,
  start, open the browser.
- **[Install & Run — Local Kubernetes](doc/install/LocalK8s.md)** (Docker Desktop) — the same
  application in its redundant deploy shape, every service running twice.

Both seed a demo organization tree with a working accounting example, so there is something to click
through the moment it is up. Background, the architecture, and the developer workflow are in
[Developer Setup](doc/Esquire.DevSetup.md).

---

## Project Structure

| Repository | Description |
|---|---|
| [esquire.services](https://github.com/mir0n-pro/esquire.services) | Backend microservices and API gateway |
| [esquire.explorer](https://github.com/mir0n-pro/esquire.explorer) | Angular frontend: entity tree explorer and operation dialogs |
| [esquire.ui.lib](https://github.com/mir0n-pro/esquire.ui.lib) | Shared Angular UI library (`@mir0n-pro/esquire.ui`) |
| [esquire.db.seed](https://github.com/mir0n-pro/esquire.db.seed) | Database schema and seed scripts (Oracle and Postgres) |

---

## Release History

**[Releases.md](Releases.md)** -- full release notes for every version, plus milestone reports across all four repositories.

---

## Documentation

### Architecture & Design
- [Esquire Messaging Bus — the vendor-agnostic bus subframework](doc/Esquire.MessagingBus.md) *(topology catalog, x-rod frontend, and a pluggable transport-provider SPI: tp-activemq / tp-kafka / tp-redis)* — suite: [how Esquire uses the bus](doc/Esquire.Messaging.md), [message structure](doc/Esquire.MessagingBus.MessageStructure.md), [integration guides](doc/Esquire.MessagingBus.Guides.md), [design Q&A](doc/Esquire.MessagingBus.Q&A.md), [continuing development](doc/Esquire.MessagingBus.ContinuingDev.md)
- [Observability Stack](doc/Esquire.ObservabilityStack.md) *(metrics, tracing, and logging as common tooling)* — suite: [logging strategy](doc/Esquire.ObservabilityStack.Logging.md), [Grafana dashboards guide](doc/Esquire.GrafanaGuide.md)
- [Authentication & Authorization — the tree-shaped security model](doc/Esquire.Auth.md) *(identity claims `esq_uid` / `esq_rootpath`; the keySmith / kcMaster / KeyCloak collaboration; `ep_path` visibility + role-based authority)* — suite: [token patterns](doc/Esquire.Auth.TokenPatterns.md) (BFF / JWT / Vanilla & Phantom Token Relay; JWE parked under stock KC) and [keySmith credential routines](doc/Esquire.Auth.keySmithRoutine.md)
- [bizTree — Taijitu Recoverable Cache Architecture](doc/Esquire.BizTree.md) *(the Supreme Ultimate Cache: two-monad anti-entropy double-buffer + night-watch sweep)*
- [Audit Logging Stack](doc/Esquire.AuditLoggingStack.md) *(pluggable audit seam over the generic keep engine; six selectable strategies, ActiveMQ / Kafka / Redis transport, the auKeep consumer service)*
- [High Availability](doc/Esquire.HighAvailability.md) *(replica topology, the resilience budget, and how each delivery channel survives duplication)*
- [Design Q&A](doc/Esquire.Q&A.md) *(cross-cutting design questions and how they were resolved)*

### Domain & Data Model
- [Entity Dictionary](doc/EntityDictionary.md) *(what an entity says about itself: its fields, labels, allowed values, defaults, and how the screens are built from them; the full kind enumeration is its appendix)*
- [Database Dictionary](doc/DatabaseDictionary.md)

### Testing
- [Testing Stack — frameworks, scope, coverage](doc/Esquire.TestingStack.md) *(every framework in use across all Esquire projects, what it covers, current test counts)*
- [Haubergeon — Gatling harness reference](doc/Esquire.Haubergeon.md)

### Deployment & Setup
- [Developer Setup](doc/Esquire.DevSetup.md) *(local build, the docker stack, and OCI / OKE cloud setup)*
- [CI/CD — Automated Build and Release Pipeline](doc/Esquire.GitHubActions.md) *(three stages: automatic build-and-test on every change, deploy to a local test cluster during development, and a human-approved cloud release deploy checked against the live site)*
- [Configuration Reference — every service parameter, logging, gateway routes, audit-logging modes/options](doc/services.configuring.md)

### Development Process
- [Development Process](doc/Esquire.DevProcess.md) *(the sprint lifecycle, the documentation routine, and the release flow)*
- [Continuing Development](doc/Esquire.ContinuingDev.md) *(cross-cutting backlog and deferred design notes)*

### Value Proposition & Roadmap
- [Esquire Application Frameworks — What, Why, and for Whom](doc/Esquire.Vision.md)
- [v1.2.x Goal](doc/v1.2.x.Goal.md) *(what the v1.2.x horizon set out to achieve, and the maturity it reached)*
- [v1.2.x Planning — release-line roadmap, sprint themes, and the road to v1.3.0](doc/v1.2.x.Planning.md) *(rolling roadmap / white paper across all four Esquire repositories)*

---

## Component Model

![Component Model](doc/media/ComponentModel.png)

---

The **gateway**, the six backend **services** (bizTree, enyMan, pacMan, keySmith, kcMaster, auKeep), and the
**BFF** each run **redundant, with (optional) autoscale**, for high availability — spread across separate
nodes so losing one keeps the site up. The databases, Keycloak, the message broker, and the Session Store
run as a single instance here; each is a third-party platform that brings its **own** high-availability
(clustering / replication), set up by the operator when wanted — outside Esquire's concern.

**Esq2025**
<img src="./doc/logo/postgres.svg" alt="postgres logo" valign="middle" height="24">
<img src="./doc/logo/oracle.svg" alt="oracle logo" valign="middle" height="24">
<br> The database (Postgres or Oracle); persistent store for all entity data,
transactions, permissions, configuration parameters, and the audit log (when triggers are enabled).

**Esq2025 audit**
<img src="./doc/logo/postgres.svg" alt="postgres logo" valign="middle" height="24">
<img src="./doc/logo/oracle.svg" alt="oracle logo" valign="middle" height="24">
<br> Optional, the database (Postgres or Oracle); persistent store for the entity audit log (`*_log` tables).

**Messaging Bus**
<img src="./doc/logo/activemq.png" alt="ActiveMQ logo" valign="middle" height="24">
<img src="./doc/logo/redis.svg" alt="Redis logo" valign="middle" height="24">
<img src="./doc/logo/kafka.svg" alt="Kafka logo" valign="middle" height="24">
<br> Three logical channels:
- *IAM Request-Response Bus* — carries identity commands (create / update / delete user)
  from keySmith to kcMaster, and acknowledgement replies back
- *Entity Broadcast Bus* — enyMan and pacMan publish entity change events on every mutation;
  bizTree and other interested consumers subscribe
- *Audit Broadcast Bus* — optional (off by default); the entity-updating services (enyMan, pacMan, keySmith)
  publish each committed change event. Transport vendors: ActiveMQ, Kafka, or Redis. 

**pacMan**
<img src="./doc/logo/java.svg" alt="Java logo" valign="middle" height="24">
<img src="./doc/logo/spring-boot.svg" alt="Spring Boot logo" valign="middle" height="24">
<img src="./doc/logo/pac-man.svg" alt="pacMan logo" valign="middle" height="24">
<br>Personal Account Manager; the accounting service; manages account balance
operations: deposit, withdrawal, cross-currency transfer; the place where business logic lives;
all other services exist to support it.

**bizTree**
<img src="./doc/logo/java.svg" alt="Java logo" valign="middle" height="24">
<img src="./doc/logo/spring-boot.svg" alt="Spring Boot logo" valign="middle"  height="24">
<img src="./doc/logo/h2.svg" alt="H2 logo" valign="middle" height="24">
<img src="./doc/logo/bizTree.png" alt="bizTree logo" valign="middle" height="24">
<br>The entity tree service; maintains an H2 database in-memory cache of the business entity
tree; serves tree navigation to the frontend; stays current by consuming the broadcast bus.
A **recoverable cache service**: a two-buffer anti-entropy design whose periodic
night-watch rebuilds a shadow from the database, compares the two, and self-heals any drift —
so an event missed while the service was down is reconciled automatically, with no restart.

**enyMan**
<img src="./doc/logo/java.svg" alt="Java logo" valign="middle" height="24">
<img src="./doc/logo/spring-boot.svg" alt="Spring Boot logo" valign="middle" height="24">
<img src="./doc/logo/enyMan.3.png" alt="enyMan logo" valign="middle" height="28">
<br>Entity Manager; manages organizations and users; handles create, update, delete,
and move operations; publishes entity change events to the entity broadcast bus, and now also
receives from it — a two-way link, so the redundant copies stay coordinated, each aware of the
entity changes the other makes.

**keySmith**
<img src="./doc/logo/java.svg" alt="Java logo" valign="middle" height="24">
<img src="./doc/logo/spring-boot.svg" alt="Spring Boot logo" valign="middle" height="24">
<img src="./doc/logo/keySmith.3.png" alt="keySmith logo" valign="middle" height="40">
<br>Authentication and access profile service; manages IAS integration and
JWT-based authorization; serves access profiles to the frontend; publishes identity change
requests to the IAM bus.

**kcMaster**
<img src="./doc/logo/java.svg" alt="Java logo" valign="middle" height="24">
<img src="./doc/logo/spring-boot.svg" alt="Spring Boot logo" valign="middle" height="24">
<img src="./doc/logo/kcMaster.png" alt="kcMaster logo" valign="middle" height="24">
<br>Keycloak IAS sync coordinator; the only service that writes to Keycloak directly;
consumes identity commands from the IAM bus and executes create / update / delete in the IAM. Also
listens on the entity broadcast bus to keep a moved entity's Keycloak path in sync.

**auKeep**
<img src="./doc/logo/java.svg" alt="Java logo" valign="middle" height="24">
<img src="./doc/logo/spring-boot.svg" alt="Spring Boot logo" valign="middle" height="24">
<img src="./doc/logo/keep.svg" alt="x-rod logo" valign="middle" height="24">
<br> Optional, the audit consumer service, writes audit events to the `*_log` tables.

**Redis DB**
<img src="./doc/logo/redis.svg" alt="Redis logo" valign="middle" height="24">
<br>Optional, the alternative non-SQL (document-DB) audit sink; the Redis stream itself holds the
audit trail (the stream *is* the log). Feeding it from the Kafka transport needs one extra component, a
**Kafka Connect Redis Sink**
<img src="./doc/logo/kafka.svg" alt="Kafka logo" valign="middle" height="16">.

**gateway**
<img src="./doc/logo/java.svg" alt="Java logo" valign="middle" height="24">
<img src="./doc/logo/spring-boot.svg" alt="Spring Boot logo" valign="middle" width="24">
<img src="./doc/logo/gateway.svg" alt="Gateway logo" valign="middle" width="24">
<br> Spring Cloud Gateway; the API router; routes requests to the appropriate
backend service by path and entity kind; validates JWT tokens on every request and, for
opted-in clients, accepts two additional auth shapes. Reachable two ways: in-cluster
from the BFF on `/api/*`, and externally at `https://api.esquire.mir0n.pro` — the
**public REST API** for non-browser callers.

**Keycloak**
<img src="./doc/logo/keycloak.png" alt="Keycloak logo" valign="middle" height="24">
<br>External IAM; issues JWT access tokens; manages user identities, realm
configuration, and authentication flows including TOTP; runs as a containerized service.

**Esquire Explorer Backend**
<img src="./doc/logo/node.js.svg" alt="Node.js logo" valign="middle" height="24">
<br>Node.js BFF tier — Backend-for-Frontend; the **administrative
GUI entry point** at `https://esquire.mir0n.pro`. Owns the OIDC code+PKCE flow with Keycloak
(`/auth/login`, `/callback`, `/logout`, `/me`); proxies `/api/*` to the gateway with bearer
injection; caches static entity dictionaries (`/esq-kinds`, `/esq-dictionary`) per pod, shared
across users; bakes the Angular SPA into its image at build time and serves it on `/`. The
browser never sees the access token — it holds an opaque session cookie. When run redundant, login
sessions are kept in the **Session Store** so any copy can serve any request.

**Session Store**
<img src="./doc/logo/redis.svg" alt="Redis logo" valign="middle" height="24">
<br>Optional; a Redis instance holding the BFF's login sessions, so the redundant BFF copies share them —
any copy can serve any request. Only needed when the BFF runs as more than one copy; distinct from the
audit-sink Redis.

**Esquire Explorer Frontend**
<img src="./doc/logo/node.js.svg" alt="Node.js logo" valign="middle" height="24">
<img src="./doc/logo/angular.svg" alt="Angular logo" valign="middle" height="24">
<img src="./doc/logo/esquire.png" alt="Esquire logo" valign="middle" height="24">
<br>Angular SPA — the user-facing tree explorer and operations UI;
consumes the `@mir0n-pro/esquire.ui` library; communicates only with the BFF via same-origin
`/auth/*` and `/api/*`, never directly with the gateway or Keycloak. Shipped baked into the
BFF image; one deployable.

**Public REST API**
<img src="./doc/logo/hauberk.svg" alt="Hauberk logo" valign="middle" height="24">
<img src="./doc/logo/gatling.svg" alt="Gatling logo" valign="middle" height="24">
<br>Exposed for gatling-based load / smoke harnesses and other integrations.

The two public hosts at a glance:

| Host | Purpose | Auth shape | Who talks to it |
|---|---|---|---|
| `esquire.mir0n.pro` | Administrative GUI (BFF + SPA) | OIDC code+PKCE -> opaque session cookie | Humans in a browser |
| `api.esquire.mir0n.pro` | Public REST API (gateway direct) | Bearer JWT on every request | Service-to-service callers, integrations, load / smoke harnesses |


**Observability Stack**
<img src="doc/media/o11yStack.png" alt="Observability Stack" width="790">

Every piece below is open-source, and any of it can be swapped out — no lock-in, the same rule as the rest of the platform.

**Postgres Exporter**
<img src="doc/logo/postgres.svg" alt="Postgres logo" valign="middle" height="24">
<br> Turns the database's own statistics into numbers the metrics store can read.

**Prometheus**
<img src="doc/media/prometheus_logo.svg" alt="Prometheus logo" valign="middle" height="24">
<br> The metrics store — collects the numbers (rates, timings, counts) from every service and keeps their history.

**OpenTelemetry Collector**
<img src="doc/logo/OTelCollector.png" alt="OpenTelemetry Collector logo" valign="middle" height="24">
<br> The traces hub — a separate service that gathers each request's trace from every service, passes it to the trace store, and builds the live map of which service calls which.

**Grafana Tempo**
<img src="doc/media/tempo_logo.svg" alt="Tempo logo" valign="middle" height="24">
<br> The trace store — keeps each request's end-to-end trace, found by the same id as its logs.

**Grafana Alloy**
<img src="doc/logo/alloy_icon.png" alt="Alloy logo" valign="middle" height="24">
<br> The log collector — gathers the log lines from every service and passes them to the log store.

**Grafana Loki**
<img src="doc/media/loki_icon.svg" alt="Loki logo" valign="middle" height="24">
<br> The log store — keeps all the logs, searchable, and serves them to the dashboard.

**Grafana**
<img src="doc/media/grafana_icon.svg" alt="Grafana logo" valign="middle" height="24">
<br> The one screen — dashboards for logs, traces, and numbers together; because everything shares one id, a log line, its trace, and its numbers are one click apart.

For how the pieces connect — the protocols, the ports, and the full detail — see the [Observability Stack](doc/Esquire.ObservabilityStack.md) design doc.


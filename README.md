
| ![Alt text](./favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|


> 
> **v1.2.7 — complete.** An **audit-logging** sprint: a pluggable audit seam over the entity services, a generic **x-Rod** fan-out substrate, a new **xx-rod** service, six selectable strategies, ActiveMQ/Kafka transport.
>

Esquire is a **business entity framework** — the structural backbone for any system that
needs to organize people, organizations, and resources in a tree, enforce who can see and
do what within that tree, and run business operations against it.

The backoffice scenario — onboarding, profile maintenance, permissions, accounting — is the
demonstration domain. Accounting in particular is the "everybody's know-how" example: a
universally understood domain that exercises the full framework stack end to end. It is not
the destination. It is the proof of concept for the real idea.

**See it live: [esquire.mir0n.pro](https://esquire.mir0n.pro)** — sign in, browse the tree, run the operations.

## Project Structure

| Repository | Description |
|---|---|
| [esquire.services](https://github.com/mir0n-pro/esquire.services) | Backend microservices and API gateway |
| [esquire.explorer](https://github.com/mir0n-pro/esquire.explorer) | Angular frontend: entity tree explorer and operation dialogs |
| [esquire.ui.lib](https://github.com/mir0n-pro/esquire.ui.lib) | Shared Angular UI library (`@mir0n-pro/esquire.ui`) |
| [esquire.db.seed](https://github.com/mir0n-pro/esquire.db.seed) | Database schema and seed scripts (Oracle and Postgres) |

---

## Documentation

### Architecture & Design
- [Messaging Architecture — Entity Broadcast and IAM Request/Response](doc/Messaging.md)
- [Observability Stack](doc/Esquire.ObservabilityStack.md)
- [Logging Strategy](doc/Logging.md)
- [Keycloak / Gateway — Authentication Patterns](doc/keyCloak-gateway.JWE.md) *(four working patterns: BFF / JWT / Vanilla Token Relay / Phantom Token Relay; JWE parked under stock KC, gateway-side lab kept armed)*
- [KeySmith Credential Routines — State Machine & Collaboration](doc/keySmithCredentialRoutine.md)
- [bizTree — Taijitu Recoverable Cache Architecture](doc/Esquire.BizTree.md) *(the Supreme Ultimate Cache: two-monad anti-entropy double-buffer + night-watch sweep)*
- [Audit Logging Stack](doc/Esquire.AuditLoggingStack.md) *(pluggable audit seam over a generic x-Rod fan-out substrate; six selectable strategies, ActiveMQ/Kafka transport, the xx-rod service)*

### Domain & Data Model
- [Object Kind Enumeration](doc/Object.Kind.enum.md)
- [Entity Path Semantics](doc/entity.path.semantics.md)
- [Default Field Rule](doc/DefaultRule.md)
- [bizTree — H2-backed In-Memory Cache](doc/H2BizTree.md)
- [Database Dictionary](doc/DatabaseDictionary.md)

### Testing
- [Testing Stack — frameworks, scope, coverage](doc/Esquire.TestingStack.md) *(every framework in use across all Esquire projects, what it covers, current test counts at v1.2.6)*
- [Testing — Gatling as Esquire standard](doc/Testing.md) *(policy note for integration / load / stress / race-repro)*
- [Haubergeon — Gatling harness reference](doc/Esquire.Haubergeon.md)

### Reports


|                                                                   |                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **esquire.services**| - [v1.2.7 Milestone Report](doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](doc/reports/report_v1.2.2.md)                                                                                                                                                                                  |
| **esquire.explorer**| - [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.7.md)<br/>- [v1.2.6 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.6.md)<br/>- [v1.2.5 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.2.md) |
| **esquire.ui.lib**| - [v1.2.3 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.3.md)<br/> - [v1.2.2 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_2026_04_19_31750f3.md)                                                                                                                     |
| **esquire.db.seed**| - [v1.2.7 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.7.md)<br/> - [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.4.md)<br/> - [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.2.md)                                                                                                                          |


### Deployment
- [Configuration Reference — every service parameter, logging, gateway routes, audit-logging modes/options](doc/services.configuring.md)
- [Where To Go — Deployment Plan and Platform Decisions](doc/WhereToGo.md)
- [OCI Pricing Reference](doc/OCI.Pricing.md)

### Value Proposition
- [Esquire Application Frameworks — What, Why, and for Whom](doc/Esquire.Vision.md)

---

## Component Model

![Component Model](doc/media/ComponentModel.png)

---

**Esq2025** — the database (Oracle or Postgres); persistent store for all entity data,
transactions, permissions, configuration parameters, and the audit log (when triggers are enabled).

**Esq2025 audit** — optional, the database (Oracle or Postgres); persistent store for the entity audit log (`*_log` tables).

**Messaging Bus** — ActiveMQ broker; three logical channels (the third added in v1.2.7, optional):
- *IAM Request-Response Bus* — carries identity commands (create / update / delete user)
  from keySmith to kcMaster, and acknowledgement replies back
- *Entity Broadcast Bus* — enyMan and pacMan publish entity change events on every mutation;
  bizTree and other interested consumers subscribe
- *Audit Broadcast Bus* — optional (off by default); the entity-updating services (enyMan, pacMan, keySmith)
  publish each committed change event. Transport vendors: ActiveMQ, Kafka, or Redis. 

**pacMan** (Personal Account Manager) — the accounting service; manages account balance
operations: deposit, withdrawal, cross-currency transfer; the place where business logic lives;
all other services exist to support it.

**bizTree** — the entity tree service; maintains an in-memory cache of the business entity
tree; serves tree navigation to the frontend; stays current by consuming the broadcast bus.
A **recoverable cache service**: a two-buffer anti-entropy design whose periodic
night-watch rebuilds a shadow from the database, compares the two, and self-heals any drift —
so an event missed while the service was down is reconciled automatically, with no restart.

**enyMan** (Entity Manager) — manages organizations and users; handles create, update, delete,
and move operations; publishes entity change events to the broadcast bus.

**keySmith** — authentication and access profile service; manages IAS integration and
JWT-based authorization; serves access profiles to the frontend; publishes identity change
requests to the IAM bus.

**kcMaster** — Keycloak IAS sync coordinator; the only service that writes to Keycloak directly;
consumes identity commands from the IAM bus and executes create / update / delete in the IAM. Also
listens on the entity broadcast bus to keep a moved entity's Keycloak path in sync.

**xx-rod** — optional, the audit consumer service, writes audit events to the `*_log` tables.

**Redis DB** — optional, the alternative non-SQL (document-DB) audit sink; the Redis stream itself holds the
audit trail (the stream *is* the log). Feeding it from the Kafka transport needs one extra component, a
**Kafka Connect Redis Sink**.

**gateway** — Spring Cloud Gateway; the API router; routes requests to the appropriate
backend service by path and entity kind; validates JWT tokens on every request and, for
opted-in clients, accepts two additional auth shapes. Reachable two ways: in-cluster
from the BFF on `/api/*`, and externally at `https://api.esquire.mir0n.pro` — the
**public REST API** for non-browser callers.

**Keycloak** — external IAM; issues JWT access tokens; manages user identities, realm
configuration, and authentication flows including TOTP; runs as a containerized service.

**Esquire Explorer Backend** (Node.js BFF tier) — Backend-for-Frontend; the **administrative
GUI entry point** at `https://esquire.mir0n.pro`. Owns the OIDC code+PKCE flow with Keycloak
(`/auth/login`, `/callback`, `/logout`, `/me`); proxies `/api/*` to the gateway with bearer
injection; caches static entity dictionaries (`/esq-kinds`, `/esq-dictionary`) per pod, shared
across users; bakes the Angular SPA into its image at build time and serves it on `/`. The
browser never sees the access token — it holds an opaque session cookie.

**Esquire Explorer Frontend** (Angular SPA) — the user-facing tree explorer and operations UI;
consumes the `@mir0n-pro/esquire.ui` library; communicates only with the BFF via same-origin
`/auth/*` and `/api/*`, never directly with the gateway or Keycloak. Shipped baked into the
BFF image; one deployable.

**Public REST API** exposed for gatling-based load / smoke harnesses and other integrations.

The two public hosts at a glance:

| Host | Purpose | Auth shape | Who talks to it |
|---|---|---|---|
| `esquire.mir0n.pro` | Administrative GUI (BFF + SPA) | OIDC code+PKCE -> opaque session cookie | Humans in a browser |
| `api.esquire.mir0n.pro` | Public REST API (gateway direct) | Bearer JWT on every request | Service-to-service callers, integrations, load / smoke harnesses |

---
## Release History
### v1.2.7 — complete (06/10/2026)

v1.2.7 is the **audit-logging** sprint. Entity-change auditing — and, beyond entities, user/office custom
parameters today and role/permission maintenance later — is reframed from in-database triggers into a
**pluggable, decoupled concern**: one audit seam in the entity-updating services (enyMan, pacMan, keySmith),
with several interchangeable strategies behind it, each selected by configuration.

The mechanism is the **x-Rod substrate** — a generic entity **fan-out** producer/consumer couple
(`xy-Rod` producer / `xx-Rod` consumer), with audit as its first sink (replication and search-index feeds
are future siblings on the same producer). Six strategies sit behind the seam: **(0)** persist nothing,
**(a)** DB triggers, **(b)** in-process write, **(c)** bus to the **xx-rod** service to SQL,
**(d)** Redis Stream, and **(e)** log-based CDC (out of framework scope); the bus options also run over
**Kafka** in place of ActiveMQ. The framework default is **(0)** — a fresh deploy audits nothing; each
deployment then configures its own topology (dev runs the decoupled bus path, the OKE demo runs DB
triggers). A new **xx-rod** service consumes the audit bus and owns the log-DB write. The
measured finding: at the request level audit is **cheap to free** in every mode — the decoupling buys
write-offload and transactional independence, not request latency.<br>
[The Audit Logging Stack](doc/Esquire.AuditLoggingStack.md)<br>

### v1.2.6 — complete (06/02/2026)

v1.2.6 is an **enyMan-redundancy** sprint: instance-aware entity-id minting consolidated into enyMan, account CREATE moved from pacMan to enyMan, an async move queue, the race-8b / race-8c move-path fixes, and bizTree event work-batching.<br>
[More Details: v1.2.6 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.6?tab=readme-ov-file#project-structure)

### v1.2.5 — complete (05/24/2026)

bizTree's cache rebuilt as the **Taijitu** recoverable cache service — a two-monad anti-entropy
double-buffer reconciled by a periodic night-watch that rebuilds a shadow from the database, checksums
both, and self-heals drift, so a non-durable broadcast subscription suffices.<br>
[More Details: v1.2.5 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.5?tab=readme-ov-file#project-structure)

### v1.2.4 — complete (05/14/2026)

v1.2.4 is the **stress / load / race-repro + auth-pattern sprint** — a pure-REST Gatling 3.13
harness (codename "hauberk") becomes the platform's standard testing surface, and the gateway
gains two non-browser auth patterns — **Vanilla Token Relay** and **Phantom Token Relay** —
alongside BFF and plain JWT.<br>
[More Details: v1.2.4 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.4?tab=readme-ov-file#project-structure)

### v1.2.3 — complete (05/08/2026)

v1.2.3 is the **BFF sprint** — Backend-for-Frontend tier introduced between the Angular SPA and
the Spring Cloud Gateway. The browser is now a thin client over an opaque session cookie; the
access token never leaves the cluster.<br>
[More Details: v1.2.3 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.3?tab=readme-ov-file#project-structure)

### v1.2.2 — complete (04/20/2026)

v1.2.2 is the first complete vertical slice of the Esquire framework — schema, services,
UI library, and frontend all built, connected, and tested as one working system.
<br>
[More Details: v1.2.2 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.2?tab=readme-ov-file#project-structure)


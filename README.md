
| ![Alt text](./favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|


> 
> **v1.2.5 — complete.** bizTree's internal cache rebuilt as the "Taijitu" Supreme Ultimate Cache — a **recoverable cache service** whose background night-watch self-heals dropped events, closing the cache-load race behind an unchanged API surface.
>

Frameworks for organizing business entities in a tree, for any business or activity.
The framework covers the traditional backoffice feature set: entity onboarding, profile
maintenance, permissions, authorization, and accounting.

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

### Domain & Data Model
- [Object Kind Enumeration](doc/Object.Kind.enum.md)
- [Entity Path Semantics](doc/entity.path.semantics.md)
- [Default Field Rule](doc/DefaultRule.md)
- [bizTree — H2-backed In-Memory Cache](doc/H2BizTree.md)
- [Database Dictionary](doc/DatabaseDictionary.md)

### Testing
- [Testing Stack — frameworks, scope, coverage](doc/Esquire.TestingStack.md) *(every framework in use across all Esquire projects, what it covers, current test counts at v1.2.5)*
- [Testing — Gatling as Esquire standard](doc/Testing.md) *(policy note for integration / load / stress / race-repro)*
- [Haubergeon — Gatling harness reference](doc/Esquire.Haubergeon.md)
- [Race Conditions — reproduction protocol](doc/Race.Conditions.Repro.md)

### Reports


|                                                                   |                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **esquire.services**| - [v1.2.5 Milestone Report](doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](doc/reports/report_v1.2.2.md)                                                                                                                                                                                  |
| **esquire.explorer**| - [v1.2.5 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.5.md)<br/>- [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.2.md) |
| **esquire.ui.lib**| - [v1.2.3 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_v1.2.3.md)<br/> - [v1.2.2 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_2026_04_19_31750f3.md)                                                                                                                     |
| **esquire.db.seed**| - [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.4.md)<br/> - [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.2.md)                                                                                                                          |


### Deployment
- [Where To Go — Deployment Plan and Platform Decisions](doc/WhereToGo.md)
- [OCI Pricing Reference](doc/OCI.Pricing.md)

### Value Proposition
- [Esquire Application Frameworks — What, Why, and for Whom](doc/Esquire.Vision.md)

---

## Component Model

![Component Model](doc/media/ComponentModel.png)

---

**Esq2025** — the database (Oracle or Postgres); persistent store for all entity data,
transactions, permissions, audit log, and configuration parameters.

**Messaging Bus** — ActiveMQ broker; two logical channels:
- *IAM Request-Response Bus* — carries identity commands (create / update / delete user)
  from keySmith to kcMaster, and acknowledgement replies back
- *Entity Broadcast Bus* — enyMan and pacMan publish entity change events on every mutation;
  bizTree and other interested consumers subscribe

**pacMan** (Personal Account Manager) — the accounting service; manages account balance
operations: deposit, withdrawal, cross-currency transfer; the place where business logic lives;
all other services exist to support it.

**bizTree** — the entity tree service; maintains an in-memory cache of the business entity
tree; serves tree navigation to the frontend; stays current by consuming the broadcast bus.
As of v1.2.5 a **recoverable cache service**: a two-buffer anti-entropy design whose periodic
night-watch rebuilds a shadow from the database, compares the two, and self-heals any drift —
so an event missed while the service was down is reconciled automatically, with no restart.

**enyMan** (Entity Manager) — manages organizations and users; handles create, update, delete,
and move operations; publishes entity change events to the broadcast bus.

**keySmith** — authentication and access profile service; manages IAS integration and
JWT-based authorization; serves access profiles to the frontend; publishes identity change
requests to the IAM bus.

**kcMaster** — Keycloak IAS sync coordinator; the only service that writes to Keycloak directly;
consumes identity commands from the IAM bus and executes create / update / delete in the IAM.

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

---

The two public hosts at a glance:

| Host | Purpose | Auth shape | Who talks to it |
|---|---|---|---|
| `esquire.mir0n.pro` | Administrative GUI (BFF + SPA) | OIDC code+PKCE -> opaque session cookie | Humans in a browser |
| `api.esquire.mir0n.pro` | Public REST API (gateway direct) | Bearer JWT on every request | Service-to-service callers, integrations, load / smoke harnesses |

---

## v1.2.5 — complete (05/24/2026)

v1.2.5 is the **bizTree cache-refactor sprint** — bizTree's internal cache is rebuilt as the
"Taijitu" Supreme Ultimate Cache (anti-entropy double-buffer with shadow promotion): two equal
in-memory monads behind one director, reconciled by a periodic **night-watch** sweep that rebuilds
the shadow from the database, checksums both, and self-heals drift (log / swap / terminate). The
result is a **recoverable cache service** — it closes the cache-load race deferred from v1.2.4 and
reconciles events missed during downtime, so a non-durable broadcast subscription is enough. The
external surface — REST paths, JMS topic, cache interface — is unchanged; the new implementation
slots in behind it.<br>
[Architecture: doc/Esquire.BizTree.md](doc/Esquire.BizTree.md)

## v1.2.4 — complete (05/14/2026)

v1.2.4 is the **stress / load / race-repro + auth-pattern sprint** — a pure-REST Gatling 3.13
harness (codename "hauberk") becomes the platform's standard testing surface, and the gateway
gains two non-browser auth patterns — **Vanilla Token Relay** and **Phantom Token Relay** —
alongside BFF and plain JWT.<br>
[More Details: v1.2.4 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.4?tab=readme-ov-file#project-structure)

## v1.2.3 — complete (05/08/2026)

v1.2.3 is the **BFF sprint** — Backend-for-Frontend tier introduced between the Angular SPA and
the Spring Cloud Gateway. The browser is now a thin client over an opaque session cookie; the
access token never leaves the cluster.<br>
[More Details: v1.2.3 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.3?tab=readme-ov-file#project-structure)

## v1.2.2 — complete (04/20/2026)

v1.2.2 is the first complete vertical slice of the Esquire framework — schema, services,
UI library, and frontend all built, connected, and tested as one working system.
<br>
[More Details: v1.2.2 README](https://github.com/mir0n-pro/esquire.services/tree/release/v1.2.2?tab=readme-ov-file#project-structure)


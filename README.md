| ![Alt text](./favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|

> **v1.2.4 — complete (05/14/2026).** Gatling 3.13 stress / load / smoke / race-repro harness (codename "hauberk"). Gateway gains 4 (four) auth patterns. Public REST API re-exposed at [api.esquire.mir0n.pro](https://api.esquire.mir0n.pro).


In addition to the existing auth patterns (BFF and plain JWT), two non-browser auth patterns were added — **Vanilla Token Relay** and **Phantom Token Relay** — both keep authorization claims off the client side while preserving local validation downstream of the gateway.


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

### Domain & Data Model
- [Object Kind Enumeration](doc/Object.Kind.enum.md)
- [Entity Path Semantics](doc/entity.path.semantics.md)
- [Default Field Rule](doc/DefaultRule.md)
- [bizTree — H2-backed In-Memory Cache](doc/H2BizTree.md)
- [Database Dictionary](doc/DatabaseDictionary.md)

### Testing
- [Testing Stack — frameworks, scope, coverage](doc/Esquire.TestingStack.md) *(every framework in use across all Esquire projects, what it covers, current test counts at v1.2.4)*
- [Testing — Gatling as Esquire standard](doc/Testing.md) *(policy note for integration / load / stress / race-repro)*
- [Haubergeon — Gatling harness reference](doc/Esquire.Haubergeon.md)
- [Race Conditions — reproduction protocol](doc/Race.Conditions.Repro.md)

### Reports


|                                                                   |                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **esquire.services**| - [v1.2.4 Milestone Report](doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](doc/reports/report_v1.2.2.md)                                                                                                                                                                                  |
| **esquire.explorer**| - [v1.2.4 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.4.md)<br/>- [v1.2.3 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.3.md)<br/>- [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.2.md) |
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

## v1.2.4 — complete (05/14/2026)

v1.2.4 is the **stress / load / race-repro + auth-pattern sprint** — a pure-REST Gatling 3.13
harness lands as the platform's standard testing surface, and the gateway
gains two new non-browser authentication patterns alongside BFF and plain JWT:
**Vanilla Token Relay** (no token on the wire from client) and **Phantom Token Relay**
(stripped JWT at client, full JWT restored at gateway via RFC 8693 token-exchange).

### Hauberk: Gatling harness under `explorer/hauberk/`

A new Maven module sits alongside `backend/`, `frontend/`, and `e2e-test/`:

- **Gatling 3.13 Java DSL** as the engine; runnable via a picocli CLI
  (`hauberk.cmd run <simulation> [--config <profile>]`).
- **26 reusable Chains** + **17 Simulations** spanning smoke (`ChainsSmoke`),
  cleanup (`CleanHouse`), seed (`PrepareForAnything`), tree-diff (`CompareTrees`),
  KC-through-BFF integration (`KcIntegrationSmoke`), 4-deep move (`MoveSmoke`),
  five parallel forever-loop scenarios (`SuperLoad`), and the Phase 8 race repros.
- **Profile overlays** (`hauberk.properties`, `hauberk-k8s.properties`,
  `hauberk-oke.properties`) pick environment endpoints without code changes.
- **Identity grounded in real entities:** `client_credentials` against KC's
  `esq-hauberk` / `esq-hauberk-S` / `esq-hauberk-M` clients; service-account claims carry
  `esq_uid` and `esq_rootpath` so downstream authorization gates fire exactly as for a
  real admin user.

Full reference: [doc/Esquire.Haubergeon.md](doc/Esquire.Haubergeon.md).

### Gateway gains added: "Vanilla Token Relay" and "Phantom Token Relay"

Two new authentication patterns land on the gateway alongside the existing BFF and plain
JWT. Both are non-browser patterns that keep claims off the client side
while preserving local validation downstream of the gateway (no per-request KC callback).

### Tree-comparison endpoints + `EsqTreeNode.entityPath`

Race verification needs to diff what biztree's H2 cache holds against the relational
truth in Postgres. Two endpoints serve the two sides:

- **`/esq-cmd-tree`** (enyMan) — recursive CTE over `esq_entity` + `esq_entity_path`;
  the relational FK walk. Returns `entityPath` straight from `ep_path`.
- **`/esq-tree`** (biztree) — the H2 in-memory cache; `entityPath` is derived from
  `tree_path` by stripping virtual-folder segments. No duplicate column.

`CompareTrees` reads both, filters to `kind=34` regular USRs (the kinds where
`isPathParentOnly()` doesn't cause expected divergence), and reports any path or
membership mismatch. The same plumbing serves the race-repro verifiers.

### Four-layer observability protocol

Every request now carries a precise time-decomposition header chain so post-flight
reports can separate client-side, gateway-side, and service-side latency:

| Header | Set by | Measures |
|---|---|---|
| `X-Response-Time` | client (hauberk) | full wall-clock client RTT |
| `Esq-Gw-Inner-Time` | gateway | time inside the gateway pod, incl. Vanilla Token Relay brokering / Phantom Token Relay exchange when active |
| `Esq-Srv-Outer-Time` | downstream service | service pod time, request-to-response |
| `Esq-Srv-Inner-Time` | downstream service | business-logic time only, excluding framework overhead |

A per-request `PerformanceMatrix` CSV row is captured when the request carries
`X-Capture-Metrics: 1`; sims emit it for every load run. Full schema:
[doc/Esquire.ObservabilityStack.md](doc/Esquire.ObservabilityStack.md).

### Gatling becomes the Esquire testing standard

Decided mid-sprint: Gatling 3.13+ Java DSL is the Esquire standard for integration,
stress, load, and race-repro testing. Vocabulary is Gatling's verbatim — Simulation,
Scenario, Chain, VU, injection profile — no parallel project-local terms. Playwright
keeps the browser-end e2e surface; JUnit keeps the per-service unit / Spring slice
surface. Policy note: [doc/Testing.md](doc/Testing.md).

### Public REST API host: `api.esquire.mir0n.pro`

The gateway is publicly reachable again — but on a **separate host** from the BFF,
both terms on a single SAN cert (`esquire.mir0n.pro` + `api.esquire.mir0n.pro`).
Two ingress rules, one TLS Secret. Non-browser callers (service-to-service, hauberk,
future mobile / API consumers) point at `api.esquire.mir0n.pro` with their own
bearer token; the BFF stays as the only entry path for the browser.

### JWE position: parked under stock KC

The `client_credentials` re-test confirmed that stock Keycloak 26.4.7 still does not
emit JWE on `/token` for *any* standard grant; `DefaultTokenManager` is core-coupled
with no SPI seam for token-format changes. Vanilla Token Relay and Phantom Token Relay landed
as **partition patterns** -- both keep KC off the hot path through the downstream
microservices and absorb the claim-hiding cost at one well-defined seam
(client/gateway edge) rather than baking it into the token itself. Gateway-side JWE lab
kept armed (`JweAwareJwtDecoder`, `JwksController`, `/jwe-jwks`, keypair, `esq-hauberk`
JWE attributes) -- one config flip from re-test when KC ships proper access-token JWE
or an alternative IAS enters scope. Full closing record:
[doc/keyCloak-gateway.JWE.md](doc/keyCloak-gateway.JWE.md).

---

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


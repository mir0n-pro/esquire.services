| ![Alt text](./favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|

> **v1.2.3 — in development.** All four repositories (esquire.services, esquire.explorer, esquire.ui.lib, esquire.db.seed) are on their `pending-v1.2.3` branches. Themes, candidate work items, and progress are tracked in [v1.2.x Planning](doc/v1.2.x.Planning.md).

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
- [Keycloak / Gateway — JWE Token Encryption](doc/keyCloak-gateway.JWE.md) *(on hold — pending BFF)*

### Domain & Data Model
- [Object Kind Enumeration](doc/Object.Kind.enum.md)
- [Entity Path Semantics](doc/entity.path.semantics.md)
- [Default Field Rule](doc/DefaultRule.md)
- [bizTree — H2-backed In-Memory Cache](doc/H2BizTree.md)
- [Database Dictionary](doc/DatabaseDictionary.md)

### Service Reference
- [KeySmith Credential Routines — State Machine & Collaboration](doc/keySmithCredentialRoutine.md)

### Reports

- **esquire.services** [v1.2.2 Milestone Report](doc/reports/report_v1.2.2.md)

- **esquire.explorer** [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.explorer/blob/develop/doc/reports/report_v1.2.2.md)

- **esquire.ui.lib** [v1.2.2 Release Report](https://github.com/mir0n-pro/esquire.ui.lib/blob/develop/doc/reports/report_2026_04_19_31750f3.md)

- **esquire.db.seed** [v1.2.2 Milestone Report](https://github.com/mir0n-pro/esquire.db.seed/blob/develop/doc/reports/report_v1.2.2.md)

### Deployment
- [Where To Go — Deployment Plan and Platform Decisions](doc/WhereToGo.md)
- [OCI Pricing Reference](doc/OCI.Pricing.md)

### Value Proposition
- [Esquire Application Frameworks — What, Why, and for Whom](doc/Esquire.Vision.md)

---

## Component Model

![Component Model](doc/media/ComponentModel.svg)

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

**gateway** — Spring Cloud Gateway; the single entry point for all frontend traffic; routes
requests to the appropriate backend service by path and entity kind; validates JWT tokens on
every request.

**Keycloak** — external IAM; issues JWT access tokens; manages user identities, realm
configuration, and authentication flows including TOTP; runs as a containerized service.

**Esquire Explorer** (Node.js / Angular) — the frontend application; tree-based entity
browser and operations UI; consumes the `@mir0n-pro/esquire.ui` library; communicates
exclusively through the gateway REST API.

---

## v1.2.2 — complete (04/20/2026)

v1.2.2 is the first complete vertical slice of the Esquire framework — schema, services,
UI library, and frontend all built, connected, and tested as one working system.

### esquire.services

The backend grew from a scaffold into a working backoffice engine.

Microservice topology finalized: **gateway** (Spring Cloud Gateway), **keySmith**
(auth + IAS integration), **enyMan** (entity management), **pacMan** (accounting),
**bizTree** (in-memory entity tree), **kcMaster** (Keycloak sync coordinator).

Entity lifecycle complete: Create, Move, Delete for all entity kinds (org / user / account),
with path propagation, Keycloak sync via JMS, and a unified REST facade under kind-aware
gateway predicates (`/esq-cmd-new`, `/esq-cmd-save`, `/esq-cmd-del`, `/esq-acct`).

Asynchronous messaging via ActiveMQ: entity broadcast on every mutation; bizTree consumer
with kind-dispatched handlers; kcMaster decoupled from keySmith via JMS queue.

Accounting operations: deposit, withdrawal, and cross-currency transfer with conversion
rate; dictionary-driven field validation; two-leg transfer linked by a shared transaction key.

Observability: three-tier logging (console / develop / msg-audit), MDC correlation across
REST and JMS, Prometheus metrics.

Vendor independence by design: database vendor SQL isolated in named query XML files (Oracle
and Postgres today; further RDBMS targets are additive); ActiveMQ is the current broker with
a vendor-agnostic bus abstraction planned; kcMaster is the only Keycloak-aware service —
replacing the IAM means replacing kcMaster only, the rest of the system is unchanged.

### esquire.explorer + esquire.ui.lib

The frontend reached full operational capability.

The UI layer was extracted into a standalone published package (`@mir0n-pro/esquire.ui`):
tree explorer, CRUD dialogs, dictionary-driven field rendering, permission model, and pluggable
command handler infrastructure. The server defines the UI — field layouts, control types,
and validation rules are delivered at runtime from server configuration, with no hardcoded
field definitions in the frontend.

On top of the library, the explorer delivers the full entity lifecycle with permission gating
and tree refresh, and a complete accounting command set: Deposit, Withdrawal, and Transfer
dialogs with AmountEffect validation, account picker, and dynamic cross-currency rate label.

Quality: 30-test Playwright e2e suite covering full entity and accounting lifecycles; unit
tests at both library and app levels.

### esquire.db.seed

The schema reached production shape.

Audit infrastructure: BRIUD triggers on all entity tables (Oracle and Postgres); full audit
log tables; correlation / request / uid columns on every writing. Entity path extracted to a
dedicated satellite table (`ESQ_ENTITY_PATH`). `ESQ_ACCT_TRANSACTION` fully specified: string
PK, transfer cross-link column (`ATR_PK_TX`), conversion rate (`NUMERIC(12,6)`), reference
and memo fields. Referential integrity: ON DELETE CASCADE on all user and org FK chains;
sequence-based PKs throughout seed data.

---

With v1.2.2, the Esquire framework has reached the first and most important horizon: a working, connected, tested system from database to browser.

**The Establishment is real — and it's up and running at [esquire.mir0n.pro](https://esquire.mir0n.pro). At your convenience, for your curiosity.**

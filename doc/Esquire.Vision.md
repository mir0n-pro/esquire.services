| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|

# Esquire Frameworks — Value Proposition

---

## The Vision

Every organized domain has a natural structure: a hierarchy. Organizations have divisions.
Divisions have teams. Teams have members. Members have assets. Assets belong to portfolios.
Cases belong to clients. Projects belong to programs.

The insight behind Esquire is that **this hierarchy is not just data — it is the authorization
model.** In most systems, the org chart lives in one table and the access control rules live
somewhere else, maintained separately, diverging over time. Esquire collapses those two things
into one: the tree itself defines what you can see and what you are allowed to do.

**Hierarchical visibility** is the first dimension: your position in the tree determines your
data scope. You see your branch. Nothing above you, nothing beside you. No explicit filter
written per query — visibility is structural, resolved from where you sit.

**Hierarchical roles** is the second dimension: in most business domains, authority is
positional — the same person carries different responsibilities at different nodes across
different organizational structures. Esquire is designed to reflect that reality: authority
is not a global property of your account, but a function of where you sit in the tree.

**Entity self-description** is the third dimension: every business entity type carries its
own definition — the fields it exposes, the validation rules it enforces, the commands it
permits, the way it is rendered. This definition lives at the business layer, expressed once
in server configuration. The UI receives it at runtime and adapts. There is no field layout
written in frontend code, no validation rule duplicated between layers, no interpretation of
business logic on the client side. The entity defines itself; every tool that touches it reads
that definition from the same authoritative source.

Together, these three concepts form the Esquire backbone. Any structured domain placed on a
tree inherits visibility scoping, positional authority, and entity-driven behavior without any
additional rules being written. The framework resolves them from the structure.

**The Biz Explorer is the first tool built on this idea.** The accounting operations are the
first domain it demonstrates. Neither is the point. They are evidence that the model works
end to end, from database to browser, under real operational conditions.

The point is the pattern: any hierarchy of any entities — organizations, assets, cases,
portfolios, projects, contracts — can be placed on an Esquire tree. Tools built on top of
that tree inherit governance for free. There is no limit to the number or type of trees a
deployment can maintain, and no limit to the tools that can be built on each one.

> A tree is the most natural model for any organized domain.
> Once your data is on a tree, visibility, authority, and operations
> are all resolved by position — not by rules written separately for every feature.
> Every entity knows what it is, what it permits, and how it behaves.
> Define it once at the business layer. Everything else follows.

That is the Esquire idea.

---

## What is it?

Esquire is a **business entity framework** — the structural backbone for any system that
needs to organize people, organizations, and resources in a tree, enforce who can see and
do what within that tree, and run business operations against it.

The backoffice scenario — onboarding, profile maintenance, permissions, accounting — is the
demonstration domain. Accounting in particular is the "everybody's know-how" example: a
universally understood domain that exercises the full framework stack end to end. It is not
the destination. It is the proof of concept for the real idea.

The real idea is this: **authorization shaped by the business tree itself.**

Traditional role-based access control answers the question "what operations is this user
allowed to perform?" Esquire extends that with a second, orthogonal dimension: **hierarchical
visibility** — "what data is this user allowed to see at all?"

In Esquire, every entity lives at a position in the tree. Every authenticated user carries
a root path — the branch of the tree they belong to. Read operations are automatically
scoped to that branch: a user sees their own subtree and nothing outside it. A regional
manager sees their region. A system administrator at the root sees everything. No explicit
filter needs to be written into each query; the scope is structural, derived from where the
user sits in the tree.

The combination — role-based rules for operations, tree-based rules for visibility — is
what Esquire calls the authorization backbone. Any domain can be placed on top of it.
Accounting is one. HR, portfolio management, service provisioning, case tracking — any
domain where data ownership follows an organizational hierarchy fits the same model.

Beyond authorization, Esquire treats every business entity as a self-describing object:
its field structure, validation rules, available commands, and rendering behavior are all
defined once at the business layer and delivered to the UI at runtime. There is no business
logic written in the frontend, no field layout duplicated between server and client, no risk
of the two drifting apart. The entity is the single source of truth for how it looks, what
it accepts, and what can be done with it.

Esquire is not a product you subscribe to. It is a framework you own, deploy, and extend.
No SaaS lock-in, no monthly seat fees, no vendor standing between you and your data.

---

## Who needs it?

**Any engineering team building a backoffice system from scratch.**

### Startups

A startup's most precious resources are time and people. Neither can be spent rebuilding
infrastructure that already exists. The permission model, the audit trail, the entity
lifecycle, the admin UI — these are not features that differentiate your product. They are
the floor you stand on before a building that actually matters.

Without a framework like Esquire, a small team either cuts corners on all of it (and pays
the debt later, usually at the worst possible moment) or assigns one engineer to build it
properly (and loses that engineer to infrastructure for months while the product waits).

Esquire hands a startup a production-grade structural foundation on day one. The team
ships the product. The framework holds the floor.

### Fintech and payment platforms

You need accounts, balances, and multi-currency transfers before your first client goes live.
You need an audit trail before a regulator asks for one. You need role-based access control
before your first support engineer accidentally modifies a production record. Esquire covers
all three on day one — not as afterthoughts, but as the structural core of the system.

### B2B SaaS companies

Your data model is always some variation of: clients (organizations), people under those
clients (users), and service tiers or entitlements (accounts). You need an onboarding flow,
a permission model that scales with client tiers, and an admin panel your operations team
can actually use. That is exactly the Esquire entity tree and permission model.

### Digital banks and brokers

The regulatory surface — KYC data, audit logs, role-based access, TOTP authentication,
correlation tracking across all operations — is covered by the framework, not by your
application team. You focus on the product differentiators; Esquire holds the compliance
infrastructure steady underneath.

### Platform integrators

Teams that build and hand off backoffice subsystems to clients. Esquire is the reusable
structural layer. Client-specific domain logic and UI customizations are the thin application
shell on top. You deliver faster because you are not rebuilding the foundation every time.

### Enterprise internal platforms

HR systems, ERP front-ends, CRM administrative shells. Any system where a hierarchy of
entities needs to be managed, permissioned, and audited over a multi-year lifespan. Esquire
gives you the infrastructure that does not need to be revisited every time the product evolves.

---

## Why it matters

### Every team builds the same five things

Before any product delivers value, an engineering team must build:

1. A way to store and manage users and organizations
2. A permission system (and then rework it two or three times)
3. An audit log (typically after the first incident)
4. Some kind of account or balance concept
5. An admin UI that someone promises to clean up later

These are not competitive differentiators. They are infrastructure. Building them from
scratch on every project is expensive, slow, and consistently underestimated. Buying a
monolithic ERP means inheriting ten years of someone else's assumptions. Low-code admin
tools give you a panel quickly but leave the hard problems — permissions, audit, accounting
logic — entirely to you.

Esquire is the middle path: a framework that solves the structural problems correctly once,
so your team never has to solve them again.

---

## What Esquire does that others do not

### 1. The server defines the UI

Field layouts, control types, validation rules, and available commands are delivered at
runtime from a server configuration. Add a new entity type, change a field label, adjust a
validation rule — the Angular frontend adapts without a redeployment. The UI team is not
in the loop for every schema change.

This is unique in the competitive landscape. Every comparable tool — React Admin, AdminJS,
Forest Admin, Retool — uses a static component library. Fields are defined in frontend code.
A schema change is a deployment. In regulated environments where compliance requirements
change quarterly, or in multi-tenant platforms where different client tiers see different
field sets, that model does not hold.

### 2. A permission model that enforces itself at both layers

Every command and every editable field is gated by the logged-in user's access profile,
resolved server-side, and reflected in the frontend without duplication. There is no way to
call an endpoint the user is not permitted to call, and no way to render a field the user
is not permitted to edit. The frontend and backend enforce the same model from the same
server configuration.

Most competitors implement RBAC at the authentication boundary and leave field-level and
command-level enforcement to the application team. In Esquire, it is structural — the
permission check is part of every service method signature, not a layer the developer
remembers to add.

### 3. Audit trail as a first-class, pluggable concern

Every write to every entity carries a correlation ID, request ID, and the acting user ID, so
audit entries tie back to distributed-tracing correlation IDs. Audit logging itself is
optional and pluggable: a deployment picks from a wide set of models — from in-database
triggers (audit and entity write in one transaction, no race) to a fully decoupled,
message-driven pipeline — or turns it off entirely, with no application-code change.

Apache Syncope and Appwrite mention audit logging. No other tool in this space ties audit
entries to distributed tracing correlation IDs, making it possible to reconstruct exactly
what sequence of operations — across multiple services, over JMS — produced any given
database state.

### 4. Accounting as the demo business model

Everyone knows what to expect from accounting — that is exactly why it is the demo.
The accounting domain — multi-currency deposit, withdrawal, and two-leg transfer with
conversion-rate validation, balance constraints, and status guards — is implemented as
the framework's demonstration, not as a framework feature. Pick a domain the reader
already understands, and the framework's behavior speaks for itself: tree-scoped reads,
positional permission resolution, server-driven UI rendering, audit, async entity
broadcast — all visible in a single click-through.

Most comparable tools demonstrate themselves with CRUD over toy data. n8n integrates
with external accounting systems (QuickBooks, Xero) but does not run a domain end to
end. Broadleaf Commerce has a payment layer scoped to eCommerce checkouts. No other
entry in the competitive landscape ships a framework demonstration this complete. The
accounting domain is the proof. Any other domain — KYC, case management, contracts,
portfolios — sits on the same backbone and inherits the same structural rules.

### 5. Asynchronous entity synchronization

Entity changes propagate across services via an ActiveMQ broadcast bus. The in-memory
entity tree (bizTree) stays current without polling. Keycloak identity operations are
decoupled from the entity write path — keySmith publishes a command to the IAM bus;
kcMaster executes it asynchronously. The entity mutation and the downstream synchronization
never block each other.

JHipster and Apache Syncope work in similar architectural territory (Spring Boot,
microservices, Java) but neither ship a pre-wired messaging topology for entity sync.
Teams using those tools build the messaging layer themselves.

### 6. No vendor lock-in at any layer

The backend is Spring Boot and Spring Cloud — the most widely deployed Java microservices
stack in the industry. The frontend is Angular with a published npm library. Every component
is a well-known open-source technology. You are never depending on a proprietary protocol,
a vendor API, or a hosted service you cannot replace.

Vendor independence is an architectural principle, not a feature. It operates at three layers:

**Database.** Oracle and Postgres are supported today, with vendor-specific SQL isolated in
named query XML files so switching does not touch application code. MySQL and other RDBMS
targets are on the roadmap — the same isolation principle makes them straightforward additions.

**Messaging broker.** ActiveMQ is the current implementation. A future Esquire milestone will
introduce a vendor-agnostic messaging bus abstraction — the broadcast and request-response
patterns remain the contract; the underlying broker becomes a deployment choice.

**Identity and access management.** Keycloak is today's IAM. The architecture was designed
from the start so that Keycloak is never touched directly by the core services. keySmith
publishes identity commands; kcMaster executes them against Keycloak. Replacing Keycloak
means replacing kcMaster with an xyzMaster that speaks to a different IAM — the rest of
the system is unchanged. This is a deployment decision, not an architectural one.

With v1.2.2, Esquire has reached the Establishment: a working, connected system from database
to browser. From here, many routes are open — new database targets, new brokers, new IAM
providers, new entity domains — without any dramatic change to the core architecture.

---

## The competitive landscape

### Low-code admin panel tools

[Forest Admin](https://www.forestadmin.com/) · [Retool](https://retool.com/) · [AdminJS](https://adminjs.co/) · [React Admin](https://marmelab.com/react-admin/)

These tools generate a working UI quickly. The tradeoff is that they are UI tools, not
backoffice frameworks. They have no opinion about your permission model beyond what the
database schema implies. They have no audit trail. They have no accounting layer. They work
well for internal dashboards and data browsing. They are not a foundation for a regulated
backoffice system.

Esquire is slower to get started with than Retool. It is incomparably more complete.

### Identity and access management

[Apache Syncope](https://syncope.apache.org/) · [Frontegg](https://frontegg.com/) · [OpenFGA](https://openfga.dev/)

Apache Syncope is the closest technical cousin — hierarchical entity model, Spring-based,
multi-database, audit-aware. But it is an identity management system. Its hierarchy
exists to support provisioning and reconciliation of users and groups across external
systems. There is no server-driven UI, no domain-agnostic entity layer for arbitrary
business hierarchies, no async entity broadcast bus, and no demonstration that runs a
business domain end to end. Syncope solves identity provisioning very well; Esquire
solves the layer above it.

Frontegg is purpose-built for B2B SaaS multi-tenancy and user management. Its permission
model is strong. But it is a SaaS product with proprietary IAM, and it stops at the
organization/user boundary — there is no entity layer below users, no server-driven UI,
no async messaging topology, no path to host it yourself.

OpenFGA is a fine-grained authorization engine — it solves one part of the permission
problem with great depth. Esquire's permission model is less academically rigorous than
ReBAC, but it is fully integrated with the entity model and the UI, and ships as part of
the framework rather than as a separate service to integrate.

### Microservice generators

[JHipster](https://www.jhipster.tech/)

JHipster generates a microservices scaffold. It does not give you a pre-built, working
backoffice — it gives you the starting point for building one. After generation, the
hierarchical entity tree, the positional permission model, the server-driven UI, and
the async messaging topology are all still on your backlog. Esquire starts where
JHipster leaves off.

### eCommerce and CMS platforms

[Broadleaf Commerce](https://broadleafcommerce.com/) · [Strapi](https://strapi.io/)

These platforms ship strong admin panels for a fixed domain (products, content). Their
admin domain is closed — extending it means rebuilding what the platform was built around.
Esquire's admin domain is open: any hierarchy of any entities sits on the framework's
structural backbone with no domain-specific assumptions baked in. Broadleaf's Spring Boot
foundation is relevant; its domain model is not.

---

## What Esquire does not do

Esquire is a framework for developers building platforms. It is not a no-code tool.
It is not a hosted service. It does not provide workflow automation, BI dashboards, or
content management. It does not replace Keycloak, RabbitMQ, or your database — it
integrates them into a coherent architecture.

It is also not a full ERP. It is the structural layer an ERP or backoffice product
is built on.

---

## The one-line version

**Esquire is what you build before you build your product — solved once, so you
never solve it again.**

---

## Feature comparison

| Feature | Esquire | Apache Syncope | JHipster | Broadleaf | Forest Admin | Retool | Frontegg |
|---|---|---|---|---|---|---|---|
| Hierarchical entity tree (domain-agnostic) | Yes | Partial | No | No | No | No | Partial |
| Server-driven UI | Yes | No | No | No | No | No | No |
| Audit trail (pluggable, optional) | Yes | Yes | No | No | No | No | No |
| Distributed trace correlation | Yes | No | No | No | No | No | No |
| Microservices + async messaging | Yes | Partial | Yes | Yes | No | No | No |
| Field-level permission enforcement | Yes | Partial | No | No | No | No | No |
| Tree-shaped (positional) authorization | Yes | Partial | No | No | No | No | No |
| Pluggable IAM adapter (Keycloak today) | Yes | No | No | No | No | No | No |
| Oracle + Postgres | Yes | Yes | Yes | Yes | Partial | Yes | No |
| No vendor lock-in | Yes | Yes | Yes | Partial | No | No | No |
| Open source / self-hosted | Yes | Yes | Yes | Partial | No | No | No |
| End-to-end demonstration domain | Yes | No | No | Partial | No | No | No |

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*

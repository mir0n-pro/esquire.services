<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Frameworks — Value Proposition</h1>
    </td>
  </tr>
</table>

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

Every framework promises that you write only business logic. The promise is kept when the parts that promise
covers are actually in the box and already joined to each other: storage, identity and sign-in, permissions,
the messaging between services, the audit trail, metrics, logs and traces. Esquire holds those, wired
together and running, which is what leaves the domain as the only thing to write. What it does NOT hold is
stated as plainly, in "What Esquire does not do" below.

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

## Why a framework at all

A framework earns its name by doing one thing: letting the people who write business logic write
*only* business logic. Networks, protocols, message brokers, persistence, identity, deployment — the
plumbing every application drags along — is the framework's job, not the domain developer's. On Esquire
you place an entity on the tree and describe what it *means*; the framework already carries how it is
stored, how it is synchronized across services, who may see it, how it is audited, and how it reaches
the browser. The business-logic developer writes no query, no message handler, no permission filter, no
deployment manifest. They write the domain. Everything else is inherited.

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

### 1. Tree-shaped visibility, resolved from position rather than written by hand

**This is the claim to lead with, and the only one no comparable framework can currently
match.** A user sits somewhere in a hierarchy — region, branch, team, portfolio — and what they
may see is their subtree. Not a rule attached to their role, not a filter added to each query:
their position resolves it, for every entity, everywhere.

The alternative is the state of the art elsewhere, and it is worth being precise about, because
it is where the difference lives:

- **[Jmix](https://www.jmix.io/)** has row-level security, and it is good. But every constraint
  is authored per entity class by the developer — `@JpqlRowLevelPolicy(entityClass =
  Customer.class, where = "{E}.region = :current_user_region")` — and the context available is a
  flat attribute on the user. Its own documentation states there is no native support for a
  "user sees their own subtree" constraint: explicit constraints must be written for each level,
  with no automatic cascade.
- **[Apache Causeway](https://causeway.apache.org/)** does not filter rows at all. SecMan grants
  ALLOW/VETO over namespaces, types and members — authorization over what exists in the UI,
  not over which rows come back.
- **[OpenFGA](https://openfga.dev/)**, **[SpiceDB](https://authzed.com/)** and the other
  Zanzibar engines answer relationship questions with far more rigour than Esquire does, as a
  separate service. Their hard case is the same one Esquire is built around: filtering a list to
  a subtree, on every screen, rather than checking one object at a time.

So the honest form of the claim is not "nobody does authorization" — plenty do, some better in
their own dimension. It is that **a hierarchy is a structure here rather than a facility**, and
one forgotten hand-written filter is a data leak nobody notices.

A survey of eight codebases that all scope authorization by hierarchy sharpens that into the property worth
comparing on. Six of the eight scope by hierarchy in some form, so having a tree is not the difference. In
half of them the scope is **opt-in** — a filter fragment added per query, a checker called before the work, a
session wrapper opened around the call — and the opt-in is invisible to review, to the build and to static
analysis, because **a query missing the filter looks exactly like a correct query**. The three that make it
structural give the same reason independently: one enforcement point instead of one per method, which an
oversight in a future endpoint cannot skip. That is the line to hold: not that Esquire has tree-shaped
authorization, but that here the scope cannot be left out.

### 2. The server defines the UI

Field layouts, control types, validation rules, and available commands are delivered at
runtime from a server configuration. Add a new entity type, change a field label, adjust a
validation rule — the Angular frontend adapts without a redeployment. The UI team is not
in the loop for every schema change.

Against the low-code tools — react-admin, AdminJS, Forest, Retool — this is a real difference:
they use a static component library, fields are defined in frontend code, and a schema change is
a deployment.

**It is not unique, and this document used to say it was.** Apache Causeway generates its UI
directly from the domain model — that is the entire identity of the project, and it runs on
Spring Boot as Esquire does. [Jmix](https://www.jmix.io/) generates UI from the entity model
too. The distinction that survives is narrower: Causeway derives the UI from *code* — the domain
classes as written — while Esquire derives it from runtime server *configuration*, so the
change that reaches the browser is a configuration change rather than a redeployment. That is
worth claiming. "Nobody else does server-driven UI" is not.

### 3. A permission model that enforces itself at both layers

Every command and every editable field is gated by the logged-in user's access profile,
resolved server-side, and reflected in the frontend without duplication. There is no way to
call an endpoint the user is not permitted to call, and no way to render a field the user
is not permitted to edit. The frontend and backend enforce the same model from the same
server configuration.

**Field-level enforcement itself is not exclusive**, and the earlier version of this section
implied it was. Jmix has entity-attribute permissions, with UI components adapting
automatically; Causeway's SecMan permissions reach member level. What remains true is the
*single source*: one server-side resolution drives both the API refusal and the rendered form,
rather than a backend rule and a frontend rule that have to be kept in agreement by hand.

### 4. Audit trail as a first-class, pluggable concern

Every write to every entity carries a correlation ID, request ID, and the acting user ID, so
audit entries tie back to distributed-tracing correlation IDs. Audit logging itself is
optional and pluggable: a deployment picks from a wide set of models — from in-database
triggers (audit and entity write in one transaction, no race) to a fully decoupled,
message-driven pipeline — or turns it off entirely, with no application-code change.

Apache Syncope and Appwrite mention audit logging. No other tool in this space ties audit
entries to distributed tracing correlation IDs, making it possible to reconstruct exactly
what sequence of operations — across multiple services, over JMS — produced any given
database state.

### 5. Accounting as the demo business model

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

### 6. Asynchronous entity synchronization

Entity changes propagate across services over the Esquire Messaging Bus (an ActiveMQ broadcast
topic by default). The in-memory entity tree (bizTree) stays current without polling. Keycloak identity operations are
decoupled from the entity write path — keySmith publishes a command to the IAM bus;
kcMaster executes it asynchronously. The entity mutation and the downstream synchronization
never block each other.

JHipster and Apache Syncope work in similar architectural territory (Spring Boot,
microservices, Java) but neither ship a pre-wired messaging topology for entity sync.
Teams using those tools build the messaging layer themselves.

### 7. No vendor lock-in at any layer

The backend is Spring Boot and Spring Cloud — the most widely deployed Java microservices
stack in the industry. The frontend is Angular with a published npm library. Every component
is a well-known open-source technology. You are never depending on a proprietary protocol,
a vendor API, or a hosted service you cannot replace.

Vendor independence is an architectural principle, not a feature. It operates at four layers, and
at each one the same test applies: carrying an open component onto another cloud is deployment, not
portability. The claim only bites against a **cloud-native** service — the managed one with no
equivalent elsewhere, which is where lock-in actually lives.

That test has been run on **AWS**, against the AWS-native database, messaging and observability
described below. It is a method rather than a one-off: a cloud is brought up, the framework is pointed
at that vendor's own services, the result is measured, and the deployment comes down again. What
carries from one vendor to the next is the seams, not the vendor.

**Database.** Oracle and Postgres are supported today, with vendor-specific SQL isolated in
named query XML files so switching does not touch application code. MySQL and other RDBMS
targets are straightforward additions — the same isolation principle applies.

Postgres and Oracle are ours to run, and run anywhere. The harder case is the **AWS-native**
database — a managed service with no equivalent elsewhere — and Esquire runs on that too: the
same Postgres profile ran on **RDS for PostgreSQL** and on **Aurora PostgreSQL** with no change
to a single line of application code or SQL — a host name and a password, nothing else. All
three options work, so the choice could be made on measurement instead of preference:

| where Postgres runs | how it compared | cost |
|---|---|---|
| **in a pod** — what Esquire ships | same PostgreSQL 17.11, 20% fewer writes per second than RDS | **~$1.60/month** |
| **RDS for PostgreSQL** | the managed baseline | ~$25/month |
| **Aurora PostgreSQL** | **46% slower on writes** than the smaller, cheaper RDS, and a PostgreSQL version behind | ~$55/month |

Aurora is built for storage-layer replication, fast failover and read scaling. A single-replica
demo exercises none of that and pays for all of it, so it is the wrong tool here — not a worse
product. The point is that the framework did not care which one it was talking to.

**Messaging Bus.** Esquire ships a vendor-agnostic **Messaging Bus** — the broadcast and
request-response patterns are the contract, and the transport is a deployment choice behind a
pluggable transport-provider interface. Six transport providers exist on that one interface:
ActiveMQ, Kafka and Redis, and on AWS **Amazon SNS**, **Amazon SQS** and **Amazon Kinesis**.

The first three are open brokers: Esquire can carry them onto any cloud, which costs the vendor
nothing to allow. The **AWS-native** three are the harder case — managed services with no
equivalent elsewhere — and they carry the same three buses the brokers do: the entity broadcast,
the identity request/response and the audit log, with no change above the seam. They also show what the seam
is worth: SNS has no receive call at all, so the driver arranges a queue per consuming instance
underneath, and none of that reaches the application. A service still asks for a bus by name and
role, exactly as it does against a broker.

**And AWS is never built in.** No service depends on the AWS modules and no service image
contains a byte of the AWS client. The drivers are ATTACHED at deployment — mounted on docker,
copied in by an initContainer on Kubernetes — so the images that run on AWS are the same images
that run everywhere else. A deployment that is not on AWS carries none of it.

**Observability.** The three signals leave Esquire in open formats and nothing downstream is
assumed: logs as ECS JSON on stdout, traces as OTLP, metrics as a Prometheus page. Esquire ships
Grafana, Loki, Tempo and Prometheus reading them — a default, not a requirement.

Those are open components, so they follow Esquire onto any cloud unchanged; that much is just
deployment. The claim worth testing was the opposite one — that Esquire can hand its signals to the
**AWS-native** services, which is where observability lock-in actually lives. It can: all three
pillars were moved onto **AWS X-Ray**, **CloudWatch** and **CloudWatch Logs** on a running cluster, and **no service code changed and no image was rebuilt** — the image digests running against
CloudWatch were the digests that had been running against Prometheus and Tempo. One deployment value
moved, naming where spans are posted.

So it works both ways: Esquire does not need a cloud's observability, and it can use one. What does
not travel is the dashboards, because a query language is not a data format — the signals port
completely, the boards have to be rebuilt.

**Identity and access management.** Keycloak is today's IAM. The architecture was designed
from the start so that Keycloak is never touched directly by the core services. keySmith
publishes identity commands; kcMaster executes them against Keycloak. Replacing Keycloak
means replacing kcMaster with an xyzMaster that speaks to a different IAM — the rest of
the system is unchanged. This is a deployment decision, not an architectural one.

Esquire is a working, connected system from database to browser. Many routes are open from here —
new database targets, new transports, new IAM providers, new entity domains — without any dramatic
change to the core architecture.

---

### 8. One framework, more than one shape

A service in Esquire is a logical boundary, not a fixed program. The same code and the same configuration
run as **eight processes** in the classic shape, as **five** in the compact one, and as **four** in the cloud
profile where the audit trail is carried by database triggers instead of a drain process. Nothing about the
framework changes between them; only how many programs carry it.

That is possible because the framework aggregates services in two ways, and a Spring Boot application can
host both at once.

**Aggregation by MVC.** A REST-facing service is a package root with its controllers, entities, repositories
and beans. The host application imports that configuration — component scan, entity scan, repositories — and
the service keeps its own paths and its own handlers inside the shared process. A caller cannot tell the
difference: the route answers the same way it did across the network.

**Aggregation by events.** A service with no REST surface is smaller still — a set of bus listeners and the
beans behind them. It joins a host application as plain Java, with no web layer to merge, and keeps
listening on the same bus legs it listened to before.

The sprint that introduced this shape has one worked example of each:

- **Mesnie** is the write side: two REST services and one event-driven service in a single process. The
  identity work that used to travel as a request/response pair over the bus is served in-process behind the
  `IIdentityGateway` seam, so a hop disappears without a contract changing.
- **gateWard** is the read side: the gateway and the business-tree cache together, so the five tree routes
  are answered in the process that received them.

**The boundary is a rule, not a preference.** The framework aggregates *framework* services. Domain logic
stays outside: the accounting service is its own process in every shape, because a domain grows and changes
at its own pace and must not be welded to the backbone that carries it. The same rule keeps the browser tier
separate. Aggregation is a way to spend fewer machines on the framework, never a way to blur what belongs to
whom.

**What it is worth, measured.** On the cloud deployment the same load rig turns **27% more work** with
observability off and **42% more** with it fully on, the whole application uses **1.5 GiB** of memory under
load at one replica each, and the fleet is **7 pods instead of 13**. The four running-stack suites pass on
every shape without a change to any of them -- which is the real result, since a suite that needed editing
would be reporting a difference the framework promises is not there.

**And the cost, in the same breath.** Services that share a program share its lifecycle: they restart
together, they scale together, and one crash takes all of them down at once. What does not change is the
work — the same requests, the same messages, the same load, carried by fewer machines rather than by less
code — nor the difficulty of reasoning about the framework, which still has the same seven services in it.
Grouping buys machines. It does not buy simplicity, and it costs independence.

The shape is therefore a deployment decision, taken per target and reversible. A single-machine installation
stops paying for a fleet, a cloud deployment keeps the separations it needs, and the code, the settings and
the behaviour are the same in both. Portability of this kind is not the reason the framework exists — it is
what falls out of drawing the boundaries logically in the first place.

---

## A mature framework — everything the stack makes possible

Esquire has reached its **mature phase**. It is not a prototype or a half-built foundation: it takes the ordinary,
widely used technology stack — a relational database with JPA, Spring Boot, Node.js with Angular, the
Grafana/Prometheus observability tooling — and delivers **everything that stack makes possible**, as one working
system from database to browser.

That is the whole cloud-native picture, not a slice of it. Esquire answers all **fifteen factors** of a modern
cloud-native application — from API-first design through configuration, backing services, and disposability, to
telemetry, authentication, and authorization. Concretely, it already carries what most teams reach only years in:

- **High availability** — every service runs redundantly, spread across nodes and availability domains, so a lost
  pod or a lost node does not take the system down.
- **Full observability** — metrics, logs, and distributed traces on one screen, joined by a single correlation id,
  so any request's whole story is one search away.
- **A hybrid REST + event-driven engine** — synchronous APIs and an asynchronous messaging bus, each carrying its
  own resilience.
- **Open at every layer** — database, messaging transport, and identity are each a deployment choice behind a
  stable interface; no single vendor is baked in.
- **Running for real** — the complete stack is deployed live in the cloud (Kubernetes), not only on a developer's
  laptop.

The three backbone ideas — tree-shaped visibility, positional authority, entity self-description — sit on a
finished, production-grade cloud-native foundation. The floor is not just laid; it is complete.

The full goal — *what Esquire is, and what the stack was made to deliver* — is stated in
[`v1.2.x.Goal.md`](v1.2.x.Goal.md).

---

## The competitive landscape

### Domain-model-driven application frameworks — the closest comparison

[Apache Causeway](https://causeway.apache.org/) · [Jmix](https://www.jmix.io/) · [OpenXava](https://www.openxava.org/) · [Skyve](https://skyve.org/)

**This is the category Esquire is actually in, and the comparison worth having.**

**Apache Causeway** — formerly Apache Isis — runs on Spring Boot and generates its UI directly
from the domain model, as a webapp, REST or GraphQL, with Spring Security and Keycloak
integration. It is the closest project alive to Esquire's shape, and closer than Syncope, which
this document previously called the nearest cousin. The difference is where the UI definition
comes from: Causeway derives it from the domain classes as written, Esquire from runtime server
configuration. Causeway does not filter data rows — SecMan grants ALLOW/VETO over namespaces,
types and members, which governs what appears in the UI rather than which rows are returned.

**Jmix**, from Haulmont and the successor to CUBA Platform, is the closest commercial-grade one:
Spring Boot, Apache 2.0, built for large data models and complex internal UIs. It has genuine
row-level security and entity-attribute permissions, both of which Esquire has too. The
distinction is how a hierarchy is handled. In Jmix each constraint is authored per entity class
by the developer — a JPQL `where` clause with the session user's attributes available — and its
documentation states plainly that there is no native support for a user seeing their own
subtree: explicit constraints must be written for each level, with no automatic cascade. That is
the per-query filter written by hand, which is precisely what Esquire exists to remove.

Esquire's honest position against this category: **not more capable across the board, but
structural where they are procedural.** Both are frameworks you build on; Esquire additionally
ships the operational floor — HA, correlated metrics, logs and traces, async messaging —
assembled and running.

### Low-code admin panel tools

[Retool](https://retool.com/) · [Forest](https://forest.app/) · [AdminJS](https://adminjs.co/) · [react-admin](https://marmelab.com/react-admin/)

These generate a working UI quickly, and they are UI tools rather than backoffice frameworks:
fields are defined in frontend code, so a schema change is a deployment, and the permission
model is whatever the application team writes around them.

Two qualifications this document previously lacked. **react-admin is a framework, not a low-code
tool** — you write React with it, it does not generate a panel by inspecting a schema, and it is
maintained by the agency [Marmelab](https://marmelab.com/). And **Forest Admin now appears to
trade as Forest** at `forest.app`.

Esquire is slower to get started with than Retool, and Retool covers a breadth of integrations
Esquire does not attempt. The distinction is the destination, not the speed: a panel over an
existing schema is the right tool for an internal data browser and the wrong starting point for
a regulated back office whose permission model is a tree.

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

### Authorization engines

[OpenFGA](https://openfga.dev/) · [SpiceDB](https://authzed.com/) · [Cerbos](https://cerbos.dev/) · [Casbin](https://casbin.org/) · [OPA](https://www.openpolicyagent.org/) · [Oso](https://www.osohq.com/)

A field rather than a product, and this document used to name one member of it. The
Zanzibar-derived engines — OpenFGA, SpiceDB, Ory Keto, Permify — model relationships with far
more rigour than Esquire does; SpiceDB is the most faithful to the original paper and runs at
very large scale. Alongside them sit policy-as-code (OPA/Rego, Cerbos, AWS Cedar) and embedded
libraries (Oso, Casbin, the broadest language coverage of any of them).

Esquire's permission model is less academically rigorous than ReBAC. What it is instead is
**integrated**: part of the entity model and the UI, shipped with the framework rather than
operated as a separate service, and resolved from tree position rather than from tuples somebody
maintains.

The interesting common ground is the hard case both share. Point checks — may this user do this
to this object — are the easy half. Filtering a list to a subtree, thousands of rows, on every
screen, is where these systems are tested, and it is the operation Esquire is built around.

### Microservice generators

[JHipster](https://www.jhipster.tech/)

JHipster generates a microservices scaffold. It does not give you a pre-built, working
backoffice — it gives you the starting point for building one. After generation, the
hierarchical entity tree, the positional permission model, the server-driven UI, and
the async messaging topology are all still on your backlog. Esquire starts where
JHipster leaves off.

### eCommerce and CMS platforms

[Broadleaf Commerce](https://broadleafcommerce.com/) · [Strapi](https://strapi.io/)

The weakest comparison on this page, kept short for that reason.

These ship strong admin panels shaped around their own domain — commerce objects, content
documents. Extending them means working through those abstractions, which is exactly right if
you are building commerce or publishing and the wrong starting point otherwise. Esquire's entity
layer carries no domain assumptions.

Two corrections to an earlier version. **"Closed" was the wrong word**: Broadleaf's
extensibility has always been a selling point, and Strapi's Content-Type Builder exists
precisely so its domain is *not* fixed — describing a headless CMS as a fixed-domain platform is
a mistake about the central thing it does. The distinction that survives is narrower: Strapi
models content, with documents, fields, relations and a publish workflow; it does not resolve
"this user sits here and therefore sees this subtree". Broadleaf now describes itself as
**source-available** rather than open-source.

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

Against the nearest comparable frameworks rather than against every tool that ships an admin
panel. A row that only Esquire can answer is not evidence of anything if the products in the
columns were never trying.

| Feature | Esquire | Apache Causeway | Jmix | Apache Syncope | JHipster |
|---|---|---|---|---|---|
| Hierarchical entity tree (domain-agnostic) | Yes | Yes | Yes | Partial | No |
| **Tree-shaped (positional) authorization** | **Yes** | **No** | **No** | Partial | No |
| Row-level data filtering | Yes | No | Yes | Partial | No |
| Field-level permission enforcement | Yes | Yes | Yes | Partial | No |
| UI derived from the model | Yes, from runtime config | Yes, from domain code | Yes | No | No |
| Audit trail (pluggable, optional) | Yes | Partial | Yes | Yes | Partial |
| Distributed trace correlation | Yes | No | No | No | No |
| Microservices + async messaging | Yes | No | Partial | Partial | Yes |
| High availability, shipped assembled | Yes | No | No | Partial | Partial |
| Full observability, shipped assembled | Yes | No | No | No | Partial |
| Pluggable IAM adapter (Keycloak today) | Yes | Partial | Partial | No | No |
| Oracle + Postgres | Yes | Yes | Yes | Yes | Yes |
| Open source / self-hosted | Yes | Yes | Yes | Yes | Yes |
| End-to-end demonstration domain | Yes | Partial | Partial | No | No |

**The bolded row is the argument.** Everything else in this table is a difference of degree;
that one is a difference in kind, and it is the reason the framework exists. Causeway does not
filter rows at all. Jmix filters them well, per entity, with a constraint the developer writes —
and its documentation states that hierarchy is not handled natively.

Two rows that appeared in earlier versions have been removed because they were not true.
*Server-driven UI* was marked as Esquire-only; generating the UI from the model is the entire
identity of Apache Causeway. *Field-level permission enforcement* was marked as Esquire-only;
both Causeway and Jmix have it. A comparison table with two false exclusives discredits its
other eleven rows for any reader who checks one.

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*

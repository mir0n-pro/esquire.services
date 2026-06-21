# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Audit Logging Stack

## At a glance

Every business mutation (create / update / delete / move) must leave an audit trail — *who changed what,
when, from what to what*. The traditional OLTP answer is **DB triggers** — the row-level shadow-table
pattern audit trails have historically come from. The v1.2.7 sprint reframes
audit as a **pluggable concern**: one seam in the services, several interchangeable strategies behind it,
each picked by configuration so a deployment chooses the trade-off that fits its environment.

The mechanism is the **x-rod substrate** — a generic entity **fan-out** messaging frontend. Every messaging
participant (audit, entity broadcast, KC request/response) is an **x-rod** obtained from `XRodManager`
by logical **bus key + role**; a single x-rod type, **`XRod`**, whose wiring (a transmit leg and/or a receive
leg) makes it producer-only, consumer-only, or in-process. The producer captures each committed change and
relays it to one or more **sinks**; **audit is the first sink** (replication / search-index / cache-warm are
future siblings on the same frontend). The audit sink writes per-entity `*_log` tables, or streams to Redis,
or rides Kafka — the wire vendor is a **pluggable transport** picked per bus (§A), never named by the
framework.

**Options:** **(0)** persist nothing · **(a)** DB triggers · **(b)** in-process write · **(c)** bus → a
standalone `auKeep` consumer → SQL · **(d)** Redis Stream (the stream *is* the log) · **(e)** log-based CDC
(out of framework scope). Both broadcast options also run over **Kafka** instead of ActiveMQ: **(c-k)**
`service → Kafka → auKeep → SQL`, and **(d-k)** `service → Kafka → Kafka Connect Redis sink → Redis`.

**The framework default is (0) — persist nothing** (all disabled; only the INFO request log). A fresh
deploy with no audit config imposes no audit. Each **deployment then configures a topology** on top of that
baseline, chosen for footprint / monitoring needs, *not* performance:
- **Dev (Docker + local k8s) → (c) bus → auKeep over ActiveMQ, with the async publisher pool** (`publisher-
  pool-size=4`), selected by **`ESQUIRE_AUDIT_BUS_ID=audit-c`**. Reason: **minimum external images** —
  ActiveMQ is **already in the stack** (the entity-broadcast bus runs on it), so (c) adds **no new external
  image**, only auKeep as an app pod; (d) would pull in Redis, Kafka a broker + Connect. The two dev
  environments run the **identical** topology so the GitHub Actions deploy scripts stay consistent; the async
  pool (not the single sync publisher) avoids the saturation tail (§8).
- **OKE → (a) DB triggers.** On the Always-Free tier the goal is an **always-on, zero-extra-pod** way to
  **monitor user activity** for the demo: triggers live in the DB (the app producers stay OFF), so there is
  no auKeep pod and no extra broker audit traffic — OKE is a demo, not a load-test sandbox. (The trigger DDL
  is applied to the OKE postgres.)

Every other topology is a config flip (plus its own infra) away. Note: (c) is actually the *heaviest* on the
request path (§8) — these are footprint / monitoring choices, never cost.

**Headline cost finding:** at the request level audit is **cheap to free** in every mode, with a clear best
and worst.
- **Best (cheapest on the request path): (d) Redis** — effectively free (its direct `XADD` out-drains the
  feed worker), on par with audit-off and (a) triggers (which are unmeasurable end-to-end). (b) in-process
  is close behind (light).
- **Worst: (c) bus → auKeep with a single *synchronous* publisher** (`publisher-pool-size=0`) — the one
  mode that **saturates under high load** (one broker round-trip per event caps the rate; p99 spikes). This
  is exactly why the (c) **default uses the async publisher pool** instead — the pool removes the
  saturation, and (c)'s real payoff is **offload** (the `*_log` writes leave the business DB/JVM entirely).

The real reason to move off triggers is **write offload + transactional independence**, not request latency.

---

## 1. Why — the problem

Triggers work, but they pin the audit trail **inside the live operational database** — and *that*, not the
trigger code, is the real cost:

- **Operational burden on the production DB (the core con).** Every mutation writes a second row into the
  *operational* store, kept forever — so audit growth becomes **continuous DBA work on the live database**:
  watching and clearing the active tablespace, partitioning the log tables, managing retention. There is no
  separate place to offload or age the data into. It bites hardest when the DB runs as a **pod with bounded
  storage** (self-run / Always-Free), where the write amplification simply fills finite space.
- **In-transaction coupling** — the audit INSERT rides the mutation's own transaction, so an audit-side
  failure (full tablespace, constraint violation) can roll back the *real* operation, and it lengthens
  lock-hold time.
- **Hard to evolve** — changing the audit shape means a schema migration on the live DB.

Note what is **not** on this list. The Oracle/Postgres **trigger-dialect difference**, and the fact the
trigger **logic lives in two places**, are a *one-time* write kept in the seed — **not a real problem**.
Nor is "hidden / invisible to the service code": out-of-band auditing is equally true of CDC (e) and of the
decoupled bus options (c/d) — and of the entity-broadcast bus itself — which all treat publisher-blind
decoupling as a *feature*. The honest case against triggers is the **operational / space burden of keeping
audit *in* the live DB**; the case for moving it off (b/c/d) is **write offload + transactional
independence**, not request latency (§8).

The sprint does not throw triggers away. It makes audit **one seam with interchangeable strategies** —
triggers become *one* option, not *the* option.

---

## 2. The design seam

The app-side outcomes — **0** (no store), **b**, **c**, **d** — sit behind a single audit-logging strategy
(an `IAuditLogger`-style SPI), selected by configuration / Spring profile. The database-side options — **a**
triggers and **e** CDC — capture *below* the app and never touch the seam. This is the same shape Esquire
already uses for swappable concerns (the Taijitu cache behind `IBizTreeCacheRepository`, TokenRelay
strategies behind one filter). The entity services emit a uniform **audit event** and stay ignorant of
where it lands.

![The IAuditLogger seam: the app emits one uniform audit event; a strategy (0 / b / c / d) routes it.](img/audit-seam.svg)

*(a) DB triggers and (e) CDC capture **below** the app (in the DB / redo log) — they never touch the seam.*

(0) persists nothing (the change is still broadcast on the entity bus and traceable on the per-leg msg-audit
log; request-level INFO logging is the only standing trace). The others persist it. Because the change is
already broadcast, **(c) is simply (0) plus an auKeep consumer that lands it in SQL**.

When audit is OFF the audit bus resolves to the **`XRodDisabled`** no-op x-rod — the default x-rod when a bus
is unconfigured.

---

## 3. What — the options

Two questions, in order. **First — do you persist a data-delta audit at all?** Option **0** says no.
**If yes — how?** Options **a–e** span *tightly coupled / synchronous / same DB* to *decoupled /
asynchronous / separate store / non-SQL*.

### 0) No audit store — *"persist nothing"* (baseline)
- **Where:** nowhere. The change rides the existing entity-broadcast bus; nothing persists it for audit.
- **Visibility:** at **INFO**, the existing request IN/OUT logging records every operation at the request
  boundary (who called which command, when) — trail enough for many deployments. Per-leg message-audit detail
  goes to the separate **msg-audit** channel (§A.5), to trace the bus. We deliberately do **not** INFO-log
  update payloads (floods logs; a log line is not a queryable, retained record).
- **+** zero audit cost and coupling; removes trigger write-amplification outright.
- **–** no SQL-queryable record of *what changed*, no retention beyond the log pipeline.
- **Stance:** the no-store baseline and the natural first build step — get the decoupled change event
  flowing, then (c) adds the SQL sink when a real audit trail is needed.

### a) Local Sync Logging — *"in place, in transaction"*
- **Where:** log tables in the **same** operational DB. **Filled by** DB triggers, synchronously, inside
  the mutation's transaction.
- **+** strongest integrity (the audit row commits atomically; cannot be lost or bypassed); zero app code.
- **–** write amplification + the **operational burden** on the live DB (tablespace cleanup, partitioning,
  retention — continuous DBA work; finite space when the DB is a pod); the audit INSERT shares the
  mutation's transaction. (Dialect-specific trigger code is a one-time seed write, not the real con.)
- **Use when:** integrity is paramount and the operational DB has headroom + DBA care.

### b) Local Async Logging — *"off the hot path, still ours"*
- **Where:** log tables in a **separate log DB**. **Filled by** the originating service, asynchronously
  over JDBC — after commit, the in-process **`XRodInProcess`** x-rod (rod-class on a service-level leg) feeds
  each record to its worker pool, which runs the audit keep applier to write the `*_log` table. The applier's
  pool is either DEDICATED (its own auto-commit Hikari pool, the default -- can target a different DB/dialect) or
  SHARED (`log-db.shared=true` -> reuses the service's own datasource pool, dialect from the service profile);
  either way the writes run outside any Spring transaction. Selected by **`ESQUIRE_AUDIT_BUS_ID=audit-b`**.
- **+** removes write pressure from the operational DB and the request hot path; the service keeps the
  audit logic in plain Java; no new infrastructure or deployable.
- **–** the write leaves the mutation's transaction (a crash in the commit→write gap can drop a row unless
  backed by an outbox); each service grows a small queue + pool.
- **Stance:** *possible, not recommended* — mostly a stepping-stone toward an async stream. Since the
  trigger is invisible end-to-end (§8), moving the write into the service only spends app CPU + a second
  connection for no real gain.

### c) Distributed Logging — *"auKeep"* (the in-framework decoupled option)
- **Where:** log tables in the log DB. **Filled by** the **auKeep** consumer — the service's `XRod` producer
  posts the event to the audit bus (ActiveMQ queue under `audit-c`, Kafka topic under `audit-ck`); the
  standalone auKeep consumes it and owns the log-DB write. Selected by `ESQUIRE_AUDIT_BUS_ID=audit-c` (or
  `audit-ck` for Kafka).
- **+** full decoupling — the entity services carry no JPA or log-DB dependency, they just publish; the
  audit pipeline is its own independently-deployable, scalable concern and can fan out to many sinks;
  the only decoupled option that can be hardened to **zero-loss** (§5).
- **–** more moving parts; eventual consistency; the consumer needs throughput headroom (§4 pool).
- **Stance:** *the* decoupled-but-still-SQL option, and the one we would actually build — RDBMS log storage
  via messaging over the bus we already run; no Kafka or Debezium required (though Kafka is a swap-in, §6).
  **This is the dev-deploy topology (Docker + local k8s), with the async pool** — over the framework's (0)
  baseline (§7).

### d) Streamed Doc DB Logging — *"new fashion"* (Redis)
- **Where:** a non-SQL append-only store — **Redis Streams**. **Filled by** the `XRod` producer (producer-
  only, no receive leg) XADD-ing each committed event straight to a stream via the Redis transport provider —
  **the stream IS the audit log**, so there is no auKeep consumer (read with `XRANGE`); consumer groups can
  fan out later. Selected by `ESQUIRE_AUDIT_BUS_ID=audit-d` (the Kafka-log variant is `audit-dk`).
- **+** schema-flexible, very high write throughput, native streaming; cheapest on the request path (§8).
- **–** new infrastructure (Redis); eventual consistency; ad-hoc *relational* audit queries ("who changed
  entity X, ranges by user") are answered more directly by the SQL `*_log` of (b)/(c).
- **Streams, not RedisJSON:** a Stream matches the append-only trail and stays portable to managed
  Redis/Valkey (OCI Cache ships core only, no modules); RedisJSON/RediSearch would lock us to self-hosting
  (explored locally — `research/RedisJSON-local.md`, `research/Redis-on-OCI.md`).
- **No native partitioning:** a stream is a single key on one node. To scale out you shard at the
  application level (N stream keys routed by `hash(entityId)`); order is preserved only within each stream.
  For our volume a single stream is ample. This DIY-partitioning is exactly where Kafka differs.

### e) Log-based CDC — *"capture from the redo log, outside the framework"*
- **Where:** any sink. **Filled by** the DB's own transaction log — a CDC connector (Debezium on the
  Postgres WAL, Oracle LogMiner on redo) tails the log the DB already writes.
- **+** zero write-amplification on the operational path (reads redo the DB already produces); no app CPU.
- **–** raw row diffs, not semantic events; external infra + ongoing ops (slot/lag monitoring, schema
  evolution); cannot see app-level crl/req/uid.
- **Stance:** **out of framework scope** — a DBA / tooling concern. Listed for completeness as the "free"
  async alternative; we are not implementing it.

### Kafka as the audit transport (a transport for (c)/(d), not a new option)
Kafka can stand in for ActiveMQ under **(c)** (`service → Kafka → auKeep`) and front Redis under **(d)**
(`service → Kafka → Kafka Connect Redis sink → Redis`) — a **transport swap, not a new option**. Its draws
are native, declarative **partitioning** (parallelism + per-key ordering, auto-distributed across brokers)
and **multi-sink fan-out** (one topic, many consumer groups) — the axes where Kafka is structurally stronger
than Redis Streams (DIY partitioning) and the ActiveMQ queue (not a partitioned log). Full treatment, incl.
the BUILT (c-k) path and the consumer model, in **§6**.

### Comparison

| Option | Store | Filled by | Sync? | New infra | Integrity | Decoupling |
|---|---|---|---|---|---|---|
| **0** No store | none (request INFO log) | nothing — broadcast + msg-audit | n/a | none | none (request-level) | high |
| **a** Local Sync | operational DB | DB triggers | sync | none | highest | lowest |
| **b** Local Async | log DB | originating service (JDBC) | async | log DB | high (outbox) | medium |
| **c** Distributed (auKeep) | log DB | auKeep consumer (bus) | async | log DB + Rod | medium (zero-loss-capable) | high |
| **d** Streamed Doc | Redis | producer XADD | async | Redis | medium | highest |
| **e** Log-based CDC | any sink | DB redo log (Debezium/LogMiner) | async | connector + stream + sink | medium | highest |

**Whose decision this is.** The framework's responsibility ends at **emitting the change event** and
logging each operation at the request boundary. *Whether and how to persist an audit trail is the deploying
user's decision*, sized to their compliance / reporting / retention needs. Esquire ships the mechanism and
a sensible default — not a mandate.

---

## A. Pluggable transport layer (v1.2.8)

The v1.2.8 messaging-bus rework replaced the audit-specific producer/consumer wiring (mode constants and a
hard-coded `transport=activemq|kafka` switch) with a **uniform x-rod frontend over a declarative bus catalog
and a per-vendor transport SPI**. The option taxonomy of §3 is unchanged — (b) in-process, (c) bus, (d)
redis, plus the Kafka variants — but an option is now selected by **bus-id**, not a mode constant, and the
wire vendor is a swappable module the framework never names.

### A.1 One frontend — the x-rod

Every messaging participant in the platform — audit, entity broadcast, and the KC request/response sync —
is an **x-rod** obtained from **`XRodManager`** by a logical **bus key** plus a **role**. There is no
longer a separate producer class and consumer class: a single x-rod type, **`XRod`**, carries a **transmit
leg** and/or a **receive leg**. Its wiring decides its character — a transmit leg only makes it producer-only
(audit-d/dk), a receive leg only makes it consumer-only (auKeep's bus consumer), both legs make it a
transceiver, and the in-process **`XRodInProcess`** (audit-b) has no wire leg at all — it feeds each event to
its own worker pool, which applies it locally.

### A.2 Rod-class — the x-rod type

A bus leg names a **rod-class**, the x-rod implementation, resolved by name: a **bare** name maps to the
convention package `pro.mir0n.esquire.messaging.xrod.impl.<name>`, a **dotted** value is taken as a FQCN.
The rod-classes relevant to audit:

| rod-class | role | what it does |
|---|---|---|
| **`XRod`** | the standard bus transceiver | feed + pool + the wire codec; the producer leg of c/ck/d/dk and the auKeep's receive leg |
| **`XRodRR`** | request/response | two role-routed nodes (request-node / response-node); used by the KC bus, not audit |
| **`XRodInProcess`** | in-process audit (option b) | generic in-process x-rod: feeds each event to its own worker pool, which runs the audit keep applier against the `*_log` tables — no transport. The applier's pool (auto-commit, outside any Spring tx) is DEDICATED (its own, the default) or SHARED (`log-db.shared=true` -> the service's own pool); its datasource is read from the leg's `log-db` sub-block. FQCN `pro.mir0n.esquire.dataKeep.keep.XRodInProcess` |
| **`XRodInfo`** | log-only | logs the whole event instead of sending it (the future access-violation-log seam) |
| **`XRodDisabled`** | no-op | the default x-rod when a bus is unconfigured (audit OFF, option 0) |

### A.3 The bus catalog (topology)

Every bus is defined **once, cross-service, in an external topology file** (`spring.config.import:
file:/etc/esquire/topology.yml` — a bind-mount on Docker, a ConfigMap on k8s). The shape:

```
bus (bus-id)
 -> slot (slot-id)          -- a leg of the bus (the term is "slot", never "service")
    -> x-rod {
         rod-class                                  -- the x-rod type (A.2)
         pool-size, feed-capacity, virtual-threads  -- consumer/feed sizing
         publisher-pool-size, concurrency           -- producer sizing
         transport {                                -- the wire vendor (A.4); absent for in-process
           provider, endpoint, destination,
           params,                                  -- generic per-vendor pass-through (e.g. ActiveMQ pubSubDomain)
           request-node / response-node + node list -- R&R only (XRodRR)
         }
       }
```

A service **selects a sink by bus-id**: the audit sink is the entry `esquire.audit-bus.messaging-bus ->
{ bus-id: ${ESQUIRE_AUDIT_BUS_ID}, slot-id: audit }`. One env var, `ESQUIRE_AUDIT_BUS_ID`, names which
catalog bus the audit producer (and the auKeep) binds — `audit-b` / `audit-c` / `audit-ck` / `audit-d` /
`audit-dk`, or unset → `XRodDisabled` (option 0). Docker and k8s default to **`audit-c`**; the code default
in `application.yml` is **`audit-b`**. The catalog unions the shared cross-service topology with a service
overlay under the service's own key `<spring.application.name>.messaging-bus` (where the per-service audit-b
in-process leg lives, since its keep datasource is service-specific).

### A.4 The transport SPI — per-vendor modules

The wire vendors are **pluggable modules implementing the `ITransportProvider` SPI**, resolved by name via
**`TransportProviders`**: a **bare** provider name maps to `pro.mir0n.esquire.tp.<name>.TransportProvider`,
a **dotted** value is a FQCN. The shipped providers:

| module | wire form | direction |
|---|---|---|
| **`tp-activemq`** | a queue | producer + consumer |
| **`tp-kafka`** | a topic | producer + consumer |
| **`tp-redis`** | a stream | producer-only |

A deployment carries **only the modules it uses**. To keep a service transport-agnostic, each module ships an
**`AutoConfigurationImportFilter`** (via `META-INF/spring.factories`) that **suppresses Boot's matching
auto-configuration** — so adding a module pulls in its vendor wiring deliberately, through the SPI, rather
than having Spring Boot auto-configure a broker the service never asked for.

**The framework names no vendor.** There is one logical audit destination, **`esquire.rod.audit`**, and each
provider maps it to its own wire form (an ActiveMQ queue / a Kafka topic / a Redis stream). Vendor-specific
knobs pass through **generically** via `transport.params` (e.g. `jms.useAsyncSend`, a Kafka `group-id`, a
Redis `max-len`) — the catalog stays declarative and the framework stays vendor-neutral.

### A.5 Message-audit logging — the per-leg msg channel

The msg-audit logger is now **per-leg**, named `msg.<bus-id>.<slot-id>` (was `msg.<class>`). The **transmit
leg logs `TX`**, the **receive leg logs `RX`**, in the format
`<TX|RX> | <msgType> | <op> | <kind> | <entityId> | <subId> | <rodId> | <requestId>`. The channel is its own
file with `additivity=false` (it does not bleed into the console/develop logs). A `UA` audit event shows on
the producer leg (`TX`) and, for the `audit-c` / `audit-ck` buses, again on the auKeep leg (`RX`); the
producer-only buses (`audit-d` / `audit-dk`) log only `TX`.

---

## 4. Architecture — where & how

### 4.1 The x-rod substrate (generic fan-out)

**Rod = RoD = Relay of Data.** The x-rod is a generic entity fan-out frontend, **not** audit-specific: a
producer leg captures every committed (sub)entity change and relays it to one or more **sinks**; a consumer
leg (option c) relays it onward. **Audit is the first sink** — replication, search-index feed, webhook,
cache-warm are future sinks on the *same* frontend, plugged in behind the sink seam without touching the
write sites. So the substrate types carry no "audit" in their names. The generic substrate lives in
**`messaging.xrod`** (the `XRodManager`, the `XRod` and its rod-class impls, the transport SPI), and the
generic keep engine in **`esquire-dataKeep`** (the in-process `XRodInProcess`, `RodEventDbWriter`,
`KeepSqlStore`, `KeepApplier` — none of it audit-aware); the audit specifics are a thin **`esquire-audit`**
module (`AuditBusBridge`, the `AuditKeepDirector`, the `*_log` SQL).

- **Producer leg** — male/transmitter. One `XRod` per asset-updating service (enyMan, pacMan, keySmith),
  obtained from `XRodManager` for the audit bus. The transactional buffering lives in **`AuditBusBridge`**
  (not the x-rod): it stamps `msgType=UA`, buffers each row-change in the current tx, and **after commit**
  builds each `RodEvent` and calls `IXRod.transmit(event)`; the x-rod is a pure relay — its feed (sized by
  `feed-capacity` / `publisher-pool-size`) carries the event onto the transmit leg's transport, off the
  request thread.
- **Consumer leg (auKeep)** — female/receiver. A `Semaphore(pool-size)`-bounded worker pool with **no queue
  of its own**; the receive leg's transport hands each decoded `RodEvent` to it, the keep applier resolves
  the event's `kind` to a statement key, and writes it. The same consumer mechanism in every bus-fed option;
  only its **location and feed** differ — in-process `XRodInProcess` for (b); standalone, bus-fed auKeep for
  (c)/(ck).

> **As-built note.** Per-`kind` work is a dialect-keyed SQL statement: the keep applier (`KeepApplier`)
> resolves the event `kind` through the director's kind->statement-key map and `RodEventDbWriter` binds the
> event to that statement (loaded by `KeepSqlStore`). The in-process (b) x-rod (`XRodInProcess`) skips the wire
> entirely and runs the same applier against its own keep pool.

The **body is built by the entity itself** via `IMappable.fillMap(Map)` (a JPA-layer capability —
`EsqEntityJpa` emits `name`/`desc`/`parentId`, concrete entities override) — so the producer carries **no
domain field names** and there is no reflection. The keep applier binds that body straight to dialect-keyed
SQL via `RodEventDbWriter`. There is **no Rod-owned context type**: the audit triple
(`crl_id`/`req_id`/`uid`) already lives in the v1.2.7 `EsqRequestContext`, read via
`RequestContextUtils.getContext()` — captured once per request and re-established on worker threads.

### 4.2 The Rod event (`RodEvent`)

The `RodEvent` is the carried event: `{ op, kind, entityId, subId, actionTime, correlationId, requestId,
uid, rodId, msgType, body }`. On the wire it **extends the entity `UE` message** with **three optional audit
header fields** and a **full body**; the audit message's `msgType` is **`UA`**. No structural fork; the
cache-broadcast simply omits the optional fields.

**Header:** the existing envelope (`op` C/U/D, `kind` → routes to the `*_log` table, `entityId` = owning
entity, `correlationId`=crl_id, `requestId`=req_id, `rodId` = the producing instance, `msgType`=`UA` for
audit) **plus** three audit header fields on the wire: **`SubID` (50011)** (the sub-row's own id; absent for
the entity itself), **`Uid` (50012)** (the acting user), and **`ActionTime` (50013)** (epoch-ms captured
**at commit** — the audit "when" → `*_log.action_ts`, distinct from the build/flush time). The audit triple
(crl/req/uid) is **header**, not body — infra metadata, not entity data.

**Body (`Text`, JSON):** strictly (sub)entity **data** fields (no audit triple, no `path` this phase).
**CREATE/UPDATE → the FULL committed row**; **DELETE → empty** (id+kind are in the header; the last full
state is recoverable from prior CREATE/UPDATE entries). The body is a complete self-contained record; the
header *duplicates* the identity so the consumer can route and dedup **from the header alone** without
parsing the body. `path` is skipped deliberately — it keeps the producer fully generic (removes the only
extra read, the `esq_entity_path` lookup the old triggers did); `*_log.*_path` goes NULL, path stays
recoverable via a join.

### 4.3 Identity & routing — the uniform model

```
entity_id = the OWNING entity id        (usr_pk / org_pk / acct)
kind      = the (sub)asset kind         -> routes to the *_log table
            (a person carries its OWN sub-kind here: 992/994/996 = primary/secondary/joint)
sub_id    = a discriminator, present ONLY when (entity_id, kind) is not unique on its own
            -- multiple rows of one kind under an owner: ad_pk (address) | par_name (param); else null

   row identity     = (entity_id, kind, sub_id)
   footprint group  = (crl_id, entity_id)                  -- the owner's whole footprint in one operation
   dedup key        = (crl_id, entity_id, kind, sub_id)    -- exactly-one row per (operation, row)
```

`sub_id` is needed **only when `(entity_id, kind)` does not already pin a single row** — i.e. address
(`sub_id`=`ad_pk`) and custom param (`sub_id`=`par_name`). Not for the entity itself, nor for person (its
sub-kind is in `kind`). **`kind` selects the table, `sub_id` selects the row within it.**

| Asset | kind | entity_id | sub_id | `*_log` |
|---|---|---|---|---|
| org / usr / acct | (existing kind) | own id | null | esq_org_log / esq_user_log / esq_account_log |
| person | person sub-kind (992/994/996…) | usr_pk | null | esq_person_log |
| address | ADDRESS | usr_pk | ad_pk | esq_address_log |
| custom param (user) | **USR_PARAM** (new) | usr_pk | par_name | esq_usr_par_log |
| custom param (org) | **ORG_PARAM** (new) | org_pk | par_name | esq_org_par_log |

`USR_PARAM`/`ORG_PARAM` are **two** new reserved kinds (not one) because `esq_usr_par`/`esq_org_par` are two
tables and a global `entity_id` can't tell owner type — keeping routing header-only. `auth` is **not** here
— it is the access profile, owned by keySmith (`esq_auth_log`). `esq_address_log` has no owner column (it
stores `adl_pk`=ad_pk, unique); the owner link is the FK chain `address → person → user`, not denormalized.

### 4.4 Lifecycle

![Audit lifecycle: post() buffers in the tx (last-snapshot-wins); the tx commits with one commit-time actionTime; afterCommit flushes the batch out of the entity tx.](img/audit-lifecycle.svg)

- **Coalesce within the tx.** A request may write the same row twice (insertPerson then updatePerson);
  those are intra-tx intermediate states. Last-snapshot-wins yields **one event per row per transaction =
  its final committed state**, keeping `(crl_id, entity_id, kind, sub_id)` a single entry. Op precedence:
  any insert → CREATE; only updates → UPDATE; ends in delete → DELETE; created **and** deleted in one tx →
  emit nothing (never committed).
- **Flush is post-commit, out of the entity tx.** In-tx flush is rejected — it would let an audit failure
  roll back the business operation (the trigger coupling we removed) and hold the connection.
- **Loss window:** a crash between *commit* and the sink durably accepting the batch loses those events.
  Inherent to async audit; accepted. *(An outbox — event row in the business tx, relay ships it — closes it
  at the cost of one in-tx write; opt-in for strict-compliance deployments only.)*

### 4.5 Per-operation workflow

- **CREATE** — one full-snapshot event per touched row (user/person/address/usr_par + the initial `auth`
  row created at user-create). `op=CREATE`.
- **UPDATE** — one full-snapshot event per *changed* row, carrying the **final committed state**. Since the
  named-query writes touch only changed columns, the producer must **materialize the full row** — one extra
  full read per audited row on updates.
- **DELETE** — gated by the enabled flag: enabled → enumerate child **ids** (pk-only, cheap), `post({op=D,
  kind, id})` per row, then delete (body empty); disabled → plain DB cascade. Params cascade with the owner
  → no per-param delete events.
- **MOVE** — audit **only the parent-reference update** (`org_org_pk`/`usr_org_pk`, old→new); the
  `entity_path` rewrites are not audited (the move broadcast carries the path; `entity_path` has no `*_log`).
  Runs on the move-queue worker thread; context comes from the rehydrated `EsqRequestContext`.

### 4.6 Transport & consumer

- **(b)** — in-process `XRodInProcess`: the worker pool runs the keep applier → JDBC insert into the `*_log`
  tables. No transport leg, no JSON.
- **(c)** — the `tp-activemq` provider maps the logical `esquire.rod.audit` destination to a **dedicated,
  durable audit QUEUE** (ActiveMQ), single-purpose, **separate** from `esquire.entity.broadcast`. A
  **queue**, not a topic, because audit is **fan-in** (every producer → one sink) and a queue persists +
  buffers + load-levels — and needs no durable-sub `clientId`, so it dodges the rolling-update clientId trap.
- **Consumer = a redundant set of auKeeps** — k8s replicas all competing on the queue. HD comes from
  **redundancy**, not a single durable box: a pod dying shifts its share to the others, no stall. Each
  message is a self-contained full snapshot and audit is pure append, so processing is **order-independent**
  — competing/parallel consumers are safe with **no per-key assignment** (unlike the cache-sync path, which
  is read-modify-write and must be parents-first). This HD shape is essentially **free**: the queue's
  competing-consumer semantics + `replicaCount` give it with no custom coordination. At-least-once; **ack
  only after the log row commits** ⇒ consumer/DB downtime holds messages in the queue, no loss.
- **Dedup / exactly-one:** the key `(crl_id, entity_id, kind, sub_id)` maps to a per-table unique
  constraint `*_crl_id + the row's own pk` (e.g. `esq_org_log(orgl_crl_id, orgl_pk)`,
  `esq_address_log(adl_crl_id, adl_pk)`). A redelivered message collides and is dropped. **This is a
  (c)/(d)-only concern** — (b) has no redelivery (one in-process delivery) so no duplicates by definition;
  the Postgres `ON CONFLICT DO NOTHING` is a forward-compatible clause inert under (b), active under (c).

![Option (c) pipeline: N durable producers publish to one dedicated durable audit queue; auKeep replicas (competing consumers, order-independent) drain it to the log DB.](img/audit-pipeline.svg)

### 4.7 The auKeep consumer as a generic keep host — pluggable `IKeepDirector`

The standalone (c) consumer service, **auKeep**, is built **director-agnostic**, not audit-only. It is a
generic keep host: it reads the audit-bus ref from the catalog, opens the configured bus's consumer via the
resolved transport provider, decodes each message into a `RodEvent`, and applies it through the generic keep
engine (`esquire-dataKeep`). What it applies is declared by one **`IKeepDirector`** — reduced to a pure
declaration of `sqlGroup()` (the classpath SQL group) + `kinds()` (the kind->statement-key map). The generic
engine does everything else (builds the pool, the `RodEventDbWriter`, and the kind registry). The audit
director (`AuditKeepDirector`) declares the SQL group `"audit"` + the audit kinds, so the engine writes the
`*_log`. auKeep **carries all `tp-*` modules** and drains whichever bus the ref names (`audit-c` ActiveMQ,
`audit-ck` Kafka); the producer-only buses (`audit-d` / `audit-dk`) have **no auKeep** at all. **Adding a
sink** (replication, doc-DB) is config-selected — drop in a new `IKeepDirector`.

### 4.8 SQL externalization — the `META-INF/audit/` spec folder

The `*_log` INSERT/MERGE statements are **not in code**. They live in a spec folder named by the director's
SQL group — `src/main/resources/META-INF/audit/{postgres,oracle}.xml` (CDATA, keyed by sql-key);
`KeepSqlStore` (in `esquire-dataKeep`) is a **generic, dialect-keyed loader** (no SQL of its own, no audit
knowledge). The audit `*_log` SQL ships in the `esquire-audit` module; each asset service that produces audit
carries **only the statements for the tables it writes** (enyMan: org/user/person/address/params/account;
pacMan: account; keySmith: auth), while the standalone **auKeep** carries the **full set**.

**Why a spec folder (deploy-time toggle):** the loader tolerates an absent resource (no file → empty map),
so audit is opt-in at **packaging/deploy time**, not only via the `enabled` flag — a setup that does not
need audit simply omits the `META-INF/audit/` files (no code change). The same seam swaps dialect or
restricts which tables are logged. SQL stays a **deployable spec artifact**, decoupled from the code.

---

## 5. Delivery semantics — loss, duplication, and why dedup lives only on the bus path

**In plain terms:** once a service commits its change, the audit event is held in memory and then sent to
the bus. If the broker is down, or the process crashes, before that send lands, the event is dropped —
there is no automatic resend and nothing is written to disk to recover it. So an audit event is **not
guaranteed to follow the change it records** — except option (a), which writes the audit row inside the
same transaction as the change. This is not a gap to close: async audit is decoupled from the change's
transaction on purpose, to keep the change path as fast as possible when everything works — it is the
fast-path record, not the source you reconstruct an outage from. The rest of this section is the per-option
detail behind that statement.

Each option sits at a different point on the **loss vs duplication** spectrum, and only some can be made
zero-loss. This decides where a dedup mechanism is needed — and is the real reason the `*_log` dedup unique
index exists on the bus path but nowhere else.

| Option | Delivery | Failure mode | Dedup | Zero-loss capable? |
|---|---|---|---|---|
| (a) triggers (in-tx) | **exactly-once**, transactional | none (atomic) | N/A | **Yes, by construction** (it *is* the tx) |
| (b) in-process (post-commit) | best-effort / at-most-once | loss (commit→write gap) | not needed (no redelivery) | No |
| (c) bus → auKeep | best-effort *as built*; **at-least-once capable** | loss now; dup once hardened | `*_log` unique index + ON CONFLICT/MERGE | **Yes** — ack-after-write + the index |
| (d) redis stream (XADD) | best-effort / at-most-once | loss (fire-and-forget) | not needed; entry-ID if a consumer is added | No (as built) |
| (e) CDC | at-least-once | dup (connector replay) | downstream keys on the log offset (LSN/SCN) | Yes (the DB's own log) |

**The spine of it:**
- **(a) is the only transactionally exactly-once option** — commit gives both rows, rollback gives neither.
  Nothing to lose, nothing to dedup. Its price is structural (audit schema in the business DB; can't see
  crl/req/uid except via carried columns).
- **(b), (c), (d) all write *after* the business commit**, off the request thread — so all three can
  **lose** an event in the commit→write gap. That gap is the inherent cost of decoupling audit from the tx.
- **Duplicates only appear where something *redelivers*.** (b) hands off once in-JVM; (d) fires `XADD` once
  with no ack — neither can redeliver, so **neither needs dedup**. Only the **broker** (c) can redeliver an
  un-acked message (consumer crash / recovery before ack).
- **(c) is the one post-commit option that can be hardened to zero-loss** — that is its whole reason to
  exist over (b). Ack **after** the `*_log` write (CLIENT_ACKNOWLEDGE / transacted listener / Kafka
  ack-after-process): a crash between write and ack now **redelivers** instead of losing → at-least-once →
  and the dedup index makes the re-write idempotent → effective exactly-once. The index is the cheap (one
  DDL) prerequisite paid up-front so hardening is a config flip, not a migration.
- **(d) trades the zero-loss door for the fastest request path** — `XADD` has no ack protocol, nothing to
  harden. To make (d) zero-loss you bolt a consumer group on top and dedup on the **stream entry ID**
  (unique/stable — actually simpler than dedup on the bus).
- **(e) is zero-loss via the DB's own log**, at the cost of living outside the framework.

**Takeaway:** dedup is not a tax the broker imposes — it is the *enabler* of the bus path's selling point
(recoverable, zero-loss-capable audit). Pick the option by the loss posture you want: **(a)** never-lose-
on-the-tx, **(c)-hardened** never-lose-off-box, **(b)/(c)-best-effort/(d)** lose-rarely-and-cheap.

---

## 6. Kafka as the audit transport — partitioning & the consumer model

Kafka is a **transport choice for the bus options, not a new option**: it can replace ActiveMQ under **(c)**
and front Redis under **(d)**. Both are proven:
- **(c)-style — BUILT (`audit-ck`):** `service → Kafka → auKeep` — Kafka in place of ActiveMQ; the producer
  and auKeep both bind the `audit-ck` bus, whose leg names the `tp-kafka` provider (topic). Same codec,
  director, `*_log`, dedup.
- **(d)-style (`audit-dk`):** `service → Kafka → Redis Kafka Sink (Kafka Connect) → Redis`, or the Kafka
  topic as the log itself (producer-only, no auKeep). Smoke-proven infra-only (no framework code).

The rest of this section is the (c) case in depth — partitioning and the consumer model — since that is the
path with framework code; the (d-k) variant is infra-only.

(c) keeps its shape (producer → bus → auKeep → `*_log`) with the **transport swappable by bus-id**:
`ESQUIRE_AUDIT_BUS_ID=audit-c` (the `tp-activemq` leg) vs `audit-ck` (the `tp-kafka` leg). Codec, director,
writer, dedup index unchanged. How to run it well for audit:

- **6.1 Partitioning — key = none.** Audit is order-independent, so we do not need per-key ordering.
  `key=none` (round-robin / sticky) gives maximum even spread, no hot partitions, full parallelism — the
  default. Rejected: `key=entityId` (per-entity order we don't need; risks a hot partition from a busy
  entity — write skew, not cardinality, is the issue); `key=kind` (only ~8–10 keys, heavily skewed →
  guaranteed hot+idle partitions — the worst). Partitions are the unit + cap of consumer parallelism; size
  to the max replicas you'd ever want (with `key=none` they can be grown later freely).
- **6.2 Consumer model — one per instance, scale by replicas.** auKeep's consumer side is a **single
  consumer thread** (`@KafkaListener`). **Do NOT raise `listener.concurrency` above 1** (that puts N
  threads on one shared pool — head-of-line blocking). **Add auKeep replicas** in the same group instead;
  Kafka distributes partitions across instances (competing consumers, also the redundancy story).
- **6.3 A dedicated worker pool per consumer.** Each consumer owns its **own** auKeep pool — never share.
  Isolation (a blocked partition mustn't steal permits), per-partition offset correctness, backpressure
  locality. The process boundary gives this for free (one instance = one director = one pool = one Hikari
  pool); the only way to violate it is `concurrency>1`.
- **6.4 No internal queue — the topic is the buffer.** The consumer pulls at its own pace; the pool's
  semaphore backpressures `submit()`, which makes Spring Kafka **pause polling** when writers saturate; the
  backlog stays in the broker, replayable by offset. Nothing accumulates in memory.
- **6.5 Delivery.** As built: async pool, offset commits before write → best-effort. Zero-loss upgrade:
  write synchronously, commit offset after (ack-mode `RECORD`/`BATCH`) → at-least-once → dedup index →
  effective exactly-once. Cost: one-write-at-a-time per consumer, so lean on partitions + replicas.
- **6.6 DB connection budget.** Total audit-DB connections = **replicas × pool-size** (the smoke hit `too
  many clients already` from this). Size the catalog leg's `pool-size` + Postgres `max_connections` together,
  or front the DB with pgbouncer.
- **6.7 Recommended:** `key=none` · N partitions (= max replicas) · `listener.concurrency=1` · scale via
  replicas · one dedicated pool per instance · no internal queue · best-effort default, ack-after-write +
  dedup index for zero-loss · `pool-size × replicas ≤ DB connection budget`.

**6.8 Why partitioning earns its keep** (the case for Kafka over the ActiveMQ queue), most to least
relevant for audit:
1. **Fan-out to many independent sinks (decisive).** One topic, **multiple consumer groups**, each getting
   *every* record. The same `esquire.rod.audit` topic feeds **auKeep → SQL `*_log` AND Kafka Connect → Redis
   AND a future search index** off a **single publish** — impossible on the ActiveMQ queue (delivers each
   message once, to one consumer). Demonstrated by the (d-k) variant (producer unchanged, a Connect Redis
   sink added as just another group).
2. **Throughput / scale-out (the dial).** Partitions = unit of parallelism at every stage; add partitions +
   replicas → near-linear scale, no code change.
3. **Replay / backfill (free with retention).** A new consumer group starts at offset 0 and replays the
   whole history — a sink added months later **backfills itself**; a wiped sink is **rebuilt** by replaying.
4. **Ordering on demand (key choice).** Order within a partition; `key=none` (default) or `key=entityId`
   *only if* a particular sink needs per-entity order.
5. **HA via replication.** `replication.factor>1` survives a broker loss.

**Net for us:** the leverage points we'd genuinely use are **fan-out** and **replay**; scale + HA are
have-it-when-needed dials. None exist on the ActiveMQ queue — they, more than the transport swap, are the
case for Kafka under (c).

---

## 7. Configuration & deploy defaults

Everything is external — which audit style runs is decided entirely by config + deploy artifacts, no
framework code change. Four layers compose the choice: **(1) the bus catalog** (the shared external
`topology.yml` defining every audit bus, plus the one env var `ESQUIRE_AUDIT_BUS_ID` selecting which bus the
audit sink binds — §A.3), **(2) DB deploy** (`db.seed`: the `*_log` tables, triggers, dedup indexes), **(3)
SQL spec artifacts** (`META-INF/audit/{dialect}.xml`, shipped or omitted at packaging), **(4) infra**
(ActiveMQ + auKeep, or Redis, or Kafka — plus the matching `tp-*` module on the deployable). Full recipe per
option + the env reference: [services.configuring.md](services.configuring.md).

**Deploy defaults — the code baseline is (0), each deployment configures its own topology:**

- **Code default `application.yml`:** `ESQUIRE_AUDIT_BUS_ID=audit-b` (in-process); a deployment that names no
  bus, or names an unconfigured one, resolves to `XRodDisabled` → **(0)**, persist nothing (only the INFO
  request log).
- **Dev (Docker + local k8s) → (c) with the async pool.** Producers set `ESQUIRE_AUDIT_BUS_ID=audit-c`; the
  `audit-c` catalog leg names `tp-activemq` and the async publisher pool (`publisher-pool-size=4`). `aukeep`
  is a standard pod/service (Docker: a non-profile-gated compose service; k8s: the `esquire-aukeep` chart
  deployed by `k8s-up`). The shared topology file is delivered as a Docker bind-mount and a k8s ConfigMap
  (the `esquire-topology` chart, installed first by `k8s-up`/`k8s-rebuild`). (c) reuses the ActiveMQ already
  in the stack — **no Redis/Kafka deployed by default**. Both dev environments are identical (GHA-script
  consistency).
- **OKE → (a) DB triggers.** The producer overlays (`k8s-oci/values/*`) leave the audit bus unconfigured
  (app audit OFF); the audit comes from **DB triggers** (`db.seed/<vendor>/triggers/all.sql` applied to the
  OKE postgres) — always-on user-activity monitoring with **no auKeep pod / no extra broker load** on the
  Always-Free tier.

Switch topology purely by **bus-id** (chart values / env), no rebuild — the named bus already exists in the
catalog:
- **(b) in-process:** `ESQUIRE_AUDIT_BUS_ID=audit-b` (no auKeep; the `XRodInProcess` x-rod runs the audit keep
  applier to write `*_log` directly). Its `log-db` datasource comes from
  `ESQUIRE_AUDIT_LOG_DB_{VENDOR,URL,USERNAME,PASSWORD,POOL_SIZE}`.
- **(d) redis:** `ESQUIRE_AUDIT_BUS_ID=audit-d` + `REDIS_HOST=esq-redis`, deploy a Redis and carry the
  `tp-redis` module. **Name the service `esq-redis`, never `redis`** — see §9.
- **(c) over Kafka:** `ESQUIRE_AUDIT_BUS_ID=audit-ck` (+ `KAFKA_BOOTSTRAP`), deploy Kafka and carry the
  `tp-kafka` module.

The `*_log` tables are seeded on a fresh cluster by the `esquire-postgres` image (`create/all.sql` chains to
`create.log/all.sql`); (b)/(c) need them, (a) needs them + the triggers, (d) needs neither.

---

## 8. Measured cost

### 8.1 What triggers actually cost (option a, Postgres + Oracle, 2026-06-03)

**End-to-end (service layer).** Sustained `POST /esq-cmd-save` address edits, three runs changing only the
trigger state:

| Trigger state | Throughput | mean | p95 | p99 |
|---|---|---|---|---|
| dropped | 995 rps | 8 ms | 10 | 12 |
| disabled | 1003 rps | 8 ms | 10 | 12 |
| enabled | 1004 rps | 8 ms | 10 | 12 |

All within ~1% — **end-to-end the trigger is unmeasurable**, dwarfed by gateway routing, JWT, the JMS
broadcast, and the network round-trip (the audit INSERT is well under 1 ms of an ~8 ms request). A
*disabled* trigger costs exactly nothing.

**DB layer (isolated, 50k single-row UPDATEs, trigger ON vs OFF):**

| DB | OFF | ON | ratio | added/row |
|---|---|---|---|---|
| Postgres 17 | 128 ms | 437 ms | **3.4×** | +6 µs |
| Oracle 19 | 121 ms | 771 ms | **6.4×** | +13 µs |

At the database the trigger multiplies the bare write 3–6× — real, but only when the *DB itself* is the
bottleneck. **The real con of (a) is not latency.** Its true costs: **DB write amplification + operational
burden** (a second write per mutation + a row kept forever, so WAL/vacuum/backup/tablespace/partitioning/
retention all become continuous DBA work on the live DB — acute when it is a bounded pod) and
**transactional coupling** (the audit INSERT shares the mutation's transaction). The dialect-specific
trigger code maintained twice is a one-time seed write, **not** a real con. So the case for moving off
triggers rests on **write offload + transactional independence**, not on shaving request latency.

### 8.2 Request processing time, options a/b/c/d (Docker, srvOuter, 2026-06-08)

The right metric is **request processing time inside the service** (`srvOuter` = wall-clock around the
in-service request; `srvInner` = JPA time; `srv_self` = the non-JPA work where the post-commit enqueue
lives). Same `UpdateLoadSimulation`, warm, flipping only enyMan's mode.

**Normal load (4 workers)** — audit is effectively free in every mode (all within ~0.6 ms; p99 6–9): off
3.90, (a) 3.85, (b) 3.80, (c) sync 4.36, (c) pool×4 4.04, **(c-k) Kafka 3.90**, (d) redis 3.83, (d-k) 4.28.

**High load (8 workers)** — pushes (c) sync past its single-publisher drain ceiling:

| Audit mode | srvOuter | p50 | p95 | p99 | cost lands in |
|---|---|---|---|---|---|
| (0) off | 5.36 | 5 | 7 | 9 | — |
| (a) triggers | 6.46 | 6 | 9 | 12 | srvInner (in-tx) |
| (b) in-process | 6.21 | 6 | 8 | 10 | srv_self (~+1 ms) |
| (c) bus → auKeep, **sync** | **10.64** | 9 | **24** | **51** | srv_self (saturation tail) |
| (c) bus → auKeep, async pool×4 | 6.16 | 6 | 8 | 10 | srv_self (~+1 ms) |
| (c-k) bus → auKeep, Kafka | 5.87 | 6 | 8 | 10 | srv_self (no saturation) |
| (d) redis stream | 5.67 | 6 | 8 | 9 | srv_self (~+0.3 ms, no saturation) |
| (d-k) Kafka → Connect → Redis | 6.68 | 6 | 9 | 12 | = c-k producer + host load |

- **srvInner (JPA) is flat across every mode** — no option pushes meaningful cost onto the request thread's
  DB time. The audit cost, when any shows, is in `srv_self` (the post-commit enqueue).
- **(a), (b), (c)-pool, (c-k), (d) all add only ~0.3–0.9 ms** — the request thread only does `feed.put()`
  after commit; the difference is purely what the feed worker does downstream, off the request thread.
- **(c) sync is the only one with a problem, and only under saturation.** The single feed worker publishes
  **synchronously** (one broker round-trip per event, ~1.5 ms); when ingest exceeds that drain rate the
  bounded feed fills and `feed.put()` blocks → p95/p99 jump to 24/51. A throughput-cap effect, not a
  per-request tax: same mode, varying workers — 1w 2.59, 2w 3.10, 4w 3.95, 8w 14.65 (knee 4→8). **(c) sync
  is fine for normal load**; the async pool raises the ceiling.
- **(d) redis is the cheapest and does NOT saturate even single-worker** — the synchronous `XADD` is a
  sub-ms local round-trip, not a broker hop, so one worker out-drains 8-worker ingest. Needs no pool.
- **Kafka producer tuning barely moved c-k** (5.98→5.87) — *which is the finding*: the sender was never the
  bottleneck; the residual gap to Redis is the **co-located broker JVM's host footprint**, not config. It
  disappears when Kafka runs on its own node. Same theme for (d-k): the sink never touches the request path.
- **(c)'s real payoff is offload** — the `*_log` writes leave the business DB/JVM entirely (verified:
  EnqueueCount == DequeueCount == rows, queue drained to 0, zero errors). Under a DB-bound workload that
  offload is the win.

### 8.3 Local k8s client-side throughput, a/b/c/d (clean (c)-default cluster, 2026-06-09)

Measured on the **clean-rebuilt (c)-default stack** (chart-deployed auKeep, chart-default bus producers,
fresh `esquire-postgres` seeding the `*_log` tables — the authoritative deploy default). hauberk
`update-load`, 8 workers × 60s, warm, via the ingress. **Client-side** (incl. ingress), so compare rows to
each other, not to §8.2. All **0 KO**.

| Audit on enyMan | rps | p50 | p95 | p99 | proven path |
|---|---|---|---|---|---|
| (a) disabled | 1450 | 5 | 8 | 10 | baseline |
| (b) in-process | 1113 | 7 | 11 | 13 | wrote esq_address_log directly (+187k) |
| (c) bus → auKeep (default) | 591 | 12 | 39 | 60 | auKeep drained 108k rows |
| (d) redis stream | 1456 | 5 | 7 | 9 | stream XLEN 128,360 |

Ranking: **disabled ≈ redis > in-process > bus.** (d) is effectively free; (b) costs ~23%; (c) is heaviest
(~41% of baseline) with a saturation tail — `publisher-pool-size=0` so the single feed worker does one
synchronous JMS publish per event (the §8.2 c-sync pattern; the async pool is the lever, not exercised
here). (c)'s payoff remains offload — the `*_log` writes leave the business JVM/DB entirely.

---

## 9. Decisions & rationale, lessons learned

### 9.1 Design choices
- **Generic fan-out frontend, audit the first sink** — `messaging.xrod` (the generic `XRodManager` / `XRod`
  / rod-class impls / transport SPI) and the generic keep engine `esquire-dataKeep` (`XRodInProcess` +
  the applier engine, audit-unaware) split from the thin `esquire-audit` (the sink specifics:
  `AuditBusBridge`, the `AuditKeepDirector`, the `*_log` SQL); `IMappable` at the JPA layer so entities
  depend downward. Future sinks plug in with a new `IKeepDirector` and no write-site change.
- **(0) → (c) is one path in two phases** — shared producer; step 3 only adds the consumer + sink.
- **The code default is (0); each deployment picks a topology for footprint / monitoring, not performance.**
  Dev (Docker + local k8s) configure **(c) with the async pool** — chosen only because ActiveMQ is
  **already in the stack**, so (c) adds **no new external image** (just auKeep as an app pod), whereas (d)
  needs Redis and Kafka needs a broker + Connect; both dev environments are identical for GHA-script
  consistency. OKE configures **(a) DB triggers** — always-on, zero-extra-pod user-activity monitoring on
  the Always-Free demo. (c) is in fact the heaviest on the request path (§8); its merits — first-class
  pipeline, zero-loss-*capable* — are why it is a *good* option, not why dev defaults to it.
- **SQL externalized to `META-INF/audit/`** — a deployable spec artifact; audit is opt-in at packaging.
- **The generic engine holds only abstract code** — no SQL, no audit knowledge in `esquire-dataKeep` (the
  audit `*_log` SQL and the kinds live in the thin `esquire-audit` module); the vendor wiring lives in the
  per-vendor `tp-*` transport modules (each behind the `ITransportProvider` SPI), carried only by a
  deployment that uses it.
- **Kafka key = none; dedup only on the bus path** — order-independent audit wants even spread, not
  per-key order; dedup enables (c)'s zero-loss, so it lives on (c) alone.
- **Streams, not RedisJSON** — portability to managed Redis (OCI Cache has no modules) over richer queries.
- **`TolerantSource` null-binding is a documented feature** (`RodEventDbWriter`): a `:param` the body lacks
  binds to NULL instead of failing — that is what lets the empty-body DELETE bind every data column to NULL
  without the writer knowing each table's columns. The trade is loss of fail-fast on a future name drift;
  `EntityFillMapTest` + the SQL pin the keys independently. Lowest priority; resolved structurally by a
  future DaBaBeRe persistence layer.
- **`actionTime` = app-node wall-clock ms** (M6) — not monotonic across pods; accepted (no robust id also
  guarantees ordering; queries sort by `action_ts`, crl/req disambiguate).

### 9.2 Operational lessons / gotchas (k8s)
- **Docker Desktop containerd image store:** an `:latest`-tagged local-only image with
  `imagePullPolicy: Never` fails to resolve (`ErrImageNeverPull`). The fix the deploy uses: a **unique
  stamped tag + `IfNotPresent`** (what `k8s-rebuild` does for every service). A manual `docker build` adds
  buildx attestation manifests; `docker compose build` (what the deploy uses) is fine.
- **A k8s Service named `redis` is a trap** — Kubernetes injects `REDIS_PORT=tcp://ip:6379` into *every*
  pod, which collides with the producers' `spring.data.redis.port=${REDIS_PORT:6379}` and crashes them
  (`NumberFormatException`). **Name the audit redis service `esq-redis`** (injects `ESQ_REDIS_PORT`).
- **Postgres seed must bake `create.log`** — `create/all.sql` chains `\i ../create.log/all.sql`, but the
  image Dockerfile must `COPY db.seed/postgres/create.log` or a fresh cluster misses the `*_log` tables and
  (c)/(b) cannot write.
- **auKeep's Dockerfile has no ENTRYPOINT** (compose supplies the command) — the k8s chart sets
  `args:[java,-jar,app.jar]` (the temurin base entrypoint execs them); and **log paths go to `/tmp`** (no
  `./logs` volume in k8s).
- **The manual working-tree → git promotion is the failure mode CI guards** — twice CI caught
  "correct-in-the-dev-tree, missing-from-the-commit" gaps. Keep dev tree / git repo / runner checkout
  strictly separate.

---

## 10. The xx queue worker pool (enabling infrastructure)

Options (b), (c), and the high-volume end of (d) share the **xx queue worker pool** shape, so they all need
it to keep up. The next chapter for the queue-rig framework is a **multi-worker pool with an assignment** —
records distributed across N workers by an assignment key (the target `*_log` table / asset type), so writes
fan out in parallel across tables while **per-table FIFO ordering is preserved**. (A separate track from the
LMAX-Disruptor "endgame" in the queue-rig research; the xx consumer is its first real customer.)

---

## 11. The Rod — name, metaphor

<img src="img/x-rod.7.svg" alt="Rod logo" align="left" width="100" height="100">

**Rod = RoD = Relay of Data.** The async distributing collaboration that relays data from A to B — here, an
audit event from the originating service to its resting place. A single x-rod type, the **`XRod`** (obtained
from `XRodManager` by bus key + role), reusable beyond audit for any migrate/replicate job: a transmit leg
makes it a producer, a receive leg makes it a consumer, both make it a transceiver. Prose: **x-rod** for the
relay; the standalone audit consumer SERVICE that hosts a bus-fed consumer x-rod and lands events in the
`*_log` is **auKeep** (image `esquire.aukeep`).

**The metaphor:** a **lightning rod** on a castle's corner tower channels the strike safely to the ground. The
audit event is the lightning; the Rod catches it and conducts it to the log store without it tearing through
the operational path.

---

## 12. Cross-references

- [services.configuring.md](services.configuring.md) — the external config reference (*Selecting an audit
  option*, the env table, deploy defaults).
- [Object.Kind.enum.md](Object.Kind.enum.md) — the `kind` codes (entity + sub-entity kinds 988–998).
- [DatabaseDictionary.md](DatabaseDictionary.md) — §2 Entity Structure + the `*_log` tables.
- [Message.Structure.md](Message.Structure.md) — the `UE` entity message this design extends.
- [Messaging.md](Messaging.md) — the entity-broadcast + IAM buses (the audit queue is parallel to, and
  separate from, the entity-broadcast bus).
- `doc/research/Redis-on-OCI.md`, `doc/research/RedisJSON-local.md` — the Redis sink research.

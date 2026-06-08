# Esquire Audit Logging — *The Saga*

> **Status: planning draft (v1.2.7).** This document captures the design intent for the
> v1.2.7 audit-logging sprint — the **option space and rationale**. It is a working artifact now and
> will be promoted to the public **specification** once the options are settled. Decisions still open
> are marked *(open)*.
>
> The detailed infrastructure design — producer facade, message structure, identity model, workflow,
> transport — is in **[Esquire.AuditLogging.Design.md](Esquire.AuditLogging.Design.md)** (the #3 deliverable).
> Option **(b)** is now **built and smoke-proven**; the post-build design-review **verdicts** (which
> trade-offs are deliberate, which work belongs to the later (c)/(d) bus sprint) are recorded in
> **§10 of the Design doc**.

---

## 1. The problem

Every mutation of a business entity — create, update, delete, move — must leave an audit
trail: *who changed what, when, from what to what*. Today that trail is produced by
**database triggers**: triggers on the entity tables write history rows inside the same
transaction as the mutation.

Triggers work, but they bind the audit concern tightly to one place and one technology:

- **DB-dialect lock-in** — Oracle and Postgres trigger dialects differ; the logic lives twice.
- **In-transaction coupling** — the audit INSERT runs inside the mutation's own transaction, so
  an audit-side failure (a full log tablespace, a constraint violation) can roll back the *real*
  operation, and it lengthens lock-hold time.
- **Write amplification** — every mutation doubles its write footprint on the *operational*
  database; retention and scaling of the audit data are chained to the operational store.
- **Hard to evolve** — changing the audit shape means a schema migration on the live DB.

We deliberately do **not** count "hidden / invisible to the service code" against triggers: that
auditing is an out-of-band side-effect is equally true of CDC (e), and is the same
publisher-blind decoupling the broadcast options (c / d) — and the entity-broadcast bus itself —
treat as a *feature*. Invisibility is a property of decoupled audit in general, not a
trigger-specific flaw, so it is not on the list above.

The sprint does not throw triggers away. It **reframes audit logging as a pluggable
concern** — one seam in the services, several interchangeable strategies behind it — so the
deployment can pick the trade-off that fits its environment. Triggers become *one* option,
not *the* option.

---

## 2. The design seam

The app-side outcomes — **0** (no store), **b**, **c**, **d** — sit behind a single audit-logging
strategy (an SPI / `IAuditLogger`-style interface), selected by configuration or Spring profile.
(The database-side options — **a** triggers and **e** CDC — capture below the app and never touch
the seam.) This is the same shape Esquire already uses for swappable concerns — the Taijitu cache
behind `IBizTreeCacheRepository`, the TokenRelay strategies behind one filter. The entity services
(enyMan, pacMan) emit a uniform **audit event** through the seam and stay ignorant of where it lands.

```
  entity mutation (enyMan / pacMan)
            |
            |  (a) DB triggers and (e) CDC capture BELOW the app -- no seam involved
            v
     [ IAuditLogger seam ]   <-- app emits ONE uniform audit event; a strategy routes it
            |
   +--------+--------+--------+--------+
   |        |        |        |
   0) none  b) JPA   c) Rod   d) stream
   (off)    -> logDB -> bus    -> Redis
```

(Options (a) and (e) are database-side capture and never touch the seam. (0)/(b)/(c)/(d) are the
app-side outcomes behind it — (0) persists nothing (the change is just broadcast + DEBUG-traceable
on the Rod path; request-level INFO logging is the only standing trace), the others persist it. The
change is already broadcast, so (c) is simply (0) plus a Rod consumer that lands it in SQL.)

> **Delivery semantics (loss / duplication / dedup / zero-loss) per option:** see
> [Esquire.AuditLogging.Design.md](Esquire.AuditLogging.Design.md) §13.
> **How to select & configure each option entirely from outside the code:** see
> [services.configuring.md](services.configuring.md) — *Selecting an audit option*.

*(open)* Single active strategy vs. several in parallel — should a deployment be allowed to
run, say, sync-DB **and** Redis-stream at once (belt-and-suspenders)? Default assumption: one
active strategy per deployment, but the seam should not forbid composition.

---

## 3. The options

Two questions, in order. **First — do you persist a data-delta audit at all?** Option **0** says
no. **If yes — how?** Options **a–e** are a spectrum from *tightly coupled / synchronous / same DB*
to *decoupled / asynchronous / separate store / non-SQL*.

### 0) No audit store — *"persist nothing"* (baseline / default)

- **Where:** nowhere. No audit table, no Rod, no stream, no fan-out. The entity change still rides
  the existing entity-broadcast bus (bizTree and friends already consume it), but nothing persists
  it for audit.
- **Visibility:** two levels, both already present. At **INFO**, the existing **request IN/OUT
  logging** records every operation at the request boundary — who called which command, when —
  which for many deployments is trail enough. At **DEBUG**, the entity-change detail (before /
  after) is logged only to *debug the xy/xx-Rod*. We deliberately do **not** INFO-log
  entity-update payloads: at frequent-update volume that floods the logs, and a log line is not a
  queryable or retained audit record anyway.
- **Strengths:** zero audit cost and coupling — the simplest possible state; removes the trigger
  write amplification outright.
- **Costs:** no dedicated audit store — no SQL-queryable record of *what changed* (before /
  after), no retention beyond the log pipeline. The request-level INFO log is the only standing
  trace; fine where that suffices, but not a data-delta audit trail.
- **Stance (Esquire): the no-store baseline (request-log only), and the natural first build
  step.** Get the decoupled change event flowing (DEBUG-traceable, nothing stored), then (c) adds
  the Rod → SQL sink for deployments that need a real, queryable audit trail. Since the change is
  already broadcast, (c) is just "(0) plus a consumer."

### If you persist — how (a–e)

### a) Local Sync Logging — *"in place, in transaction"*

- **Where:** log tables in the **same** operational database as the entity data.
- **Who fills them:** **DB triggers**, synchronously, inside the mutation's own transaction.
- **Character:** this is today's mechanism, formalized as option (a).
- **Strengths:** strongest integrity — the audit row commits atomically with the change and
  cannot be lost or bypassed. Zero application code.
- **Costs:** DB-dialect-specific; write amplification on the operational DB; audit retention
  and scaling coupled to the operational store.
- **Use when:** integrity is paramount and the operational DB has headroom.

### b) Local Async Logging — *"off the hot path, still ours"*

- **Where:** log tables in a **separate log database**.
- **Who fills them:** the **originating service**, **asynchronously**, over JPA — after the
  mutation commits, an in-process **xx queue worker pool** (a queue-rig drain) routes each audit
  record to its **particular audit table** in the log DB. Same consumer shape as (c)'s Rod, just
  in-process and fed by a local queue instead of the bus.
- **Strengths:** removes write pressure from the operational DB and from the request hot
  path; the service still owns the audit logic in plain Java.
- **Costs:** the audit write leaves the mutation's transaction — a crash between commit and
  audit-write can drop a row unless backed by an outbox / retry. Each service grows a small
  audit queue + worker.
- **Use when:** you want storage separation without standing up new infrastructure or a new
  deployable.
- **Stance (Esquire): possible, not recommended.** Mostly a temporary stepping-stone toward an
  async log stream. Since the trigger is invisible end-to-end (see *Measured cost* below), moving
  the write into the service only spends app CPU + a second connection for no real gain. If an
  async stream is the actual goal, (e) achieves it without any app cost.

### c) Distributed Logging — *"xx-Rod"*

- **Where:** log tables in the **log database**.
- **Who fills them:** **another component** — the **Rod** (see §4). The originating service
  publishes an audit event onto the messaging bus (ActiveMQ); the **xx-Rod** consumer owns
  the log-DB write.
- **Strengths:** full decoupling — the entity services carry no JPA or log-DB dependency at
  all; they just publish. The audit pipeline is its own scalable, independently-deployable
  concern and can fan out to multiple sinks.
- **Costs:** more moving parts; eventual consistency; the consumer needs throughput headroom
  (see the queue-rig multi-worker pool, §5).
- **Use when:** audit is a first-class, independently-scaled pipeline — the cloud-native shape.
- **Stance (Esquire): the in-framework way to decouple while keeping audit in SQL.** RDBMS log
  storage via messaging fan-out over the bus we already run — no Kafka, no Debezium, no extra
  tooling. The relational cousin of (e), and the one decoupled option we would actually build.

### d) Streamed Doc DB Logging — *"new fashion"*

- **Where:** a non-SQL, JSON-friendly document / stream store — **Redis** (Redis Streams as
  an append-only audit log; RedisJSON for richer documents).
- **Who fills them:** audit events streamed as JSON documents, naturally paired with the Rod
  producer.
- **Strengths:** schema-flexible (entity shapes vary freely), very high write throughput,
  native streaming with consumer groups; the modern event-log approach.
- **Costs:** new infrastructure (Redis); eventual consistency; reporting/query model differs
  from SQL.
- **Use when:** high-volume, schema-loose, stream-first auditing is the goal.
- **Stance (Esquire): BUILT as a producer-side Redis Streams option (`mode=redis`).** The producer
  XADDs each committed event straight to a Redis Stream — the stream IS the append-only audit log, so
  there is no consumer service (read with `XRANGE`). **Streams, not RedisJSON:** a Stream matches the
  append-only audit trail and stays portable to managed Redis/Valkey (OCI Cache ships core only, no
  modules), whereas RedisJSON / RediSearch would lock us to self-hosting (explored locally — see
  `doc/research/RedisJSON-local.md` and `doc/research/Redis-on-OCI.md`). The original caveat still holds:
  ad-hoc *relational* audit queries ("who changed entity X, ranges by user") are answered more directly
  by the SQL `*_log` of (b)/(c). So (d) is the fast, schema-loose, fire-and-forget option — cheapest on
  the request path (see the matrix) — not the reporting-first one.
- **Scaling note — Redis Streams have no native partitioning.** A stream is a single key, so it lives on
  **one shard / node** (single append-only log, single-threaded write); consumer groups parallelise
  *delivery*, not storage. To scale out you shard **at the application level** — N stream keys
  (`esquire.rod.audit.{0..N}`) routed by `hash(entityId)`, which distribute across nodes in Redis Cluster
  / a sharded OCI Cache; order is preserved only *within* each stream (route by entity to keep per-entity
  order). For our volume a single stream is ample. This DIY-partitioning is exactly where the next option
  is expected to differ.

### *(next research)* f) Kafka as the audit transport

- **Where:** Apache Kafka as the durable, natively-partitioned bus, in two shapes:
  - **(c)-style:** `service -> Kafka -> xxRod` — Kafka in place of (or alongside) ActiveMQ; the pluggable
    consumer / `IRodDirector` seam swaps the transport, xxRod still writes the `*_log`.
  - **(d)-style:** `service -> Kafka -> Redis Kafka Sink (Kafka Connect) -> Redis` — Kafka in front of
    Redis via a sink connector, instead of the producer XADD-ing directly.
- **Why it is worth a look:** **native, declarative partitioning** — N partitions per topic as the unit of
  parallelism + per-key ordering, auto-distributed across brokers, consumer groups auto-bound to
  partitions. That is the one axis where Kafka is structurally stronger than Redis Streams (where you DIY
  the partitioning) and ActiveMQ (a queue, not a partitioned log). To be evaluated as its own research
  before the full audit-logging write-up.

### e) Log-based CDC — *"capture from the redo log, outside the framework"*

- **Where:** any sink (a log DB, a stream, a search / columnar store).
- **Who fills them:** the **database's own transaction log** — a CDC connector (Debezium on the
  Postgres WAL, Oracle LogMiner on redo) tails the log the DB already writes and streams row
  changes out. No trigger, no app code on the write path.
- **Strengths:** zero write-amplification on the operational path — it reads redo the DB already
  produces, sidestepping the 3–6x trigger cost measured below; fully async; no service CPU.
- **Costs:** captures raw row diffs, not semantic business events; needs external infrastructure
  (connector + stream + sink) and ongoing ops (replication-slot / lag monitoring, schema
  evolution).
- **Stance (Esquire): out of framework scope — a DBA / configuration / tooling concern, not
  framework code.** Listed for completeness as the "free" alternative to (b) and (c) for getting
  changes into a separate log store asynchronously — it replaces the in-service JPA write (b) and
  the bus-fanned Rod write (c) with a redo-log tail. We are **not** implementing it; if that need
  ever becomes real, CDC is how to get the stream without paying app or bus cost.

### Comparison

| Option | Store | Filled by | Sync? | New infra | Integrity | Decoupling |
|---|---|---|---|---|---|---|
| **0** No audit store | none (request INFO log only) | nothing — broadcast + DEBUG trace | n/a | none | none (request-level only) | high |
| **a** Local Sync | operational DB | DB triggers | sync | none | highest | lowest |
| **b** Local Async | log DB | originating service (JPA) | async | log DB | high (outbox) | medium |
| **c** Distributed (xx-Rod) | log DB | xx-Rod consumer (bus) | async | log DB + Rod | medium | high |
| **d** Streamed Doc | Redis | stream consumer | async | Redis + Rod | medium | highest |
| **e** Log-based CDC | any sink | DB redo log (Debezium / LogMiner) | async | connector + stream + sink | medium | highest (no app coupling) |

**Whose decision this is.** The framework's responsibility ends at **emitting / broadcasting the
entity-change event** and logging each operation at the request boundary (INFO request IN/OUT).
*Whether and how to persist an audit trail is the deploying user's decision*, sized to their own
compliance, reporting, and retention needs: leave it at the request log with no store (0), persist
it to a queryable SQL store (a / c), stream it (d), or capture it out-of-band with CDC (e). Esquire
ships the mechanism and a sensible default — not a mandate that every deployment keep a relational
audit trail.

**Esquire's own path** is phased, not a single pick:
1. **(a) today** — triggers, unchanged.
2. **(0) decouple** — delete the triggers; the change is still broadcast and every operation is
   already on the INFO request log, with entity-delta detail at DEBUG for Rod work. Removes the
   coupling + write amplification immediately; persists no data-delta store.
3. **(c) when a queryable audit trail is required** — add a Rod consumer that lands those same
   events in an RDBMS log store: SQL-queryable, retained, decoupled; growth handled with time-based
   partitioning + retention, not by leaving SQL.

The other options sit off this path: (b) is possible but not recommended, (d) and (e) are
out-of-scope alternatives. And (0) → (c) is **one path in two phases, not two designs** — they
share the same producer, so step 3 only *adds* the xx consumer and its SQL sink to step 2. A
deployment takes that step exactly when it needs a queryable, relational audit trail.

### Measured cost of option (a) — what triggers actually cost (Postgres, 2026-06-03)

We measured the trigger overhead empirically before arguing against it. The per-mutation work
of the address trigger is a single 18-column INSERT into `esq_address_log` (the address trigger,
unlike the org/account ones, does **not** do the `esq_entity_path` subquery). Measured two ways
against the docker compose stack.

**End-to-end (service layer).** The hauberk `UpdateLoadSimulation` drives sustained
`POST /esq-cmd-save` address edits (8 VUs, 45s, ~45k requests over a 25-user pool), run three
times changing only the trigger state:

| Trigger state | Requests | Throughput | mean | p95 | p99 |
|---|---|---|---|---|---|
| dropped  | 44,782 | 995 rps  | 8 ms | 10 ms | 12 ms |
| disabled | 45,149 | 1003 rps | 8 ms | 10 ms | 12 ms |
| enabled  | 45,182 | 1004 rps | 8 ms | 10 ms | 12 ms |

All three within ~1% — **end-to-end the trigger is unmeasurable.** It is dwarfed by gateway
routing, JWT validation, the JMS broadcast publish, and the network round-trip; the audit
INSERT is well under 1 ms of an ~8 ms request. The enabled run wrote 45,175 log rows, so it
demonstrably fired. A *disabled* trigger (object present, not firing) costs exactly nothing —
mere existence is free.

**DB layer (isolated).** 50,000 distinct-row UPDATEs on a bench table, trigger ON vs OFF,
alternating across iterations (mean of 3; Postgres VACUUMed between, Oracle committed between).
Self-contained bench table + bench log + bench trigger — no real table touched.

| DB | OFF (no trigger) | ON (trigger) | ratio | added/row |
|---|---|---|---|---|
| Postgres 17 | 128 ms (2.6 us/row) | 437 ms (8.7 us/row)  | **3.4x** | +6 us  |
| Oracle 19   | 121 ms (2.4 us/row) | 771 ms (15.4 us/row) | **6.4x** | +13 us |

**At the database the trigger multiplies the bare write-statement cost 3–6x depending on engine**
— Oracle's per-row PL/SQL trigger + redo is ~2x heavier than Postgres plpgsql, on an almost
identical bare-UPDATE baseline. Real and significant, but only when the *database itself* is the
bottleneck. (The end-to-end service test was run on **both** engines and the trigger is invisible
on each: Postgres ~1000 rps regardless of state; Oracle, warm, 764 rps enabled vs ~775 disabled —
within run-to-run noise, same 10 ms / p99 16 ms. Even a 6.4x audit INSERT is tens of microseconds
inside an ~8–11 ms request. Note: on the Oracle stack a *cold* first run read low, 691 rps; it
converged to ~770 once warm, so warm-up — not the trigger — was the only real e2e effect there.)

**Conclusion — the real "con" of option (a) is not user-facing latency.** Under normal service
load the trigger is invisible. Its true costs are:

1. **DB write amplification** — every mutation does a second write (~3.4x the bare UPDATE at the
   DB) and leaves one extra row forever (45k rows from a single 45s load test). This bites under
   *DB-bound* load (batch jobs, high write concurrency, a shared operational DB) and grows the
   operational store without bound — WAL, vacuum, backup size, retention all coupled to the live DB.
2. **Coupling** — DB-dialect-specific (Oracle and Postgres trigger code maintained twice and kept
   in sync with the schema), the audit INSERT shares the mutation's transaction (an audit failure
   can roll back the real operation), and a schema migration is needed to evolve it.

So the case for moving audit off triggers (options b/c/d) rests on **write offload and
transactional independence**, not on shaving request latency — a sharper, more honest argument
than "triggers are slow" (they are not, at the request level) or "triggers hide logic" (so does
every decoupled option).

> *Method note.* End-to-end measured via hauberk `UpdateLoadSimulation` (Gatling native stats)
> against both the docker Postgres 17 stack and the same stack switched onto the local host
> Oracle 19 / `MIR0N` (compose.oracle overlay); DB-layer micro-benchmark on docker Postgres 17
> (psql) and host Oracle 19 (sqlplus, self-contained bench objects, dropped after). The hauberk `--metrics` perf-matrix summary has an
> off-by-one in `PerformanceMatrix.column()` that throws on large per-request sample counts and
> aborts the run; these measurements used Gatling's own stats instead. That bug is logged for a
> harness fix.

### Measured cost of options (a) / (b) / (c) / (d) — request processing time (2026-06-08)

The right metric for "what does audit cost" is the **request processing time inside the service** — not
the Gatling end-to-end (which folds in gateway + KC + network + client contention), and not the time-to-
log-published (the async tail). `MdcFilter` stamps two response headers the perf-matrix records per request:

- **`srvOuter`** = `System.currentTimeMillis()` around the whole in-service request (controller + tx commit
  + the post-commit `feed.put()`). This is the request processing time.
- **`srvInner`** = JPA/DB time only (`performance.getTotalJpaTime()`); `srv_self = srvOuter - srvInner` is
  the non-JPA service work, where the post-commit audit enqueue lives.

Same `UpdateLoadSimulation` (`POST /esq-cmd-save` postal-address edit → enyMan; one `esq_address_log` event
per request — confirmed by the row counts, not user+person+address), ~10-client pool under the Test House,
on the dockerized stack (gateway + KC + enyMan + ActiveMQ + xxRod + Redis + Postgres 17), warm, flipping
only enyMan's audit config between runs — **all six modes measured in one same-day sweep**. All values ms,
for the `POST /esq-cmd-save` request. (Run-to-run jitter on this shared host is ~±1 ms; read the relative
story, not the third digit.)

**Normal load (4 update workers, pre-saturation)** — audit is effectively free in every mode:

| Audit mode | srvOuter mean | p50 | p95 | p99 | srvInner mean (JPA) |
|---|---|---|---|---|---|
| (0) off                          | 3.90 | 4 | 6 | 8 | 1.15 |
| (a) triggers (DB, in-tx)         | 3.85 | 4 | 5 | 6 | 1.20 |
| (b) in-process                   | 3.80 | 4 | 5 | 6 | 1.23 |
| (c) bus -> xxRod, sync publish   | 4.36 | 4 | 6 | 9 | 1.37 |
| (c) bus -> xxRod, async pool x4  | 4.04 | 4 | 6 | 7 | 1.30 |
| (d) redis stream, single worker  | 3.83 | 4 | 5 | 7 | 1.32 |

All six sit within ~0.6 ms of each other (p95 5-6, p99 6-9). At normal load even the single synchronous
publisher / XADD worker drains faster than events arrive, so the bounded feed never fills — no backpressure.

**High load (8 update workers, 60 s)** — pushes (c) sync past its single-publisher drain ceiling:

| Audit mode | srvOuter mean | p50 | p95 | p99 | srvInner mean (JPA) | cost lands in |
|---|---|---|---|---|---|---|
| (0) off                          | 5.36  | 5 | 7  | 9  | 1.29 | — |
| (a) triggers (DB, in-tx)         | 6.46  | 6 | 9  | 12 | 1.49 | srvInner (in-tx) |
| (b) in-process                   | 6.21  | 6 | 8  | 10 | 1.37 | srv_self (~+1 ms) |
| (c) bus -> xxRod, sync publish   | 10.64 | 9 | 24 | 51 | 1.49 | srv_self (saturation tail) |
| (c) bus -> xxRod, async pool x4  | 6.16  | 6 | 8  | 10 | 1.61 | srv_self (~+1 ms) |
| (d) redis stream, single worker  | 5.67  | 6 | 8  | 9  | 1.60 | srv_self (~+0.3 ms, NO saturation) |

- **srvInner (JPA) is flat across every mode (~1.1–1.6 ms).** No option pushes meaningful cost onto the
  DB-time of the request thread — even (a)'s trigger INSERT, riding inside the already-open business tx,
  only nudges srvInner (+0.2 ms vs off; matching the host-pg micro-benchmark above: triggers ~0 % at this
  load). The audit cost, when any shows, is in `srv_self` (the post-commit enqueue), not in JPA.
- **(a) triggers ≈ free, and what little it costs is in-tx.** The trigger is a cheap extra INSERT in a
  transaction committing anyway — its real cost lands in `srvInner` (+0.2 ms), the rest of its srvOuter is
  within run jitter. Its real downsides are structural (it cannot see crl/req/uid except via columns the app
  must carry on every business row, and it couples the audit schema into the business DB), not latency.
- **(b), (c) async-pool and (d) all add only ~0.3–0.9 ms** to the baseline, tight tails (p99 ≤ 10). In all
  three the request thread only does `feed.put()` after commit; the difference is purely what the single
  feed worker does downstream, off the request thread (in-JVM `XXRod.submit` for (b), an async JMS publish
  for c-pool, a local `XADD` for (d)).
- **(c) sync is the only one with a problem, and only under saturation.** The single feed worker publishes
  **synchronously** (one broker round-trip per event, ~1.5 ms). When sustained ingest exceeds that drain
  rate the bounded feed (4096) fills and `feed.put()` blocks the request thread — p95/p99 jump to 24/51 ms.
  This is a throughput-cap effect, not a per-request tax (see the load gradient below).
- **(d) redis is the cheapest audit option here — and does NOT saturate even single-worker.** Despite the
  same single feed worker as (c) sync, the synchronous **`XADD` is a cheap local Redis round-trip (sub-ms)**,
  not a broker hop — so the one worker out-drains 8-worker ingest and the feed never fills (8w: 5.67 ms,
  p99 9, n=56,224, below even (b)/(c)-pool). (d) therefore needs **no publisher pool** at this load (the
  `publisher-pool-size` option still applies if you ever push past the single-XADD ceiling). Its trade is the
  opposite of (c)'s: the audit log lives in Redis (fast, fire-and-forget) but there is no broker buffering /
  competing-consumer redundancy — see `doc/research/Redis-on-OCI.md`.

**c-sync is load-dependent — regular traffic passes straight through.** Same c-sync mode, varying only the
worker count (`srvOuter` ms):

| load | srvOuter mean | p95 | p99 |
|---|---|---|---|
| 1 worker (regular) | 2.59 | 3 | 4 |
| 2 workers (regular)| 3.10 | 4 | 5 |
| 4 workers (busy)   | 3.95 | 5 | 7 |
| 8 workers (saturate)| 14.65 | 46 | 49 |

Below the single-publisher drain ceiling the queue passes through without blocking (p99 <= 7); the knee is
between 4 and 8 workers here. So **(c) sync is fine for normal load**; the async pool simply raises that
ceiling for high-bandwidth bursts.

- **(c)'s real payoff is offload, not request latency:** the `*_log` writes leave the business DB/JVM
  entirely — xxRod wrote the `esq_address_log` rows on its own connection pool (end-to-end verified:
  EnqueueCount == DequeueCount == rows, queue drained to 0, zero errors). Under a DB-bound business workload
  (the reason to move audit off-box) that offload is the win.
- **The pool is opt-in** via `...audit-logging.x-rod.bus.publisher-pool-size` (0 = single-worker sync publish,
  kept as an option for normal load; N>0 = N async publisher threads over a dedicated `useAsyncSend`
  connection, for high-bandwidth). Audit is order-independent, so parallel publishers are safe; batching the
  publish remains a further lever.

---

## 4. The xy / xx-Rod

**Rod = RoD = Relay of Data.** The **Rod** is the async distributing collaboration that relays
data from one place to another — here, an audit event from the originating service to its
resting place. It is **not a service** (yet — the eventual standalone is named `x-rod`); it is a
producer/consumer *pair*, reusable beyond audit for any migrate/replicate-data-from-A-to-B job.
Names: `xy-Rod` / `xx-Rod` in prose, `XYRod` / `XXRod` as classes (capital XX/XY).

- **xy-Rod** — *male, producer*. Lives in (or beside) the originating service; emits the event
  onto the bus / stream. Present in the **over-the-bus** options (c, d).
- **xx-Rod** — *female, consumer*. A **queue worker pool** that pulls each audit record off its
  queue and **distributes it to the particular destination** — the asset's own audit table (log
  DB; options **b and c**) or the stream store (Redis; d). It is the *same* consumer mechanism in
  every persisted option; only its **location and feed** differ: **in-process, fed by a local
  queue** for (b); **standalone, fed by the bus** (paired with xy) for (c) / (d). So (b) is the xx
  consumer *alone*, in-process; (c) / (d) are the full xy → xx pair over the bus.

**The metaphor.** A **lightning rod** — a copper spike on the castle's corner defensive
tower — channels the strike safely to ground. The audit event is the lightning; the Rod
catches it and conducts it to the log store without it tearing through the operational path.

**Logo / theme.** Minimalistic; Middle Ages / Mediterranean / Knight / cartoon. A copper
lightning rod on a castle corner tower; a lightning bolt striking the rod. Consistent with
the platform's medieval naming line (cf. Haubergeon).

**Paired-antenna marks.** The single lightning rod resolves into a transmitter/receiver pair:
- **xx-Rod** (consumer) -- the rod becomes a **receiver antenna**: the rod with **XX** crossbars
  attached as the receiving elements.
- **xy-Rod** (producer) -- a **transmitter antenna**: the **YX** form, with the **Y** standing on
  top and a **lightning bolt arcing between the two upper corners (forks) of the Y**.

---

## 5. Enabling infrastructure — queue-rig multi-worker pool *(deferred track)*

Options (b), (c) and the high-volume end of (d) all share one consumer shape — the **xx queue
worker pool** — so they all need it to keep up. That is the next chapter for the **queue-rig
framework**: a **multi-worker pool with an assignment** — records distributed across N workers by
an assignment key, here the **target audit table / asset type**, so writes fan out in parallel
across tables while **per-table FIFO ordering is preserved**. (A separate track from the LMAX
Disruptor "endgame" noted in the queue-rig research; the xx consumer is its first real customer.)

---

## 6. Related sprint item — entity *system flag* (anti-deletion)

Tangential to audit but captured here because it travels with this sprint's planning.

Add a **system flag** to an entity (office / user) that **protects it from deletion**:

- **Not editable** by anyone through the application — set on the **database only** (e.g. in
  the seed scripts).
- **Enforced at the service (or DB) level** — the delete pre-checks reject a flagged entity.
- **Not visible on the GUI side.**
- **Protects:** the root system, the root system users, and the seed **Test House** office
  plus its users.

This complements the existing delete pre-checks (USR `connectFlg`, ACCT `status`) — a
hard, data-level "do not delete" that operators cannot toggle off.

---

## 7. Implementation constraints

- **ret-pattern is mandatory.** All new service code follows the single-return `ret` pattern
  — declare `ret` at the top, assign throughout, one return at the end; no early returns in
  value-returning methods. This is non-negotiable for every line written this sprint.
- **No dynamic SQL, no biz/DB-layer mixing.** New repository methods are one self-contained
  SQL each; JPA stays out of the service layer (option b's JPA usage lives in a repository
  tier, not the service tier).
- **ASCII only** in code, log messages, and string literals.

---

## 8. Open questions

1. *Strategy selection* — single active strategy per deployment, or composition allowed (§2)?
2. *Default strategy* — does (a) remain the shipped default for safety, with the others
   opt-in by profile?
3. *Loss tolerance for (b)/(c)/(d)* — is an outbox/retry required, or is best-effort async
   acceptable for audit given the operational DB still holds the live state?
4. *Log-DB identity* — one shared "log database" for both (b) and (c), or per-strategy?
5. *Event shape* — is the audit event the existing entity-broadcast message, or a dedicated
   audit envelope (who/when/before/after)?
6. *Rod packaging* — is xx-Rod a standalone deployable, a mode of an existing service, or a
   library both sides embed?

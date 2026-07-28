<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire -- Continuing Development backlog

> The **active-development horizon is complete.** The framework built out the full stack and now moves into
> **support / continuous-development mode**: it runs, it is maintained, and it grows by sprints defined against a
> target -- but no longer as a **single sequential line**. Where the build-out advanced one sprint at a time,
> continuous development can run **several sprints in parallel**, each against its own target. This file is where the
> candidate targets are tracked.
>
> Framework-wide future-work items -- things that are ACCEPTABLE as-is today and are tracked here to be improved in a
> later cycle.

**Related backlogs (kept separate, on purpose):**
- **The Messaging Bus keeps its own continuing-dev list** -- [Esquire.MessagingBus.ContinuingDev.md](Esquire.MessagingBus.ContinuingDev.md)
  (the alive protocol, the full resilience-pattern set, the FIX-like async protocol, replay/seek, and more). It stays
  a list of its own because **lifting the Messaging Bus out of Esquire into its own product / repository is itself the
  next continuing-dev arc** -- so the bus carries its backlog with it rather than dissolving it into this file.

This file collects the **framework-wide** forward work: the "improve what already works" items and the
**new-feature directions**. It is a running collection.

---

## Identity / onboarding

### CD-1 -- Harden Keycloak user onboarding (replace the shared `"changeit"` temporary password)

**Today:** `kcMaster` creates every interactive Keycloak user with the same literal temporary password
(`"changeit"`), `enabled=true`, and `UPDATE_PASSWORD` forced on first login
(`KcRequestHandler.handleCreate` -> `KcIdentityService.createUser`). It works -- the owner is forced to change the
password on first login -- and it is acceptable for now, not a big issue.

**Why improve it later:** the shared, well-known constant leaves a pre-first-login takeover window if an adopter
ships it as-is on a public deployment with guessable `loginId`s (someone who logs in with `"changeit"` before the
owner gets the change-password prompt and can set their own).

**Improvement:** create the user with a **per-user random** password that is never transmitted, and drive the first
credential via Keycloak's `executeActionsEmail(UPDATE_PASSWORD)` -- the owner sets the password from an emailed link
and the account is unusable until then -- or create `enabled=false` until the first credential is set. One-call
change at the create seam; deferred because it adds an email/SMTP dependency and is onboarding UX an adopter may
prefer to own.

---

## Messaging / entity apply

### CD-2 -- A per-entity change number (out-of-order-safe apply, ordered audit, simpler dedup)

**Today:** the entity-broadcast x-rod delivers in order (`concurrency: 1`, one topic consumer) but applies on a
flat `receiver-pool` (default size 4) with no per-entity affinity, so two events for the SAME entity
(CREATE->UPDATE, UPDATE->DELETE) can be applied out of order in bizTree's cache. It is NOT a blocking issue -- the
**app-logic collaboration keeps one editor per entity at a time** (the editing flow coordinates so a second
concurrent write to the SAME entity does not arise), and the Taijitu night-watch reconciles the cache against the DB
(source of truth) anyway -- so nothing is lost or corrupted; it self-heals. A good-to-have guarantee, not a fix.

**The improvement -- a per-entity change number.** Carry a per-entity **change number** (a version counter) kept in
the DB. When the entity is written the DB increments it atomically (`SET change_no = change_no + 1`), so every change
gets a strictly higher number than the one before it, and the number rides on the broadcast event. Then:
- **Replication (bizTree cache):** an apply compares the event's change number to the cached node's -- apply only if
  it is HIGHER, skip if lower or equal. An out-of-order or duplicate apply becomes harmless: the older event is
  skipped and the newer wins regardless of arrival order. Workers stay fully parallel, no per-worker routing.
- **Audit:** the same change number is a per-entity order key, so the audit trail can be sorted back into true order
  even when events were produced or applied out of order (also gives the demo accounting ledger the monotonic order
  key it lacks -- today it orders only by a random-UUID PK plus a timestamp).
- **Audit dedup:** it also simplifies the audit-log dedup key. Today the `*_log` dedup unique index is keyed
  `(crl_id, pk[, kind/sub])` -- the correlationId is the per-operation discriminator. Once every change carries a
  strictly-increasing per-entity change number, `(pk, change_no)` IS the operation identity: a redelivered event
  carries the same change number → collides → dropped; two real changes carry different numbers → both kept. The
  dedup key drops correlationId and simplifies to `(pk, change_no)` -- one number tied to the entity itself rather
  than to a request-scoped id.

This is more general than per-worker affinity: ONE DB-maintained number fixes order for BOTH the cache replication
AND the audit record (and simplifies the audit dedup key) -- the correct way to handle out-of-order updates in a
replication flow. It also makes the move-broadcast's parents-first `ORDER BY` redundant (the version guard settles
apply order regardless). Deferred because the usage model plus the night-watch already cover it today; worth it if a
future workload produces same-entity bursts or if audit / the ledger ever needs a guaranteed event order.

---

## Documentation / diagrams

### CD-3 -- Detailed collaboration / sequence diagrams for every async workflow

**Today:** most async workflows are correct and settled, but their step-by-step collaboration lives only in code and
scattered header comments. Recalling exactly how a flow works -- who publishes, who receives, what rides on the same
single-FIFO queue, and in what order -- takes a full re-read across several files each time.

The two costliest flows are documented with sequence diagrams:
- **The enyMan move queue** and the create-while-move repair -- parents-first event ordering, the system lock, the
  single-FIFO worker, the local vs peer reconcile, and the elastic grace -- in the *Path consistency under a move*
  appendix of [Esquire.BizTree.md](Esquire.BizTree.md) (`img/move-race.svg`, `img/move-ordering.svg`).
- **The Taijitu recoverable cache** -- the two-monad apply, off-worker CHECKSUM, night-watch sweep, and
  `onMismatch=SWAP` heal -- in [Esquire.BizTree.md](Esquire.BizTree.md) (`media/BizTreeModel.png`).

**Improvement:** carry the same treatment to the async workflows that still live only in code --
**entity-broadcast apply**, **KC request/response**, and the **audit keep** -- a collaboration / sequence diagram
each showing participants, messages, and ordering guarantees. Deferred because those flows work as-is; this is a
comprehension / maintenance investment.

---

## Testing / resilience

### CD-4 -- A chaos / fault-injection harness for the resilience budgets

**Today:** the resilience budgets (timeouts, fail-fast, retries, the redundant fleet) are designed and their
behaviour is reasoned about, but there is no harness that **injects a fault on purpose** -- a killed broker, a
stalled database socket, a paused pod, a dropped network -- and then asserts the budget held (the request failed
fast rather than hanging, the fleet kept serving, no data was lost). Resilience is verified by design review and by
whole-stack load tests that happen to survive, not by a repeatable fault drill.

**Improvement:** a fault-injection harness that scripts each failure and checks the response against the budget it is
supposed to enforce -- so a regression in a timeout or a health gate is caught by a test instead of in production.
Deferred because building it is a real project in its own right and the budgets are sound as designed; it is a
confidence / regression-safety investment, not a fix.

---

## Database

### CD-5 -- A schema-integrity checker for db.seed

**Today:** `db.seed` ships the schema and the seed data -- a clean starting point -- but nothing **verifies** an
existing database still matches the intended schema (columns, constraints, indexes, the dedup and audit overlays).
Drift between what the seed would create and what a long-lived database actually holds is invisible until something
breaks on it.

**Improvement:** a schema-integrity tool that diffs a live database against the seed's intended shape and reports
drift -- run on demand, not part of the seed. Deferred because the seed is a starting point, not an
integrity-enforcing product, and the config-parity tooling already covers the deployment-config half of this.

---

## Observability

### CD-6 -- Business-statistics collection (a decision record, not a TODO)

Recorded so the next person does not re-litigate it from instinct.

**Where it stands.** `EsqBizMeters` writes each business meter directly (static + registrar), at ~84 ns per call and
0.45 calls per request. At that cost the collection is not a bottleneck -- there is nothing to fix today.

**What was rejected, with evidence.**
- **A queue in front of the meter writes** -- the benchmark dropped ~1.48M events, ran slower single-threaded, and
  could not carry gauges. Batching meter writes through a queue is a net loss here.
- **Raw pre-resolved meter handles** -- premature: they would break the generic-in-`common` facility design to save
  ~0.12 us/s, not worth the coupling.

**If it ever does need speed,** the lever is not the meter path but work-batching in the queue rigs that carry heavy
work (the bizTree cache write, the enyMan move) -- `IQueueListWorker.process(Collection)`, and beyond that a
Disruptor.

---

## Dependency / upgrade debt -- the time-bomb sweep

These items **work today and fail on a future upgrade** -- silent, and only visible if you go looking. They are
collected in one place so the eventual bump is one deliberate task, not several surprise build breaks.

### CD-7 -- The startup-log deprecation check (the real deliverable)

Every JVM item below sits in plain text in the container's own startup log and nobody reads it. A CI (or smoke) check
that **fails the build on** `WARNING: .*(terminally deprecated|will be removed|restricted method)` in a service's
startup output turns the whole sweep from archaeology into a build error the day it appears. **This check is the
deliverable**; the items below are the backlog it would have caught.

### CD-8 -- The tracked upgrade-debt items

- **`sun.misc.Unsafe` -- every Java service, terminally deprecated, "will be removed."** Two callers, neither ours:
  OpenTelemetry (`opentelemetry-sdk-trace` → `Unsafe::objectFieldOffset`, all services) and Netty
  (`netty-common` → `Unsafe::allocateMemory`, gateway). The fix is an OTel / Netty version bump once they migrate off
  Unsafe; the day the JDK removes it, **every service stops booting**. Our own code is clean
  (`-Xlint:deprecation` reports zero deprecated-API use).
- **JEP 472 restricted native access -- resolved, kept as the pattern.** The gateway launches with
  `--enable-native-access=ALL-UNNAMED` (compose + chart). It is the template for the item above: the JVM says what it
  will break and when, in the log, and nobody reads it.
- **Java version drift: the build targets 24, the runtime is 25.** `pom.xml` sets `<java.version>24</java.version>`
  while the images are `eclipse-temurin:25-jre`. It works (24 bytecode on 25) but the two must move together -- this
  exact gap let the Java-25 JEP-472 behaviour arrive unannounced. Settle on one.
- **Floating image tags -- the build is not reproducible.** `redis:8` (a moving major) and
  `redis/redisinsight:latest`. Pin them, as every other image already is.
- **The BFF (Node) is a major behind on OpenTelemetry, worse on the exporter.** `@opentelemetry/*` core/sdk 1.30.1
  vs 2.9.0, and `exporter-trace-otlp-http` 0.57.2 vs 0.220.0 -- the component that ships the BFF's spans, where a
  protocol change breaks the first hop of every trace. Also `express` 4 → 5 and `http-proxy-middleware` 3 → 4 -- and
  that proxy is the `/api/*` token-relay path, so a silent change there is security-shaped.
- **Exporter-vs-target coupling (generalised).** A postgres-exporter pinned against a PG shape the DB had moved past
  fails on every scrape, silently, if no panel uses those metrics. The class is general: every exporter is coupled to
  the version of the thing it observes, and nothing checks it -- any Postgres / KeyCloak / ActiveMQ upgrade must
  re-verify its exporter.
- **Infra images to bump as one set** (all pinned and working; listed so it is one task, not several): Grafana,
  Prometheus, Loki, Tempo, OTel Collector, Alloy, Kafka, KeyCloak, ActiveMQ, Postgres.

---

## New-feature directions

Bigger directions -- new capability rather than improving what exists. Any of them can become a sprint target, and
several can run in parallel depending on interest and contributors.

### CD-9 -- Persistence beyond JPA

- Move off JPA -- adopt **DABABERE** (DataBase BEan REpository framework); compare JPA vs DBBR.
- DB API isolation: CRUID vs procedural APIs, DB messaging patterns.
- Use OpenAPI to generate the DB API -- model + queries + repository classes.
- Extend the RDBMS targets beyond Postgres / Oracle: MySQL, MS SQL, Sybase.

### CD-10 -- UI improvements (explorer + ui.lib)

- Nested-tree implementation of the same components (removes the block layout).
- Filtering / search and pagination for list views and tree views.
- An **Options / Settings** item in the logon submenu for user-level preferences: reset stored resize cookies,
  show/hide "deleted" entities, force bizTree cache validation.
- A **permission** sysadmin tool (manage roles and permissions).
- A **custom-parameters** sysadmin tool (manage the custom-field dictionary -- the entity dictionary's custom side).

### CD-11 -- pacMan → pacMaxRay (event-driven account processor)

The accounting engine redesigned around per-account concurrency and an open, messaging-first surface:

- **pacMax + xy-/xx-Ray** -- a multithreaded, event-driven account-transaction processor.
- One **virtual thread per account**, a queue as the entry point to each account; an **LMAX ring (Disruptor)**
  distributes events from the messaging bus to the account thread (multi-producer → multi-consumer).
- An **object DB** as the single place for account-state synchronization; an M2M snapshot schema.
- Replication thread(s) for account-change events (logs, account, transaction); `xyRay → Kafka (or any fan-out
  stream) → xxRay → (esq2025, logs, transactions)`.
- **Messaging-only access to pacMax**, and a **WebSocket / async-media facade** (open architecture) as its front --
  the BFF gaining a direct REST-messaging route, WebSocket first.

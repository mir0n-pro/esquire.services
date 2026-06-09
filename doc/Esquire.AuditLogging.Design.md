# Esquire Audit Logging — Infrastructure Design

> **Status: option (b) BUILT (v1.2.7) — design + post-build verdicts.** The audit option
> space and rationale live in [Esquire.AuditLogging.md](Esquire.AuditLogging.md) (the *saga*); this
> document specifies the **producer facade, the audit-event/message structure, the identity model,
> the per-operation workflow, and the transport** — originally the build spec for #4, now the
> declaration-of-use for the implemented infrastructure.
>
> The in-process **(b)** path (enyMan + pacMan + keySmith) is implemented and smoke-proven against a
> trigger-free Postgres. **§10 records the post-build design-review verdicts** (what holds, what is a
> deliberate (b)-only trade, what is deferred to (c)/(d)). Items still open for the (c)/(d) bus path
> are marked *(open: c/d)*.

---

## 1. Scope

The saga's options are: **0** (no store), **a** (sync triggers), **b** (local async), **c**
(distributed / xx-Rod), **d** (streamed doc), **e** (CDC). This document designs the in-framework
path — **(b) → (c)** — as **one producer design** that serves both: the **(b)** sink is in-process
(JDBC to a log DB), and **(c)** adds the dedicated bus + the xx-Rod consumer with no producer
change. We start on **enyMan** (the most complex producer); the same facade is later instantiated
in pacMan and keySmith.

Out of scope here: trigger removal (#1/#2, done), option (0) default behaviour, CDC (e), Redis (d).

The five guiding decisions, condensed:
1. **Audit must never block business** — capture inside the transaction, flush *after* commit.
2. **Reuse the entity (`UE`) message structure** — a light extension, not a new protocol.
3. **Full snapshot on CREATE/UPDATE; `id`+`kind` only on DELETE.**
4. **One uniform shape over entity / sub-entity / custom-param** — no special cases.
5. **Order-independent, append-only** — the consumer is embarrassingly parallel.

---

## 2. The producer — `xy-Rod`

> **Rod = RoD = Relay of Data.** The xy/xx-**Rod** couple is a generic entity **fan-out** substrate:
> the **xy-Rod** producer captures every committed (sub)entity change and relays it to one or more
> **sinks**; the **xx-Rod** consumer (option c) relays it onward to its resting place. **Audit is the
> first sink.** Replication, search-index feed, external webhook, and cache warm are future sinks on
> the *same* producer — they plug in behind `RodSink` without touching the write sites. So the
> substrate types carry no "audit" in their names; `AuditSink` is one concrete `RodSink` impl,
> `ReplicationSink` a future sibling. There is **no Rod-owned context type**: the audit triple
> (`crl_id`/`req_id`/`uid`) already lives in the v1.2.7 **`EsqRequestContext`**, which the Rod reads
> via `RequestContextUtils.getContext()`.

A facade in `common`, instantiated once per asset-updating service. It is the **xy (producer)**
half; the **xx (consumer)** half is the `XXRod` worker pool, and each `kind` is applied by an
`IRodRepository`.

> **As-built note.** The original design's `RodSink` (a `flush(List<RodEvent>)` strategy) and the
> `PooledQueueRig` were **subsumed by `XXRod`** during the build (mir0n, 2026-06-04): there is **one
> queue** — the xy-Rod's single-worker `BoundedQueueRig` — and the xx-Rod is *nothing but* a
> concurrency-bounded worker pool that is *called* (no second queue). Per-`kind` work is an
> `IRodRepository.apply(RodEvent)` resolved through a `RodRepositoryRegistry`. The audit **sink** is
> not a `RodSink` impl but the `AuditLogWriter` an `IRodRepository` lambda delegates to. The generic
> substrate lives in **`common.xrod`**; the audit specifics in **`common.audit`**.

```java
package pro.mir0n.esquire.common.xrod;

/** Producer facade -- one instance per service (enyMan, pacMan, keySmith). Buffers each row-change in
 *  the current tx; flushes the batch AFTER commit through a single-worker BoundedQueueRig to a
 *  dispatcher ((b) XXRod::submit ; (c)/(d) bus send). */
public final class XYRod {
    public boolean isEnabled();          // disabled -> post() is a cheap no-op (option 0)
    // self-guarding builder used at the write site: builds the body via IMappable.fillMap ONLY when
    // enabled and only for CREATE/UPDATE (DELETE carries id+kind in the header -> empty body):
    public void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source);
    public void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String,Object> body);
    public void post(RodEvent.Op op, int kind, String entityId, String subId);   // no-body (DELETE)
}

/** The xx-Rod: a Semaphore(poolSize)-bounded worker pool with NO queue of its own. submit() is called
 *  by the xy-Rod worker (b) or the bus consumer (c); it resolves the kind's repository and applies. */
public final class XXRod { public void submit(RodEvent event); }

/** The ONLY service-specific piece: how to apply one event to its *_log table. Registered by kind in a
 *  RodRepositoryRegistry; must be thread-safe (the pool applies distinct events concurrently). */
public interface IRodRepository { void apply(RodEvent event); }

// No Rod-owned context type. The audit triple (crl_id / req_id / uid) is the v1.2.7
// EsqRequestContext (common.backend.service), read via RequestContextUtils.getContext().
```

The **body is built by the entity itself** via `IMappable.fillMap(Map)` (a JPA-layer capability;
`EsqEntityJpa` emits `name`/`desc`/`parentId`, concrete entities override to add their own) — so the
producer carries **no domain field names** and there is no reflection. The audit sink binds that body
straight to vendor-keyed SQL (`AuditLogSql` — Postgres `INSERT … ON CONFLICT DO NOTHING` / Oracle
`MERGE`) via `AuditLogWriter`; `AuditRod.build(...)` wires the registry + XXRod + XYRod and chooses the
log datasource (shared service DB, or a dedicated Hikari pool for phase 2/3).

- **Enabled flag** = whether any sink is wired. No sink ⇒ `post()` is a cheap no-op (option 0). It
  also **gates the DELETE path** (§6).
- **Context** (`crl_id` / `req_id` / `uid`) is read from the **unified `EsqRequestContext`** (via
  `RequestContextUtils.getContext()`) — not repeated at each call site, and **not a Rod-owned type**.
  The v1.2.7 context unification captures it once per request and re-establishes it on worker threads
  (the move worker hydrates the holder), so the Rod gets the right context on whatever thread it runs.
- **Naming.** `Rod` = RoD = **Relay of Data**. Prose: `xy-Rod` (producer) / `xx-Rod` (consumer).
  Classes: **`XYRod`** (producer) / **`XXRod`** (consumer). Package + eventual standalone service:
  **`x-rod`** — the couple family name.

---

## 3. The Rod event / message (`RodEvent`)

The `RodEvent` **is the entity `UE` message** (`EsqMsgConstants`) with **three optional header
fields added** and a **full body**. No structural fork; the cache-broadcast simply omits the
optional fields. The same event feeds any sink (audit today, replication later).

### Header

| Field | Source | Notes |
|---|---|---|
| `EventType` | `FIELD_EVENT_TYPE` | `C` / `U` / `D` (the op) |
| `EntityKind` | `FIELD_ENTITY_KIND` | the **(sub)asset kind** — routes to the `*_log` table |
| `EntityID` | `FIELD_ENTITY_ID` | the **owning entity** id (`usr_pk` / `org_pk` / acct id) |
| `sub_id` | `FIELD_SUB_ID` **(new, optional)** | the sub-row's own id; **absent** for the entity itself |
| `actionTime` | new, optional | **`long` epoch-ms, captured at COMMIT** — the audit "when" → `*_log.action_ts` |
| `SendingTime` | `FIELD_SENDING_TIME` | when the message was **built/sent** (flush) — transport metadata, **distinct** from `actionTime` |
| `CorrelationID` | `FIELD_CORRELATION_ID` | `crl_id` (already a header field) |
| `RequestID` | `FIELD_REQUEST_ID` | `req_id` (already a header field) |
| `uid` | `FIELD_UID` **(new, optional)** | the acting user |
| `ApplMsgID`, `BusID`, `ServiceID`, `MsgType`, `MessageEncoding` | existing | envelope; `BusID`/`ServiceID` name the **audit channel** (§8) |

The audit triple (`crl_id`/`req_id`/`uid`) is **header**, not body — it is infra metadata, not entity
data. (`crl_id`/`req_id` are already header fields on every message; `uid` is the one genuinely new
field — today it lives only in the `*_uid` columns, never in a message.)

### Body (`Text`, JSON)

- **Strictly (sub)entity *data* fields** — no audit triple, **no `path`** (this phase).
- **CREATE / UPDATE → the FULL row** (all data columns of the changed row's final committed state).
- **DELETE → empty** — `id` and `kind` are already in the header; the deleted row's last full state
  is recoverable from its prior CREATE/UPDATE log entries.

Because the body is the full row, it **includes the row's own id and kind columns** (the pk and et-pk
data fields) — so the body is a **complete, self-contained record** of the (sub)entity. The header's
`entity_id` / `kind` / `sub_id` *duplicate* that identity, so the consumer can **route and dedup from
the header alone** without parsing the body — but the body is not lacking it; it could place itself.

`path` is skipped this phase deliberately: it keeps the producer fully generic (it removes the only
extra read — the `esq_entity_path` lookup the old org/user/account triggers did). The `*_log.*_path`
columns go NULL for new writes; path stays recoverable for reporting via a join to `esq_entity_path`.

---

## 4. Identity & routing — the uniform model

> The entity / sub-entity structure and the `kind` codes are defined canonically in
> [Object.Kind.enum.md](Object.Kind.enum.md) (the kind enumeration, incl. the sub-entity kinds
> 988–998) and [DatabaseDictionary.md](DatabaseDictionary.md) (§2 *Entity Structure* and the `*_log`
> tables). This section maps that structure onto the audit identity `(entity_id, kind, sub_id)` — it
> does not redefine it.

```
entity_id = the OWNING entity id        (usr_pk / org_pk / acct)
kind      = the (sub)asset kind         -> routes to the *_log table
            (a person record carries its OWN sub-kind here, e.g. 992/994/996 = primary/secondary/joint)
sub_id    = a discriminator, present ONLY when (entity_id, kind) is not unique on its own
            -- i.e. multiple rows of one kind under an owner:
               ad_pk (address) | par_name (param) ;  null otherwise (entity, person)

   row identity     = (entity_id, kind, sub_id)
   footprint group  = (crl_id, entity_id)            -- the owner's whole footprint in an operation
   dedup key        = (crl_id, entity_id, kind, sub_id)   -- exactly-one row per (operation, row)
```

`sub_id` is needed **only when `(entity_id, kind)` does not already pin a single row** — i.e. when an
owner can hold *several* rows of the *same* kind. That is **address** (a user has many addresses →
`sub_id` = `ad_pk`) and **custom param** (many params → `sub_id` = `par_name`). It is **not** needed for
the entity itself, nor for **person**: a person record declares its own sub-kind in the `kind` field
(992/994/996…), so `(usr_pk, kind)` already identifies it — `sub_id` is `null`.

### Asset map

| Asset | kind | entity_id | sub_id | `*_log` | sub_id → column |
|---|---|---|---|---|---|
| org / usr / acct | (existing kind) | own id | `null` | `esq_org_log` / `esq_user_log` / `esq_account_log` | — |
| person | the person sub-kind (992/994/996…) | `usr_pk` | `null` | `esq_person_log` | kind → `pel_kind` |
| address | reserved ADDRESS | `usr_pk` | `ad_pk` | `esq_address_log` | `adl_pk` |
| custom param (user) | **USR_PARAM** (new) | `usr_pk` | `par_name` | `esq_usr_par_log` | `uprl_par_name` |
| custom param (org) | **ORG_PARAM** (new) | `org_pk` | `par_name` | `esq_org_par_log` | `oprl_par_name` |

Reserved sub-asset kinds for person (its 992/994/996… sub-kinds) and address already exist and are
reused for logging — the person sub-kinds route to `esq_person_log`. **`USR_PARAM` / `ORG_PARAM` are
the two new reserved kinds** — two, not one, because `esq_usr_par`/`esq_org_par` are two tables and a
global `entity_id` can't tell owner type; this keeps routing **header-only**.

In short — **`kind` selects the table, `sub_id` selects the row within it.** Every asset's `kind` maps
to exactly one `*_log` table (a `kind → table` registry); `sub_id` is only needed to pick the row when
an owner holds several rows of that kind (addresses, params).

### What each entity owns (concrete)

```
user                                entity_id=usr_pk  kind=USR         sub_id=null      ( id = usr_pk )
 ├─ person  [many, per kind]        entity_id=usr_pk  kind=992/994/996 sub_id=null      ( id = usr_pk + person_kind )
 │    └─ address  [2 per person]    entity_id=usr_pk  kind=ADDRESS     sub_id=ad_pk     ( id = ad_pk / ad_pk_biz )
 └─ param   [many, per name]        entity_id=usr_pk  kind=USR_PARAM   sub_id=par_name  ( id = usr_pk + par_name )

office (org)                        entity_id=org_pk  kind=ORG         sub_id=null      ( id = org_pk )
 └─ param   [many, per name]        entity_id=org_pk  kind=ORG_PARAM   sub_id=par_name  ( id = org_pk + par_name )

account                             entity_id=acc_pk  kind=ACCT        sub_id=null      ( id = acc_pk )   -- single entity, no sub-rows
```

Everything an entity owns carries the **top entity's `entity_id`** (`usr_pk` / `org_pk` / `acc_pk`), so
`(crl_id, entity_id)` gathers that entity's *entire* footprint for an operation. For the **user** that's
the user + all persons + all params + all addresses (addresses physically hang off the *persons*, but
`ad_pk` is unique so it pins the right one — the person-link is the FK chain). An **office (org)** owns
only its **params**; an **account** is a **single entity** with no sub-rows. `auth` is **not** here — it
is the access profile, owned by keySmith.

> **Note on `esq_address_log`:** it has no owner column — it stores `adl_pk` (= `sub_id` = `ad_pk`),
> not `usr_pk`. So for that table, `entity_id`(owner) lives on the message only; `(crl_id, ad_pk)`
> already dedups it (ad_pk is unique). The owner is **not denormalized** into the address log — by
> design, the owner link is the FK chain `address → person → user`.

---

## 5. Lifecycle

```
post(e) … post(e)          // buffered in the tx (any thread); ctx from EsqRequestContext on first post
   │                       // buffer is keyed by (entity_id, kind, sub_id), LAST-SNAPSHOT-WINS
   ▼
[ entity tx commits ]      // <- stamp ONE commit-time actionTime for the batch
   │
afterCommit → sink.flush(batch)     // OUT of the entity tx ; b: own JDBC tx -> log DB
```

- **Coalesce within the tx.** enyMan may write the same row twice in one request (`insertPerson`
  then `updatePerson`); those are intra-tx intermediate states that never commit independently. The
  buffer keyed by `(entity_id, kind, sub_id)` with last-snapshot-wins yields **one event per row per
  transaction = its final committed state**. This matches the commit-time/committed-state rule and
  keeps `(crl_id, entity_id, kind, sub_id)` a single entry — so `crl_id` is a clean correlation key
  for debugging.
  - Op precedence per key: any insert → **CREATE**; only updates → **UPDATE**; ends in delete →
    **DELETE**; created **and** deleted in the same tx → **emit nothing** (the row never committed).
- **Flush is post-commit, out of the entity tx.** In-tx flush is **rejected**: it would let an
  audit-write failure roll back the business operation (the trigger's in-transaction-coupling con we
  just removed), and it holds the DB connection / extends the tx for an unrelated task.
- **`actionTime` (commit) ≠ `SendingTime` (flush).** The audit's semantic time is commit; the send
  time may drift if the sink retries.
- **Loss window:** because the flush is post-commit, a crash between *commit* and the sink durably
  accepting the batch loses those events. Inherent to async audit; acceptable. *(open: an outbox —
  event row written in the business tx, relay ships it — closes it at the cost of one in-tx write;
  opt-in for strict-compliance deployments only.)*

---

## 6. Per-operation workflow (enyMan)

> Convention: **user profile ≠ access profile.** enyMan's profile path is `user / person / address
> (/ usr_par)`; `auth` is the **access profile**, owned by **keySmith**. They never occur in one
> request and are never shown together.

- **CREATE** — one full-snapshot event per touched row: entity_path is not audited (no `*_log`);
  user/person/address/usr_par (and the initial `auth` row, which is keySmith's asset but created here
  at user-create). `op = CREATE`.
- **UPDATE** — one full-snapshot event per *changed* row, carrying the **final committed state**.
  Since the named-query writes touch only changed columns, the producer must **materialize the full
  row** (load-full + apply, or re-`SELECT` at post) — one extra full read per audited row on updates.
- **DELETE** — the enabled flag gates the path:
  - **enabled** → enumerate the child **ids** (pk-only, cheap — *not* full rows), `post({op=D, kind,
    id})` per deleted row, then delete. Coverage is preserved; payload is `id`+`kind` only.
  - **disabled** → plain DB cascade, no audit.
  - Body is empty (`id`+`kind` in header). `person`/`auth` ids = `usr_pk` (known); `address`/`account`
    need a pk-only lookup. Params cascade with the owner → **no per-param delete events** (the owner's
    DELETE implies them).
- **MOVE** — audit **only the parent-reference update** (`org_org_pk` / `usr_org_pk`, old → new); the
  `entity_path` rewrites are **not** audited (the move broadcast carries the path; `entity_path` has
  no `*_log`). One audit event per move. Runs on the queue-worker thread; context comes from the
  unified `EsqRequestContext` (the move worker hydrates the holder from the queued item — crl/req/uid
  do not ride MDC into the worker), read via `RequestContextUtils.getContext()`.

---

## 7. Transport & consumer

- **(b)** — `AuditSink` is in-process: drains the batch → JDBC insert into the `*_log` tables. No bus,
  no JSON.
- **(c)** — a **dedicated, durable audit QUEUE**, single-purpose (audit only), **separate** from
  `esquire.entity.broadcast`. A **queue**, not a topic, because audit is **fan-in** (every producer
  instance → one sink), and a queue persists + buffers + load-levels — and needs no durable-sub
  `clientId`, so it **dodges the rolling-update `clientId` trap**. New destination + `BusID`/`ServiceID`
  in `EsqMsgConstants`, parallel to `TOPIC_ENTITY_BROADCAST` — same format, separate channel.
- **Consumer = a redundant set of xx-Rods** — k8s replicas all competing on the queue. HD comes from
  **redundancy**, not a single durable box: a pod dying just shifts its share to the others — no stall.
  Because each message is a self-contained full snapshot and audit is pure append, processing is
  **order-independent**, so competing/parallel consumers are safe with **no per-key assignment** (unlike
  the cache-sync path, which is read-modify-write and *must* be parents-first ordered). This HD shape is
  essentially **free**: the JMS queue's competing-consumer semantics + k8s `replicaCount` give it with no
  custom coordination (and no `clientId` to deadlock rollouts). Each replica may still run an internal
  worker pool for per-pod throughput. At-least-once; **ack only after the log row commits** ⇒
  consumer/DB downtime holds messages in the queue, no loss; the dedup unique constraint (below) settles
  any redelivery/concurrency race across replicas.
- **Dedup / exactly-one:** the key `(crl_id, entity_id, kind, sub_id)` maps to a per-table unique
  constraint `*_crl_id + the row's own pk` (e.g. `esq_org_log(orgl_crl_id, orgl_pk)`,
  `esq_usr_par_log(uprl_crl_id, uprl_usr_pk, uprl_par_name)`, `esq_address_log(adl_crl_id, adl_pk)`).
  A redelivered message collides and is dropped ⇒ exactly one log row per (operation, row).
  **This is a (c)/(d)-only concern.** **(b) has no redelivery — one in-process delivery per committed
  change — so there are no duplicates by definition, and the `*_log` tables are deliberately left
  index-free** (PK-less append). The Postgres `ON CONFLICT DO NOTHING` in `AuditLogSql` is therefore a
  forward-compatible safety clause that is *inert* under (b) (no unique constraint to trip) and *active*
  once (c)/(d) lands; the Oracle `MERGE` dedups on its `ON` predicate regardless. **The unique-index
  add belongs to the (c)/(d) sprint** (when the bus introduces at-least-once redelivery), not to (b).
  See §10 (verdict B1). *(open: c/d — add the per-table unique index, or consumer-side dedup.)*

```
N durable producers  ──▶  1 dedicated durable audit QUEUE  ──▶  xx-Rod replicas (HD)  ──▶  log DB
 (enyMan×M, pacMan,        (persistent, fan-in,                 (k8s replicas, competing
  keySmith)                 no clientId)                         consumers, order-independent)
```

---

## 8. Worked example — custom param (profile side; auth shown separately, never together)

A profile request sets the custom param `theme = "dark"` on user `12345`:

```
RodEvent
  header:  { op=U, kind=USR_PARAM, entityId="12345", sub_id="theme",
             actionTime=<commit ms>, crl_id, req_id, uid }
  body:    { par_et_pk: 34, value: "dark" }          // par_name is the sub_id; value+par_et_pk are data
  ⇒ esq_usr_par_log: U, uprl_usr_pk=12345, uprl_par_name="theme",
                     uprl_par_et_pk=34, uprl_value="dark", + uprl_crl/req/uid + action_ts
```

Three params changed in one request → three such events (the coalesce key includes `sub_id`, so
distinct params stay separate). `esq_parameter` metadata (type/label/default) is reference data —
never shipped in the event.

---

## 9. Open items

- `path` skipped this phase (recoverable via `esq_entity_path` join).
- Outbox — opt-in for strict zero-loss; not the default. *(see §10 verdict B2 — the residual loss
  window is inherent to async (b) and cannot be closed in-process; (f)/(0) then (b) is the order.)*
- ~~The `(b)` `AuditSink` source→`*_log` column mapping registry (mechanical `org_`→`orgl_` prefix
  swap).~~ **Resolved at build time differently:** the entity fills its own body by **property name**
  via `IMappable.fillMap`, and those names bind straight to the `:param` names in the vendor-keyed
  `AuditLogSql` — no prefix-swap registry, no reflection (see §2, and §10 verdict M4).
- `MsgType` value for log messages — reuse `UE` on the audit channel, or a log-specific marker.
  *(c/d only.)*

---

## 10. Implementation review — verdicts (v1.2.7, post-build)

After option (b) was built and smoke-proven, the solution was reviewed (good / bad / missed). The
findings and the rulings on each are recorded here so the trade-offs are part of the spec, not folder
lore. The headline: **nothing needs to change for (b)** — the items below are either deliberate (b)
trades, or work that belongs to the later (c)/(d) bus sprint.

### What holds (the strong parts)

- **Transaction discipline.** `post()` buffers in a per-tx `ThreadLocal`, registers exactly one
  `TransactionSynchronization`, and emits only in `afterCommit()`; the buffer is wiped in
  `afterCompletion` on commit *or* rollback. A rollback emits nothing, the audit write never sits in
  the business transaction, and nothing leaks onto a pooled request thread. This is the core
  decouple-from-triggers requirement, met.
- **Self-contained, off-thread-ready events.** One commit-time `actionTime`, the audit triple
  snapshotted into the `RodEvent` — so the same event already serializes onto the (c) bus with no
  request context downstream.
- **Generic substrate / audit sink split.** `common.xrod` (generic fan-out) vs `common.audit` (the
  first sink); `IMappable` at the JPA layer so entities depend *downward*. Future sinks (replication,
  search-index) plug in with no write-site change.
- **Security hygiene.** `EsqAuthJpa` carries only the managed, non-secret auth fields; the security
  question/answer are never placed in the body and never logged.

### Verdicts on the findings

| # | Finding | Verdict |
|---|---|---|
| **B1** | Postgres `ON CONFLICT DO NOTHING` is inert — the `*_log` tables have no unique index on the dedup key. | **By design for (b); a (c)/(d) task.** (b) has no redelivery ⇒ no duplicates by definition ⇒ no unique index needed. Oracle `MERGE` dedups regardless. Add the per-table unique index in the (c)/(d) sprint when the bus introduces at-least-once delivery. See §7. |
| **B2** | Audit is best-effort/lossy: a crash between commit and the async apply loses the event; a saturated feed queue drops with a `warn`. | **Correct and unavoidable in-process.** No in-process design can close this *even in theory* — the order is always **(f)/(0) baseline → (b)**, and zero-loss needs the outbox/durable-queue of (c)/phase 2-3. Accepted. |
| **B3** | The post-commit `feed.put()` runs on the request thread and can block; in `shared` mode the apply pool competes for business DB connections. | **A con of (b) only**, inherent to in-process. Note: (c)/(d) have an analogous failure when the comms channel is fully broken — it just moves to the bus. Accepted as the (b) trade. |
| **B4** | A no-op save (nothing changed) still posts an `UPDATE`, writing a `*_log` row. | **Not a real problem.** Fixing it adds code for little gain; revisit only if it ever becomes *the* issue. |
| **M1** | Denied / failed attempts are not audited (denials throw before `post()`). | **Marked for a SEPARATE sprint.** Not a gap in (a)/triggers either (they too see only committed rows). Resolve later via a dedicated x-Rod "access-denied logging" component or a uniform denied-log pattern across services. |
| **M2** | Move audits only the moved node's parent-ref, not descendant `entity_path` rewrites. | **Not needed.** Tracking the parent reference is sufficient for audit. By design (§6). |
| **M3** | No dedup regression test. | **Folds into B1** — only relevant once (c)/(d) can redeliver; no test now. |
| **M5** | `esq_bank_info_log` is a declared-but-empty sink. | **Treat as an appendix — do not track it.** No code writes it; it exists for an undefined historical reason, like a vestigial organ. Not a gap. |
| **M6** | `actionTime` is app-node wall-clock ms — not monotonic across pods. | **Accepted.** There is no robust unique id that *also* guarantees ordering; ms granularity is not ideal but is good enough for audit. Queries sort by `action_ts`; `crl_id`/`req_id` disambiguate. |

### M4 — `TolerantSource` null-binding: a documented feature

`AuditLogWriter.TolerantSource` reports `hasValue == true` for every parameter, so a `:param` the body
map lacks resolves to **NULL** instead of failing. **This is a feature, not a bug:** it is what lets
the empty-body **DELETE** bind every data column to NULL without the writer needing to know each
table's column list. The body that *does* arrive on CREATE/UPDATE always carries the keys the SQL
needs, because each entity's `fillMap` is the single source of those names.

The only thing traded away is **fail-fast on a future name drift**: normally Spring JDBC throws
`"No value supplied for parameter X"` when a SQL `:param` has no value, which would catch a `fillMap`
property rename that forgot the matching `AuditLogSql` `:param` (or vice versa). With `TolerantSource`
that drift would instead log a silent NULL column, and `EntityFillMapTest` pins the `fillMap` keys and
`AuditLogSql` pins the `:param` names *independently* (no cross-check). This is fundamentally a **JPA /
Spring-JDBC binding limitation**, in the same "don't pre-solve" bucket as B4 — **left as-is, lowest
priority.**

- **Deep-troubleshooting option (not implemented, not required now):** add a `devLog.trace()` in the
  writer that emits the `:param` names that bound to NULL, so a drift shows up in a trace run.
- **Future resolution:** a future **v1.x** moves persistence under the in-house **DaBaBeRe**
  (DataBase-Bean-Repository) framework, where property↔column binding is owned end-to-end and this
  fail-fast gap is resolved structurally — making the `TolerantSource` workaround unnecessary.

---

## 11. SQL externalization — the `META-INF/audit/` spec folder

The `*_log` INSERT/MERGE statements are **not in code**. They live in a per-module spec folder
`src/main/resources/META-INF/audit/{postgres,oracle}.xml` (CDATA, keyed by sql-key); `common.audit.AuditLogSql`
is a **generic loader** (no SQL) that reads them from the classpath, and `common` itself stays SQL-free
(abstract/generic only). Each asset service ships **only the statements for the tables it writes** in (b)
— enyMan: org / user / person / address / params / account; pacMan: account; keySmith: auth — while the
standalone **xxRod** consumer ships the **full set**.

**Why a spec folder (deploy-time toggle).** The loader tolerates an absent resource (no file → empty map),
so audit is opt-in at **packaging / deployment time**, not only via the `enabled` flag: a setup that does
not need audit logging simply **omits the `META-INF/audit/` files** from the build/image — no audit SQL is
shipped and no code changes. The same seam lets a deployment swap the dialect (postgres/oracle) or restrict
which tables are logged by shipping a different `META-INF/audit/` set. SQL stays a **deployable spec
artifact**, decoupled from the code.

---

## 12. The xxRod consumer as a generic xRod host -- pluggable `IRodDirector`

The standalone (c) consumer (`xxRod`) is built as a **director-agnostic host**, not an audit-only service.
The transport (`RodAuditConsumer`, an `@JmsListener`) decodes each message into a `RodEvent` and hands it to
one `IRodDirector` -- the pluggable consumer-side strategy:

- `type()` -- the director's selection id.
- `init(Environment)` -- the director reads its OWN `xxrod.director.<type>.*` properties and wires its sink.
- `accept(RodEvent)` -- process one event.
- `shutdown()` -- release resources.

The active director is selected by **`xxrod.director.type`** (default `audit`); each impl is a `@Component`
gated by `@ConditionalOnProperty` on that key, so exactly one is wired. The generic **`RodDirectorHost`**
drives the lifecycle (calls `init()` at startup, `shutdown()` at stop) and knows nothing about any specific
sink. The audit director (`AuditRodDirector`) is the first impl: its `init()` reads `xxrod.director.audit.*`
(pool-size, virtual-threads) + the active vendor and builds the `AuditLogWriter` + `AuditKinds` registry +
`XXRod` pool.

**Adding a sink (replication, doc-DB) is code-local and config-selected:** drop a new `IRodDirector`
`@Component` gated on its own `type`, give its `init()` the `xxrod.director.<type>.*` it needs, and set
`xxrod.director.type`. The host, transport, codec, and queue are unchanged -- this is the seam the (d)
doc-DB sink plugs into.

---

## 13. Delivery semantics -- loss, duplication, and why dedup lives only on the bus path

Each option sits at a different point on the **loss vs duplication** spectrum, and only some can be made
zero-loss. This is what decides where a dedup mechanism is needed -- and it is the real reason the `*_log`
dedup unique index exists on the SQL/bus path but nowhere else.

| Option | Delivery semantics | Failure mode | Dedup mechanism | Zero-loss capable? |
|---|---|---|---|---|
| (a) triggers (DB, in-tx) | **exactly-once**, transactional | none -- atomic with the business write | N/A (no separate delivery) | **Yes, by construction** (it *is* the business tx) |
| (b) in-process (post-commit) | best-effort / at-most-once | **loss** (crash in the commit->write gap) | not needed (no redelivery -> no dups) | No |
| (c) bus -> xxRod | best-effort *as built*; **at-least-once capable** | loss now; dup once hardened | `*_log` unique index + `ON CONFLICT` / `MERGE` | **Yes** -- ack-after-write + the index |
| (d) redis stream (XADD) | best-effort / at-most-once | **loss** (fire-and-forget, no retry) | not needed; entry-ID if a consumer is ever added | No (as built) |
| (e) CDC (out of scope) | at-least-once | dup (connector replay) | downstream keys on the log offset (LSN / SCN) | Yes (the DB's own durable log) |

**The spine of it:**

- **(a) is the only transactionally exactly-once option** -- the trigger INSERT rides inside the business
  transaction, so commit gives you both rows and rollback gives you neither. Nothing to lose, nothing to
  dedup. Its price is structural (audit schema coupled into the business DB; can't see app-level
  crl/req/uid except via columns carried on every business row), not delivery.
- **(b), (c), (d) all write *after* the business commit**, off the request thread -- so all three can
  **lose** an event in the gap between commit and the off-thread write/publish (JVM/process crash). That
  post-commit gap is the inherent cost of decoupling audit from the business tx.
- **Duplicates only appear where something *redelivers*.** (b) hands off once in-JVM and (d) fires `XADD`
  once with no ack protocol -- neither can redeliver, so neither can duplicate, so **neither needs dedup**.
  Only the **broker** (c) can redeliver an un-acked message, and even there only in a narrow window
  (consumer crash / connection recovery before ack).
- **(c) is the one post-commit option that can be hardened to zero-loss**, and that is its whole reason to
  exist over (b). Switch xxRod to ack **after** the `*_log` write (CLIENT_ACKNOWLEDGE or a transacted
  listener): a crash between write and ack now triggers a **redelivery** instead of a loss -- at-least-once
  -- and the dedup index makes the re-write idempotent, giving effective exactly-once. The index is the
  cheap (one DDL) prerequisite paid up-front so that hardening is a config flip, not a schema migration.
  It also covers the narrow redelivery window that exists even in today's best-effort mode.
- **You could run (c) in pure best-effort, like (d)**, and drop the index: ack-before-write +
  `maximumRedeliveries=0` (redelivery -> DLQ/discard) makes a crash *lose* the message instead of
  duplicating it. But that throws away the bus path's distinguishing capability -- it collapses (c) into a
  heavier (d). Note the difference in *how* each is duplicate-free: redis is dup-free **by construction**
  (no redelivery mechanism exists); ActiveMQ is dup-free only **by configuration** (left at its
  at-least-once default it duplicates).
- **(d) trades the zero-loss door for the fastest request path and the simplest topology.** Fire-and-forget
  `XADD` has no ack protocol, so there is nothing to harden -- to make (d) zero-loss you would have to bolt
  a consumer group on top, and *then* dedup on the **stream entry ID** (unique and stable across
  redeliveries -- a stable id JMS never gave us, so consumer-side dedup on Redis is actually simpler than
  on the bus).
- **(e) is zero-loss via the database's own log** (the WAL/redo is the durable source), at the cost of
  living outside the framework and seeing only committed rows (like triggers, no app-level crl/req/uid).

**Takeaway:** dedup is not a tax the broker imposes -- it is the *enabler* of the bus path's selling point
(recoverable, zero-loss-capable audit). It lives on (c) because (c) is the only decoupled option that can
be made not to lose; (a) needs none (transactional), (b)/(d) need none (no redelivery), (e) keys on the log
offset. Pick the option by the loss posture you actually want: **(a)** never-lose-and-on-the-tx,
**(c)-hardened** never-lose-and-off-box, **(b)/(c)-best-effort/(d)** lose-rarely-and-cheap.

---

## 14. Option (c) over Kafka -- transport, partitioning, and the consumer model

(c) keeps its shape -- producer -> bus -> xxRod -> `*_log` -- with the **bus transport made swappable**:
`...audit-logging.x-rod.bus.transport = activemq | kafka` on the producer and `xxrod.transport =
activemq | kafka` on the consumer. The codec (`RodEventCodec`), the director / `*_log` writer, and the
dedup index are all unchanged; Kafka slots in as another bus. The design notes below capture *how* to run
it well for an audit workload.

### 14.1 Partitioning -- key = none

Audit is **order-independent** (each event is an append to `*_log` with its own `actionTime`; you sort the
history on read), so we do not need Kafka's per-key ordering. Therefore:

- **key = none** (round-robin / sticky partitioner) -- maximum even spread across partitions, no hot
  partitions, full parallelism. **This is the default.**
- Rejected -- **key = entityId**: gives per-entity ordering we do not need, and risks a *hot partition* if a
  few entities dominate writes (a busy office/account pins its traffic to one partition). Cardinality is
  NOT the issue (100k entities just hash onto N partitions, millions of keys per partition are fine) -- the
  issue is write skew.
- Rejected -- **key = kind**: only ~8-10 distinct keys with **heavily skewed volume** (address/account
  dominate, auth is rare) -> guaranteed hot + idle partitions. The worst choice. (Per-kind *routing* to
  different sinks, if ever wanted, is **topic-per-kind**, not partition-per-kind.)

Partitions are the **unit of consumer parallelism and the cap on it**; size the partition count to the
maximum number of consumers (replicas) you would ever want. With key=none, partitions can be grown later
freely (no key->partition stickiness to preserve).

### 14.2 Consumer model -- one consumer per instance, scale by replicas

xxRod's consumer side is a **single consumer thread** (the `@KafkaListener`), matching the ActiveMQ
`@JmsListener` it replaces. Scale **out**, not **up**:

- **Do NOT raise `spring.kafka.listener.concurrency`** above 1 -- that puts N consumer threads inside one
  instance all calling `director.accept` on one shared pool (the shared-pool trap, below).
- **Add xxRod replicas** in the same consumer group instead -- Kafka distributes the partitions across the
  instances (competing consumers). N partitions -> up to N useful replicas, each owning a subset. This is
  also the horizontal-redundancy story (a replica dies -> its partitions rebalance to the survivors).

### 14.3 A dedicated worker pool per consumer

Each consumer must own its **own** `XXRod` write pool -- never share one across consumers. Reasons:

1. **Isolation** -- a slow/blocked partition must not steal write permits from the others (shared pool =
   head-of-line blocking across partitions).
2. **Per-partition offset correctness** -- to commit an offset honestly the consumer must know *its own*
   writes finished; a shared pool decouples completion from the committing consumer.
3. **Backpressure locality** -- a dedicated pool pauses only its own partition's polling.

The **process boundary gives this for free**: each xxRod instance has its own `AuditRodDirector` -> its own
`XXRod` pool -> its own Hikari connection pool. So "one consumer per instance" (14.2) *is* "one dedicated
pool per consumer". The only way to violate it is `listener.concurrency>1`; hence keep it at 1.

### 14.4 No internal queue -- the topic is the buffer

Unlike the producer side (which needs the bounded xy-Rod feed because the request thread cannot pause), the
consumer needs **no internal queue**: the **Kafka topic is the durable buffer**. The consumer pulls at its
own pace; the `XXRod` pool's semaphore backpressures `submit()`, which makes Spring Kafka **pause polling**
when the writers saturate; the unprocessed backlog stays in the broker, replayable by offset. Nothing
accumulates in memory.

### 14.5 Delivery -- best-effort now, zero-loss-capable

As built, the listener hands each record to the **async** pool and returns, so the **offset commits before
the write completes** -> best-effort (a crash between commit and write loses that record), the same profile
as the ActiveMQ path. The **zero-loss** upgrade is natural on Kafka:

- write **synchronously** in the listener, then **commit the offset after** the `*_log` write (ack-after-
  process / container ack-mode `RECORD` or `BATCH`) -> **at-least-once**;
- the `*_log` **dedup unique index** makes the redelivery idempotent -> **effective exactly-once**.

The cost: per-consumer throughput becomes one-write-at-a-time, so you lean on **partitions + replicas** for
parallelism rather than the in-instance pool. This is the (c) bus path's distinguishing value (recoverable,
zero-loss audit, see section 13) -- and Kafka makes it a config posture rather than a project.

### 14.6 Operational caveat -- DB connection budget

A dedicated pool per consumer means **total audit-DB connections = replicas x pool-size**. (The smoke hit
`too many clients already` from exactly this -- pools competing for a finite `max_connections`.) Size
`XXROD_AUDIT_POOL_SIZE` and the audit-DB `max_connections` together, or give each replica its own DB / front
the DB with pgbouncer.

### 14.7 Recommended configuration (audit)

> **key = none** | **N partitions** (= max replicas wanted) | **one consumer thread per instance**
> (`listener.concurrency = 1`) | **scale via replicas** (competing consumers) | **one dedicated `XXRod`
> pool per instance** | **no internal queue** (the topic buffers) | best-effort by default, **ack-after-
> write + dedup index** for zero-loss | **pool-size x replicas <= audit-DB connection budget**.

This keeps the single-consumer interface intact, isolates each partition, scales horizontally on the
partition count, and makes the zero-loss posture reachable per partition -- the reason to pick Kafka for (c)
over the best-effort ActiveMQ queue.

### 14.8 Why partitioning earns its keep -- the value case for Kafka

Partitioning *is* Kafka's sharding (the data is sharded across partitions, which spread across brokers).
The earlier sections cover *how* to run it; this is *why* it is worth the extra infrastructure over the
ActiveMQ queue. From most to least relevant for audit:

1. **Fan-out to many independent sinks (the decisive one).** One topic, **multiple consumer groups**, each
   receiving *every* record independently. The same `esquire.rod.audit` topic can feed **xxRod -> SQL `*_log`
   *and* Kafka Connect -> Redis *and* a future search index / cold store** -- all off a **single publish**,
   each sink its own group, each scaling its own consumers across the partitions. This is "audit as a
   first-class, multi-sink pipeline" and is **impossible on the ActiveMQ queue** (a queue delivers each
   message once, to one consumer). Demonstrated by the (d-k) variant (producer unchanged, a Connect Redis
   sink added as just another consumer group).

2. **Throughput / scale-out (the dial).** Partitions are the unit of parallelism at every stage: producers
   write different partitions concurrently, partitions distribute storage + load across brokers, and a
   consumer group runs up to N consumers for N partitions. Audit volume grows -> add partitions + add xxRod
   replicas -> near-linear scale, no code change.

3. **Replay / backfill (free with retention).** Partitions retain data, so a *new* consumer group can start
   at offset 0 and replay the whole history. Concretely: a sink added months later **backfills itself** from
   the retained topic; a corrupted/wiped sink is **rebuilt** by replaying; logic can be reprocessed without
   re-emitting from the services. (Set topic retention to the audit window you want replayable.)

4. **Ordering on demand (key choice).** Order is guaranteed within a partition, so the key picks the grain:
   `key=none` (our default -- order-independent, even spread) or `key=entityId` *only if* a particular sink
   needs per-entity order (e.g. a current-state materialization). Different sinks read the same topic and
   interpret order as each needs.

5. **HA via replication.** `replication.factor>1` places each partition's replicas on different brokers --
   a broker dies, no data lost, consumers rebalance to the survivors. (Replication is distinct from
   partitioning but uses the partition as its unit.)

**Sizing rules:** partitions ~= peak consumers-per-sink + headroom (a handful for dev; real concurrency in
prod). You can *add* partitions but not easily remove them, so leave headroom without over-provisioning
(too many -> rebalance storms, file-handle overhead, more end-to-end latency). More consumers than
partitions = idle consumers; more partitions than consumers is fine. Keyed -> watch for hot-partition skew;
`key=none` -> free even spread.

**Net for us:** the two leverage points we would genuinely use are **fan-out** (`*_log` + Redis + future
sinks off one publish, each scaling independently) and **replay** (add or rebuild a sink anytime from the
retained topic). Scale and HA are have-it-when-needed dials. None of these exist on the ActiveMQ queue --
they, more than the transport swap, are the case for Kafka under (c).

---

## Cross-references

- [Esquire.AuditLogging.md](Esquire.AuditLogging.md) — the option space (0/a/b/c/d/e) and the stance.
- [Object.Kind.enum.md](Object.Kind.enum.md) — the `kind` codes (entity + sub-entity kinds 988–998).
- [DatabaseDictionary.md](DatabaseDictionary.md) — §2 *Entity Structure* + the `*_log` tables.
- [Message.Structure.md](Message.Structure.md) — the `UE` entity message this design extends.
- [Messaging.md](Messaging.md) — the entity-broadcast and IAM buses (the audit queue is parallel to,
  and separate from, the entity-broadcast bus).

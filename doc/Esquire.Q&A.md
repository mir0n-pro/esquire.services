<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire -- Design Q&A

> **WORKING doc** -- a living log, appended as questions come up. The framework-wide Design Q&A for Esquire
> OVERALL: things that *sound like* defects on a first read of Esquire, and why each is a deliberate choice (or a
> non-issue in the actual usage model) rather than a bug. This is the Esquire-level Q&A; it is NOT tied to any
> single subsystem -- framework-wide questions live HERE. (A self-contained subframework keeps its own notes --
> e.g. the messaging bus has its own continuing-development doc, `Esquire.MessagingBus.ContinuingDev.md`.)

## Contents

**Start with [verify once, at the boundary](#the-principle-behind-many-of-these-answers-verify-once-at-the-boundary)** -- one decision that answers a whole class of "why is this not checked here?".

**And [a bad configuration stops; a bad message is recorded](#a-bad-configuration-stops-a-bad-message-is-recorded)** -- one rule that decides what a failure does.

Design questions grouped by context. Each group collects the "this *sounds like* a defect -- here is why it is a deliberate choice (or a non-issue in the actual usage model)" entries for one area of the framework.

1. [Entity model and lifecycle](#entity-model-and-lifecycle) -- How entities are edited, identified, and deleted -- and why the usual version-column / cascade prescriptions do not apply.
2. [Move ordering and the bizTree cache](#move-ordering-and-the-biztree-cache) -- Re-parenting a subtree (moving a branch to a new parent), the order events apply in, and the recoverable Taijitu cache that backs it up.
3. [Messaging bus](#messaging-bus) -- how ESQUIRE uses the x-rod bus: delivery guarantees on its channels and rod identity in its deployment. (Generic bus-framework Q&A: `Esquire.MessagingBus.Q&A.md`.)
4. [Audit trail](#audit-trail) -- How the *_log audit is keyed so a redelivered message still makes one row (idempotency), and what audit is on at deploy time.
5. [Security and authentication](#security-and-authentication) -- Token format and the trusted-perimeter model, onboarding credentials, and CORS.
6. [Accounting (the pacMan demonstration domain)](#accounting-the-pacman-demonstration-domain) -- What the demonstration accounting service deliberately simplifies, and where the line to a production ledger is.
7. [Database schema and migrations](#database-schema-and-migrations) -- The seed-plus-idempotent-forward-patch model, and why no migration tool is imposed on adopters.
8. [Frontend and UI](#frontend-and-ui) -- The UI library's deliberately fixed look, and the Explorer's status as a reference example rather than a product.
9. [Testing and QA](#testing-and-qa) -- Why shared types + shared constants + e2e are the effective contract at this scale.
10. [Deployment shapes and naming](#deployment-shapes-and-naming) -- Why the same service answers to two workload names across the classic and compact shapes, and what has to know about it.

---

## The principle behind many of these answers: verify once, at the boundary

*Read this first if an answer below looks like a missing check.*

**A boundary is where a thing is checked. Inside it, the check is not repeated.** That is one decision, applied
in four places, and most of the "why is this not validated here?" questions in this file are the same question
about one of them.

| the boundary | what it checks | what is trusted inside it |
|---|---|---|
| the gate | the token's signature, its claims, the realm role | a service reads `esq_uid` / `esq_rootpath` from the payload without re-verifying the signature |
| the channel | whether it guarantees delivery | a leg that is not durable accepts loss; a consumer is not given retry / park / DLQ machinery to compensate |
| the fan-out | nothing -- a broadcast has no acknowledgement by nature | a lost broadcast is a bounded window, healed by the night-watch, and the requirement is that it be COUNTED |
| the value | caller input, at the validator | a value the service itself produced is written as given -- validation is for what comes in from outside |

**Why once and not everywhere.** A second check costs what the first one cost, and it can disagree with it --
and a disagreement between two authorities is worse than one authority. Re-verifying a signature at every
service does not make the tree safer; it makes two places that can answer differently about the same token.
Running a generated value through rules written for caller input does not make it safer; it subjects a derived
name to a `personal` gate that was never about it.

**What it costs, said plainly.** Each of these reads as a missing check when the code is read from inside one
file. A signature never verified. A message that can be dropped. A value written without validation. From that
distance they are indistinguishable from an oversight, and they will be re-raised by every fresh reader --
three cold-pass audits have now raised all four. That is the cost of distributed computing: you cannot have
both one authoritative check and a check in every process, and choosing the first means living with code that
looks incomplete where it is merely downstream of the place that decides.

**So the answer to "X is not re-checked here" is to name the boundary that already checked it.** If no boundary
did, it is a defect. If one did, the absence is the design.

---
## A bad configuration stops; a bad message is recorded

*The second rule a fresh reader keeps re-deriving: what should a failure DO?*

Two kinds of wrong reach a running service, and they get opposite treatment:

| | what it is | what happens |
|---|---|---|
| **configuration** | the service was told to run in a way that cannot work | **fail fast** -- refuse to start, and name the setting |
| **message flow** | this one item could not be processed | **log it, count it, skip it** -- and keep going |

**A bad configuration stops** because it is wrong for every message, not one, and it is wrong before any traffic
arrives. Coming up anyway means running a service that cannot do its job while reporting that it can. Two
examples: auKeep has no second job, so with the audit bus disabled or the keep datasource absent it refuses to
start and the records stay on the durable queue until someone fixes it; a `retry-backoff` ladder that does not
parse throws rather than quietly degrading a live leg's retry policy to a one-second step. A ladder known to be
malformed is not something to retry on.

**A bad item is logged, counted and skipped, and the worker keeps going** because the next item is very likely
fine, and stopping the leg turns one bad message into an outage. What the rule demands instead is that it be
WRITTEN DOWN -- the log line and the counter -- so a silent drop never passes for a success. The failure that is
neither recorded nor thrown is the one this rule exists to forbid.

**SKIP means skip the whole item, not part of it.** Half-applying is worse than not applying: a clean absence is
visible to the next reader and repairable by the next sweep, while a half-written row looks finished. So a
handler that cannot place a node writes none of it, rather than writing what it can.

**So "why does this not fail?" and "why does this fail so hard?" have the same answer:** name which of the two
it is. A setting cannot be worked around; a message can be skipped.

---
## Entity model and lifecycle

*How entities are edited, identified, and deleted -- and why the usual version-column / cascade prescriptions do not apply.*

**Q1. There is no optimistic locking (a version check that rejects a save when the row changed since it was read) on
entity edits -- no version column / ETag / `If-Match` on `ESQ_ORG` / `ESQ_USER` / `ESQ_ACCOUNT`. Two edits of the
same entity at the same time look like a lost update: the first writer's change is silently overwritten, with no
`409` conflict returned.**

A. Not a defect in the Esquire model. A classic lost update needs three conditions to be true at the same time,
and Esquire removes at least one of them:

1. **Update requests carry only the changed fields**, not the whole entity. So a change to field X and a change
   to field Y do not overwrite each other the way a full-row rewrite would -- there is no read-the-whole-row /
   write-the-whole-row window in which one request's untouched fields overwrite another's.
2. **The usage model is one change per entity at a time.** The UI/API drives a single editor per node at
   human pace, so there is no second writer to lose against -- not even under multi-instance (more than one
   running copy of a service), because two copies only race if two requests hit the *same* entity in the *same*
   instant, which the model does not produce.
3. **Same-field last-wins is the intended result, and nothing is lost even then.** The one case that does
   resolve by last-wins -- two edits to the *same* field -- is the correct outcome, and the overwritten value is
   still kept: every field change is written to the **audit log** (the `*_log` tables, via the x-rod / keep
   stack), so the earlier value is recorded and can be recovered.

The generic "add a version column + `If-Match` + `409`" advice assumes full-row writes and concurrent
editors; Esquire has neither.

Note the account **balance** is not an exception. A balance is never changed by the entity update / save
path at all -- it is maintained only by the **accounting-transaction command**, a separate mechanism where
transaction **ordering** is enforced (the last transaction lands last). That is a different thing from an
entity field edit, so there is no lost-update case for balances either.

---

**Q2. The entity-id generator (`EntityIdGenerator`) packs a per-millisecond sequence into just 3 decimal digits, so
it can mint at most 1000 ids per millisecond per instance. Above that the sequence wraps and two ids collide --
isn't that a latent duplicate-PK (primary-key) bug?**

A. It is a real ceiling, and it is intended -- it sits about a thousand times above anything a single instance can
actually reach. The id is a compact decimal-positional **single `BIGINT`** (one 64-bit integer whose digit
positions each carry a part of the id):

```
id = (ms since the esquire era) * 10000  +  instanceNo * 1000  +  (sequence % 1000)
```

The bottom four digits are one instance digit (up to 10 enyMan copies, `0..9`) plus three sequence digits (up to
1000 ids per instance **per millisecond**). This shape is a deliberate choice, not an oversight: it keeps the
PK a plain sortable number that also serves as the `ESQ_ENTITY_PATH` shared key, and it keeps the numeric
globally-unique-PK guarantee -- a UUID or a central DB sequence would drop those properties and add coordination
between instances.

Why the ceiling is out of reach in practice: **on today's hardware a single enyMan instance cannot create
anything close to 1000 entities in a whole second, let alone in one millisecond.** Each CREATE is a full multi-step
transaction -- permission check, the PK + `ESQ_ENTITY_PATH` inserts, sub-entity rows (person / address / ...), the
audit post, and the entity broadcast -- and entity creation runs at admin / back-office pace, not as high-rate data
loading. So real throughput per instance today is well under 1000 per *second*; the collision point of 1000 per
*millisecond* is about 1000x higher. To reach it you would need 1000 committed creates inside the same 1 ms window
on one instance, far beyond what the create path does on current machines. (This is spare capacity, not a
permanent law -- if future hardware ever minted that fast, the sequence width would be revisited then; spreading
load across the 10 instance digits also multiplies the room.)

And if it somehow were reached, it fails **visibly, not silently**: a wrapped sequence would repeat an id already
minted in that same millisecond, so the second INSERT hits the primary-key constraint and is rejected -- a failed
CREATE, never a silently corrupted or overwritten row. The encoding also fails fast on the other axis: an
`instanceNo` outside `0..9` throws on the first mint (one instance digit only), and the sequence is forced
non-negative so an `AtomicInteger` wrap after 2^31 mints cannot borrow into the instance/time digits. Cap copies at
10; the per-millisecond ceiling needs no runtime guard because the workload cannot get near it.

---

**Q3. When you delete an org or a user, the service does not first check whether it still has children (sub-orgs,
users, accounts), and the cache delete is not recursive. Doesn't that risk orphaning the children or leaving the
tree inconsistent?**

A. No -- deleting a non-empty parent is prevented by the DATABASE, on purpose, so the service does not re-check it.

1. **The structural tree foreign keys are RESTRICT (the database refuses to delete a row that still has children).**
   `esq_account.acc_usr_pk -> esq_user`, `esq_user.usr_org_pk -> esq_org`, and `esq_org.org_org_pk -> esq_org` all
   carry NO `ON DELETE` clause (both Oracle and Postgres), so the default NO ACTION / RESTRICT applies. `deleteOrg`
   then throws, the transaction rolls back, and the entity-path row is never removed -- no orphan, no half-written
   state. (The satellite records a user OWNS -- auth, person, params -- are the opposite: their FKs are
   `ON DELETE CASCADE`, so they are removed together with the user, which is correct.)

2. **A service-layer children-exists check is deliberately NOT added.** It would add an extra query to EVERY
   delete, including the common leaf delete, to re-verify something the foreign key already enforces for free. The
   framework keeps the delete path to one strict rule -- attempt the delete and let the foreign key be the
   guard -- which keeps entity maintenance simple.

3. **The non-recursive cache delete does not matter here.** The database never lets a non-empty parent be deleted,
   so the cache is never asked to remove a node that still has children; any leftover drift is reconciled by the
   bizTree night-watch sweep (the background pass that re-checks the cache against the database).

An adopter who wants richer delete semantics -- a cascade delete, a soft delete, or a friendly "this still has
children" message instead of a raw constraint error -- adds it at their own business-rules layer. The framework
provides the safe minimum (the foreign key) rather than baking one deletion policy into every adopter.

---

## Move ordering and the bizTree cache

*Re-parenting a subtree (moving a branch to a new parent), the order events apply in, and the recoverable Taijitu cache that backs it up.*

**Q1. A CREATE that races a MOVE checks `inMove()` only AFTER it has inserted and broadcast. If the move finishes
before that check, the create gets no reconcile and its path is left stale -- doesn't the bizTree night-watch heal
it anyway?**

A. No -- and that is exactly why the check must not miss the create. Follow the sequence: the create reads the
parent's OLD path, the move rewrites that subtree in the DB (not seeing the not-yet-inserted child), then the create
inserts the child with the OLD path. The child's **DB row** is now stale. The reconcile is the only thing that
repairs it -- `processReconcile` fixes the DB (`updatePath`) and then rebroadcasts -- so if the create gets past the
move queue (no `CreateReconcileItem` enqueued), the DB stays wrong. The night-watch reconciles the bizTree CACHE
against the DB; when the DB itself is wrong, cache and DB agree on the wrong path and nothing heals (the same limit
noted above for a dropped move reconcile). So the create MUST be caught by the move queue.

The gate is the **"elastic end of move"**: `inMove()` stays true for a grace window (a short extra period) after
the last move drains (`enyman.move-queue.in-move-grace-ms`, default 200 ms; 0 disables). Without it, a move that
both ran and fully drained inside the create's read->check span would read `inMove()` false and the create would
slip through; the grace keeps the queue catching such a late create, so its reconcile still fixes the DB. It is a
best-effort rule, not a hard guarantee -- a create that stalls longer than the grace between its read and its check
would still miss -- but that is deliberate: a hard per-create guarantee (a move-generation token) only hardens the
LOCAL single-instance path, while real deployments are multi-instance (more than one running copy) where the
cross-instance create-during-move already has its own unconditional cover (the peer-create reconcile off the entity
bus, not `inMove()`-gated). 200 ms catches every realistic case; the rest (an extreme stall, or a total
broadcast-bus outage) falls to a later move or, once the DB is corrected, the night-watch. The grace is stamped when
the move drains the queue to zero (not at submit -- a queue-full rollback there must not erase a still-valid grace),
and only ever extends the window, never cuts it short.

---

**Q2. The entity-broadcast bus sets `concurrency: 1` (one in-order consumer). What keeps that order all the way
into bizTree's cache, and what happens if two events for the SAME entity arrive close together?**

A. The order is kept by design at every step, and the change number guards whatever the order cannot cover.

1. **`concurrency: 1` is REQUIRED by the topic, not a preference.** The entity broadcast is `pubSubDomain: true`
   (a topic), and the consumer is a `DefaultMessageListenerContainer` with `concurrentConsumers = concurrency`. On
   a topic, `concurrentConsumers > 1` makes EACH consumer session receive its own copy of every message -> the
   same event is applied N times (duplicate processing) -- Spring warns against it. So one consumer is mandatory
   for correctness, and it also delivers in order.

2. **The receive pool is 1 for the cache, so nothing re-parallelizes that order.** `ENTITY_BROADCAST_POOL_SIZE`
   is `1` in bizTree and gateWard: one thread carries each event from the consumer to the monad queue. More
   threads there would race each other to reach the queue, which is the only place the delivery order could be
   lost by our own hand. Keeping it at 1 costs nothing, because under the Taijitu director the pool applies
   nothing: `ATaijituRig.onEntityBroadcast` builds a `QueueItem` and offers it to both legs, and each leg's single
   worker does the apply through `_processItem`. Everything downstream of the pool is serial already.

3. **An out-of-order apply is refused, not tolerated.** Every event carries the change number of the row it
   describes, and the cache keeps the last number it applied per node. `MessageHandlerHub.dispatch` compares them
   and skips anything that is not newer, then stamps the number it just applied. So a late or repeated event --
   a broker redelivery, say -- cannot overwrite a newer one.

4. **The usage model makes the case rare, and the night-watch is the backstop.** Esquire's model is one
   modification per entity at a time, at human pace (see the optimistic-locking entry above), so two events for
   the SAME entity arriving together is not what it produces. And bizTree is a recoverable Taijitu cache: the
   night-watch sweep reconciles it against the DB (the source of truth) and heals any drift.

So the chain is ordered end to end -- one topic consumer, one receive thread, one apply worker -- with the change
number covering redelivery and the night watch covering the rest. That is why per-key affinity in the pool is not
needed.
---

**Q3. In bizTree's cache-apply path, `MessageHandlerHub.dispatch` catches a handler exception, logs it, and
returns -- so the event-batch still COMMITS with that one event not applied, instead of rolling back. Why swallow
the exception instead of handling the failure?**

A. Because a handler failing here is a should-not-happen condition, not an expected failure we plan for, and the
recovery for it already exists elsewhere -- the night-watch sweep.

1. **The failure is hypothetical.** A handler applies a broadcast that our own enyMan published AFTER its DB commit,
   keyed by an id we generated, into an embedded H2 cache. The parse cannot fail (we produced the id), and the
   cache write cannot fail under normal running (fresh unique key, parent already present, in-process H2). There is
   no realistic runtime path that throws.

2. **We do NOT build recovery machinery for impossible conditions.** Handling such an exception "correctly and in
   full" (compensation, retry, partial-write repair) is a lot of code, debugging, and testing whose OWN blind spots
   would be worse than the hole it guards. So the deliberate policy is: LOG it (app log, plus the develop log with
   the full stack) and count `outcome=failed` on the `esq.biz.tree.handler.dispatch.total` meter -- the maximum
   sane response -- then continue.

3. **The guard is the night-watch sweep.** If the impossible ever happens and the cache is left short an entity,
   bizTree is a recoverable Taijitu cache: the sweep reloads from the DB (the source of truth) and, under
   `onMismatch=SWAP` (shipped), heals the drift. Rolling the whole batch back on one swallowed error would instead
   discard the events that DID apply, for no gain.

So swallow-log-and-continue is intentional: no machinery for a condition that cannot occur, with the sweep as the
single, already-built safety net.

---

**Q4. A move (re-parenting a subtree) starts by locking a single sentinel row -- `SELECT ep_pk FROM esq_entity_path
WHERE ep_pk = 1 FOR UPDATE` -- which serializes EVERY move across all instances, even two moves in unrelated
subtrees. Isn't that a bottleneck? Shouldn't it lock only the affected subtree?**

A. It is deliberate. A move rewrites many rows by path prefix; two moves running at once could interleave those
rewrites or deadlock on overlapping ranges. Running every move one at a time (serializing) through one control row
makes that impossible with almost no code -- it mimics the classic OLTP (online transaction processing) "lock a
control row" pattern, done with one raw JPA `FOR UPDATE` call.

A finer, per-subtree lock would need overlap detection and deadlock avoidance -- heavy machinery that would spoil
the simple structure -- to speed up an operation that is rare, admin-initiated, and human-paced. So running all
moves one at a time is the right trade: trivial and correct, at no cost that matters. Moves do not run in parallel,
and nothing needs them to.

---

## Messaging bus

*How ESQUIRE applies the x-rod bus -- delivery guarantees on its channels, and rod identity in its deployment.
These are questions about Esquire's USE of the bus; questions about the generic bus subframework itself (config
resolution, subscriptions, the envelope, catalog validation) live in `Esquire.MessagingBus.Q&A.md`.*

**Q1. The bus consumers run `AUTO_ACKNOWLEDGE` (each message is acknowledged to the broker the moment it is
received) with a catch-and-log listener -- so a failed DB apply is still acked and LOST (no nack / redelivery /
dead-letter queue, DLQ). And the audit `INSERT .. ON CONFLICT DO NOTHING` has no explicit conflict target, so it
silently depends on a unique index existing on every `*_log` table.**

A. Two separate concerns, both already handled by design.

*Delivery loss is bounded per channel -- a generic DLQ is not the mechanism:*
- **Audit** -- best-effort *by design*: the documented async-audit loss boundary. The consumer acks on RECEIPT (the
  apply runs on an async worker pool), so a failed audit apply -- whether a broker-restart backlog drop OR a
  TRANSIENT, recoverable keep-DB blip while the broker is healthy -- is logged, not retried or redelivered. This is
  acceptable because the audit trail is not on the request's critical path AND the broker is non-persistent (it
  does not keep messages across a restart), so audit is already best-effort end to end. If a deployment ever wants
  durable, redelivered audit, the way to change it is a per-consumer ack-after-apply mode (the keep write is
  idempotent -- safe to run twice -- via `ON CONFLICT`, so redelivery is safe) -- not a generic DLQ.
- **Entity-broadcast** -- a lost apply leaves bizTree's *cache* stale, but the **Taijitu night-watch**
  anti-entropy (a background compare-and-heal pass) reconciles cache-vs-DB and heals it (the DB is the source of
  truth here; contrast the dropped
  *move* reconcile, where the DB row itself goes stale and the night-watch cannot help). The SAME heal covers a
  *missed delivery*, not only a failed apply: the entity broadcast is a NON-durable topic subscription (the broker
  is non-persistent), so a consumer that is disconnected -- a restart or a network blip -- does not receive whatever
  was published during the gap; those events are simply gone from the broker's view. Non-durable is deliberate, not
  a gap: a durable topic sub needs a stable per-subscriber `clientId`, which deadlocks a k8s RollingUpdate, and
  buys nothing against a non-persistent broker -- so the night-watch anti-entropy is the "no event loss" mechanism
  by design, and it heals a missed-during-disconnect event exactly as it heals a failed apply (see
  `Esquire.BizTree.md`, "Broadcast subscription is non-durable"). kcMaster's entity-broadcast consumer is only a
  race-8c SAFETY-NET (it parks a not-yet-created user's new path); the authoritative KC channel is the URQ
  `EVENT_UPDATE_PATH` request/response, so a missed broadcast there costs nothing.
- **R&R (KeyCloak request/response)** -- the one channel where lost delivery is a genuine reliability question;
  that is the R&R reply-tracking / timeout / replier-down work, tracked in
  `Esquire.MessagingBus.ContinuingDev.md`, not a generic DLQ.

*The `ON CONFLICT` target is deliberate.* The dedup unique indexes DO exist for all eight `*_log` tables
(`db.seed/.../dedup/all.sql`), keyed on the row plus its change number -- `(pk, change_no)`, or the row's
composite identity plus the number where a pk alone does not pin one row. `ON CONFLICT DO NOTHING` *without* a
named target is a forward-compatible clause on purpose: **active** (dedups at-least-once redelivery -> exactly
one log row) when the dedup overlay is applied (bus audit, option c/ck), **inert** when it is not. So it is not
a silent dependency -- it is an intended, documented overlay.

*The key is the row's own version, not the request.* A redelivered event carries the same change number,
collides, and is dropped; two real changes carry different numbers and both survive. The same change
re-announced under a different correlation id is caught as well, because the key names a version of a row
rather than an operation. And because every physical write carries its own version, the DB-trigger option and
the dedup overlay apply together -- each write produces a distinct key, so nothing collides.

---

**Q2. R&R rod-id uniqueness is unenforced -- rod-id defaults to `<app>.<instanceNo>` and there is no runtime check
that two rods don't share an id. A plain Deployment gives every replica `<app>.0`, so replicas would share the
reply selector and steal each other's replies.**

A. Not worth a runtime enforcement. rod-id is **unique by default**: the charts deploy every service as a
**StatefulSet**, so each replica gets a distinct ordinal (`<app>.0`, `<app>.1`, ...) and therefore a distinct
rod-id -- this is structural (the charts deploy StatefulSets), not a "run it as a StatefulSet" hope. You
*can* set rod-id manually in config, but that is a deliberate, not-recommended, expert override -- defining a
colliding rod-id by hand means you know what you are doing. A runtime fail-fast on duplicate rod-ids would need
real cross-instance coordination (to see another instance's id) -- a large mechanism for a case that does not
arise by default and only arises by deliberate misconfiguration. A large amount of work for a case that does not
exist.

(Note this covers ONLY the rod-id **uniqueness** question. The related R&R **reply-timeout / pending-request
tracking / replier-down detection** -- and the delivery-reliability piece -- are a separate, still-open question,
tracked in `Esquire.MessagingBus.ContinuingDev.md`.)

---

## Audit trail

*How the *_log audit is keyed so a redelivered message still makes just one row (idempotency), and what audit is turned on at deploy time.*

**Q1. The audit log dedups with `INSERT .. ON CONFLICT DO NOTHING`. Since the correlationId can be supplied by
the client, could a client reuse one across two real edits of the same entity and make the audit drop the second
record -- hiding their own change history?**

A. No. **The dedup key is the row plus its own change number, and a client cannot touch either.** The number is
raised by the service under the row's lock on every write, so two real edits are two versions and produce two
records no matter what correlation id arrives with them.

1. **Two distinct updates of one (sub)entity CANNOT collapse into one record.** The dedup key is per (sub)entity
   and carries that row's change number -- an org, but also its parameters, and a user with its person / address /
   auth records, each keyed on its own identity plus its own number. Every write raises that row's number, so two
   genuinely-separate edits carry two different numbers and produce two audit records. The dedup never collapses
   them: there is no "second edit" for it to hide, because a second edit is a second version of the row.

2. **The dedup exists for redelivery idempotency (safe redelivery -> still one row).** The bus is at-least-once (a
   message can arrive more than once), so a replayed message must not write a second log row. The row's version
   plus `ON CONFLICT DO NOTHING` gives exactly one record per version -- that is the point. It also catches the
   same change re-announced under a different correlation id, which a request-scoped key cannot.

3. **Per-physical-change auditing is a different mode, already offered.** If someone wants one audit row per
   physical database change (an insert / update / delete, "DML") regardless of the operation id, that is the
   DB-trigger audit (option a), which the framework provides. The bus audit (option c) is deliberately one-row-per-operation. Both granularities exist by choice.

So the audit is keyed on something the caller does not control: the version the row actually reached. The
correlationId still rides along as the through-system key for one operation -- the same id threads the edge, the
services, the bus, the logs and the audit -- but it identifies the operation, it does not decide what the audit
keeps.

---

**Q2. On OKE the services run with audit turned OFF (`audit-off` bus, no auKeep pod), yet the deploy notes say
"OKE audits via DB triggers." Nothing actually applies those triggers -- so does OKE audit anything at all?**

A. No -- and that is a deliberate choice for the free-tier demo, not a defect. OKE runs with NO audit by design:

1. **The app-side audit is off.** The OKE overlays point the audit ref at `audit-off` (a defined DISABLED bus), so
   the services publish nothing and there is no auKeep pod / audit bus traffic -- deliberately, to keep load off the
   Always-Free tier (it is a demo, not a load-test sandbox).

2. **The DB-trigger audit (option a) is SHIPPED but not APPLIED.** The trigger DDL (the SQL that defines the triggers) is baked into the
   esquire-postgres image (`db.seed/postgres/triggers`), but it is an OPT-IN overlay: the base seed is trigger-free
   (`create/all.sql`), `initdb/init.sh` runs only create + fill, and no deploy step runs `triggers/all.sql`. Baked
   into the image means AVAILABLE, not ACTIVE.

3. **So OKE writes no `*_log` rows** unless an operator applies the triggers by hand (`\i ../triggers/all.sql`)
   against the OKE database. For the demo tier that is intended -- audit is a local / full-deployment concern, and
   the framework offers both audit modes (bus option c, DB-trigger option a) for a real deployment that wants one.

So "no audit on OKE" is the deliberate free-tier setup; the DB-trigger overlay sits ready to switch on by hand
(or to wire into the deploy) if a particular OKE deployment ever needs audit.

---

## Security and authentication

*Token format and the trusted-perimeter model (validate once at the edge, trust inside), onboarding credentials, and CORS (cross-origin browser calls).*

**Q1. Access tokens are signed JWTs (JWS), not encrypted (JWE) -- so any holder can read the claims (`esq_uid`,
rootPath, roles). And the gateway "token relay" hands a full downstream JWT to a client that presented only
credentials, or a claim-stripped token. Isn't a readable, or a relayed, token a security weakness?**

A. No -- it is the deliberate, documented compromise the identity server forces, not a gap. The full rationale, the
pattern catalog, the measured cost, and the "which pattern when" decision matrix are in
[Keycloak / Gateway -- Authentication Patterns](Esquire.Auth.TokenPatterns.md); the framework-level summary:

1. **JWE (an encrypted access token) is the only single-token format that hides claims from the client AND stays
   self-contained on the request's fast path -- but stock Keycloak 26 does not emit JWE.** Its `DefaultTokenManager`
   has no encryption path on `/token` for any standard grant (the `access.token.encrypted.response.alg` attribute is
   silently ignored). So the ideal is inaccessible until KC ships it or the identity server (IAS) is swapped (Auth0
   / Okta / ForgeRock / Ping all emit JWE). A gateway-side JWE decoder is kept in tree, inert, for that day.

2. **The recommended production patterns are BFF and plain JWT.** The browser never holds a token at all -- the BFF
   keeps it server-side and the browser carries an opaque session cookie (meaningless on its own). A
   service-to-service caller holds a standard signed JWT, validated locally at every hop against KC's public keys
   (JWKS). Readable claims are by design here:
   authorization comes from validating the signature and the claims at each hop, not from hiding them;
   confidentiality on the wire is TLS.

3. **Vanilla and Phantom Token Relay are a NOT-recommended lab "detour", not a production path.** They approximate
   JWE's claim-hiding by partitioning across tiers, but neither delivers its headline benefit under stock KC, so
   they are exploration patterns and are **not armed on the public OKE API** (disabled 2026-07-20):
   - *Vanilla* -- the client presents only static credentials (HTTP Basic); the gateway runs the
     `client_credentials` grant and caches the JWT per `client_id`. The secret is checked for real on a cache miss;
     on a cache hit the cached token is served by `client_id` alone -- a lab-grade shortcut, which is exactly why
     it is not a production auth path.
   - *Phantom* -- the client holds a JWT whose business claims are stripped; the gateway runs an RFC 8693 token-
     exchange to obtain the **full signed JWT** and forwards **that JWT** downstream (services validate it locally
     against JWKS, like plain JWT). It deliberately does NOT introspect-and-inject-claims-as-headers -- that
     alternative (Opaque+Introspection / Lightweight+Userinfo) was rejected because it puts a per-request KC
     roundtrip back on every service. The claim-stripping half is currently disabled anyway: KC 26.4.7's v1 token-
     exchange propagates the stripping through the exchange (leaving the backend claim-less), so full stripping
     waits on KC token-exchange v2.

So the token is a readable signed JWT by design (validated at every hop, not hidden; TLS on the wire); JWE is the
parked ideal; and the two relay patterns are a documented lab detour, not the recommended production shape.

---

**Q2. Every interactive user that kcMaster creates in Keycloak gets the SAME hardcoded temporary password
(`"changeit"`), enabled immediately, with only a "must change password" flag. Anyone who knows a not-yet-onboarded
user's `loginId` can log in with `"changeit"` and take the account over before the owner's first login -- isn't
that an account-takeover hole?**

A. It is the framework's MINIMAL connect flow, and **Esquire deliberately ships NO onboarding application** -- the
initial-credential experience (a sophisticated first-password flow) is the adopter app's responsibility, the same
line the entries above draw (frontend hardening, schema evolution, theming): the framework provides the MECHANISM,
the adopting product owns the hardened end-user experience.

1. **What the framework owns is the SYNC, not a credential policy.** kcMaster keeps Keycloak's user set in step
   with the Esquire entity tree. The initial credential is a placeholder so the synced account exists and is
   forced to change on first login (`UPDATE_PASSWORD`); it is not a claim that `"changeit"` + force-change is a
   production onboarding.

2. **A production adopter replaces the placeholder at the same create seam**, using Keycloak's own built-in features -- a
   per-user random password that is never transmitted plus `executeActionsEmail(UPDATE_PASSWORD)` (the owner sets
   the password from an emailed link; the account is unusable until then), or create-disabled until first
   credential set. It is a one-call change; the framework does NOT bake in an email/SMTP dependency for every
   adopter by making that the default.

3. **Residual, stated plainly:** if an adopter ships the placeholder AS-IS on a public deployment with guessable
   loginIds, the pre-first-login window is real -- which is exactly why it is written here as the thing to
   replace, not left implicit.

So it is the framework/demo connect flow by design; hardened onboarding is the adopter's, layered at the same seam.

---

**Q3. The individual services do NOT verify the token they receive -- `JwtService.extractAllClaims` base64-decodes
and JSON-parses the JWT payload with the signature check commented out, and the per-service filter checks only that
the claims are PRESENT (it never checks the signature or the expiry). So a request that reaches a service directly
with a hand-crafted, unsigned, or expired token carrying the right claims is accepted. Isn't that a hole?**

A. No -- it is the deliberate TRUSTED-PERIMETER model: the gateway is the single point that validates a token, and
everything behind it trusts the claims the gateway forwards.

1. **The gateway is the one validation point.** Every request from the outside enters through the gateway, which
   validates the token's signature and expiry against Keycloak's keys (JWKS). A request that fails validation never
   reaches a service. The services sit behind that perimeter and read the already-validated claims (`esq_uid`,
   rootPath, roles) rather than re-validating on every hop -- re-checking the signature at each service would add a
   cost the perimeter model is designed to avoid.

2. **The services are not directly reachable in a real deployment.** On k8s / OKE the services are ClusterIP
   (reachable only inside the cluster) -- only the gateway is exposed; no outside actor can hand a service a forged
   token. The only place a service port is
   directly reachable is the local sandbox (host-published ports for development), which is not a production surface.

3. **Reading the claims without re-verifying is intentional, not an unfinished check.** The unused
   `isClaimsValid` / expiration helpers on `JwtService` are the leftover of an earlier per-hop idea that the
   perimeter model made unnecessary; they are kept inert, not wired. If an adopter wants defense-in-depth (validate
   the signature and expiry at each hop as well), that is a one-place change at the service filter -- but the
   framework's shipped stance is: validate once at the gateway, trust inside.

So per-service claim-trust is the deliberate perimeter design (single validation at the gateway; ClusterIP services
inside); it is not a missing check.

---

**Q4. Each service's CORS allows ANY origin (`addAllowedOriginPattern("*")`, all methods, all headers). Isn't an
any-origin CORS policy a hole?**

A. No -- it is the SAFE wildcard combination: any origin, but WITHOUT credentials. The dangerous pattern is
any-origin **with** credentials; this is not that.

1. **No credentials ride a cross-origin request.** `setAllowCredentials` is left `false`, so a browser attaches no
   cookie or credential when a third-party page calls a service.

2. **The services authenticate by a `Authorization: Bearer` header, not a cookie.** A browser never auto-attaches a
   bearer header cross-origin (unlike a cookie), and the token lives in the BFF's server-side session, out of reach
   of any other web page. So a hostile origin can send a request but has nothing of the victim's to attach -- there
   is nothing to steal.

3. **The services are not a browser-reachable origin in a real deployment.** On k8s / OKE they are ClusterIP behind
   the gateway / BFF; only the gateway is exposed, and the gateway's OWN CORS is the strict one (an explicit origin
   list *with* credentials). The wildcard exists on the internal services to remove k8s CORS friction, where it is
   harmless. (Do not pair this wildcard with credentials -- Spring accepts `"*"` origin pattern + credentials, and
   that pairing would turn it into a genuine any-origin-with-credentials hole.)

So the per-service any-origin CORS is a deliberate, safe choice (wildcard WITHOUT credentials, bearer-authenticated,
behind the perimeter); the strict origin list lives where it matters, at the gateway edge.

---

**Q5. Assigning a user to a role sends the role NAME from the request body: keySmith writes the membership row
from the role `id` and the bus event carries the `name`, which kcMaster matches against the realm role list. So one
request could name one role for `esq2025` and another for KeyCloak. Why is the name not resolved from the id?**

A. Because roles are **static configuration**, not runtime data, and the tool that will own them is not built yet.

1. **What the operation is.** There is no editing of a role here. The role catalogue is defined at deployment and
   is the same on both sides by construction; the only runtime operation is **assigning a user to a role**. The
   fields in the request are the triple the browser just read back -- `id`, `kind`, `name` -- and the client sends
   them unchanged.

2. **What is trusted, and by whom.** The caller must already pass the `AdminCmd.AUTH` gate for the target's kind,
   so this is an administrator acting on someone else's assignment; `personal` is refused outright, so it cannot
   be done to oneself. The ceiling is the realm role set -- `realm-management` roles are CLIENT roles and are not
   in `realmResource.roles().list()`, so the assignment cannot reach realm administration.

3. **Where the resolve belongs.** In **Marshal**, the reference administration tool, when it ships: the assignment names a role by
   **id**, the server looks the row up, and `name` and `kind` come from the resolved row rather than the request.
   That is one line of intent, and it also lets the "no more than one administrative role" rule count a `kind`
   the server owns. Doing it before the tool exists would harden the path the tool is going to replace.

So the trust here is the same trust the AUTH gate already grants, and the correction is written into the future
tool's requirement rather than bolted onto the current path.

---

**Q6. The Grafana boards have no login -- the chart sets anonymous access with the Admin role, and the
administrator credentials are `admin` / `admin`. Isn't that a hole?**

A. Three things are being asked at once, and they have different answers.

1. **The boards being unauthenticated is deliberate, on the local targets.** Docker publishes Grafana on
   `localhost:3009` and local k8s serves it at `grafana.localhost`, a name RFC 6761 resolves to 127.0.0.1 --
   so the isolation is the port, not a password, and a login prompt on a developer's own machine buys
   nothing. Grafana's authentication is Grafana's own concern: an adopter running these boards for real
   configures it there, exactly as they would for any Grafana.

2. **The `admin` / `admin` credentials are a chart VALUE, not a constant.** They live in the grafana chart's
   `values.yaml` as `admin.user` / `admin.password` and reach the container as `GF_SECURITY_ADMIN_USER` /
   `GF_SECURITY_ADMIN_PASSWORD`, so an adopter replaces them the ordinary Helm way -- a values overlay, a
   `--set`, or a secret reference -- with no chart change and no code change. Esquire ships the well-known
   development pair on purpose: a generated password on a developer's own board is one more thing to look up
   for a stack that is meant to come up in one command. It is the same class of decision as the demonstration
   database password, and the same rule applies -- **replace it wherever the board is not on your own
   machine**. Note also what the credentials do NOT gate today: with anonymous access at Admin, the login is
   not the way in, so changing the password without turning anonymous access off changes nothing.

3. **The door is what needed closing, not the password.** ingress-nginx routes on the **Host header**, so an
   Ingress rule for `grafana.localhost` on a cloud cluster is reachable from anywhere with a one-line curl,
   whatever the name resolves to publicly. The chart used to render that rule unconditionally. It is now
   gated in the **compact** chart -- `ingress.enabled`, default `true`, so local k8s is unchanged -- and the
   compact OKE arm passes `--set ingress.enabled=false`. The cloud runs compact and nothing else, so that is
   the whole of the cloud exposure; the classic chart is left alone, and on local k8s its host resolves to
   127.0.0.1 anyway. On the cloud the boards are reached the way the arm script always said they were:
   `kubectl port-forward svc/esquire-infra-grafana 3009:3000` (`oke-grafana-forward.bat`), which is also why
   observability there is transient rather than standing.

So the anonymous board stays, because on a machine only its owner can reach it the password is theatre; the
public route is gone, because on a cloud target the port isolation the first answer relies on does not exist.

---

**Q7. The repository is public and it carries working credentials -- the `esq-kcMaster` client secret sits in
`keycloak/import/esquire.json`, and `deploy-oke.sh` falls back to the same literal for the OKE deployment. Isn't
a published secret for a realm-admin service account a hole?**

A. It is a published DEVELOPMENT credential, and that is the deliberate posture of this deployment.

1. **What esquire.mir0n.pro is.** A demonstration of the framework, running demonstration data -- the seeded
   Test-House tree, no real people and no real money. It exists to be looked at. The credentials that bring it
   up are part of what is being shown, the same way the realm import, the seed and the `changeit` first password
   are (section 5 Q2).

2. **Every one of them is an override, not a constant.** `deploy-oke.sh` reads `MIR0N_PWD`, `BFF_KC_SECRET`,
   `GW_EXCHANGE_SECRET`, `BFF_SESSION_SECRET` and `KCMASTER_ADMIN_SECRET` from the environment, and the deploy
   workflow passes all five from the GitHub Environment. Set one there and the literal is not used. An adopter
   deploying this for real sets all five, and replaces the client secret in their own realm import -- which is
   theirs to write, not ours.

   For the kcMaster client the plumbing is one variable, `KCMASTER_ADMIN_SECRET`, read by every path: the two
   local bring-ups, the local rebuild, both OKE scripts, the compose stacks and the deploy workflow. The
   **scripts carry no value** -- with the variable unset they fall back to `OBTAIN-FROM-KEYCLOAK`, which cannot
   authenticate, and each says so on the console with where to fetch the real one. The **compose stacks keep the
   seed value as their default**, so a fresh clone still comes up in one command against the realm that seed
   creates. The realm import is where that value lives, and it is a demonstration seed, not a production
   credential.

3. **What the fallback means, stated plainly.** `${VAR:-default}` treats an EMPTY value as unset, and an unset
   GitHub secret expands to empty -- so a secret that is not configured falls back to the published literal
   silently. That is acceptable for a demonstration whose credentials are published anyway; it is the first
   thing to change for a deployment where they are not. Make the fallbacks fail-closed (`${VAR:?}`, the way
   `MIR0N_PWD` already is) and a missing secret stops the deploy instead of quietly using a known one.

4. **Rotation has two sides.** The Keycloak image bakes `import/esquire.json`, so changing a running realm's
   client secret fixes the live realm, and changing the file fixes the next fresh init. Doing one without the
   other means a re-init restores the old value.

So: published on purpose for a demonstration, overridable everywhere it matters, and the one thing that would
bite a real deployment -- an empty secret silently falling back -- is named here rather than left to be found.

---

**Q8. keySmith answers 200 as soon as the row is committed, and the KeyCloak side is pushed afterwards over a
queue that can refuse or be shut down. So the database can say a user is connected while KeyCloak has no such
user, and the caller was told everything succeeded. Where is that caught?**

A. The 200 is for the WRITE, and the write did happen -- it is committed before the identity request is posted.
The response was never a delivery receipt for the identity provider: that leg is asynchronous by design, so
nothing about it can be reported in the answer to the request that triggered it.

**The drift is detected by a pair of meters, not by the response.** `esq.biz.key.identity.total` counts what
keySmith ASKED the identity provider to do; `esq.biz.kc.sync.total{op,outcome}` counts what kcMaster actually
DID. **A gap between the two is a sync that never landed** -- the comment at the call site in
`KeySmithService.saveAndSync` says exactly that.

That is also why the ask is counted unconditionally, including when the queue refuses it. It reads like a bug
-- counting a request that was dropped -- and it is the opposite: if a refused put skipped the counter, asked
and done would agree and the loss would vanish from the gap. The counter is not claiming delivery; it is
recording intent, so that the absence of the matching outcome is visible.

**The repair is a tool, not an automatic correction.** `hauberk kc-reconcile [--repair]`
(`reconcile.KcRecover`) detects drift between the entity tree and KeyCloak and optionally repairs it. That is
the deliberate posture stated in `Esquire.ContinuingDev.md` CD-13: detect, repair with a tool, and do not build
a guard ahead of a real need.

So all three are present -- a truthful response, a detector, and a repair -- and what is deliberately absent is
automatic correlation of a request with its outcome (`KcBusAdapter.onResponse` carries the `// todo`). Making
the caller wait for the identity provider would put an external system inside the request's transaction
boundary, which is what the asynchronous design exists to avoid.

---
## Accounting (the pacMan demonstration domain)

*What the demonstration accounting service deliberately simplifies, and where the line to a production ledger is.*

**Q1. Transaction idempotency (a retry must not post the money twice) -- `AcctTransactionProcessorSingle.generateTransId()`
mints a fresh `UUID.randomUUID()` per call, so a retried / timed-out transaction POST would insert a SECOND
transaction and move the balance again. There is no client idempotency key, no dedup window, no 409 on duplicate.**

A. Not a real exposure in Esquire. A duplicate transaction requires two POSTs carrying the SAME identity, and the
only thing that produces that is an automated retry re-sending the identical request -- which reuses the
client-supplied `X-Request-ID` (the client sends `X-Request-ID`; `X-Correlation-ID` is the server-generated one).
So the "client-supplied idempotency key" the fix asks for already exists. Two things follow:

1. A user **double-clicking**, or two genuinely different transactions, each carry a NEW request id -> two
   transactions is the CORRECT outcome, not a duplicate. The currently existing GUI offers **no way** to submit
   two transactions under one request id.
2. The one machine that could re-send -- the gateway -- **never retries a write on timeout** (only on
   connection-not-established, where the request provably never landed), so it cannot create a duplicate either.

Verified (2026-07-02): the explorer already sends `X-Request-ID` on every request -- the frontend tracing
interceptor generates one UUID **per outbound call** (so the GUI cannot even emit two POSTs under one id), and the
BFF preserves a client-supplied id, fabricating one only when absent.

PLANNED HARDENING (accepted -- a code change): make `X-Request-ID` **required for
writeable operations** (the write commands), so every write carries a client-controlled identity rather than a
server-fabricated fallback. This is **presence-only**; a **uniqueness / dedup validation is deliberately NOT
added** (there is no source of duplicate transactions today) -- but requiring the id keeps the door open to add
that dedup cheaply, on the key that is then guaranteed present, if a real duplicate source ever appears.

---

**Q2. A money transfer runs the debit (take from the source) and the credit (give to the target) as two SEPARATE
database transactions, not one. The debit is already committed before the credit runs -- so if the credit failed,
the source would be short and the target would never get the money. Why isn't a transfer one all-or-nothing
transaction?**

A. Two legs is the deliberate design -- linked only by a shared transfer id (`pkTx`) -- and it is the safer choice
here, not an oversight. (Accounting is Esquire's demonstration domain; this boundary is stated plainly rather than
hidden.)

1. **Both accounts are checked BEFORE either leg runs.** The transfer reads the source and the target first --
   a plain read scoped by the caller's path, no `FOR UPDATE`, no lock held -- and refuses unless both exist,
   are visible to the caller, and are **open**. Two extra round trips, and they are what makes the rest of this
   answer true: the credit leg has somewhere to land before any money moves.

2. **The two legs are then asymmetric, which removes the "credit fails" case.** The FIRST leg is the WITHDRAWAL
   (negative), fully validated: is the account open, is there enough balance, is the amount sign correct. If the
   money cannot be taken, the transfer stops there and nothing has moved. The SECOND leg is the DEPOSIT, and it
   runs WITHOUT those checks (`skipValidation=true`) on purpose -- the account state it would re-check was
   already established by the pre-read, and adding money to an open account it just saw is always valid. So once
   the debit succeeds, the credit does not fail on account state.

3. **One big transaction would invite DEADLOCKS.** Making a transfer all-or-nothing means locking BOTH accounts
   inside a single database transaction. Two transfers running at the same time in opposite directions (A to B and
   B to A) would each hold one account and wait for the other -- a classic deadlock (two workers each stuck waiting
   for a row the other holds). The two-leg design locks ONE account at a time, so that cross-account deadlock cannot
   arise. Trading a sub-second, infrastructure-only failure window for freedom from deadlocks is the deliberate call.

4. **The shape has a name: this is a saga.** Replacing one transaction that spans several resources with a
   SEQUENCE of local transactions, each committing on its own, is the saga pattern -- the standard answer to a
   distributed transaction, and the reason a two-phase commit is not needed here. Esquire's is the minimal
   form: no orchestrator, no compensating transaction. It can be minimal because the pre-read (point 1) moves
   every foreseeable failure BEFORE the first commit -- a target that does not exist, is invisible or is not
   open cannot be discovered late -- so there is nothing left for a compensating leg to undo except the one
   case below, which no compensation could catch either. A domain with more steps, or steps that can fail on
   their own terms, is where an orchestrator and compensating actions start to earn their keep.

5. **The residual, stated plainly.** The only failure left is the process dying in the sub-second gap AFTER the
   debit commits and BEFORE the credit commits -- a pod crash, not an application error. Then the source is debited
   with no matching credit. Both legs carry the same `pkTx`, so such a pair is identifiable. This window is accepted
   as-is: it is rare and infrastructure-only, and closing it with one atomic transaction would cost the deadlock
   safety above.

---

**Q3. Accounting (the pacMan service) is called Esquire's "demonstration" domain. What does that mean in practice --
what is deliberately simplified, and where is the line?**

A. Accounting exists to DEMONSTRATE the framework -- entities, permissions, the messaging bus, audit -- with a
familiar money example. It is not meant to be a production ledger. The framework MACHINERY it shows off is real and
fully used (database transactions, row locks, the audit trail); the accounting BUSINESS rules are kept only as rich
as the demonstration needs. The deliberate simplifications, each stated plainly:

- **A transfer is two legs, not one atomic transaction.** Debit-then-credit, linked by a shared transfer id -- see
  the transfer-atomicity entry above for the full reasoning (asymmetric legs + deadlock avoidance).

- **The conversion rate is a trusted operator input.** A cross-currency transfer takes the exchange rate (FX rate)
  from the request and checks only that it is positive; there is no server-side rate service, and no `rate == 1`
  guard when the two currencies are the same. Supporting foreign exchange "in full" would require online
  infrastructure to recommend and verify a rate -- which is not a goal of the framework. In the demonstration the
  operator supplies a sensible rate.

- **Money is carried as `double`, rounded on every write.** Amounts and balances are plain `double`, which
  cannot represent every decimal value exactly, so raw arithmetic can leave a tiny binary-floating-point
  "dust." The write path rounds the amount AND the new balance to the scale the entity dictionary declares for
  amount fields before saving, so the ledger and the balance are exact at that scale; because every write
  rounds, the dust is discarded each time and never accumulates. At a fixed decimal scale this round-on-write
  approach is correct and sufficient -- `BigDecimal` (or scaled integers) is not needed. A transfer's credit is
  computed from the amount that was DEBITED, already rounded, so the two legs cannot disagree by a discarded
  decimal multiplied by the rate.

---

## Database schema and migrations

*The seed-plus-idempotent-forward-patch model, and why no migration tool is imposed on adopters.*

**Q1. There is no schema migration tooling (Flyway / Liquibase). db.seed manages schema with hand-written
`patch/v*/forward.sql` files -- ordering, rollback, and "which patches ran" are manual. Shouldn't the framework
enforce a migration tool?**

A. No -- that would be the wrong thing for a FRAMEWORK to impose.

1. **Esquire ships a foundation schema, not a finished product database.** db.seed is a SEED -- the foundational
   tables an adopter is EXPECTED to EXTEND with their own domain specifics. Esquire is a framework, not a
   product-from-a-box, so it does not own (or dictate) the adopter's schema-evolution tooling any more than it
   dictates their build system. Forcing Flyway / Liquibase onto the seed would push that choice onto every
   adopter.

2. **The framework's own convention is deliberate and disciplined**, not improvised: a per-release
   `patch/v<ver>/forward.sql` for Postgres, idempotent, transactional, re-runnable (`ADD COLUMN IF NOT EXISTS`,
   `ON_ERROR_STOP`, `BEGIN..COMMIT`), never reseeding, with the exact apply command in its header, a
   `DB_VERSION` marker the database carries, and a header that says plainly what the patch does to the data it
   finds -- including any object it drops. Oracle is brought up from the seed, so an Oracle database starts
   with the version already in it. A single-history / checksum tool like Flyway does not map cleanly onto TWO
   dialects + a baked seed that only runs on an empty data dir.

3. **The real risk it is pointed at -- "did this DB get the right patch?" -- is a DEPLOY check, not a tool.**
   Asserting `DB_VERSION` at deploy time catches drift (a database that missed a patch) without a migration framework; that belongs with the deploy
   step (the OKE-migration commit), not with imposing Flyway on every adopter.

So the seed-plus-idempotent-forward-patch model is the intended framework contract; migration tooling is the
adopter's choice layered on top of it.

---

## Frontend and UI

*The UI library's deliberately fixed look, and the Explorer's status as a reference example rather than a product.*

**Q1. The UI library hardcodes its colors (the Windows-beveled palette -- `#ccc`, `#f0f0f0`, ... repeated across
the SCSS) with no CSS custom properties, no theme file, and no dark-mode path. Shouldn't there be a theming layer
so the look can be customized?**

A. No -- a theming layer has never been a requirement, and is not planned.

The Windows-era beveled look is a DELIBERATE design identity, not a customization surface. The palette is small and
consistent; it is applied directly because it IS the intended, fixed appearance -- not a default an adopter is
expected to restyle. A CSS-custom-property / theme-file / dark-mode layer would be machinery for a customization
goal Esquire does not have.

More fundamentally: **Esquire is a BACK-OFFICE framework.** Every domain that adopts it is expected to develop its
OWN domain UI on top of the framework -- the shipped components are the back-office tooling surface (the tree
explorer, the entity dialogs), not a consumer-facing skin that each adopter re-themes. So a theming API is doubly
beside the point: adopters build their own UI for their own users, rather than re-colouring Esquire's back-office.
An adopter who does want to restyle a shipped component can override its styles at their own layer; the framework
does not owe a theming API it was never meant to provide.

---

**Q2. The frontend (Esquire Explorer) has gaps a production app would close -- raw `console.*` instead of
structured / shippable logging, no global Angular `ErrorHandler` for unhandled exceptions, no theming layer.
Shouldn't the framework harden these?**

A. No -- because the **Esquire Explorer is NOT a final product; it is an EXAMPLE of using `esquire.ui.lib`.**

The Explorer is a reference application that demonstrates the framework's UI library (the tree explorer, the
entity dialogs, the server-driven forms) end to end. It is a worked example, not a shipping product an end user
runs. So the product-hardening a real app needs -- centralized / structured frontend logging, a global
error-boundary (a catch-all that shows a graceful "something went wrong" screen), a theming layer / dark mode -- belongs to the DOMAIN
PRODUCT an adopter builds on top of `esquire.ui.lib`, not to the example.

On the specific gaps:
- **Frontend logging** -- `console.*` goes to the BROWSER console (client-side dev output), NOT the service-side
  observability chain; the framework's observability is server-side (Pino + tracing). The Explorer using
  `console.*` (a thin `EsqUtils.log` wrapper for the common case) is fine for an example; there is no fully
  flexible JS log wrapper because there is no product requirement for one here.
- **Global error handler** -- the HTTP-error path (the errors a data app cares about) IS handled user-facing (the
  rfc9457 interceptor -> an error message + the error-report dialog). A global `ErrorHandler` wrapping unhandled
  JS exceptions in a graceful shell is product UX, which the adopter's app owns.
- **Theming** -- see the "Windows-beveled look" entry above: a deliberate fixed identity, and each domain builds
  its own UI anyway.

So these are not framework gaps; they are the line between the EXAMPLE (the Explorer) and the PRODUCT (the
adopter's app built on `esquire.ui.lib`).

---

## Testing and QA

*Why shared types + shared constants + e2e are the effective contract at this scale.*

**Q1. There are no API contract tests -- neither the REST (gateway-routed) nor the messaging (bus) interfaces have
Pact / Spring Cloud Contract tests. A field renamed in one service's DTO (data transfer object -- the shape of a
request / response) could silently break a consumer, and drift is caught only by the (late) e2e tests. Shouldn't
the framework add contract tests?**

A. The "silent break" it guards against is already prevented structurally, so a contract-test framework is
disproportionate here.

1. **Messaging body fields are SHARED CONSTANTS, not stringly-typed (matched by raw text on each side).** The entity-broadcast body keys
   are `EsqConstants.TEXT_*` used by BOTH the producer (enyMan `publishEntityEvent`) and the consumer (bizTree). A
   rename changes the ONE constant -> both sides move together at compile time. It is compile-linked, not a silent
   string-vs-string mismatch. (The generic RodEvent envelope carries no per-message schema, but the KEYS are the
   shared constants.)

2. **REST DTOs live in `common` and are exercised end-to-end.** The response DTOs (`EsqEntity`, ...) are in the
   shared `common` module -- a change is compile-visible to every user -- and springdoc's OpenAPI generates the
   explorer client, which the e2e suite drives against the real services. A breaking change fails e2e; it is
   caught, not silent (late, but present).

3. A dedicated framework (Pact / Spring Cloud Contract) would catch drift EARLIER (in CI, before e2e), but it is
   heavy infrastructure -- a broker, contract repositories, a verification stage per service pair -- for the small
   extra gain over the coupling that shared DTOs + shared constants + e2e already provide at this scale. It is the
   adopter's choice to add if their team structure needs it (many independent teams evolving services in parallel);
   the framework does not impose it.

So the effective contract is the shared `common` types + `EsqConstants` + the e2e client; a contract-test framework
is a scale-dependent add, not a gap.

---

## Deployment shapes and naming

*Why the same service answers to two different workload names, and what has to know about it.*

**Q1. On the classic profile a workload is `esquire-aukeep-aukeep`; on compact the same service is
`esquire-aukeep`. Why two namings, and is that not asking for trouble?**

A. It falls out of the charts, and it is stable -- what needs care is anything that addresses a workload BY NAME.

1. **Where it comes from.** The classic charts name the workload `{{ .Release.Name }}-<chart>`; the compact
   charts name it `{{ .Release.Name }}`. Both are ordinary Helm idiom. The compact charts are a separate copy
   by design -- the two shapes are installed from different folders and never share a release -- so on compact
   the release name already IS the program, and the suffix would only repeat it.

2. **Nothing inside the application depends on the name.** Services find each other by SERVICE DNS, the rod-id
   comes from the StatefulSet ordinal, and the charts carry their own labels. The asymmetry is visible only to
   something outside the cluster that types a workload name.

3. **So the rule is: a script that names a workload must branch on the profile.** The places that already do:
   `test/audit-smoke/run.sh` (`AUKEEP_WORKLOAD` / `BIZTREE_WORKLOAD` / `PRODUCER_WORKLOADS`, with the compact
   branch overriding), `perf-matrix.ps1` (`Wl()`, the one place that knows which), and both rebuild scripts
   (`kubectl rollout status statefulset/esquire-<svc>` on compact).

4. **What it looks like when it is missed** -- and it has been missed four times, which is why it is written
   here rather than in a review: `kubectl` answers `NotFound` for a workload that does not exist, and the thing
   the script was waiting for is not waited for at all. A rebuild returns before the new pods are Ready and
   reports an error that is not the one that happened; an audit smoke restarts nothing and then measures a
   consumer still bound to the previous bus, reporting a green cell for a path it never tested.

The names are not going to converge -- renaming a workload is a StatefulSet replace, not an update -- so the
cost is paid once, in the handful of scripts that address one by name.

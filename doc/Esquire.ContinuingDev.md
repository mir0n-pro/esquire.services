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

### CD-13 -- A freshness guard on the synchronized identity claims, if it is ever asked for

**Today:** `kcMaster` applies what the KC request queue hands it, and its only defence is a **value
comparison** -- `updateEntityPath` writes the path only when it differs from the one already on the KeyCloak
user. There is no change number on `KcSyncRequest`, and the auth row's `au_change_no` is not carried across.
That is a deliberate decision, and it is recorded here rather than in the code because it is a design
question, not a detail of any one class.

**The question is not about the path.** Every claim kcMaster writes into the identity store can go stale the
same way, and the path is only the one that showed itself first (race-8c). The full set, with the row each
claim actually comes from -- which is what a guard would have to compare against:

| claim in KeyCloak | source row | its counter |
|---|---|---|
| `esq_rootpath` | `esq_entity_path` | `EP_CHANGE_NO` |
| `username` (the login id, and a rename) | `esq_auth` | `AU_CHANGE_NO` |
| `email` | `esq_auth` | `AU_CHANGE_NO` |
| `enabled` (the connect flag) | `esq_auth` | `AU_CHANGE_NO` |
| required actions (`UPDATE_PASSWORD`, `CONFIGURE_TOTP`) | `esq_auth` | `AU_CHANGE_NO` |
| realm roles | the role tables | **none today** |
| `esq_uid` | set once at creation, never updated | cannot go stale |

**So the counters are NOT interchangeable.** A path claim is guarded by the path row's number, an
auth-derived claim by the auth row's number, and the two are never compared with each other -- the same
declared exception the wire already makes between an entity and its path (see
`Esquire.MessagingBus.MessageStructure.md`). One number covering every claim would
repeat that mistake: an auth update would look "older" than a move that had nothing to do with it, and be
skipped. **Roles have no counter at all**: guarding them means giving them one first, so they stay on
value comparison.

**Why nothing is needed today.** The request leg is a **queue**: one consumer, no fan-out, so there is no
by-design duplicate to filter. A repeat of the same value was already a no-op, because of the comparison
above. That leaves exactly one case a number would catch -- an OLDER update arriving after a newer one --
which is narrow, and is what `hauberk kc-reconcile` exists to repair. A guard here would buy that one case
and cost a field on the request, a parameter on the publisher, and a second place where "which number wins"
has to stay correct.

Contrast the entity broadcast, where the number **does** earn its place: a TOPIC, fanned out, applied on a
worker pool with no per-entity affinity -- duplicates and reordering are by design there, not exceptional.
That is why bizTree is guarded and kcMaster is not.

**The strategy IF it is ever requested** -- announced here so the shape is settled before anyone starts:

1. **Expand the identity user profile with the change number of each guarded claim's source row.** kcMaster
   instances would then synchronize against **the identity store itself as the primary source**, rather than
   each holding a private opinion in memory. That is the point: with more than one instance, an in-process
   guard is per-instance state that a restart empties and that no two instances share, so the identity store
   is the only place a shared answer can live. Applied per claim group, not once for the whole user -- one
   number for the path, one for the auth-derived claims, per the table above.
2. **Declaring the attribute is part of the work, not a detail.** KeyCloak 26 runs a declarative user profile
   (`unmanagedAttributePolicy: null`) and **silently discards** an attribute the realm does not declare -- the
   write succeeds, the log says SUCCESS, the value is not there. So it drags realm configuration with it, and a
   realm change only re-imports on a FRESH KeyCloak init.
3. **`hauberk kc-reconcile` / `reconcile.KcRecover` must move with it.** Repair a claim but not its number and
   kcMaster then SKIPS the next legitimate update for it -- the recovery tool would arm the guard against the
   truth. It repairs the path today; it would have to carry every guarded claim.

**The blocking issue, and why this stays announced rather than planned: PORTABILITY.** Esquire is meant to take
other IAM drivers, and a custom-attribute trick may be impossible or costly on another provider. Anything built
here has to be expressible by whatever identity store sits behind the driver, or it is not a framework feature --
it is a KeyCloak feature wearing one. Settle that before the first line of code.

**Size it honestly: this is a SEPARATE VERTICAL PROJECT, not a change to kcMaster.** It cuts through every
layer at once, and each layer has work of its own:

| layer | what it needs |
|---|---|
| database | a counter on every source row a guarded claim comes from -- the roles side has none today |
| message contract | the numbers on `KcSyncRequest`, per claim group, and the producers that fill them |
| identity driver | the guard itself, and the driver abstraction that lets a NON-KeyCloak store carry it |
| identity config | the realm user profile declaring each attribute, plus the fresh-init routine that lands it |
| recovery | `kc-reconcile` / `KcRecover` carrying every guarded claim, not just the path |
| test | a way to force an out-of-order arrival per claim, which today exists only for the path (race-8c) |

None of those is large on its own; together they are a sprint with its own target, not a task inside someone
else's. Take it as one vertical slice or not at all -- half of it (a guard with no recovery, or a guard on one
claim) is worse than none, because it skips real updates while looking like it works.

**Build it only when something requires it.** Not "one day, for completeness" -- today the value comparison
plus `kc-reconcile` cover the case, and a guard built ahead of a real need buys nothing and adds a way to
skip a legitimate update. What would make it required, so the condition is checkable rather than a feeling:

- **The request leg stops being a plain queue.** It is guarded by "one consumer, no fan-out, no by-design
  duplicate" -- and that premise is what the bus dual-channel item would remove, since it publishes the same
  change on two channels on purpose (see the Messaging Bus backlog). If that lands, re-open this entry first.
- **A stale claim is observed that recovery cannot explain**, i.e. `kc-reconcile` repairs it and it comes
  back -- which would mean the ordering, not the repair, is the problem.
- **An adopter needs the freshness guaranteed rather than repaired** -- a claim that must never be briefly
  wrong, instead of one that self-corrects on the next sweep.

Until one of those is true, the entry stays as written: the shape is settled, nothing is built.

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

### CD-25 -- The "sweet" shutdown: every pending and active task finalised before exit

**A queue is emptied on the way out, not dropped.** That is the contract, and today only half the system keeps
it. `WorkerPool.shutdown(awaitSeconds)` closes intake, lets the workers finish and waits -- the receive pool
drains. `BoundedQueueRig.shutdown()` has no such form: it sets `running = false`, signals the waiters and
interrupts the thread. Whatever is on the deque goes with it, unlogged, uncounted, unaudited. Producers parked
in `put` are released and return without their item and without a word.

**Four owners share that one drain-less shutdown, and they do not lose the same thing:**

- **`AXRod`** -- the transmit feed. `shutdown()` gives the pool five seconds and the feed a bare stop, so a
  restart drops the outbound backlog: audit events, entity broadcasts, KC sync requests. This is the round-4
  audit's M3.
- **`MoveQueueManager`** (enyMan) -- the sharpest one. A queued move, or a create-reconcile waiting to repair a
  path, is dropped at exit and the DATABASE is left wrong. Its own javadoc already names why that is worse than
  the rest: *"the night-watch heals cache-vs-DB, but here the DB itself is wrong, so it cannot."*
- **`KcIdentityGateway`** (kcMaster) -- queued identity work, lost the same way.
- **`AMonadY`** -- the monad queue, and the one that genuinely does not matter: the cache is rebuilt from the
  database on the next bootstrap.

**The shape.** A `BoundedQueueRig.shutdown(awaitSeconds)` mirroring the pool: close intake, let the worker drain,
wait, then interrupt and LOG what is left rather than discarding it in silence. Each owner then drains in its own
right order -- `AXRod` the feed before the pool, so the transport is still open when the last events go out.
`MessagingBus.close()` already stops the idle ticker first, so nothing new is enqueued during teardown.

**What it also settles.** `MessagingBus.close()` says *"in-flight work drains"* in its javadoc; that is true of
the pool and false of the feed. The contract and the sentence would agree again.

**Raised by:** fresh-mind audit round 4, M3, generalised by mir0n on 2026-08-26 -- the finding is about how a
QUEUE is shut down, not about a transport, and it is the same question in every queue the system owns.

---
### CD-23 -- The pursuit: a sweep raised by a burst of path events (TBD)

**Today the night watch runs on a clock.** `biztree.taijitu.sweep.interval-ms` re-arms the sweep a fixed delay
after the previous one ends (600000 ms on the deployed shapes), so the time between a cache that is wrong and
the sweep that repairs it is the same constant whatever just happened -- a tree that has been idle for an hour
and a tree that has just re-pathed a whole subtree are checked on the same schedule.

**The idea: let the backstop's latency follow the risk.** The night watch keeps the schedule; the **pursuit**
is what bizTree raises when something large has just gone past. It watches its own event stream: a run of
`UPDATE_PATH` events -- one move re-paths every descendant, so a big move arrives as a burst -- arms a
trigger, and once the stream has been quiet for a short window the director forces a sweep instead of waiting
for the next scheduled one. A quiet tree is never swept extra; a tree that has just absorbed a large move is
checked within about a second of that move settling.

**The shape.** A debounce, not a count of consecutive events: on each qualifying event, raise a counter and
re-schedule a single-shot task; when that task fires, force the sweep if the counter reached the threshold, then
reset. A strict "N in a row" counter is broken by the first unrelated `UPDATE` that lands in the middle of a
move, which is ordinary traffic.

**What it needs:**

- **No new machinery.** `sweepAsync()` is already the force-sweep entry the REST route uses, `nightWatchExec` is
  a single-thread scheduler that can carry the debounce timer, and the `sweeping` guard already drops a forced
  sweep that collides with a running one.
- **A cooldown, which is not optional.** Each sweep is a full shadow `LOAD` plus two checksums. Without a
  minimum gap between forced sweeps, a move-heavy period -- `move-load`, or a real reorganisation -- turns into
  a sweep storm. A third knob beside the threshold and the quiet window, defaulting to the sweep interval.
- **A log line and a meter** (`esq.biz.tree.sweep.forced.total` or similar), so a forced sweep is visible in the
  same places a scheduled one is.

**Decisions still open:**

- Whether the trigger counts `UPDATE_PATH` alone or any burst. A subtree delete and a bulk create also touch
  many rows for one command; the argument is not specific to a move.
- Whether it is bizTree policy (`BizTreeDirectorTaijitu`) or a generic burst-triggered sweep in `ATaijituRig`
  with the subclass naming the event types that count.
- The three defaults: burst threshold, quiet window, cooldown.

**Raised by:** mir0n, 2026-08-26 -- and recorded with his own verdict on it: this is a windmill. No move has
been shown to leave the cache wrong since the path broadcast started carrying both change numbers, so the item
defends a class of error that has not been observed. It is written down because it costs the design nothing to
make the backstop react to what happened rather than to the clock, not because something is known to be broken.

---
### CD-22 -- The monad digest kept in the row, maintained as rows change

**Today the night-watch digest is computed from scratch, twice per sweep.** `biztree.cache.sql.repo.checksum`
is an MD5 over a `GROUP_CONCAT` of every column of every row, and each leg runs its own. It rides the monad's
single-threaded queue like any command, so its position in that queue selects exactly which applied events it
covers -- and both legs receive the same items, so both cover the same set. But the scan itself runs detached
on `checksumExec` (see *Off-worker CHECKSUM with cancellation* in [Esquire.BizTree.md](Esquire.BizTree.md)),
which leaves two open ends:

- **The read happens after the position that chose it.** The worker hands the digest to `checksumExec` and
  moves on to the next queue item, so the scan reads the live table while the leg keeps applying. The set is
  chosen at the queue position; the reading is done later.
- **The cost grows with the tree.** A full content-hash scan is already described as taking
  seconds-to-minutes. `biztree.taijitu.sweep.timeout-ms` (10 s) arms a cancel and the leg comes back
  `FAILED` -- the leg's own query threw, which the sweep can only report before retrying. So past a
  certain size every sweep is inconclusive and the anti-entropy check stops answering.

**Improvement: hold the digest in the monad's own row and keep it current as rows change**, so a sweep READS
a value instead of computing one. Both ends close at once -- the value is whatever it was at the queue
position the CHECKSUM occupies, and there is no scan left to time out.

**What it needs:**

- **An order-independent combiner over per-row hashes**, so an insert, an update or a delete adjusts the
  total instead of forcing a rescan: hash the same columns the current digest covers, then ADD the row
  hashes rather than XOR them -- XOR cancels a pair of equal values, and a sum does not.
- **The total moves in the same transaction as the row.** A cache batch already runs in one transaction, so
  the digest is never half-applied against the rows it describes.
- **`LOAD` builds it and `CLEAR` resets it.** A freshly loaded shadow has to arrive with the total already
  summed over the rows the loader inserted, or its first sweep compares a value against a scan.

**Raised by:** fresh-mind audit round 4, N7 (2026-08-26). The finding as written does not hold -- the
CHECKSUM rides the same single-threaded queue as the events, so the order of the two submits cannot skew
what each leg covers -- but tracing it surfaced the detached read, and this item answers that together with
the scan cost.

---
### CD-21 -- The service-side move guard, complete -- and a cache that can place what the guard allows

**Today the move is guarded in the UI and only partly on the server.** `EsqMoveDialog` decides three things:
the destination must be an org (`isSelectable` -> `node.kind.org`), it must not be the current parent, and a
moving ORG must not land inside its own subtree. The server enforces the last two -- the same-parent case is
the no-op branch, and `destPath.startsWith(currentPath)` refuses the self-subtree move -- and does not enforce
the first at all. Whether a destination may hold the kind being moved is decided by the picker, not by the
service, so the API accepts a shape the UI would never offer.

**What blocks a general rule: nothing declares the legal tree shape.** `childKinds` looks like the answer and
is not -- it is populated on the FOLDER kinds and means *which kinds this folder holds*, which is exactly how
its one consumer reads it (`BizTreeConstants.folderKindForUsr` searches for the folder whose `childKinds`
contains a user's kind). Kind 20 (`org`) lists `[20]`, so enforcing "the destination must list the moved kind"
would refuse every legitimate user move into an org. A complete guard needs the rule stated somewhere first;
it cannot reuse this attribute.

**What is legal today, and worth writing down because it is not obvious:** users DO live under the root org --
sysadmins and admins -- and only a sysadmin can move an entity there, which the permission set already
enforces. So the root is not a forbidden destination; it is a restricted one, and the restriction is already
in place.

**A settled piece of it: the guard is in the wrong place.** `OrgService.moveOrg` refuses
`destPath.startsWith(currentPath)` -- self and own-subtree both -- but it runs on the MOVE-QUEUE WORKER, after
`EnyManService.esquireCommandMove` has checked permissions, called `submitMove` and returned. So a refused move
is answered to the caller as ACCEPTED, and the refusal is only counted by the rig outcome listeners. Round-4 P4
is what surfaced it: the move-load scenario posts a self-move for its whole run and never sees a failure, so its
`exitHereIfFailed()` cannot fire and the whole measurement is a submit plus an out-of-band refusal.

The fix is small and definite: do the two `orgRepository.orgPath` lookups at submit time -- the worker does them
a moment later anyway -- and throw there, so the caller learns. It carries a cost worth naming: two reads move
onto the request thread, on the one command that is queued precisely to keep work off it. That is the trade to
make deliberately, not the guard to add.

**And that is where the second half of this item comes from.** A legal move produces a path the cache cannot
place. An admin's `ep_path` IS its org's path, so an admin moved to the root carries `1.` -- one segment --
and `BizTreeCacheRepository.moveUsrNode` needs two, because it reads the org from `segs[len-2]`. The accounts
of a client sitting directly under the root carry `1.<usrPk>.`, and `moveAcctNode` needs three. Both now log
an error and skip rather than returning silently (round 3, N2), so the case is visible -- but the apply still
cannot render it, and the tree keeps the node under its old parent until a night-watch reload.

**The fuller form** is one rule declared once and read by both sides: the service refuses what the picker would
not offer, and the cache apply derives the org from the event rather than from a positional slice, so a legal
shallow path places correctly instead of being skipped. Until then the service guard covers orgs only.

**Raised by:** fresh-mind audit round 3, N2 (2026-08-24).

---
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

### CD-16 -- A refused delete answered as a server error

**Today:** deleting an entity that still has children is refused, and refused correctly -- every parent-child
edge carries a blocking foreign key (`esq_org_org_fk`, `esq_usr_org_fk`, `esq_acct_usr_fk`,
`esq_atr_acc_fk`, and the `*_ep_fk` path references). The domain also checks some cases itself and answers
**409**. What differs is where the refusal comes from: the checked cases answer 409, the ones the foreign key
catches answer **500**, because `DataIntegrityViolationException` is a Spring exception and misses the 409
branch in the shared handler. A run of the suites split one delete endpoint 23 x 200 / 8 x 409 / 3 x 500.

**Why this is accepted rather than fixed:** nothing is wrong underneath. The refusal is enforced by the
database, the entity broadcast is published only after the transaction returns, and the audit trail flushes
on commit -- so a refused delete leaves no ghost in the cache, in the identity provider or in the audit. The
cost is honesty, not correctness: a routine refusal wears an outage-shaped status, it lands in the counters
that are supposed to mean something broke, and one condition answers two different ways depending on which
child blocks it.

**The remedy is known and deliberately not applied:** map `DataIntegrityViolationException` to 409 in
`GlobalExceptionHandler` -- which is also the right answer for a duplicate-key violation on create. It is
left for the persistence rework (CD-9 and the frameworks planned around it), where the DB layer stops
surfacing vendor exceptions to a web handler at all.

**When a pre-check WOULD become necessary** -- none of these is true today, and each is worth re-reading
before that changes:

- work outside the database moves ahead of the commit (removing the identity-provider user, or publishing the
  broadcast, before the row is gone) -- then a failed delete leaves half-work no rollback undoes;
- a child appears that no foreign key covers -- another schema, another database, a soft reference;
- the answer has to name the reason ("three users still under this office"), which a constraint name cannot;
- a delete does substantial work before the failing statement, so failing early is cheaper.

A pre-check never replaces the foreign key in any case: a child can be inserted between the check and the
delete.

---

### CD-20 -- A compensating leg for the transfer saga

**Today:** a transfer reads both accounts before either leg runs -- existence, visibility, and both open -- so
the credit cannot fail on account state, and the debit is never written for a transfer that cannot complete.
That is two extra reads on EVERY transfer, paid on the happy path, to remove a failure that is rare.

**The alternative:** let the credit fail and compensate. The saga gains the leg it does not have today -- a
reversing entry for the debit, posted under the same `pkTx`, so the ledger carries the movement and its
reversal and the balance returns. The pre-read then becomes unnecessary for the plumbing part, and the cost
moves from every transfer to the few that fail.

**What it has to answer before it is worth doing:**

- **the reversal must be at least as reliable as the thing it repairs.** Today the only residual is a process
  dying between two commits, and it is rare because nothing is expected to fail there. With compensation the
  failure path becomes ORDINARY, so a process dying between the failed credit and the reversal stops being
  exceptional -- the window must be closed by a retry the framework owns (the reversal replayed on restart),
  not by hope.
- **the ledger changes shape.** A reversal is a row, not an undo: statements, sums and the audit trail all see
  a movement that happened and was taken back. That is the honest record, and it is a different one from what
  a reader of the account sees today.
- **idempotency.** A replayed reversal must not double-reverse -- the same key discipline the `*_log` dedup
  uses, applied to the ledger.
- **one read may stay regardless.** "Both accounts open" is a BUSINESS rule, not plumbing: refusing a transfer
  into a closed account up front is a different answer from taking the money and giving it back. Whether that
  check keeps its read is a domain decision, not a performance one.

**Why it waits:** the current shape is correct and its cost is two reads. This is an optimisation with a
correctness bill attached, and the bill is paid in the path that is hardest to test.

### CD-19 -- A role held per branch: the Vision's second dimension, finished

**The promise is already stated.** `Esquire.Vision.md` names **hierarchical roles** as a backbone idea beside
tree-shaped visibility: *"authority is positional -- the same person carries different responsibilities at
different nodes across different organizational structures ... authority is not a global property of your
account, but a function of where you sit in the tree."* The landing page says the same.

**Half of that is built.** VISIBILITY is positional today and structurally so: a read is bounded by `ep_path`,
resolved from where the caller sits, with no filter written per query. AUTHORITY is not: an admin role is held
once, for the whole tree, and the gate reads the same per-kind matrix wherever the target sits. CD-19 is the
gap between the two halves.

**Today:** a user holds one administrative role, for the whole tree. The gate reads that role's per-kind matrix
and answers from it; the roles validator refuses an assignment that would give a second one
(`Esquire.Auth.md` 5.2a). `EsqRolesStorage.findAdminPermissions` takes the first admin role it finds in the
token and stops -- correct while there is one, and the reason a hard guard has not been added is below.

**What changes:** a role becomes something a user holds **per branch** -- the same person an operator in one
office and a manager in another, which is what the Vision describes. That replaces the user-to-role relation
itself: the assignment carries the branch it applies to, the gate resolves the role for the branch the
target sits in, and "which role does this user have" stops being a single answer.

**Why nothing is hardened before then:** a database constraint enforcing one-role-per-user, or a resolver that
merged several roles into one matrix, would both be written against the shape this replaces. The reader taking
the first role is safe while the assignment path allows only one, and the assignment path is the thing being
rewritten. **Marshal** (CD-18) is where the new relation gets its administration.

### CD-18 -- Marshal, the reference administration tool

**Today:** reference data is static configuration. The role catalogue is the clearest case -- the roles and
their permission flags are defined in the seed (`db.seed/<vendor>/fill/esq_role.sql`) and in the realm import,
and the two sides match by construction. The only runtime operation is **assigning a user to a role**, through
keySmith and the identity gateway. There is no tool for the reference data itself: adding a role, changing a
permission flag, retiring one, or defining a custom parameter is a seed edit plus a realm edit plus a redeploy.

**Marshal** is the household officer who assigns rank and place -- the name says what the tool does. A Ward
guards something that already stands, which is `gateWard`'s job; the Marshal writes the roll.

**Shape.** A UI plus its service, not a script -- reference data is edited by a person, with the change visible
before it is made. **Modifying reference data requires the system-administration permission**, which is a
higher bar than the `AdminCmd.AUTH` gate that governs assigning a user to a role: assignment is daily
administration, the catalogue behind it is not.

**What it owns when it ships:**

- the catalogue -- create, amend and retire a role and its per-kind permission flags, on both sides at once, so
  the two can never be defined apart;
- the assignment path -- an assignment names a role by **id**, and the server derives `name` and `kind` from the
  resolved row instead of taking them from the request. That also lets the "no more than one administrative role"
  rule count a `kind` the server owns rather than one the caller sent (`Esquire.Q&A.md` section 5 Q5);
- the flag order -- one place that states which position means CREATE and which means UPDATE, so a seed comment
  and an enum ordinal cannot drift apart again;
- **custom parameter definition** -- the same treatment for the rest of the reference data an adopter tailors,
  so the tool is the one door to configuration that today is edited as seed plus realm plus redeploy.

**Why it waits:** the catalogue is not edited in flight, so nothing is blocked by its absence, and hardening the
current assignment path would harden a path this tool replaces.

### CD-17 -- The identity failure line does not name who asked

**Today:** when a KeyCloak sync fails, kcMaster logs the entity id, the command and the error (the develop
log adds the request id, the correlation id and the stack), and the requester logs the URR it receives. The
event carries more than either line prints: `uid`, the acting user, and `rodId`, the instance that asked.
`KcIdentityGateway.answer()` also builds the reply with a null `uid`, so the answer cannot carry it back even
when the request did.

**Why it is deferred:** the chain is recoverable as it stands. `correlationId` is on both lines and pulls the
whole story out of Loki and Tempo -- the BFF call, the service request, the URQ hop, the failure. What the
two fields buy is one step instead of two: the ERROR alone would say who asked and from which instance.

**The remedy:** carry `request.uid()` into the answer event, and add `uid` and `rodId` to the kcMaster
failure line and to the requester's URR line. Three log statements and one constructor argument.

### CD-15 -- A materialized level on `esq_entity_path`, and ordering the move broadcast by it

**Today:** a subtree move publishes one path event per node, and `EsqOrgJpa.listMovedPaths` orders them
parents-first with `ORDER BY ep_et_pk, ep_path` -- kind first, because a child's kind is always at least its
parent's, then the path prefix to separate org-under-org. It works, and it costs a sort over a string column.
The query already carries the note that a materialized level column would be the faster form.

**Improvement:** add `EP_TREE_LEVEL` to `esq_entity_path` -- the segment count, maintained wherever `ep_path`
is written -- with a supporting index, and order the move broadcast `BY ep_et_pk, ep_tree_level`. Both
vendors, both branches of the seed.

**Why it is deferred:** the result set is one moved subtree, so the current ordering is not a measured
problem, and the column is a schema change that has to be maintained by every writer of `ep_path`. It also no
longer carries a correctness argument: the consumer used to depend on that order (a node was placed from its
parent's cached row), and since 2026-08-22 it does not -- each move is derived from its own event. So this is
a performance item for a large tree, not a fix.

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

### CD-14 -- Authorization refusals as audit events, not only as error-log lines

**Priority: good to have.** Not a gap and not a missing capability -- **the information is already
recorded and already recoverable today**, by scanning the error log. What CD-14 buys is convenience and
queryability, not a fact that is otherwise lost. A workaround exists, it works, and nothing is blocked on
this. Schedule it when there is room, not ahead of anything.

**Two things get called "audit" here and only one of them is ours.**

**Not ours: the logon handshake.** Failed passwords, lockouts, MFA, session start and end. Esquire holds no
credential and never authenticates anyone, so recording those would duplicate what the identity provider
already owns -- in a shape that changes with whichever provider is deployed. KeyCloak keeps its own admin
and user event log, and that is where it belongs. **Deployment tooling that follows the IAM implementation
in place, not a framework feature.**

**Ours: the authorization refusal.** No identity provider can know that a request for a given node was
refused because the node sat outside the caller's subtree. Only Esquire knows that.

**Why it is worth more here than in most systems.** Visibility failures answer **404** -- so the server
distinguishes *"does not exist"* from *"exists, but not yours"* while the client deliberately cannot. **The
audit record is the only place that distinction survives.** Repeated concealed 404s from one principal
walking an id space is what probing looks like, and nothing downstream can reconstruct it from the
responses, because the responses were built to be indistinguishable.

**Where it stands: available as an error log, and only as a log scan.** Every refusal already reaches
`GenericExceptionHandler.handleGenericRuntimeException`, which logs class, method, URI and message for each
`GenericRuntimeException` -- so `ResourceNotFoundException` and `PermissionDeniedException` are both there.
Nothing queryable, no event, no retention beyond whatever the log shipper does.

**The shape of the fuller answer.** Everything needed exists except the last hop:

1. **An error-handler routine that posts the event to the bus.** `GenericExceptionHandler` is already the
   single funnel every refusal passes through -- one publish call there covers every service, with no
   per-endpoint work and nothing for a future endpoint to forget. It must be fail-open and off the response
   path: an audit publish that can throw, or that can slow a refusal, is worse than no audit -- the same rule
   as the meter path in CD-6.
2. **Carried on the `audit-x` topology.** The audit bus, the `auKeep` consumer, `AUDIT_BUS_ID` selection and
   topology-by-configuration are all in place already -- see
   [Esquire.AuditLoggingStack.md](Esquire.AuditLoggingStack.md). This is a new event kind on an existing bus,
   not new infrastructure, and it inherits the same off-by-default posture: a deploy with no audit config
   imposes no audit.
3. **An extra table on the keep side.** Principal (`esq_uid`), their `esq_rootpath` at the time, requested
   kind and id, the guard that refused (**visibility / permission / domain constraint** -- the three-way
   split), resulting status, timestamp, correlation and request id.

**Not available under (a) DB triggers -- and that is not a blocker.** Two reasons, and the second is the
interesting one:

- Under **(a)** the app-side producers are off by design; triggers are database setup and the services stay
  out of it. So nothing in `GenericExceptionHandler` is running to publish from.
- More fundamentally: **a trigger fires on a row change, and a refusal changes no row.** There is nothing
  for a trigger to observe -- a refusal is defined by nothing happening. No configuration of (a) can reach
  it, now or later.

That is consistent with where the seam already sits: the `IAuditLogger` strategies are **0 / b / c / d**,
and (a) was never behind the app-side seam at all. **CD-14 lands on the seam, so it inherits the seam's
coverage exactly** -- no special case, no new asymmetry.

Practically it costs nothing. (a) is the **OKE demo** choice, taken for an always-on zero-extra-pod way to
watch user activity on the Always-Free tier; a deployment that actually wants authorization audit is
configuring b/c/d regardless. Worth stating in the docs so nobody wires (a) expecting refusals to appear in
it.

**The one thing to decide before building it.** Storing the refused id is exactly what makes the record
useful *and* exactly what the 404 was concealing -- so the audit store inherits a confidentiality
requirement the API deliberately sheds. Worth settling who may read that table before there is one; it is
plausibly narrower than who may read the rest of the audit log.

**Whether this belongs in the framework at all is genuinely open** -- a refusal in Esquire is not a
near-miss, since there is no escape hatch to widen, so it records an attempt rather than an avoided breach.
That argues for an adopter's monitoring concern. The argument the other way is that only the framework is
positioned to record it. Recorded here so the choice is made deliberately.

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

### CD-24 -- Biz-domain isolation: the account's fast state kept apart from the account as an entity

**An account carries two states at once, and they have different meanings and different behaviour.** The
ENTITY state is name, description, status and a place in the tree: it changes at human pace, it is what the
tree shows, and it is what the tree cache mirrors. The BIZ state is the balance and the transactions behind
it: it changes at transaction pace, it belongs to the accounting domain, and no part of it appears in the
tree. Two states, two rhythms, two audiences.

**They share one row and one counter today.** `esq_account` holds both faces and a single `acc_change_no`
counts both. bizTree caches that number, because the entity face IS a tree node -- so a deposit raises a
number the tree cache is watching, for a field the tree does not hold.

**The paradigm: give each state its own.** The account as an entity keeps its own change number and its own
slow life. The account as a business-domain object gets its own state and its own number. The two never
share a counter, because they never meant the same thing.

**What it settles.** The entity number moves only when the entity moves, so what bizTree caches stays exact
without a single extra message on the entity broadcast, and a sweep after a transaction has nothing to
disagree about. The account transaction path stays as quiet as it is now, and stays right.

**Draft shape (mir0n): a virtual kind.** A `biz-account` kind, served by auKeep with only the business
updates for the account and carrying a `biz-change-no`. An account action bumps that number and only that
number; `acc_change_no` is left to the entity.

**Full shape (mir0n): a table of its own, one-to-one, the way a user already has one.** The pattern is in
the schema already. `ESQ_USER` holds the user as an entity; `ESQ_AUTH` holds the access profile
one-to-one beside it -- `AU_USR_PK BIGINT ... 'User primary key, one-to-one FK'` -- with its own
`AU_CHANGE_NO`, its own service (keySmith), its own `ESQ_AUTH_LOG` and trigger, and no presence in the
tree cache. A second state of the same subject, isolated completely.

`ESQ_ACCOUNT` today carries both faces in one row: `ACC_ID`, `ACC_STATUS`, `ACC_DESC`, `ACC_USR_PK` are
the entity, `ACC_BALANCE` (and arguably `ACC_CCY`, `ACC_FUNDED_DT`) are the domain, and one
`ACC_CHANGE_NO` counts them both. Giving the domain its own one-to-one table -- its own change number,
its own log and trigger, owned by pacMan's `acct` and audited by auKeep -- leaves `ESQ_ACCOUNT` genuinely
static, which is what the tree cache assumes about it.

**What that costs, named honestly.** It is schema work: a new table, a new log table, a trigger, a dedup
index, and `ACC_BALANCE` leaving `ESQ_ACCOUNT` and `ACCL_BALANCE` leaving `ESQ_ACCOUNT_LOG`. A release
that both ADDS and REMOVES schema ships as two patches -- additive under the old release, deploy, then
the drops -- and the ERD moves with it. Which columns are domain and which are entity is a decision, not
a reading: the balance is clear, the currency and the funded date are not.

**Open in the draft:** where the biz number lives (a column on `esq_account`, or state of its own); the kind
id for `biz-account` and how it meets the kind-validation rule; whether auKeep writes a log of its own or
tags the existing account log with the kind.

**Raised by:** mir0n, 2026-08-26. A sprint of its own, not a task inside one. Related: **CD-11**, which
redesigns the same processor around per-account concurrency.

---
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

---

## Deployment / packaging

### CD-12 -- "Mesnie": running several services under one roof, to cut the pod count

**Why this exists -- the reason, before the arithmetic.** This is about **someone adopting Esquire**, not
about our own upkeep. A prospective user can look at the current shape -- seven services, a broker, an
identity server, a database -- and reasonably feel it is **too much for what they need**: a detailed
microservice topology brings real cost in support effort and in resources, and not every adopter wants to
take that on to run a backoffice.

That objection is hard to answer with words. Mesnie answers it **by demonstration**: the same code and the
same configuration, deployed in fewer processes. It turns "this is too fine-grained for us" into "the split
is a deployment choice -- here is the other choice."

**State it as a CAPABILITY CLAIM, not a defence.** Not *"the split is heavy, so here is a lighter
version"* -- but:

> **The framework separates architecture from deployment topology. Compose it to fit the situation you are
> in.**

That is a claim the existing design already earns, and it is credible precisely because **most systems
cannot do it** -- their services are welded to their process boundaries. Esquire is not, for reasons
already in the code rather than new work:

- no `@Autowired` and no auto-configuration -- nothing to collide when beans share one context;
- the bus leg is chosen by configuration (`rod-class`, in-process rods), not by wiring;
- the audit stack already carries the dial -- option b removes the consumer service, option a removes the
  audit bus altogether.

So Mesnie **demonstrates something already true** about the architecture; it does not add a capability to
prop up a claim.

**Scope the claim honestly, or it invites an easy rebuttal.** It reduces **operational** complexity and
**baseline** overhead. It does **not** reduce **conceptual** complexity -- the bus, the Taijitu cache, the
audit options and the identity server are all still there, only in fewer processes. And it does not reduce
the **work** under load, only the fixed cost per process.

**Two traps that would turn the argument against itself:**

- **A second-class compact profile proves the opposite.** If it lags, is undertested, or ships dashboards
  that do not work, it demonstrates that the split *is* required. It has to be a first-class profile.
- **Two profiles must not mean two of everything.** The objection being answered is "extra effort to
  support". If the compact setup becomes a second deploy path with its own config, its own docs and its own
  tests, the objection is proved rather than answered. This is exactly why the **same messaging config
  file** and the **unmixed metrics** requirements matter -- they are what keep the argument honest, not
  conveniences.

> **Wording still to settle.** The above is the reasoning, not the final text. The public phrasing for the
> Mesnie sprint gets written **when or if we come back to this** -- and it belongs with the positioning
> material (`Esquire.Vision.md`, the landing pages), written to the same honest standard used there.

**Today:** the framework deploys as seven services, each in its own container. At two copies for high
availability that is fourteen application pods before any infrastructure -- and on a small or
capacity-limited tenancy the pod count, not the load, is what runs the tenancy out of room. The pod
arithmetic below is the **consequence** of the reason above, not the reason itself.

**The idea -- a "Mesnie" service.** *Mesnie* is the Anglo-Norman word for the permanent household under the
lord's roof: the family, the domestic staff, and the knights. It names the concept exactly -- three services
under one umbrella, one service process.

**It is ONE ordinary Spring service**, built the traditional way. It is **not** three applications bolted
into one JVM. It **reuses the existing code** of enyMan / keySmith / kcMaster, with the implementation
specific to each of them, wired into a single application. Two shapes, one codebase:

- **Mesnie** -- enyMan + keySmith
- **Mesnie3** -- the same code, kcMaster optionally included as the third

**This is the COMPACT setup** -- a second deployment profile beside the full one, not a replacement for it.
Counted on the household itself, at two copies for high availability:

| | full setup | compact setup |
|---|---|---|
| enyMan + keySmith + kcMaster | 3 x 2 = **6 pods** | 1 x 2 = **2 pods** |
| whole framework | 7 services -> **14 pods** | 5 services -> **10 pods** |

Mesnie (enyMan + keySmith) is the smaller step: 2 x 2 = 4 pods become 2.

**What happens to the messaging -- less than it first appears.**

- **Entity broadcast and audit broadcast: unchanged.** Same bus, same catalog, nothing to alter. Mesnie is
  simply one participant on them instead of three.
- **The request/response IAM bus is not needed at all** once the three are together. Today keySmith
  publishes a KeyCloak sync request and kcMaster consumes it; under one roof there is no hop to make. That
  leg is not reconfigured -- it goes away.

This is why the messaging configuration can stay the same file for both deployment shapes: nothing about
the remaining buses changes, and the one bus that would have needed changing is simply not referenced.

The kcMaster entry keeps an **asynchronous interface** -- the keySmith-to-kcMaster hop is fire-and-forget
today and stays that way, so a save still does not wait on KeyCloak. How that is built is an
**implementation detail, not settled here** (the in-process x-rod may have a place).

**The real obstacle: the three modules have never been libraries.** enyMan, keySmith and kcMaster are each
a Spring Boot **application** -- each carries `spring-boot-maven-plugin` with the default `repackage` goal,
so the published jar is an executable fat jar with its classes under `BOOT-INF/`. **Nothing in the build
depends on any of them**; they have only ever been endpoints, never ingredients. So "reuse the existing
code" is the task, and it needs one of:

- a **classifier** on the repackage goal, leaving the plain jar as the Maven artifact (small, but the
  executable jar's name changes, which ripples into the Dockerfiles and deploy scripts); or
- a **module split** -- the reusable business code (`service`, `jpa`, `messaging`) separated from the
  application shell (`*Application`, `application.yml`, controllers). Bigger, and it matches what the
  framework has already done twice: messaging lifted out of common, and mir0n-utils split out.

kcMaster is the easiest of the three to fold in: it has **no REST controller at all**, only a bus consumer
and a KeyCloak client.

**What makes the rest of it feasible.** The framework uses **no `@Autowired` and no auto-configuration** --
every bean comes from an explicit factory. Assembling several services' beans into one context normally
dies on component scanning; here there is none to collide with.

**The challenging part -- business metrics must stay UNMIXED.** The requirement is not to merge them under
one label. Each original service's business meters keep **the same names and the same meaning they have
today**, and enyMan's numbers must never blend with keySmith's or kcMaster's. Where a service has no meter
today, one gets implemented rather than borrowed from a neighbour.

Counting what exists shows the work is smaller than it sounds, and exactly where it bites:

- **enyMan owns five, all distinctly named** -- `esq.biz.entity.ops.total`, `esq.biz.dict.lookup.total`,
  `esq.biz.move.processed.total`, `esq.biz.move.failed.total`, `esq.biz.move.queue.depth`.
- **kcMaster owns two** -- `esq.biz.kc.sync.total`, `esq.biz.kc.sync.duration`.
- Because those names are already distinct, **one registry does not mix them**. Seven meters carry straight
  over with nothing to change. This half is free.
- **keySmith owns none at all.** It emits no business meter today, so there is nothing to keep separate --
  and nothing to see either. These are the ones to implement.
- **The blending happens in the SHARED meters**, and only there: `esq.biz.perm.check.total` (emitted from
  `common`, on behalf of whichever service ran the check) and `esq.biz.keep.write.total` / `.duration`
  (emitted from `dataKeep`, on behalf of whichever service wrote the audit row). Today they are told apart
  by **which pod reported them**. Under one roof that separator is gone, and enyMan's permission checks and
  audit writes become indistinguishable from keySmith's.

**REST request processing has the same requirement** -- enyMan's requests must not blend with keySmith's.
REST timing comes from Boot's standard `http.server.requests`, tagged by URI template, so under one roof
every controller's requests land in one meter.

### The tag that already exists -- this is the whole answer

`ObservabilityConfig.esqCommonMetricTags()` is a MeterFilter that already puts
**`application=<spring.application.name>` on EVERY meter** -- `http.server.requests` included, and the two
shared `esq.biz.*` meters included. So the discriminator was never only "which pod answered": every meter
already says which service produced it.

That makes the Mesnie problem narrow and precise: **one process has one `spring.application.name`, so the
tag collapses to a single value** and everything becomes `application=mesnie`.

**The idea, then, is not to add a dimension but to keep an existing one truthful:** let `application` mean
**"which Esquire service produced this"**, not "which process is running". In the seven-service shape that
is exactly what it means today -- nothing changes, no dashboard moves, and the Grafana services dropdown
keeps working because it already groups by that tag. Under Mesnie, one process simply reports several
values of it.

This also removes the earlier worry about re-tagging a framework meter. Nothing new is added to
`http.server.requests`; a label it already carries is merely made to tell the truth, so cardinality does
not grow and the REST p95 panel is unaffected.

**One mechanism, not several -- keep it as uniform as possible.** A per-request value can no longer come
from a static MeterFilter, and the temptation is to solve REST one way and the shared meters another. Do
not: that leaves two things to keep in step and two ways to be wrong.

**The framework already carries the vehicle.** `EsqContextHolder` is a `ThreadLocal<EsqRequestContext>`
established at **every entry point** and cleared in a `finally` -- a REST request sets the full context, and
a bus or queue worker calls `applyMessage(...)`. It is already how the correlation id and request id reach
MDC and the logs. Adding **"which Esquire service is this code acting as"** to that same context gives one
notion, set at the seams that already exist, read by anything that needs it:

- a REST request takes it from the controller that handled it;
- a bus worker takes it from the service whose worker is running;
- a queue worker (for example the move queue) takes it the same way, at the same `applyMessage` /
  `clear()` boundary it already uses.

Then **every meter reads one place** -- REST, the service-owned `esq.biz.*`, and the shared
`esq.biz.perm.check.total` (`common`) and `esq.biz.keep.write.*` (`dataKeep`). The shared facilities need
no new argument from their callers, because the answer is already on the thread.

This also satisfies the constraint above by construction: **at seven services the value is constant and
equal to `spring.application.name`, so nothing changes there at all.** No Mesnie-only special case is
bolted into shared code, and the common modules carry no deployment-shape assumption.

A further gain worth taking: because that context already feeds MDC, the same answer becomes available to
**logs and traces as well as metrics** -- one "which service" across all three pillars, consistent with how
the correlation id already ties them together.

**One detail to settle when it is built:** `applyMessage(...)` deliberately stamps MDC *only*, for workers
that have no full request context. So the service identity must be carried in a way that works on both
paths -- the full-context REST path and the MDC-only worker path.

**No REST split or redirect is needed.** The two controllers do not collide -- both use
`@RequestMapping(path="")` and their endpoints are disjoint (enyMan: `/esq-dict`, `/esq-cmd`,
`/esq-cmd-save`, `/esq-cmd-new`, `/esq-cmd-del`, `/esq-move`, `/esq-kinds`, `/esq-cmd-tree`; keySmith:
`/esq-key`, `/esq-key-save`). They sit in one application unchanged, with no prefix and no API change.
Two alternatives were considered and rejected:

- **An HTTP redirect to the original controllers** -- double-counts `http.server.requests` and adds a round
  trip.
- **Thin Mesnie controllers delegating to the original services** -- restates the whole REST surface, and
  the measured controller would then be the Mesnie one, so the tag would still be needed. Duplication for
  no gain.

### What else folds -- and what does not (sketch, not settled)

**auKeep does not move -- it is simply NOT DEPLOYED.** This one needs no work at all, because the dial
already exists. The audit stack is selected by `AUDIT_BUS_ID`: option **b** (`XRodInProcessKeep`) has the
producing service write its own audit row through the generic keep engine -- no consumer service, no broker
hop -- and option **a** drops to DB triggers, with no audit bus traffic at all. So a compact setup takes
option b and an auKeep pod never exists; a **super-compact** setup takes option a and the audit stack
leaves the application entirely. Zero code, one environment variable.

> Caveat that ties back to the change-number work: option **a** (triggers) and the audit-log **dedup
> overlay** are mutually exclusive. A super-compact setup on triggers therefore runs without the dedup
> indexes -- which is fine, because the trigger path is not the redelivery path, but it must be stated
> rather than discovered.

**bizTree does NOT belong under Mesnie -- it belongs with the GATEWAY.** Mesnie is the *write* side (entity
management, credentials, identity sync); bizTree is the *read* side, and its Taijitu cache holds **two**
copies of the tree in H2. Folding that into the compact write pod would make the thing it is trying to keep
small large again, and the two have opposite scaling profiles. Its natural neighbour is the tier that
serves reads.

> **The BFF was considered first and is the WORSE answer.** The BFF is **Node / Express**, so bizTree could
> only ever be a co-located container in the same pod -- saving a **pod object, not a JVM**. Worse, the BFF
> proxies `/api/*` to the gateway, so the hop only shortens if the BFF calls bizTree directly on
> localhost -- which **bypasses the gateway's token relay and auth gate**. That is security-shaped, not
> packaging. And **porting Taijitu to Node is ruled out** -- reimplementing the two-monad cache, the
> night-watch sweep and the H2 store in Node is a different project altogether.

**Why the gateway is the right host.** Three things the BFF version could not offer:

- **Both are Java, so it is a real merge** -- one JVM removed, exactly like Mesnie.
- **The auth problem disappears.** The gateway *is* the security boundary (`SecurityConfig`,
  `KeycloakRoleConverter`), so a cache read from inside it happens after authentication. There is nothing
  to bypass.
- **It removes a hop from the hottest read path**, and the gateway already carries tree awareness -- it
  routes by entity kind today (`EntityKindRoutePredicateFactory`).

**The engineering risk, and it is the whole problem: reactive vs blocking.** The gateway is
`spring-cloud-starter-gateway` -- WebFlux on a Netty event loop, stateless, with no database. bizTree is
blocking throughout: H2 JDBC to serve the tree, JDBC to Postgres/Oracle for the cache load and the
night-watch reconcile, JMS for the broadcast, plus the Taijitu monad workers. **Blocking work on an event
loop stalls the whole gateway for every route, not just the tree route** -- an availability problem at the
ingress, not a latency one. Solvable (blocking work on its own scheduler; the monads already own their
threads), but real engineering that has to be right.

**The second consequence: the gateway stops being stateless.** It gains two tree copies in H2, a database
pool and a broker consumer. Three follow-ons:

- **Scaling multiplies caches.** The gateway scales with API traffic, the cache with tree size and write
  rate -- unrelated drivers, now coupled. N gateway copies means N tree caches and N night-watch sweeps.
- **Blast radius** -- a bizTree defect takes routing down for everything.
- **Startup** -- the gateway would need the database before it is healthy; today it can start without one.

**Verdict: right for the COMPACT profile, wrong as a general architecture change.** In a small deployment
at two copies, not scaled for traffic, none of those objections bite, and a wider blast radius is the trade
this profile already makes. That is exactly why it is a *profile* and not a redesign.

**It also gives the compact setup a principled shape -- three tiers by responsibility**, rather than
merging whatever happens to fit:

| tier | contains |
|-------------------|--------------------------------------------------|
| edge / read | gateway + bizTree cache -- auth, routing, tree reads |
| household / write | Mesnie3 -- enyMan + keySmith + kcMaster |
| accounting | pacMan |

plus the BFF, with audit in-process or on triggers so no audit service exists.

**The profile ladder this suggests** (at two copies each):

| profile | shape | pods |
|---------|-------|------|
| full | 8 processes: gateway, bizTree, enyMan, keySmith, kcMaster, pacMan, auKeep, BFF | **16** |
| compact | gateway+bizTree, Mesnie3, pacMan, BFF; auKeep not deployed (audit option b) | **8** |
| super-compact | as compact, audit dropped to DB triggers (option a) | **8**, no audit bus |

### What the compact setup buys -- six services become two

**bizTree, enyMan, keySmith, kcMaster, auKeep and the gateway -- six -- become two:** `gateway+bizTree`
(edge / read) and `Mesnie3` (household / write), with auKeep not deployed at all. pacMan and the BFF are
untouched. **Scaling stays in place** -- the two tiers still run at two or more copies.

Beyond the pod count, two things get genuinely simpler. Both are worth stating at their true size:

**1. The cross-service concurrency around kcMaster goes away.** Today a user CREATE and its path broadcast
reach kcMaster by two different routes -- the KC request queue and the entity topic -- and may arrive in
either order. That is exactly why the kcMaster path buffer exists: it parks a path while the KeyCloak user does not
exist yet, and `KcRequestHandler` takes over once it does. Under one roof that hand-off is an in-memory,
locally ordered step instead of a time-of-check race across a broker, and the deep `esquire.kc.request`
queue disappears with it.

> **Draw the boundary honestly: this removes CROSS-SERVICE races, not CROSS-COPY ones.** At two copies
> Mesnie3 is still two processes, so the multi-instance concerns (the Goal-4 / race-8b territory, the
> entity-id instance digit, rod-id routing) are unchanged. Claiming both would be an overclaim.

**2. The gateway-to-bizTree round trip disappears** -- but its value is not mainly speed. The latency saved
is one intra-cluster hop, small against a client round trip our own OKE measurements put at roughly 55 ms
dominated by network distance. **The real gain is that a whole failure mode is deleted:** that route needs
a timeout, a circuit breaker and a resilience budget, and it can fail, retry and time out. Removing the
route removes its configuration and its failure paths. That belongs under *less to support*, not under
*faster*.

**And the nuance on scaling:** the two tiers now scale as units, and scaling the edge tier multiplies tree
caches -- the gateway scales with API traffic while the cache scales with tree size. Harmless at the sizes
this profile targets, but it should not surprise anyone.

### Does the compact setup help the cloud budget?

It helps the **footprint** reliably, and the **bill** only under one condition. Worth stating so the item
is not sold on the wrong number.

On OKE every classic Java service reserves the same thing (`k8s-oci/values/*.yaml`, the classic overlay):
`requests` 100m CPU / 512Mi,
`limits` 750m CPU / 768Mi. Six pods reserve **600m CPU and 3 GiB**. The two compact pods need more than
512Mi each -- they carry three services' work -- but not three times it: one JVM means one heap, one
metaspace with the framework classes loaded once instead of three times, one set of GC and JIT threads,
one actuator, one health probe. Call it **~600m CPU and ~1-1.5 GiB of reservation freed**.

- **The bill follows nodes, not pods.** A1.Flex is charged by OCPU-hour and GB-hour on the *node shape*, so
  freeing reservation changes the invoice only if it lets a node shrink or go away. Below that threshold
  the nodes are simply emptier.
- **The load balancer, block storage and egress are untouched** -- and on a small cluster the load balancer
  is usually the largest recurring line. That caps how much any of this can save.
- **A real gain that is not in the arithmetic:** today each of the three is capped at 750m and cannot lend
  headroom to the others -- one can throttle while its neighbours idle. Under one roof that burst capacity
  is pooled. Better utilisation, not merely less reservation.
- **On a fixed free allocation the effect is different and better:** there "budget" means *fitting*, and the
  freed room is what lets something else run inside the same zero -- which matters while observability has
  to be switched on and torn back down to stay there.

**The cost, stated plainly:** the three lose independent restart and independent scaling, and one crash
takes the whole household down together. Scaling granularity is the case where the compact setup costs
more rather than less: if enyMan needs another copy under load, keySmith and kcMaster are scaled with it.
Bin-packing too -- one larger pod needs a single node with room, where three small ones can spread. That is the trade being made for the pod count.

**Logo note, so it is not forgotten:** a **two- or three-keep castle** -- one keep per collocated service,
so the mark itself says which composition was deployed. Heraldic, sits beside the existing helm, and stays
readable small.

#### The identity seam this needs -- `IIdentityGateway`

Under one roof there is no request/response bus leg between the services: a request for the identity store
is posted straight onto the handler's own queue. So the household needs a seam that is **an API rather than
a transport**, and that seam is worth more than the composition that prompts it.

**What it is.** An interface plus the message structure that travels across it -- a contract package, not a
single Java file. It gives a caller two entry points against what is, to that caller, a black box: **post a
request and receive its response**, and **post a path (X) message**.

**Who is on each side.** `kcMaster` implements it. In the full deployment `kcMaster` is a service and the
call travels over the messaging bus; under one roof the household holds that implementation directly and
the call is a method call onto an in-memory queue. **The caller never learns which**, so one code base
serves both shapes with no branch in enyMan or keySmith. The hand-off stays **asynchronous and queued
either way** -- the queue is what keeps it ordered, and only the broker destination goes.

**The name is the networking one, chosen precisely:** a bridge joins two segments of one protocol; a
**gateway translates between different ones**. Here it is an Esquire message -- the `RodEvent` object -- on
one side, and the identity system's own API on the other, so the word is earned: this is a translator.

The edge service is a different thing under the same borrowed word. It takes HTTP and passes HTTP, so what
it does is route, authenticate and enforce policy -- **a reverse proxy**, whatever the industry habit of
calling such a tier an "API gateway". `gateway` there names the Spring Cloud component it is built on.

**Why it matters past the pod count.** The messaging side already has a defined way to plug in: the bus
talks to its network leg through `ITransportProvider`, with ActiveMQ, Kafka and Redis providers chosen by
configuration. **The identity side has the claim of portability without a shape for it** -- which is the
blocker CD-13 names, that anything built there has to be expressible by whatever identity store sits behind
the driver or it is not a framework feature. `IIdentityGateway` is what turns that claim into a seam, and
it gives identity the form messaging already has.

**Where it can go from here**, once more than one identity system is in the picture: a single identity
service configured to use one implementation or another -- the instance keeping whatever name suits it,
with the vendor named by configuration rather than by the service; the implementation carried in its own
jar and attached at deployment; and the household choosing the same way, by configuration rather than by
what it was compiled against.

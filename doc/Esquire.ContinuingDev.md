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

On OKE every Java service reserves the same thing (`k8s-oci/values/*.yaml`): `requests` 100m CPU / 512Mi,
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

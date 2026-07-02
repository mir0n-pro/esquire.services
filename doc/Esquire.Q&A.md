# Esquire -- Design Q&A

> TEMPORARY / WORKING FILE. The framework-wide Design Q&A for Esquire OVERALL -- things that *sound like* defects
> on a first read of Esquire, and why each is a deliberate choice (or a non-issue in the actual usage model)
> rather than a bug. This is the Esquire-level Q&A; it is NOT tied to any single subsystem. (A subsystem may keep
> its own topic-scoped Q&A -- e.g. the messaging bus has "Appendix F -- Design Q&A" in `Esquire.MessagingBus.md`
> -- but framework-wide questions like the ones below live HERE.) Temporary location: once we settle where the
> framework-level Q&A belongs, this content gets compiled into its permanent home.

---

**Q. There is no optimistic locking on entity edits -- no version column / ETag / `If-Match` on `ESQ_ORG` /
`ESQ_USER` / `ESQ_ACCOUNT`. Two concurrent edits of the same entity look like a lost update: the first writer's
change is silently overwritten, with no 409.**

A. Not a defect in the Esquire model. The classic lost update needs three conditions to hold at once, and Esquire
breaks the chain:

1. **Update requests carry only the changed fields**, not the whole entity. So a change to field X and a change
   to field Y never clobber each other the way a full-row rewrite would -- there is no read-the-whole-row /
   write-the-whole-row window in which one request's untouched fields overwrite another's.
2. **The usage model is one modification per entity at a time.** The UI/API drives a single editor per node at
   human pace; there is no second concurrent writer to lose against -- not even under multi-instance, because two
   replicas only race if two requests target the *same* entity in the *same* instant, which the model does not
   produce.
3. **Same-field last-wins is the intended resolution, and nothing is lost even then.** The one case that does
   resolve by last-wins -- two edits to the *same* field -- is the correct outcome, and the overwritten value is
   still fully preserved: every field change is written to the **audit log** (`*_log`, via the x-rod / keep
   stack), so the prior value is recorded and recoverable.

The generic "add a version column + `If-Match` + 409" prescription assumes full-row writes and concurrent
editors; Esquire has neither.

Note the account **balance** is not an exception to this: a balance is never changed by the entity update / save
path at all -- it is maintained solely by the **accounting-transaction command**, a separate mechanism where
transaction **ordering** is enforced (the last transaction lands last). It is a different thing entirely from an
entity field edit, so there is no plain-update lost-update case for balances either.

---

**Q. Transaction idempotency -- `AcctTransactionProcessorSingle.generateTransId()` mints a fresh
`UUID.randomUUID()` per call, so a retried / timed-out transaction POST would insert a SECOND transaction and move
the balance again. There is no client idempotency key, no dedup window, no 409 on duplicate.**

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

PLANNED HARDENING (accepted -- a code change, tracked in `tasks1210.md`): make `X-Request-ID` **required for
writeable operations** (the write commands), so every write carries a client-controlled identity rather than a
server-fabricated fallback. This is **presence-only**; a **uniqueness / dedup validation is deliberately NOT
added** (there is no source of duplicate transactions today) -- but requiring the id keeps the door open to add
that dedup cheaply, on the key that is then guaranteed present, if a real duplicate source ever appears.

---

**Q. The bus consumers run `AUTO_ACKNOWLEDGE` with a catch-and-log listener -- a failed DB apply is acked and
LOST (no nack / redelivery / DLQ). And the audit `INSERT .. ON CONFLICT DO NOTHING` has no explicit conflict
target, so it silently depends on a unique index existing on every `*_log` table.**

A. Two separate concerns, both already handled by design.

*Delivery loss is bounded per channel -- a generic DLQ is not the mechanism:*
- **Audit** -- best-effort *by design*: the documented async-audit loss boundary. A failed audit apply is logged,
  not retried; the audit trail is not on the request's critical path.
- **Entity-broadcast** -- a lost apply leaves bizTree's *cache* stale, but the **Taijitu night-watch**
  anti-entropy reconciles cache-vs-DB and heals it (the DB is the source of truth here; contrast the dropped
  *move* reconcile, where the DB row itself goes stale and the night-watch cannot help).
- **R&R (KeyCloak request/response)** -- the one channel where lost delivery is a genuine reliability question;
  that is tracked as R&R reply tracking / timeout / replier-down (backlog #14), not a generic DLQ.

*The `ON CONFLICT` target is deliberate.* The dedup unique indexes DO exist for all eight `*_log` tables
(`db.seed/.../dedup/all.sql`), keyed on `(crl_id + pk[, kind])`. `ON CONFLICT DO NOTHING` *without* a named target
is a forward-compatible clause on purpose: **active** (dedups at-least-once redelivery -> exactly one log row)
when the dedup overlay is applied (bus audit, option c/ck), **inert** when it is not (DB-trigger / in-process
audit, where the overlay is deliberately absent because a per-DML trigger would collide on the same dedup key).
So it is not a silent dependency -- it is an intended, documented overlay.

---

**Q. R&R rod-id uniqueness is unenforced -- rod-id defaults to `<app>.<instanceNo>` and there is no runtime check
that two rods don't share an id. A plain Deployment gives every replica `<app>.0`, so replicas would share the
reply selector and steal each other's replies.**

A. Not worth a runtime enforcement. rod-id is **unique by default**: the charts deploy every service as a
**StatefulSet**, so each replica gets a distinct ordinal (`<app>.0`, `<app>.1`, ...) and therefore a distinct
rod-id -- the v1.2.10 redundancy work already made this structural, not a "run it as a StatefulSet" hope. You
*can* set rod-id manually in config, but that is a deliberate, not-recommended, expert override -- defining a
colliding rod-id by hand means you know what you are doing. A runtime fail-fast on duplicate rod-ids would need
real cross-instance coordination (to see another instance's id) -- a large mechanism for a case that does not
arise by default and only arises by deliberate misconfiguration. Huge effort for a non-existing case.

(Note this covers ONLY the rod-id **uniqueness** half of backlog #14. The other half -- R&R **reply timeout /
pending-request tracking / replier-down detection**, plus the delivery-reliability piece folded in from #12 -- is
a separate, still-open question.)

---

**Q. The messaging-bus catalog does no config validation at load -- a typo in a slot's `rod-class` or
`transport.provider` is not caught at `catalog.load()`; it only throws later "at resolve." Shouldn't the catalog
dry-resolve every slot at load so a bad config fails fast?**

A. It already fails fast for everything that matters, and dry-resolving EVERY slot would be actively wrong.

1. **Resolution is eager at startup, not lazy.** `MessagingBus.init()` runs `catalog.load()` -> `buildRods()` ->
   `initRods()` in ONE call. `buildRods` calls `resolveRod(rod-class)` + `rod.validate(params)` for every bus the
   service USES, so a rod-class / provider typo on a used bus fails at boot with a clear message ("no x-rod class
   ... on the classpath") -- a breath after `load()`, not lazily at first message. "Throws at resolve, not at
   load" is technically true, but they are the same startup breath.

2. **A universal dry-resolve is unsafe.** The SHARED topology catalog holds ALL buses, and each slot's rod-class /
   provider class is on the classpath ONLY of the services that USE it -- `XRodInProcessKeep` ships only in
   dataKeep services; `tp-redis` / `tp-kafka` only where those brokers are used. A service correctly bundles only
   its own drivers. Dry-resolving EVERY slot would `Class.forName` a driver / rod the service does not have and
   CRASH at boot for a slot it never touches. The "validate every slot" prescription assumes ONE process owns
   every driver; Esquire's per-service classpath does not.

3. The only residual gap -- a typo in a slot NO service uses -- is harmless (nobody builds it) and impossible to
   validate universally (its driver is on no single service's classpath).

So the catalog validates STRUCTURE at load (unique bus / slot / node ids) and defers rod-class / provider
resolution to the per-service build, where it is both correct (only the drivers that service actually has) and
still fail-fast at startup.

---

**Q. There is no schema migration tooling (Flyway / Liquibase). db.seed manages schema with hand-written
`patch/v*/forward.sql` files -- ordering, rollback, and "which patches ran" are manual. Shouldn't the framework
enforce a migration tool?**

A. No -- that would be the wrong thing for a FRAMEWORK to impose.

1. **Esquire ships a basement schema, not a finished product database.** db.seed is a SEED -- the foundational
   tables an adopter is EXPECTED to EXTEND with their own domain specifics. Esquire is a framework, not a
   product-from-a-box, so it does not own (or dictate) the adopter's schema-evolution tooling any more than it
   dictates their build system. Bolting Flyway / Liquibase onto the seed would force that choice onto every
   adopter.

2. **The framework's own convention is deliberate and disciplined**, not ad-hoc: per-release
   `patch/v<ver>/forward.sql` for BOTH dialects (Oracle + Postgres), each additive-only, idempotent,
   transactional, re-runnable (`ADD COLUMN IF NOT EXISTS`, `ON_ERROR_STOP`, `BEGIN..COMMIT`), never dropping or
   reseeding, with the exact apply command in its header and a `DB_VERSION` marker the database carries. A
   single-history / checksum tool like Flyway does not map cleanly onto TWO dialects + a baked seed that only runs
   on an empty data dir.

3. **The real risk it is pointed at -- "did this DB get the right patch?" -- is a DEPLOY check, not a tool.**
   Asserting `DB_VERSION` at deploy time catches drift without a migration framework; that belongs with the deploy
   step (the OKE-migration commit), not with imposing Flyway on every adopter.

So the seed-plus-idempotent-forward-patch model is the intended framework contract; migration tooling is the
adopter's choice layered on top of it.

---

**Q. There are no API contract tests -- neither the REST (gateway-routed) nor the messaging (bus) interfaces have
Pact / Spring Cloud Contract tests. A field renamed in one service's DTO could silently break a consumer, and
drift is caught only by the (late) e2e tests. Shouldn't the framework add contract tests?**

A. The "silent break" it guards against is already prevented structurally, so a contract-test framework is
disproportionate here.

1. **Messaging body fields are SHARED CONSTANTS, not stringly-typed on each side.** The entity-broadcast body keys
   are `EsqConstants.TEXT_*` used by BOTH the producer (enyMan `publishEntityEvent`) and the consumer (bizTree). A
   rename changes the ONE constant -> both sides move together at compile time. It is compile-linked, not a silent
   string-vs-string mismatch. (The generic RodEvent envelope carries no per-message schema, but the KEYS are the
   shared constants.)

2. **REST DTOs live in `common` and are exercised end-to-end.** The response DTOs (`EsqEntity`, ...) are in the
   shared `common` module -- a change is compile-visible to every user -- and springdoc's OpenAPI generates the
   explorer client, which the e2e suite drives against the real services. A breaking change fails e2e; it is
   caught, not silent (late, but present).

3. A dedicated framework (Pact / Spring Cloud Contract) would catch drift EARLIER (in CI, before e2e), but it is
   heavy infra -- a broker, contract repositories, a verification stage per service pair -- for the incremental
   gain over the coupling that shared DTOs + shared constants + e2e already provide at this scale. It is the
   adopter's choice to add if their team topology needs it (many independent teams evolving services in parallel);
   the framework does not impose it.

So the de-facto contract is the shared `common` types + `EsqConstants` + the e2e client; a contract-test framework
is a scale-dependent add, not a gap.

---

**Q. The UI library hardcodes its colors (the Windows-beveled palette -- `#ccc`, `#f0f0f0`, ... repeated across
the SCSS) with no CSS custom properties, no theme file, and no dark-mode path. Shouldn't there be a theming layer
so the look can be customized?**

A. No -- a theming layer has never been a requirement, and is not planned.

The Windows-era beveled look is a DELIBERATE design identity, not a customization surface. The palette is small and
consistent; it is applied directly because it IS the intended, fixed appearance -- not a default an adopter is
expected to re-skin. A CSS-custom-property / theme-file / dark-mode layer would be machinery for a customization
goal Esquire does not have.

More fundamentally: **Esquire is a BACK-OFFICE framework.** Every domain that adopts it is expected to develop its
OWN domain UI on top of the framework -- the shipped components are the back-office tooling surface (the tree
explorer, the entity dialogs), not a consumer-facing skin that each adopter re-themes. So a theming API is doubly
beside the point: adopters build their own UI for their own users, rather than re-colouring Esquire's back-office.
An adopter who does want to restyle a shipped component can override its styles at their own layer; the framework
does not owe a theming API it was never meant to provide.

---

**Q. The frontend (Esquire Explorer) has gaps a production app would close -- raw `console.*` instead of
structured / shippable logging, no global Angular `ErrorHandler` for unhandled exceptions, no theming layer.
Shouldn't the framework harden these?**

A. No -- because the **Esquire Explorer is NOT a final product; it is an EXAMPLE of using `esquire.ui.lib`.**

The Explorer is a reference application that demonstrates the framework's UI library (the tree explorer, the
entity dialogs, the server-driven forms) end to end. It is a worked example, not a shipping product an end user
runs. So the product-hardening a real app needs -- centralized / structured frontend logging, a global
error-boundary with graceful "something went wrong" UX, a theming layer / dark mode -- belongs to the DOMAIN
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
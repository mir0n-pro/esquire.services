| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|


# Esquire bizTree


**The read-side query model.** bizTree owns the in-memory, hierarchical view of
every business entity in the system -- organizations, users, accounts -- shaped
exactly as it's traversed (parent / children, ep_path, kind, status). It is fed
continuously by entity-broadcast JMS events from the entity-mutation services
(enyMan for entity CRUD, pacMan for accounting state) and rebuilds itself from
the canonical DB tables at startup. Other services, the BFF, and the UI ask
bizTree for tree queries instead of stitching joins against the database --
the cache is the answer to "what does the world look like right now, from
where I'm standing in it." The DB remains the source of truth; bizTree is the
shape that makes traversal cheap.


## The name

The cache is the **Supreme Ultimate Cache** (Chinese: 太極, *Tàijí*, "Supreme
Ultimate"); its structure is the **Taijitu** (太極圖, "Diagram of the Supreme
Ultimate") -- two equal monads, Yang and Yin, behind a single director that
swaps them on drift. Three labels for three audiences:

- **Supreme Ultimate Cache** -- the formal name. Doc headings, lead sentences.
- **Taijitu** -- the short, technical name. Code identifiers (`Taijitu`,
  `Monad`, `NightWatch`), filenames, log lines.
- **Anti-entropy double-buffer with shadow promotion** -- the dry industry
  vocabulary. Use when the metaphor isn't helpful (papers, externals,
  pattern catalogues).

Same shape as **Haubergeon / hauberk / Gatling harness** in the testing layer.


## What stays the same

bizTree's external surface is **not** changing in v1.2.5. The service still
pre-aggregates the business-tree data from `esq2025` so the GUI never hits
the database directly; the cache's job is still to stay up to date so reads
are cheap. Callers see the same two API contracts they always have:

- **Read-only access** to retrieve tree data.
- **Inbound event sink** to accept commands and messages that mutate the
  tree (entity-broadcast JMS, REST refresh, etc.).

External shape before and after the refactor: identical.


## What changes: the internal implementation

The internal structure of "the cache" is what the v1.2.5 sprint reshapes.
The new shape is named **Taijitu** -- the yin-yang figure -- and holds two
independent units called **monads** sitting behind one director.

### Taijitu with two monads

```
                +-------------------- Taijitu --------------------+
                |                                                 |
                |    +----- Yang (active) -----+                  |
   REST  --->   |    |  H2 table + queue       |  <--- entity     |
                |    |  status, flags          |       broadcast  |
                |    +-------------------------+                  |
                |                                                 |
                |    +----- Yin (passive) -----+                  |
                |    |  H2 table + queue       |  <--- (during    |
                |    |  status, flags          |       night-watch
                |    +-------------------------+        only)      |
                |                                                 |
                +-------------------------------------------------+
```

Each monad is a **complete bizTree data cache** and offers the same two
functional capabilities:

1. Read-only access for JPA queries.
2. Process incoming events on its own queue.

The two monads are **never directly connected.** The Taijitu sits between
them and the outside world.

### The two modes: Yang and Yin

At any moment one monad is in **Yang** mode and the other in **Yin**:

| Mode | Character | Role |
|---|---|---|
| **Yang** | bright, active | Serves REST read requests; receives every entity-broadcast event on its queue. |
| **Yin** | dark, passive | Stays idle until the night-watch routine activates it; receives entity-broadcast events during night-watch only. |

The pair is symmetric -- either monad can be in either mode at any time.
Mode swaps happen during the night-watch sweep when drift is detected.

### The director: Taijitu

The Taijitu owns:

1. **Read routing.** Every REST read request goes to **Yang** only.
2. **Event fanout.** Entity-broadcast events always flow to Yang's queue,
   and additionally to Yin's queue while Yin is part of an active
   night-watch sweep.
3. **Command issuance.** Drives both monads' state machines via the
   command set below.
4. **Result reception.** Both monads call back into a single Taijitu
   listener when commands complete.

### Monad state

Each monad carries:

| Field | Values | Meaning |
|---|---|---|
| `queueEnabled` | ON / OFF | Whether new messages can land on the monad's queue. |
| `processingEnabled` | ON / OFF | Whether the monad processes queued messages or pauses them. |
| `status` | `Idle` / `Loading` / `Loaded` / `Failed` | Lifecycle position. |

### Monad commands

The Taijitu sends three commands to a monad's queue:

| Command | Effect |
|---|---|
| `CLEAN` | Drop all cached data; `status -> Idle`. |
| `INIT` | `CLEAN`, then load from `esq2025` into the monad's local H2 table; `status -> Loading -> Loaded` (or `Failed`). |
| `CHECKSUM` | Compute a content hash of the local H2 table and call back the Taijitu listener with the result. |

Checksum SQL is shape-agnostic and content-only -- e.g.:

```sql
SELECT BIT_XOR(HASH('SHA256', STRINGDECODE(id || c1 || c2), 1))
  FROM <table>;
```

## Routines

The Taijitu owns four routines. Three are steady-state; one is the once-at-startup
bootstrap.

### 1. Bootstrap (process startup)

Brings Yang from `Idle` to `Loaded` and opens the REST gate. Runs once.

```
0. Yang.status = Idle
   Yang.queueEnabled       = OFF
   Yang.processingEnabled  = OFF
   Taijitu rejects any REST read.

1. Taijitu places INIT on Yang's queue.
   Taijitu flips Yang.queueEnabled = ON (processingEnabled stays OFF).
   Incoming entity-broadcast events now BUFFER in Yang's queue
   but are NOT yet processed -- the cache is mid-load.

2. Yang.status = Loading
   Yang loads from esq2025 into its H2 table.
   Yang.status = Loaded   (or Failed)
   Yang invokes the Taijitu listener.

3. Taijitu observes the status update:
     Loaded -> Taijitu flips Yang.processingEnabled = ON.
               Yang drains the queue: events buffered during INIT
               are applied in arrival order on the cache that's now
               consistent with esq2025 as of INIT's snapshot point.
               Taijitu enables REST passthrough to Yang.
     Failed -> disable Yang flags, CLEAN Yang queue (the buffered
               events go nowhere -- the cache is unusable), re-issue
               INIT (retry interval TBD -- see Open questions).
```

### 2. Night-watch sweep (periodic, also forceable via REST)

Yin is built from scratch out of `esq2025`, both monads are checksummed,
they swap if the hashes disagree, and Yin is torn down regardless of
outcome. This is the cache's anti-entropy mechanism.

```
0. Yang.status = Loaded
   Yin.status  = Idle
   Yin.queueEnabled, Yin.processingEnabled = OFF

1. Taijitu places INIT on Yin's queue.
   Taijitu flips Yin.queueEnabled = ON (processingEnabled stays OFF).
   (Incoming entity-broadcast events now BUFFER in Yin's queue --
   Yang processes them normally; Yin holds them until INIT lands.)

2. Yin.status = Loading
   Yin loads from esq2025 into its own H2 table.
   Yin.status = Loaded   (or Failed)
   Yin invokes the Taijitu listener.

3. Taijitu observes Yin status:
     Loaded -> Taijitu flips Yin.processingEnabled = ON.
               Yin drains its queue: events buffered during INIT are
               applied to the freshly-loaded H2 table in arrival order.
               Yin is now caught up with Yang on the same event stream.
               Proceed to step 4.
     Failed -> disable Yin flags, CLEAN Yin queue, abandon this sweep.
               Wait for the next scheduled round; no in-sweep retry loop.

4. Taijitu places CHECKSUM simultaneously on Yang and Yin queues.

5. Each monad computes its hash and calls back the Taijitu listener.

6. Taijitu waits for both results and compares them:
     MATCH    -> Yang is healthy. Proceed to step 7 (Yin cleanup only).
     MISMATCH -> Yang has drifted. Swap roles, then proceed to step 7.

7. Yin cleanup (always runs, MATCH or MISMATCH):
     - Disable Yin.queueEnabled, Yin.processingEnabled.
     - Place CLEAN on Yin's queue.
     - Yin.status = Idle.

   On swap (step 6 MISMATCH branch), "Yin" at step 7 means the *old* Yang
   (now demoted). The freshly-loaded data is already serving REST under
   the new Yang.
```

### 3. Emergency reload (event-triggered)

Triggered when Yang transitions to `Failed` *during steady-state* operation
(bad event, DB blip, etc.) rather than at startup. Without this routine a
single Failed transition would leave the cache stuck until the next
scheduled night-watch.

Mechanically: the Taijitu observes Yang's `Failed` transition and **kicks off
an immediate night-watch sweep ahead of schedule**. Same machinery, different
cue (status change instead of timer). The sweep's normal MISMATCH path will
naturally swap Yin into Yang once Yin has loaded successfully.

```
0. Yang.status = Failed (mid-stream)
1. Taijitu observes the transition.
2. Taijitu skips the next scheduled sweep timer and triggers night-watch now.
3. Night-watch routine runs from step 1 onward (see above).
```

### 4. Shutdown (process exit)

Triggered by the Spring lifecycle on container shutdown. Cheap: nothing is
persisted -- the H2 tables are in-memory only; `esq2025` is the source of
truth and is always reachable on next bootstrap.

```
1. Taijitu disables flags on both monads (queueEnabled, processingEnabled = OFF).
2. Taijitu places CLEAN on both queues (best-effort; not strictly required
   since the JVM is going down anyway -- the CLEAN exists so the lifecycle
   logs and metrics are tidy on the way out).
3. JVM exits.
```


## Invariants

These are not knobs. They are architectural rules baked into the design:

- **Two monads, always.** "Taijitu" *is* "two equal units behind one
  director." A different count would be a different architecture.
- **Sequential message processing.** Each monad processes its queue with
  exactly **one worker thread**. Event ordering is preserved end-to-end --
  `CREATE` before `UPDATE` before `DELETE`, in the order they arrived on
  the entity-broadcast topic. Concurrency on the write path is not on the
  table; introducing it would invalidate the cache's "shape matches DB"
  guarantee.
- **Event fanout during a sweep is ALL.** Yin receives every entity-
  broadcast event from the moment its `queueEnabled` flag flips ON, with
  no filtering for "arrived before Yin's INIT completed" or similar.
  Yin must converge with Yang on the same event stream, otherwise the
  CHECKSUM comparison at step 4 can't be trusted.
- **DB is the source of truth.** Monads hold in-memory H2 tables only;
  nothing in bizTree's process is persisted to disk. Restart always
  rebuilds from `esq2025`.
- **JMS subscription is non-durable.** bizTree's consumer on the
  `esquire.entity.broadcast` topic uses a *non-durable* subscription.
  Events missed during pod downtime are not retained by the broker --
  and don't need to be, because Bootstrap and the next night-watch sweep
  rebuild Yang from `esq2025` directly, the canonical source. Anti-entropy
  reconciliation replaces durable delivery as the "no event loss" mechanism.
  Side benefits: no JMS `clientId` on the connection, which removes
  biztree from the JMS-clientId-rolling-update trap; multiple bizTree pod
  replicas become safe to run if horizontal scaling is ever wanted (durable
  topic subs are one-active-consumer-only).


## Characteristics

Every Taijitu instance is parameterised around the invariants above.
Tuning knobs split into three groups: lifecycle timing, queue sizing,
timeouts, and behaviour flags. All exposed via Spring properties
(`taijitu.*`).

| Key | Default | Role |
|---|---|---|
| **Lifecycle timing** | | |
| `taijitu.nightwatch.interval` | `60m` | Cadence of the periodic night-watch sweep. |
| `taijitu.nightwatch.escalation.interval` | (TBD) | Shorter cadence applied to the *next* sweep when the previous one Failed. Open question: do we escalate or stay on the normal cadence? |
| `taijitu.bootstrap.retry.interval` | (TBD) | Wait between Yang INIT attempts when bootstrap fails. |
| **Queue sizing** | | |
| `taijitu.monad.queue.capacity` | `10000` | Max queue depth per monad. Back-pressure boundary on the event stream. |
| **Timeouts** | | |
| `taijitu.command.timeout` | `30s` | Max time the Taijitu waits for a non-CHECKSUM command (CLEAN, INIT, message processing) before flagging the monad Failed. |
| `taijitu.checksum.timeout` | `5m` | CHECKSUM can dominate -- big tree, full table scan with content hash. Sized separately. |
| **Behaviour** | | |
| `taijitu.checksum.algorithm` | `SHA256` | Hash function inside `BIT_XOR(HASH(...))`. Rarely changed; useful for benchmarking. |
| `taijitu.nightwatch.force.endpoint.enabled` | `true` | Whether the admin REST endpoint that triggers an immediate night-watch is wired up. |


## Open questions

- Should `Loaded` transition further to `Ready` once REST passthrough is
  enabled (i.e. a distinct "in-use" status separate from "data loaded")?
- **Bootstrap retry**: interval between INIT attempts when Yang fails at
  startup?
- **Failed-sweep follow-up**: should a Failed night-watch sweep cause the
  *next* sweep to run on a shorter / forced interval (escalation), or just
  fall back into the normal cadence?
- **Checksum algorithm**: `SHA256` is the current default, picked for
  caution. `MD5` is ~3-5x faster on most hardware and is acceptable here
  because the checksum is used for **drift detection between two
  locally-computed hashes of the same data**, not for security -- there
  is no attacker influencing the input, so MD5's collision-resistance
  weaknesses are irrelevant. Counter: modern CPUs ship SHA hardware
  acceleration (Intel SHA-NI, ARM crypto extensions), which can close
  the gap. Decision deferred until we benchmark CHECKSUM against the
  largest realistic tree on the target hardware (OKE A1.Flex ARM nodes
  or Docker Desktop x86

## Implementation notes

Sketches captured here as ideas firm up. Not commitments -- the final
shape lands when we start the code.

### Per-monad pre-composed SQL

Each monad owns its **own H2 table** (e.g. `BIZTREE_YANG_NODES`,
`BIZTREE_YIN_NODES`). Every DML / DQL statement therefore needs the right
table name.

**What carries over:** SQL bodies stay in a **properties file**
(`h2-cache-sql.properties`), one template per query, just as today. A
`{table}` token stands in for the table name. Editing SQL still means
editing properties -- no Java recompile, no diff hidden in code.

**What's new in this refactor (landed in the Yang step):** SQL
composition happens **once per monad**, not per call. Two value types:

- `BizTreeCacheSql` -- the property-backed *raw templates*: vendor-
  supplied, table-agnostic, holding the `{table}` token and the fragment
  shape (`selectCols`, `selectOne`, the per-query `WHERE` fragments). One
  per JVM; every monad shares this source.
- `CacheSqlSet` -- an immutable record of the *fully-assembled, executable*
  statements for **one** table. Built once by `CacheSqlSet.forTable(...)`,
  which substitutes `{table}` and joins the read fragments (cols + where
  [+ limit]). The hot path then runs `sql.findRoot()` directly -- zero
  string work per query.

```java
public record CacheSqlSet(
        String createTable, String createIndexParent, String createIndexEntityPk,
        String findRoot, String findNodes, String findPath,
        String findByEntityId, String findByNameKind, String findSubtree,
        String updateNode, String deleteNode, String moveNode,
        String moveAcctLink, String findFolderPks,
        String insertNode, String updatePath, String selectPaths) {

    public static CacheSqlSet forTable(BizTreeCacheSql t, String table) {
        String cols = sub(t.repo.selectCols(), table);
        String one  = sub(t.repo.selectOne(),  table);
        return new CacheSqlSet(
                sub(t.ddl.createTable(), table), /* ...indexes... */
                cols + sub(t.repo.findRoot(), table),               // pre-joined once
                /* ...all reads assembled here, all writes/loader substituted... */
                sub(t.loader.selectPaths(), table));
    }

    private static String sub(String template, String table) {
        return template.replace("{table}", table);
    }
}
```

One set = one table, by construction:

- Two monads (Taijitu) means two `CacheSqlSet`s, each bound to its own
  table -- no shared SQL forced on both. Yang holds exactly one, bound to
  `ESQ_TREE`, built as a Spring bean in `BizTreeH2Config`.
- Each set is immutable after `forTable`, so it's effectively static for
  the monad's lifetime -- the per-call concatenation the old repository
  did (`selectCols() + findRoot()` on every read) is gone.
- Future-proof: a third monad shape gets its own table + its own set with
  no disturbance to the existing two.

Implications for the persistence layer:

- The cache path already uses raw `JdbcTemplate` fed by the resolved SQL
  (the repository executes `set.findRoot()` etc.); no JPA `@Entity` ->
  table coupling on the monad-internal path. JPA stays only on the
  entity-source side (the org/usr/acct repositories the loader bulk-reads).
- Indexes and the PK constraint in the H2 DDL are parameterized the same
  way (`{table}_PARENT_I`, `{table}_ENTITY_PK_I`, `CONSTRAINT {table}_PK`)
  so two tables coexist in one H2 instance without name collisions. They're
  created at INIT time alongside the data load.

### Timeouts and command cancellation

The Taijitu enforces the timeouts declared in the Characteristics table
(`taijitu.command.timeout`, `taijitu.checksum.timeout`). Enforcement is
not just "ignore the result if it's slow" -- the in-flight SQL must
actually be cancelled, otherwise a slow query keeps a DB connection and a
JVM thread tied up indefinitely while the cache thinks the command failed.

**Two execution lanes per monad:**

- The monad's **single-threaded queue worker** processes `CLEAN`, `INIT`,
  and entity-broadcast event commands sequentially. These are the
  ordering-sensitive commands; running them on one thread preserves
  event ordering (the invariant).
- `CHECKSUM` is the exception: it runs on a **temporary one-run thread**,
  spawned by the worker and detached from the queue. A full content-hash
  scan over the tree can take seconds-to-minutes; if it sat on the queue
  thread it would block every other command (event processing included)
  for that duration. Spawning a one-run thread keeps the queue free.

**Command-to-listener handshake:**

Every command carries a reference to a `CommandListener`. When a worker
*starts* a command, before it issues the SQL, it calls back the listener
with two things:

1. A handle to the command that's now in-flight.
2. A `Cancellable` interface (`void cancel()`) that, when invoked, aborts
   the in-flight SQL by calling `Statement.cancel()` on the underlying
   JDBC statement. H2 supports query cancellation cleanly; the worker
   catches the resulting `SQLException`, marks the command as cancelled,
   and the monad transitions back to a clean state for the next message.

```java
public interface CommandListener {
    void started(MonadCommand cmd, Cancellable handle);
    void finished(MonadCommand cmd, CommandResult result);
}

public interface Cancellable {
    void cancel();   // aborts the in-flight SQL via Statement.cancel()
}
```

**Timeout flow:**

1. Taijitu places a command on a monad's queue with `command.timeout` (or
   `checksum.timeout` for `CHECKSUM`) attached.
2. Monad worker picks up the command, opens the JDBC statement, calls
   `listener.started(cmd, () -> stmt.cancel())`, then runs the SQL.
3. Taijitu starts a timer when it receives `started(...)`.
4. If the timer fires before `finished(...)` lands:
   - Taijitu calls `cancellable.cancel()`.
   - `stmt.cancel()` propagates to H2; the running query throws
     `SQLException` on the worker thread (or one-run CHECKSUM thread).
   - Worker catches it, calls `listener.finished(cmd, Cancelled)`.
   - Taijitu flips the monad's `status -> Failed` and proceeds per the
     routine that issued the command (Bootstrap re-INITs, night-watch
     abandons the sweep, Emergency reload kicks, etc.).

**Why the worker notifies the listener at start, not at queue-time:**

Queue depth can be non-trivial under load. If the timeout started when
the command was *queued* rather than when execution *began*, a long
queue would cause apparent timeouts on commands that haven't even run
yet. The "started" callback ties the timer to actual execution, not
arrival.


### The two-flag INIT sequence (why queueEnabled + processingEnabled are separate)

Each monad carries two independent flags, not one. The reason is the
INIT-with-live-event-stream problem -- the same race the v1.2.5 sprint
is built to close.

While INIT is running (reading the snapshot out of `esq2025` into the
monad's H2 table), entity-broadcast events keep arriving on the JMS
topic. Three possible responses:

- **Drop events while INIT runs.** The cache misses every event that
  happened between INIT's snapshot point and the moment processing comes
  online. Drift; the night-watch sweep would have to repair it on the
  next round. Unacceptable for Yang on the live path.
- **Apply events while INIT runs.** They land on a half-loaded table.
  Some target rows aren't there yet; INSERTs collide with the bulk load;
  state corrupts. Worse than dropping.
- **Buffer events while INIT runs, apply them after.** Queue them, hold
  processing, then drain in arrival order onto the fully-loaded cache.
  No loss, no corruption. This is what the two flags enable.

```
queueEnabled       processingEnabled    behaviour
----------------   ------------------   ------------------------------------
OFF                OFF                  Monad is idle; events are dropped.
ON                 OFF                  Events buffer in the queue; nothing
                                        is applied to the cache.
                                        ^^^ the INIT-in-progress state
ON                 ON                   Normal steady-state operation:
                                        events flow into the queue and the
                                        worker pulls + applies them.
OFF                ON                   Unused; intentionally not reached.
```

Sequence on both sides (Bootstrap for Yang, Night-watch for Yin):

```
1. Taijitu sends INIT.
2. Taijitu flips queueEnabled = ON.        <-- buffer starts
   (processingEnabled stays OFF)
3. INIT runs -- bulk-loads esq2025 into H2.
4a. INIT succeeded:
      Taijitu flips processingEnabled = ON.    <-- drain begins
      Worker pulls every buffered event in
      arrival order, applies it on the now-
      consistent cache.
4b. INIT failed:
      Cache is in an unknown / partial state;
      there is no safe way to apply buffered
      events. Taijitu flips both flags OFF,
      CLEAN-s the queue (events dropped),
      and retries / abandons per routine.
```

Symmetry: same two-step flip happens on Yang at Bootstrap and on Yin at
each Night-watch sweep. The flags are the contract that makes the
"INIT + live events" problem solvable without losing data and without
corrupting the cache.


### Other implementation ideas

(none yet -- this section grows as more shape becomes clear)


## Migration plan

The refactor proceeds in three structural steps. Step 1 ships **before** any
Taijitu code lands; it reshapes the current implementation into the slots
that the Taijitu will eventually occupy, with no functional change. Steps 2
and 3 then build the new pieces and swap them in.

### Step 1 -- Preparatory refactor (no behaviour change)

Reshape the current bizTree internals to expose the seams Taijitu plugs
into. After this step bizTree behaves identically to today; only the
arrangement of classes has changed.

Three new structural elements:

- **`AccessPoint`** -- the single outer-edge object. All inbound traffic
  flows through it: REST requests from the controller, JMS messages from
  the entity-broadcast consumer. Replaces the ad-hoc collection of
  separate entry points with one routing surface.

- **`Director` (edging frame for the current implementation)** -- the
  layer between `AccessPoint` and today's cache. In step 1 this is a
  thin pass-through that delegates every call to the existing
  `IBizTreeCacheRepository` and its sibling classes. The Director slot
  is what Step 3 swaps for the Taijitu.

  Naming note: this outer-edge Director is the **slot**; the Taijitu is
  itself a director (of its two monads), so the eventual code reads
  *"Taijitu fills the Director slot"*. Two senses of "director" --
  outer (the slot occupant) and inner (the Taijitu's role over the
  monads). They never both exist at once: the slot holds either the
  Step 1 pass-through *or* the Taijitu, never both.

- **`MessageHandlerHub`** -- the per-kind handler-dispatch logic
  (currently aggregated inside `EsqEntityBroadcastConsumer`) extracted
  into its own object. In Step 1 the hub is owned by the Director
  pass-through; in Step 3 each Monad embeds one.

Step 1 is fully landable on its own -- no API change, no functional
change, fully testable against today's behaviour. Existing acceptance
tests must stay green.

### Step 2 -- Build the Taijitu pieces (independently testable)

Build the new internals without yet wiring them in. Each piece is
contained and unit-testable in isolation; nothing in this step affects
the live cache.

1. `Monad` -- state machine (`Idle` / `Loading` / `Loaded` / `Failed`),
   two flags (`queueEnabled`, `processingEnabled`), single-threaded
   queue, embedded `MessageHandlerHub` from Step 1.
2. `CacheSqlSet` -- precomposed, table-bound executable SQL, built once
   per monad by `CacheSqlSet.forTable(BizTreeCacheSql, table)` from the
   `{table}`-token properties source. (Landed in the Yang step.)
3. `MonadCommand` -- sealed type for `Clean | Init | Checksum` plus the
   `CommandListener` / `Cancellable` handshake from the Timeouts section.
4. `Taijitu` -- the director itself, wrapping two `Monad` instances and
   owning the four routines (Bootstrap / Night-watch / Emergency reload
   / Shutdown).

### Step 3 -- Swap the Director slot

Replace the Step 1 pass-through Director with the Taijitu. Mechanically:
take the `@Component` annotation off the pass-through class and put it
on the Taijitu wrapper. `AccessPoint` keeps calling `Director`; the
Director slot now resolves to the Taijitu; the Taijitu routes reads to
Yang and fans events to its internal monads.

After Step 3 the legacy implementation classes stay in tree as inert
Java (no `@Component`) -- emergency switch-back is the reverse one-line
annotation move per the [invisibility rule](#what-stays-the-same).


## Pattern identification

The Taijitu shape composes three patterns that *are* well-known
individually, but I don't know of a single canonical name for the
composite in the literature -- so the Taijitu framing is, as far as I can
tell, an Esquire-original synthesis of these building blocks:

- **Anti-entropy repair** (Dynamo / Cassandra family). Periodic
  background routine that recomputes content hashes on two replicas
  (here: Yang + a freshly-loaded Yin), compares them, and reconciles on
  divergence. The night-watch sweep is exactly an anti-entropy run.
- **Double-buffering** (graphics / OS rendering). Two equal buffers, one
  serving reads (front / Yang) while the other is prepared (back / Yin);
  atomic pointer flip promotes back to front. The Taijitu's swap on
  checksum mismatch is the pointer flip.
- **Shadow / phantom replica** (used in payments, search-indexing
  rebuilds). A parallel copy is built from the source of truth alongside
  the live system, exists only long enough to validate the live system,
  and is either discarded (no drift) or promoted (drift). The Yin
  lifecycle is the shadow lifecycle.

What's distinctive about the Esquire Taijitu vs. each of these on its own:

- Anti-entropy systems usually compare two *equal long-lived* replicas;
  here the comparator is built fresh per sweep from the canonical DB and
  discarded afterward.
- Double-buffering classically swaps on every frame; the Taijitu only
  swaps on drift.
- Shadow replicas in industry usually receive a fork of *all* traffic
  for the duration of the shadow window; the Taijitu's Yin receives only
  the *mutating* event stream, never read traffic, and only during the
  sweep.

Useful one-liner if a name is needed in code or docs:
**"anti-entropy double-buffer with shadow promotion."** Less colourful
than Taijitu, but matches the industry vocabulary exactly.

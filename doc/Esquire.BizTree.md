| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|


# ![biztree logo](media/dbltree.32.png) **Esquire bizTree**


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

The cache is the **Supreme Ultimate Cache** (Chinese: 太極, *Taiji*, "Supreme
Ultimate"); its structure is the **Taijitu** (太極圖, "Diagram of the Supreme
Ultimate") -- two equal monads, yang and yin, behind a single director that
swaps them on drift. Three labels for three audiences:

- **Supreme Ultimate Cache** -- the formal name. Doc headings, lead sentences.
- **Taijitu** -- the short, technical name. Code identifiers (`ATaijituRig`,
  `Monad`, the `sweep` night-watch), filenames, log lines.
- **Anti-entropy double-buffer with shadow promotion** -- the dry industry
  vocabulary. Use when the metaphor isn't helpful (papers, externals,
  pattern catalogues).

Same shape as **Haubergeon / hauberk / Gatling harness** in the testing layer.


## The external surface

bizTree exposes two contracts, and nothing else:

- **Read-only access** to retrieve tree data (the REST endpoints `/esq`,
  `/esq-path`, `/esq-enode`, `/esq-tree`, plus the admin `/esq-sweep`).
- **Inbound event sink** that accepts the entity-broadcast JMS events that
  mutate the tree.

The cache's internal structure -- the Taijitu below -- is invisible to callers.
They see the same API whichever director implementation is active.


## Structure: two monads behind one director

The cache is a **Taijitu**: two independent **monads** behind one **director**.


![biztree logo](media/BizTreeModel.png)

Each monad is a **complete bizTree data cache** -- its own H2 table, its own
queue, its own worker -- and offers the same two capabilities: read access for
queries, and event processing on its own queue. The two monads are never
directly connected; the director sits between them and the outside world. The
two monad instances are named `monad` and `danom` (the latter is "monad"
reversed -- a name, not a role); which one is serving vs. shadow is a role
pointer the director flips.


### The two roles: yang and yin

At any moment one monad holds the **yang** role and the other **yin**:

| Role | Character | Behaviour |
|---|---|---|
| **yang** | bright, serving | Answers REST reads; receives every entity-broadcast event on its queue. |
| **yin** | dark, shadow | Idle until a night-watch sweep loads it fresh from the DB; receives entity-broadcast events only for the duration of the sweep. |

The pair is symmetric -- either monad can hold either role. The roles swap
during a sweep when drift is detected (see *Night-watch sweep*).


### The director

The director (`ATaijituRig` in `pro.mir0n.utils.taijitu`, specialized by
bizTree's `BizTreeDirectorTaijitu`) owns:

1. **Read routing.** Every REST read goes to the serving monad (`yang()`).
2. **Event fanout.** Entity-broadcast events always flow to yang's queue, and
   additionally to yin's queue while yin is part of an active sweep.
3. **Command issuance.** Drives each monad's state machine through the command
   set below.
4. **Result reception.** Each monad calls back a per-monad listener
   (`gateFor(monad)`) when a command completes; the listener drives that
   monad's gates off the result.


### Monad state

Each monad carries:

| Field | Values | Meaning |
|---|---|---|
| `queueEnabled` | on / off | Whether new events can land on the monad's queue. |
| `processingEnabled` | on / off | Whether the worker drains the queue or holds it. |
| `status` | `IDLE` / `LOADING` / `LOADED` / `FAILED` | Lifecycle position. |


### Monad commands

The director issues three commands; each rides the monad's queue as a
`QueueItem` (`MonadCmd`):

| Command | Effect |
|---|---|
| `CLEAR` | Truncate the monad's H2 table; `status -> IDLE`. |
| `LOAD`  | Bulk-load the tree from `esq2025` into the monad's H2 table; `status -> LOADING -> LOADED` (or `FAILED`). |
| `CHECKSUM` | Compute a content hash of the monad's H2 table and report it back through the result listener. |

`LOAD` and `CLEAR` run **synchronously** on the monad's single worker thread
(`doCommand` blocks until the worker signals). `CHECKSUM` is the exception --
see *Off-worker CHECKSUM* below.


### The checksum

The digest is **order-independent** (rows concatenated in `TREE_PK` order so
physical row order doesn't matter) and **content-only** (no security role -- it
detects drift between two locally-computed hashes of the same data, so MD5 is
the right, fast choice):

```sql
SELECT RAWTOHEX(HASH('MD5', STRINGTOUTF8(COALESCE(GROUP_CONCAT(
    TREE_PK || '~' || TREE_ET_PK || '~' || COALESCE(TREE_NAME,'') || '~' || ...
    ORDER BY TREE_PK SEPARATOR '|'), ''))))
  FROM {table};
```

`COALESCE(..., '')` keeps an empty table's digest non-null (the MD5 of the
empty string), so two empty legs match rather than both reporting "no value".


## Routines

The director owns three routines: a once-at-startup bootstrap, the periodic
night-watch sweep, and shutdown.


### Bootstrap (process startup)

Brings the serving monad from `IDLE` to `LOADED` and opens the read gate. Runs
once, synchronously; a failed load retries (~5 s apart) until it succeeds.

```
0. yang.status = IDLE; gates off; the director rejects reads.
1. CLEAR yang (clean slate), then LOAD yang with queueEnabled = ON,
   processingEnabled = OFF -- events arriving mid-load BUFFER, are not applied.
2. yang.status -> LOADING -> LOADED (or FAILED).
3. LOADED  -> processingEnabled = ON; yang drains the buffered events in
              arrival order onto the now-consistent cache; reads open.
   FAILED  -> gates off, CLEAR, retry LOAD after the retry delay.
```


### Night-watch sweep (periodic; also forceable via REST)

The anti-entropy mechanism. The shadow is rebuilt from `esq2025`, both monads
are checksummed, and the director reacts to drift. The sweep is **serialized**
(one at a time, guarded) and **re-armed after it ends** -- the next sweep starts
`sweep.interval-ms` after the previous one finishes, not on a fixed clock.
`/esq-sweep` forces a sweep asynchronously (the director runs it on the
night-watch thread and returns `202` at once -- the request never blocks for a
full sweep).

```
1. LOAD the shadow (yin) fresh from esq2025 into its own table.
   Shadow LOAD FAILED -> abandon this sweep; retry next round.
2. Submit CHECKSUM to BOTH legs; collect each digest within sweep.timeout-ms.
3. A FAILED (timed-out / cancelled) digest is inconclusive -> abandon, retry next round.
4. Compare the two digests:
     MATCH    -> serving monad is healthy; nothing to do.
     MISMATCH -> react per the configured mode (below).
5. Clear the shadow back to idle (always).
```

On mismatch the reaction is configurable (`biztree.taijitu.on-mismatch`):

| Mode | Reaction |
|---|---|
| `LOG` | Record the drift, keep serving the current monad (conservative). |
| `SWAP` | Promote the freshly-loaded shadow to serving (the double-buffer pointer flip); the old serving monad becomes the next shadow. |
| `TERMINATE` | Exit the process so an orchestrator (k8s) relaunches it from a clean load. |


### Shutdown (process exit)

Spring lifecycle stops both monads' workers. Nothing is persisted -- the H2
tables are in-memory; `esq2025` is the source of truth and is rebuilt on the
next bootstrap.


## Readiness

The bootstrap LOAD blocks after the HTTP server is already accepting
connections, so a plain health check would report UP before the cache can
serve. bizTree closes that window with a readiness gate:

- `isReady()` on the director is true once the serving monad is `LOADED`.
- `CacheReadinessHealthIndicator` (health name `cacheReadiness`) reports UP/DOWN
  off `isReady()` and is wired into the **readiness** probe group only
  (`/actuator/health/readiness`). k8s holds traffic until the cache is serving.
- **Liveness** (`/actuator/health/liveness`) is `livenessState` only -- a slow
  load delays traffic but never crashloops the pod.


## Invariants

Architectural rules, not knobs:

- **Two monads, always.** "Taijitu" *is* "two equal units behind one director."
- **Sequential message processing.** Each monad drains its queue with exactly
  one worker thread; event ordering (`CREATE` before `UPDATE` before `DELETE`)
  is preserved end-to-end. There is no concurrency on the write path.
- **Event fanout during a sweep is ALL.** From the moment yin's `queueEnabled`
  flips on it receives every entity-broadcast event, unfiltered, so it converges
  with yang on the same stream -- otherwise the CHECKSUM comparison can't be
  trusted.
- **DB is the source of truth.** Monads hold in-memory H2 tables only; restart
  always rebuilds from `esq2025`.
- **JMS subscription is non-durable.** The consumer on `esquire.entity.broadcast`
  uses a non-durable subscription: events missed during downtime are not retained
  by the broker, and don't need to be -- bootstrap and the next sweep rebuild from
  the canonical DB. Anti-entropy reconciliation replaces durable delivery as the
  "no event loss" mechanism. Side benefits: no JMS `clientId` on the connection
  (so bizTree is free of the clientId rolling-update deadlock), and multiple pod
  replicas become safe to run.


## Configuration

All exposed via Spring properties (env-overridable). The **Default** column is the
shipped deployment default (compose + Helm); the **Code fallback** is the value the
`@Value` binding uses if the property is left entirely unset:

| Property | Default | Code fallback | Role |
|---|---|---|---|
| `biztree.director` | `taijitu` | `legacy` | Cache director: `taijitu` (two-monad + night-watch) or `legacy` (single-pass). |
| `biztree.taijitu.on-mismatch` | `SWAP` | `LOG` | Drift reaction: `LOG` (keep serving) / `SWAP` (promote shadow) / `TERMINATE` (exit for relaunch). |
| `biztree.taijitu.sweep.interval-ms` | `600000` (10 min) | `10000` | Delay between the end of one sweep and the start of the next. |
| `biztree.taijitu.sweep.timeout-ms` | `10000` (10 s) | `10000` | Per-leg CHECKSUM deadline within a sweep. |
| `biztree.monad.queue.capacity` | `4096` | `4096` | Max queue depth per monad (event-stream back-pressure boundary). |
| `biztree.cache.table` | `ESQ_TREE` | `ESQ_TREE` | Base cache table; each monad suffixes it (`ESQ_TREE_MONAD`, `ESQ_TREE_DANOM`). |

The compose / Helm deployments wire these from environment variables:
`BIZTREE_DIRECTOR`, `BIZTREE_TAIJITU_ON_MISMATCH`, `BIZTREE_TAIJITU_SWEEP_INTERVAL_MS`,
`BIZTREE_TAIJITU_SWEEP_TIMEOUT_MS`, `BIZTREE_MONAD_QUEUE_CAPACITY`.


## How it's built

### Per-monad pre-composed SQL

Each monad owns its **own H2 table** (`ESQ_TREE_MONAD`, `ESQ_TREE_DANOM`),
created in one shared H2 datasource. SQL bodies live in a properties file
(`h2-cache-sql.properties`), one template per query, with a `{table}` token
standing in for the table name -- editing SQL means editing properties, no
recompile.

Composition happens **once per monad**, not per call. Two value types:

- `BizTreeCacheSql` -- the property-backed raw templates (table-agnostic, holding
  the `{table}` token and the fragment shapes). One per JVM; shared.
- `CacheSqlSet` -- an immutable record of the fully-assembled, executable
  statements for one table, built by `CacheSqlSet.forTable(templates, table)`,
  which substitutes `{table}` and pre-joins the read fragments. The hot path
  then runs ready statements with zero per-query string work.

`BizTreeDirectorConfig` builds a dedicated backend per monad inline
(`buildCache(table)`): its own `CacheSqlSet` + DDL, `BizTreeCacheRepository`,
`BizTreeCacheLoader`, and read service -- all bound to that monad's table on the
shared `cacheJdbcTemplate`. This per-table isolation is what lets the shadow
load and clear its own table without colliding with the serving monad's.


### Off-worker CHECKSUM with cancellation

`LOAD` and `CLEAR` are ordering-sensitive and run on the monad's single worker
thread. `CHECKSUM` is different: a full content-hash scan can take
seconds-to-minutes, so it runs on a **separate one-run thread** (`checksumExec`)
detached from the queue, keeping the worker free to process events.

A timed-out checksum must actually abort the running query, not merely ignore a
late result (otherwise a slow scan ties up a DB connection and a thread). The
seam:

- When the checksum starts, before it issues the query, it registers an
  `ICancelable` with the result listener via `onStarted(command, cancelable)`.
- bizTree's `PrepareStatementCancelable` holds the executing `PreparedStatement`;
  its `cancel()` calls `Statement.cancel()`, which H2 honours -- the in-flight
  query throws and the leg reports `FAILED`.
- `CancelableStatement` bundles the statement with its connection so a
  try-with-resources releases both on every path (including a cancel mid-query).

The sweep's per-leg `timeout-ms` arms this: if a digest doesn't arrive in time
the director cancels the query, the leg comes back `FAILED`, and the sweep is
abandoned as inconclusive (a `FAILED` leg is not evidence of drift).


### The two-flag load sequence

Each monad carries two independent gates -- `queueEnabled` and
`processingEnabled` -- not one, to solve the load-with-live-event-stream
problem:

```
queueEnabled   processingEnabled   behaviour
------------   -----------------   ----------------------------------------
off            off                 idle; events dropped
on             off                 events BUFFER in the queue; none applied   <- mid-LOAD
on             on                  steady state: worker drains + applies
```

While LOAD reads the snapshot out of `esq2025`, events keep arriving. Dropping
them would drift the cache; applying them to a half-loaded table would corrupt
it. Buffering (queue on, processing off) then draining after LOAD completes
loses nothing and corrupts nothing. The same two-step flip runs on yang at
bootstrap and on yin at each sweep.


## A reusable pattern: `common/taijitu`

The control core lives in `pro.mir0n.utils.taijitu` (with the queue machinery in
`pro.mir0n.utils.concurrent`) and is **domain-agnostic** -- it knows nothing
about trees, entities, or SQL. It is a reusable pattern: any cache that needs
anti-entropy reconciliation against a source of truth can adopt it by supplying
two small, ordinary beans -- the most logical and easily-employed extension
points being plain POJO / JPA-backed Spring beans:

- **Extend `AMonad`** (the monad mechanics: queue, gates, status, off-worker
  CHECKSUM) and fill the one cache-work hook -- `LOAD` / `CLEAR` / `CHECKSUM` and
  event apply against whatever backing store the domain uses. bizTree's `Monad`
  does this over H2 + JPA loaders.
- **Extend `ATaijituRig`** (the two-monad director, swap, gates, night-watch) and
  add the domain read methods, routed to the serving monad. bizTree's
  `BizTreeDirectorTaijitu` (implementing `IBizTreeDirector`) does this.
- **Wire the two monads + the director** as ordinary beans in a `@Configuration`
  (bizTree: `BizTreeDirectorConfig`).

The framework supplies the control contracts (`IMonad`, `ITaijituRig`), the
command vocabulary (`MonadCmd`), the status machine (`MonadStatus`), the drift
policy (`MismatchAction`), the listener/cancel seams (`ICmdResponseListener`,
`ICancelable`), and the night-watch itself. A new application reuses all of that
unchanged and contributes only its domain-specific cache work and reads.
bizTree is the first such application.


## Pattern identification

The Taijitu composes three patterns that are well-known individually; the
composite is an Esquire-original synthesis:

- **Anti-entropy repair** (Dynamo / Cassandra family). A periodic routine
  recomputes content hashes on two replicas (yang + a freshly-loaded yin),
  compares, and reconciles on divergence. The night-watch sweep is exactly an
  anti-entropy run -- except the comparator is built fresh per sweep from the
  canonical DB and discarded afterward, not a long-lived second replica.
- **Double-buffering** (graphics / OS rendering). Two equal buffers, one serving
  (front / yang) while the other is prepared (back / yin); an atomic pointer flip
  promotes back to front. The swap-on-mismatch is the pointer flip -- except it
  only swaps on drift, not every frame.
- **Shadow / phantom replica** (payments, search-index rebuilds). A parallel copy
  is built from the source of truth, exists only long enough to validate the live
  system, and is discarded (no drift) or promoted (drift). The yin lifecycle is
  the shadow lifecycle -- except yin receives only the mutating event stream,
  never read traffic, and only during the sweep.

One-liner when the metaphor isn't wanted:
**"anti-entropy double-buffer with shadow promotion."**

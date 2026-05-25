| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|


# ![Alt text](media/hauberk.svg) Esquire Haubergeon 


**Harness for Gatling Engine.** The Gatling-based test harness for the
Esquire stack. Drives HTTP load against a running deployment from
outside; produces per-request timing data with multi-tier attribution
(client / gateway / service / DB).

## The name

A **haubergeon** is a shorter version of a **hauberk** -- medieval
chainmail body armor.
A haubergeon is mid-thigh instead of past-the-knee and has short sleeves.

- Just as a haubergeon is built from many small interlocking metal
  rings, a Gatling Simulation is composed of many small reusable
  `ChainBuilder` atoms wired together into Scenarios.
  **Same shape, same idea.**
- **The name pins the scope precisely.** Haubergeon covers *one*
  concern -- stress / load / smoke / race-repro / soak. Not unit, not
  contract, not chaos, not browser e2e. One piece of the testing
  ensemble, not the whole alwhite armor.

Alongside *haubergeon* you'll see *hauberk* used in code, config, and
system properties (`hauberk.cmd`, `pro.mir0n.esquire.hauberk`,
`esq-hauberk-S`, `hauberk.metrics`) -- same thing, shorter name.

## Where it lives

`explorer/hauberk/` -- Maven module, sibling of `explorer/frontend/`,
`explorer/backend/` (the BFF tier), and `explorer/e2e-test/`.

## Build

```
mvn -pl hauberk install            (from services/ -- builds + installs)
```

Output: `explorer/hauberk/target/hauberk.jar` -- single fat jar with
Gatling 3.13, picocli, and all transitive dependencies. One artifact
to deploy or copy anywhere.

## Run

`hauberk.cmd <subcommand> [options]` from `explorer/hauberk/`. The
launcher is a one-line `.cmd` that adds the JVM `--add-opens` flags
Gatling 3.13 needs and forwards every argument to the fat jar:

```
java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
     -jar target/hauberk.jar %*
```

### Subcommands

| Subcommand | What it does |
|---|---|
| `list` | Print the catalog of discovered `*Simulation` classes (FQCN + short alias) |
| `run <sim> [opts]` | Launch a Gatling Simulation programmatically |
| `summary <csv>` | Print per-URL percentile summary for a saved performance-matrix CSV |
| `diff <csv-a> <csv-b>` | Side-by-side per-URL percentile comparison with deltas |
| `help [command]` | picocli help -- standalone or per-subcommand |

Examples:

```
hauberk.cmd list
hauberk.cmd run entity-smoke --metrics
hauberk.cmd run entity-smoke --metrics --config hauberk-k8s.properties
hauberk.cmd run super-load --metrics --duration 120
hauberk.cmd summary output/EntitySmokeSimulation-.../entity-smoke.csv
hauberk.cmd diff   output/<run-a>/<scn>.csv output/<run-b>/<scn>.csv
```

## Identity model -- three certified clients (one per non-browser auth pattern)

The hauberk operates as one of three KC clients, each backed by a seeded
admin USR in `db.seed` and each calibrated for a different gateway auth
pattern:

| KC client | Mode | Backed by USR | esq_rootpath | Used when |
|---|---|---|---|---|
| `esq-hauberk`   | Plain JWT (gateway local-validates JWS) | Test Driver,   uid=15 | `1.14.` | Default; service-to-service style load |
| `esq-hauberk-S` | Vanilla Token Relay (client sends HTTP Basic; gateway brokers JWT via `client_credentials` and caches per `client_id`) | Test Driver S, uid=16 | `1.14.` | Claim-hiding without a BFF tier; A/B against Plain JWT |
| `esq-hauberk-M` | Phantom Token Relay (client sends stripped Bearer; gateway exchanges via RFC 8693 token-exchange and caches per `jti`) | Test Driver M, uid=17 | `1.14.` | Token-portability + claim-hiding |

All three Test Drivers are kind=32 admins under **Test House** (org pk=14,
seeded at `db.seed/postgres/fill/initial-entities.sql`). Their
`esq_rootpath="1.14."` caps their visibility -- they can see and modify
the Test House subtree, **nothing else** in the entity tree. The whole
hauberk playground roots itself under Test House (config knob
`playground.parent.id=14`).

Reserved future slot: `esq-hauberk-L` -- for an additional protocol
variant (JWE-when-supported, mTLS-bound tokens, DPoP, etc.).

> **Note (v1.2.4 close):** the hauberk harness's auth client (`KcTokenClient` +
> `RefreshableToken`) currently sends `client_credentials` and Bearer JWT for
> all three clients. The gateway-side Vanilla Token Relay enforcement
> rejects Bearer with `azp=esq-hauberk-S`; the hauberk harness needs a follow-up
> update to send HTTP Basic for `-S` and the stripped flow for `-M`. Tracked
> for v1.2.5+ alongside the race-fix work.

## Simulation catalog

The hauberk ships 16 Simulations. Discover at runtime via
`hauberk.cmd list`; each is a Java class under
`src/main/java/.../hauberk/simulations/`.

### Smoke (one VU, end-to-end functional verification)

| Simulation | What it exercises |
|---|---|
| `SmokeSimulation` | Trivial single GET; verifies KC token + gateway routing |
| `EntitySmokeSimulation` | End-to-end entity walk: ensure office, create user, create account, deposit, reads, cleanup |
| `MoveSmokeSimulation` | `/esq-move` for USR + ORG re-parenting on a 4-deep office chain |
| `KcIntegrationSmokeSimulation` | BFF / OIDC code+PKCE handshake -- enyMan + keySmith + kcMaster + KC + BFF login |
| `CompareTreesSimulation` | Diffs bizTree cache against the natural FK tree -- catches cache drift |

### Load (forever-loops capped by `--duration`)

| Simulation | What it loops |
|---|---|
| `ReadLoadSimulation` | Random `GET /esq-cmd?kind=34&id=...` against a user pool |
| `UpdateLoadSimulation` | Random `POST /esq-cmd-save` postal-address updates |
| `CreateLoadSimulation` | `CreateUser + DeleteEntity` at the deepest office in the playground |
| `MoveLoadSimulation` | Oscillates `w1-l3` between L1 and `w1-l2` (move-concurrency stress) |
| `TxLoadSimulation` | Deposit + Withdrawal cycles against random accounts (net-zero per iter) |
| `SuperLoadSimulation` | Runs all 5 above scenarios in parallel against a shared playground (a "super" sim that orchestrates 5 sims; per-scenario output) |

### Setup / teardown

| Simulation | Purpose |
|---|---|
| `PrepareForAnythingSimulation` | Builds the Load playground: D nested offices x N users x M accounts (knobs in `hauberk.properties`) |
| `CleanHouseSimulation` | Stateless playground purge -- finds `hauberk-office-smoke` by name and deletes the subtree |
| `ResidueCleanupSimulation` | Targeted purge of leftover offices matching the hauberk naming convention |

### Race-condition repros

`RaceCacheLoadSimulation` and `RaceMoveCreateSimulation` are
self-validating; both surface known races in the entity-broadcast +
bizTree-cache pipeline.

Full catalog, repro commands (compose + local k8s), and PASS/FAIL
behavior live in a dedicated doc: see
[Race.Conditions.Repro.md](Race.Conditions.Repro.md).

## Configuration

Everything that varies across runs comes from `hauberk.properties`,
overlay files, CLI flags, or `-D` system properties -- never from
hardcoded Java values.

### Property files

The utility is **client-agnostic** -- it never knows which KC client it
runs as. The loaded config tells it: `kc.client.id`, `kc.client.secret`,
and `tokenRelay.type` (`plain` | `vanilla` | `phantom` -- names the
gateway-side Token Relay pattern this run targets). One config file
per run.

`hauberk.properties` -- canonical default (compose endpoints,
`tokenRelay.type=plain`, default client, prep / move / super shape
knobs).

`hauberk-k8s.properties` -- overlay for local k8s (Docker Desktop)
endpoints. Inherits everything else from `hauberk.properties`.

`hauberk-oke.properties` -- overlay for OKE endpoints. Inherits
everything else from `hauberk.properties`.

To run against a different Token Relay pattern, edit the loaded
properties file (or supply a one-off `--config <file>.properties`) with
the `tokenRelay.type` + matching `kc.client.id` + `kc.client.secret`
you want. The utility has no per-client flags.

### CLI flags (picocli on HauberkCli.run)

| Flag | Effect |
|---|---|
| `--metrics` | Enable Performance Matrix capture (per-request CSV + per-URL summary) |
| `--output <FOLDER>` | Output base; each run goes into `<output>/<sim>-<timestamp>/` (default `./output`). Holds Gatling's report + the perf matrices side-by-side. |
| `--config <FILE>` | Load a properties overlay on top of `hauberk.properties` |
| `--duration <N>` | Override `super.duration.seconds` |
| `--read / --update / --create / --move / --tx <N>` | Per-scenario worker counts for SuperLoad |
| `--prep-depth / --prep-clients / --prep-accounts <N>` | PrepareForAnything shape knobs |
| `--move-depth / --move-clients / --move-accounts <N>` | MoveSmoke shape knobs |
| `--times <N>` | Run the sim N times sequentially (each gets its own report) |
| `--no-health-check` | Skip the pre-flight health probes (KC + GW + auth path) |

### Health pre-check

Before any sim, `hauberk.cmd run` pings:
- KC `/.well-known/openid-configuration`
- Gateway `/actuator/health`
- Gateway `/esq-kinds` with a real bearer (verifies the full auth path)

Failures cause an immediate abort with a clear message -- saves the
operator from chasing thousands of KO requests caused by an offline
service. Bypass with `--no-health-check`.

### Token refresh

`KcTokenClient` issues access tokens with a 5-minute TTL.
`RefreshableToken` wraps it and auto-refreshes when 30 seconds remain.
Wired into every Simulation via Gatling's session-lambda header
(`(session) -> "Bearer " + TOKEN.value()`). Long sims no longer fall
off the TTL cliff.

## Performance Matrix

Opt-in via `--metrics`. Captures the four observability headers emitted
by the gateway and the backend services (see
[Esquire.ObservabilityStack.md](Esquire.ObservabilityStack.md) for the
protocol definition) and writes one CSV per Gatling scenario plus a
stderr summary at sim end.

### Output

```
./output/<sim-class>-<yyyyMMddHHmmssSSS>/
    index.html              -- Gatling's HTML report
    simulation.log          -- Gatling's raw run log
    req_*.html              -- per-request detail pages
    <scenario-1>.csv        -- perf matrix CSV (only when --metrics)
    <scenario-2>.csv        -- one CSV per Gatling scenario
    ...
```

Everything for one run ships in a single sub-folder under `--output`
(default `./output`). Naming key for each CSV =
`Session.scenario()` -- the string passed to `scenario("...")` in the
Simulation source. A "super" Simulation that runs N parallel scenarios
produces N CSVs, one per scenario. SuperLoad has no aggregate of its
own -- it's just an orchestrator running sub-sims.

### CSV layout

```
# perf-matrix YAML preamble (commented; CSV body starts at next non-# line)
# simulation:    EntitySmokeSimulation
# scenario:      entity-smoke
# client:        esq-hauberk
# kc_base:       http://localhost:8080/kc-auth
# gw_base:       http://localhost:7070
# start_utc:     2026-05-12T00:24:21.979572800Z
timestamp,reqName,httpStatus,clientMs,gwOuterMs,gwInnerMs,srvOuterMs,srvInnerMs
2026-05-12T00:24:21.979572800Z,"GET /esq-enode (lookup office by name)",200,8,7,6,3,0
2026-05-12T00:24:22.045672800Z,"POST /esq-cmd-new (office, ensure)",200,18,16,15,11,3
...
```

YAML-style commented preamble (filter with `awk '!/^#/'` or
`grep -v '^#'` to feed pure CSV into any tool). The body is plain CSV
parseable by any spreadsheet or `pandas.read_csv`.

### Reading the per-URL summary

`hauberk.cmd summary output/EntitySmokeSimulation-.../entity-smoke.csv` prints:

```
[summary] entity-smoke-260512021237.csv

--- preamble ---
  # simulation:    EntitySmokeSimulation
  # scenario:      entity-smoke
  # client:        esq-hauberk
  ...

per request URL (median values)  --  24 rows across 13 request types
  GET /esq-enode (lookup office by name)            n=2   c=9  gO=7  gI=6  sO=2  sI=0  |  net=2  gw_self=1  in_cluster=4  srv_self=2  srv_inner=0
  POST /esq-cmd-new (office, ensure)                n=1   c=38 gO=34 gI=34 sO=31 sI=23 |  net=4  gw_self=0  in_cluster=3  srv_self=8  srv_inner=23
  GET /esq-tree (biztree cache subtree)             n=5   c=10 gO=8  gI=7  sO=3  sI=0  |  net=2  gw_self=1  in_cluster=4  srv_self=3  srv_inner=0
  ...
```

### Five measured values

Each value is a median (or whatever percentile you slice for) of one
request URL's samples in milliseconds:

| Abbreviation | Header | What it captures |
|---|---|---|
| `c` | (Gatling's `responseTimeInMillis`) | clientMs -- send-to-receive at the client |
| `gO` | `X-Response-Time` | gwOuterMs -- gateway full envelope (Spring Security + routing + downstream + response) |
| `gI` | `Esq-Gw-Inner-Time` | gwInnerMs -- gateway downstream-call window only (post-security) |
| `sO` | `Esq-Srv-Outer-Time` | srvOuterMs -- service full request lifecycle |
| `sI` | `Esq-Srv-Inner-Time` | srvInnerMs -- service inner umbrella (today JPA / DB) |

### Five derived bands

Each band is a layer subtraction -- attributes time to the specific leg
of the request journey:

```
client ─────────────► gw_outer ─────► gw_inner ──────► srv_outer ──────► srv_inner
       net                  gw_self            in_cluster        srv_self         (srv_inner itself)
```

| Band | Formula | What lives in that layer |
|---|---|---|
| `net` | `c - gO` | Wire transport client <-> gateway (HTTP + TCP) |
| `gw_self` | `gO - gI` | Gateway own work: Spring Security (JWT decode, Vanilla Token Relay broker call, Phantom Token Relay exchange, role check), routing decisions, response assembly. **Where the Token Relay KC roundtrip cost lands on cache MISS.** |
| `in_cluster` | `gI - sO` | Gateway <-> service network + HTTP serialization (across docker containers, k8s pod-to-pod) |
| `srv_self` | `sO - sI` | Service application logic -- Spring stack + controller + business logic minus DB |
| `srv_inner` | `sI` | Inner umbrella -- today DB; future `-DB-` / `-JMS-` / `-Cache-` decompositions |

### "Cans and chairs" rule

Per-URL is the **only honest unit** -- aggregating heterogeneous
request kinds into a single median produces a number that is just an
artifact of the sim's request mix, not a property of the system.
A `GET /esq-tree` (cached, ~7 ms) and a `POST /esq-cmd-new (account)`
(DB write, ~30 ms) summed as "ALL median" tells you nothing useful.
The matrix summary and diff outputs deliberately have **no aggregate
row** -- only per-URL.

### `diff` -- A/B comparison

`hauberk.cmd diff output/<run-a>/<scn>.csv output/<run-b>/<scn>.csv`:

```
per request URL (median values; n_A / n_B):
  url                                                | n_A/n_B | Ac  Bc delta | AgO BgO delta | AgI BgI delta | AsO BsO delta | AsI BsI delta
  ---
  GET /esq-tree (biztree cache subtree)              |  5/5    | 10 14  +4    |  8  12  +4    |  7  7   +0    |  3  3   +0    |  0  0   +0
  ...
only in A (no counterpart in B, excluded from comparison):
  ...
rows: A=24  B=24
```

Per-URL only; "only in A / only in B" call-outs make missing-counterpart
rows explicit instead of silently dropping them.

### Sample-size discipline

The matrix shows `n` per URL row. Trust per-URL medians where `n` is
large; treat `n=1` rows as warm-up-tainted singletons that aren't yet
load-test-worthy. For clean A/B numbers, run a Load sim (CreateLoad,
SuperLoad) where each URL accumulates thousands of samples -- not a
single-VU smoke.

## Common workflows

### Smoke -- one-shot functional check

```
hauberk.cmd run entity-smoke --metrics
hauberk.cmd run entity-smoke --metrics --config hauberk-k8s.properties
hauberk.cmd diff output/<run-a>/<scn>.csv output/<run-b>/<scn>.csv
```

To run the same sim against a different Token Relay pattern (Vanilla
via esq-hauberk-S, Phantom via esq-hauberk-M, ...): create a
properties file with the matching `tokenRelay.type` +
`kc.client.id` + `kc.client.secret` (and any other deltas) and pass
it via `--config`.

### Load comparison -- sustained throughput / latency under load

```
hauberk.cmd run clean-house
hauberk.cmd run prepare-for-anything --prep-depth 5 --prep-clients 10 --prep-accounts 2
hauberk.cmd run super-load --metrics --duration 120
hauberk.cmd run super-load --metrics --duration 120 --config <other>.properties
hauberk.cmd diff output/<run-a>/<scn>.csv output/<run-b>/<scn>.csv
hauberk.cmd run clean-house                                    (teardown)
```

### Race-condition repro

See [Race.Conditions.Repro.md](Race.Conditions.Repro.md) for the
step-by-step commands (compose + local k8s variants) and self-
validation behavior.

## Architecture

Three layers in `src/main/java/pro/mir0n/esquire/hauberk/`:

- **`chain/`** -- atomic `ChainBuilder` building blocks. Each Chain is
  one HTTP call (or a small composite) with session-attribute inputs and
  outputs. Reused across many Simulations. Examples: `CreateUser`,
  `Deposit`, `MoveEntity`, `CompareTrees`, `EnsureOffice`,
  `CleanupOfficeByName`, `LoginViaBff`.
- **`simulations/`** -- `Simulation` subclasses (Gatling). Each composes
  Chains into one or more `ScenarioBuilder` instances and wires
  `HttpProtocolBuilder` for KC auth + perf-matrix capture. Sims are
  Java classes discovered at runtime by `SimulationCatalog`.
- **`perf/`** -- `PerformanceMatrix` + `RefreshableToken` +
  `HealthPreCheck`. Cross-cutting infrastructure used by every
  Simulation.

The picocli CLI lives in `cli/` (`HauberkCli`, `RunCommand`,
`ListCommand`, `SummaryCommand`, `DiffCommand`, `CsvSnapshot`,
`SimulationCatalog`).

## Cross-references

- [Esquire.ObservabilityStack.md](Esquire.ObservabilityStack.md) --
  the four-layer measurement protocol (`X-Response-Time` /
  `Esq-Gw-Inner-Time` / `Esq-Srv-Outer-Time` / `Esq-Srv-Inner-Time`)
  that PerformanceMatrix consumes. Defines what each header captures
  at the wire + which filter emits it.
- [keyCloak-gateway.JWE.md](keyCloak-gateway.JWE.md) -- the four
  certified auth patterns (BFF / Plain JWT / Vanilla Token Relay / Phantom Token Relay);
  diagrams + decision matrix; "Patterns we deliberately don't use"
  section explains why Opaque + Introspection (RFC 7662) and
  Lightweight Access Tokens + Userinfo were not chosen. The hauberk's
  three non-browser clients each correspond to one of Patterns 2/3/4.
- [Testing.md](Testing.md) -- Gatling as Esquire's standard testing
  framework. The hauberk's Simulation set is the first; future testing
  work composes new Chains and Simulations on the same foundation.
- [v1.2.x.Planning.md](v1.2.x.Planning.md) -- the v1.2.4 sprint record;
  full delivered-scope summary.

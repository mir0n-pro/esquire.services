# Esquire Haubergeon

Gatling 3.13 (Java DSL) test harness for the Esquire stack. Drives HTTP
load against a running deployment from outside and produces per-request
timing with multi-tier attribution (client / gateway / service / DB).

> **Full reference:** [`doc/Esquire.Haubergeon.md`](../doc/Esquire.Haubergeon.md)
> -- command catalog, identity model, configuration knobs, performance
> matrix format, common workflows, architecture pointers.
>
> This README is the on-module overview. The reference doc above is the
> place to look for the depth.

## Build

```
mvn -pl hauberk install            (from services/, or `mvn install` from hauberk/)
```

It builds on Armoury's `armoury-hauberk`, which Maven takes from GitHub
Packages. Output: `target/hauberk.jar` -- one fat jar with Gatling, picocli,
all deps. Rebuild whenever a Simulation, Chain, or `EsqHauberkConfig` field
changes.

## Run

```
hauberk.cmd <subcommand> [options]
```

`hauberk.cmd` is the launcher; one line of `.cmd` that prepends the JVM
`--add-opens` flags Gatling 3.13 requires.

### Subcommands

| Subcommand | What it does |
|---|---|
| `list` | List discovered Simulation classes |
| `run <sim> [opts]` | Launch a Gatling simulation |
| `summary <csv>` | Print per-URL summary for a saved metrics CSV |
| `diff <csv-a> <csv-b>` | Side-by-side per-URL comparison with deltas |
| `kc-reconcile` | Check Keycloak against the database for a stale `esq_rootpath`; `--repair` fixes it |
| `help [command]` | picocli help |

### Quick start

```
hauberk.cmd list                                          (catalog)
hauberk.cmd run entity-smoke --metrics                    (JWT smoke + perf matrix)
hauberk.cmd run entity-smoke --metrics --S                (same smoke, JWS+ Introspection variant)
hauberk.cmd summary output/EntitySmoke-.../entity-smoke.csv   (per-URL summary)
hauberk.cmd diff   output/<run-a>/<scn>.csv output/<run-b>/<scn>.csv  (A vs B)
```

## Two certified clients

| KC client | Mode | Backed by USR | esq_rootpath |
|---|---|---|---|
| `esq-hauberk` | JWT (default) | Test Driver, uid=15 | `1.14.` |
| `esq-hauberk-S` | JWS+ Introspection (`--S` flag) | Test Driver S, uid=16 | `1.14.` |

Both scoped to **Test House** (org pk=14, seeded in `db.seed`). The
hauberk can only create/read/modify entities inside the Test House
subtree.

## Files in this module

```
hauberk/
|-- src/main/java/.../esquire/hauberk/
|   |-- chain/        -- ChainBuilder atoms (CreateUser, Deposit, ...), 36 classes
|   |-- cli/          -- EsqHauberkCli, EsqRunCommand, KcReconcileCommand
|   |-- config/       -- EsqHauberkConfig, EntityKinds
|   |-- reconcile/    -- KcRecover
|   `-- simulations/  -- the Gatling Simulations, over AEsqSimulation
|-- hauberk*.properties  -- canonical config plus one overlay per target
|-- hauberk.cmd          -- launcher (java + --add-opens + -jar)
|-- *.bat / *.ps1 / *.py -- smoke, perf-matrix and perf-pairs runners
|-- pom.xml              -- maven + maven-shade-plugin (fat jar)
`-- target/hauberk.jar   -- built artifact
```

## Cross-references

- [`doc/Esquire.Haubergeon.md`](../doc/Esquire.Haubergeon.md)
  -- full reference doc.
- [Armoury Haubergeon](https://github.com/mir0n-pro/armoury/blob/develop/doc/Armoury.Haubergeon.md)
  -- the generic harness these simulations are built on (`armoury-hauberk`).
- [`doc/Esquire.ObservabilityStack.md`](../doc/Esquire.ObservabilityStack.md)
  -- the four-layer measurement protocol (`X-Response-Time` /
  `Esq-Gw-Inner-Time` / `Esq-Srv-Outer-Time` / `Esq-Srv-Inner-Time`)
  the matrix consumes.
- [`doc/Esquire.Auth.TokenPatterns.md`](../doc/Esquire.Auth.TokenPatterns.md)
  -- the sign-in patterns at the edge that the harness calls through.
- [`doc/Esquire.TestingStack.md`](../doc/Esquire.TestingStack.md) -- Gatling
  as Esquire's standard testing framework.

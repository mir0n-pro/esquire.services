# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Testing Stack

The Esquire framework spans Java microservices, a Node.js BFF, an Angular SPA + library, and a Java load harness. Each tier picks the test framework that fits its language and what's being verified. This document lists every framework currently in use, what it covers, and the approximate test count as of **v1.2.8 (2026-06-20)**.

## At a glance

|                                                                          | Tier                                          | Framework(s) | Project(s) | Tests |
|--------------------------------------------------------------------------|-----------------------------------------------|---|---|---|
| ![Alt text](media/junit.svg)                                      | Java unit + service                           | **JUnit 5** + **Mockito** + **AssertJ** | `services/*` | **443** `@Test` methods across **64** classes |
| ![Alt text](media/hauberk.svg) ![Alt text](media/gatling.svg) | Java integration / load / stress / race-repro | **Haubergeon** (on **Gatling 3.13** Java DSL) | `explorer/hauberk` | **22** self-validating Simulations (smoke / load / super / race-repro / message-loss) + 3 JUnit catalog tests |
| ![Alt text](media/vitest.svg)                                      | Node.js (BFF)                                 | **Vitest** + **Supertest** | `explorer/backend` | **28** specs across **4** files (config / cache / trace / tokens) |
| ![Alt text](media/karma.svg) ![Alt text](media/jasmine.svg)  | Angular SPA                                   | **Karma** + **Jasmine** (`ng test`) | `explorer/frontend` | **25** `it()` specs in **4** files |
| ![Alt text](media/karma.svg) ![Alt text](media/jasmine.svg)  |  Angular UI library                           | **Karma** + **Jasmine** (`ng test`) | `esquire.ui.lib` | **146** `it()` specs in **23** files |
| ![Alt text](media/playwrite.svg)                                   | Browser end-to-end                            | **Playwright** | `explorer/e2e-test` | **32** `test()` cases in **16** `.spec.ts` files |
| ![Alt text](media/hauberk.svg) ![Alt text](media/gatling.svg) | Stack integration scenarios                   | **Bash** driver + `psql` / `sqlplus` / `kubectl` (drives the **hauberk** `EntitySmoke` workload) | `services/test` | **~27-cell** audit matrix (audit sink x primary DB x environment) |

---

## Java unit + service tests — JUnit 5 / Mockito / AssertJ

**Used in:** every `services/*` microservice (common, bizTree, enyMan, pacMan, keySmith, kcMaster, gateway, auKeep) and in the hauberk module's catalog contract test.

**What for:** classic unit + service-layer tests. Mock repositories, transaction templates, JMS publishers, KC clients; assert behavior against the mocked collaborators. Test files end in `Test.java` and live next to the production code under `src/test/java/`.

**How wired:** picked up via `spring-boot-starter-test` (transitive dep through the parent `pom.xml`), which bundles JUnit 5 + Mockito + AssertJ. The hauberk module pulls JUnit 5 + AssertJ directly (no Spring there).

**Pattern:** `@ExtendWith(MockitoExtension.class)` on the class, `@Mock` fields for collaborators, `assertThat(...)` / `assertThatThrownBy(...)` for assertions.

**Coverage:**

| Module | `@Test` methods | Notes |
|---|---|---|
| common | 133 | core framework: entity / field utils, roles storage, access profile, validators, Taijitu cache rigs |
| messaging | 51 | the messaging-bus + x-rod substrate extracted from common: `MessagingBusCatalogTest`, `RodEventCodecTest`, `XRodTest`, `XRodManagerTest`, `XRodDisabledTest`, `RodTransportAdapterTest`, `TransportProvidersTest`, `BusIdentityTest` |
| audit | 18 | the audit rules on the generic keep engine: `AuditSqlTest`, `AuditKeepDirectorTest`, `AuditBusBridgeTest`, `AuditKindsTest` |
| bizTree | 42 | — |
| enyMan | 73 | — |
| pacMan | 39 | — |
| keySmith | 21 | — |
| kcMaster | 44 | — |
| gateway | 21 | gateway typically light on JUnit; reactive WebFlux code is harder to mock-test cleanly |
| auKeep | 1 | the audit-bus consumer integration test |
| **total** | **443** | across **64** classes |

---

## Java integration / load / stress / race-repro — Haubergeon (Gatling 3.13 harness)

**Used in:** `explorer/hauberk/` only.

**Harness for Gatling Engine.** **Haubergeon** is the Esquire-grown harness that wraps Gatling: picocli CLI (`hauberk.cmd`), fat-jar (`hauberk.jar`), per-run output layout, `PerformanceMatrix` capture, `@SimulationInfo` catalog contract, and a base `HauberkSimulation` class. Gatling does the load engine; Haubergeon makes it driveable and observable in this project. Full reference: [Esquire.Haubergeon.md](Esquire.Haubergeon.md).

**What for:** anything that exercises the running stack end-to-end with non-trivial concurrency: smoke (does the wire light up?), load (sustained throughput), stress (saturate a single hot operation), race-condition reproduction (deliberately concurrent shapes that surface known bugs).

**How wired:** Gatling Java DSL + picocli CLI launcher. One fat-jar (`hauberk.jar`) carries Gatling + every Simulation. The CLI provides `run`, `list`, `summary`, `diff` subcommands; `PerformanceMatrix` captures per-request CSVs via the gateway's `X-Capture-Metrics` header. Runs ship into a single per-run folder under `--output` (default `./output`) alongside Gatling's own report.

**Pattern:** Each Simulation extends `HauberkSimulation` (abstract base — pulls up lazy KC token, instrumented `httpProtocol`, perf-matrix flush). Reusable `ChainBuilder` atoms compose into `ScenarioBuilder` flows. `@SimulationInfo("...")` annotation supplies the catalog description; presence enforced by `SimulationCatalogContractTest` (JUnit 5).

**Coverage:** 22 Simulations + 35 reusable Chains + 3 JUnit catalog-contract tests. `@SimulationInfo` descriptions are held under a 90-char `hauberk list` cap enforced by `SimulationCatalogContractTest`.

**Esquire-org standard since v1.2.4:** Gatling is the chosen framework for all integration / stress / load / race-repro testing across the project. See [Testing.md](Testing.md) for the standard-adoption rationale and [Esquire.Haubergeon.md](Esquire.Haubergeon.md) for the harness reference.

---

## Node.js (BFF) — Vitest + Supertest

**Used in:** `explorer/backend/` (the BFF tier landed in v1.2.3).

**What for:** unit and integration tests for the BFF — OIDC flow, session store, proxy + cache layers, log + trace utilities. Supertest provides HTTP-level assertions against the Express app without binding a real port.

**How wired:** dev deps in `explorer/backend/package.json`; `npm test` runs `vitest run`, `npm run test:watch` runs `vitest` in watch mode. Test files live under `explorer/backend/test/` mirroring `src/` layout; `tsconfig.json` excludes the `test/` tree so test code does not ship in the production image.

**Pattern:** `describe(...) / it(...) / expect(...)` Vitest syntax. `vi.mock('../../src/auth/openidClient.js')` stubs the OIDC seam without bringing in the real KC handshake. ASCII-only, no emojis, `.js` import suffix on relative paths.

**Coverage:**

| File | Specs | What it covers |
|---|---|---|
| `test/config.test.ts` | 5 | default fallback values; `ALLOWED_ORIGINS` parse + dedupe with `publicBaseUrl` first; blank entries skipped; numeric env parsing; `NODE_ENV=production` |
| `test/util/trace.test.ts` | 5 | UUID generation when `X-Request-ID` absent; preserves client-supplied id; `X-Correlation-ID` propagated only when client sets it; first-of-array headers; empty string treated as absent |
| `test/proxy/cache.test.ts` | 10 | `keyForRequest` shape for `/esq-kinds` + `/esq-dict` (incl. array-shaped kind); null for missing kind / non-dictionary paths; HIT/MISS round-trip; distinct keys per kind (no cross-pollination); size accounting; LRU eviction past `maxEntries`; TTL eviction past `ttlMs` |
| `test/auth/tokens.test.ts` | 8 | `NoSessionError` on missing tokens; returns current `access_token` when fresh; refresh within the 30s leeway window; refresh after expiry; `NoSessionError` when expiring without a `refresh_token`; `RefreshFailedError` wraps upstream KC failures; `RefreshFailedError` when the refresh response omits `access_token`; old `refresh_token` preserved when the response omits a new one |
| **total** | **28** | |

---

## Angular SPA — Karma + Jasmine

**Used in:** `explorer/frontend/`.

**What for:** Angular component + service unit tests, run inside a real Chrome browser instance via Karma. `ng test` is the standard Angular CLI command.

**How wired:** Karma + jasmine-core + karma-chrome-launcher + karma-jasmine + karma-coverage. The Angular CLI generates the `karma.conf.js` and `tsconfig.spec.*.json` shapes.

**Pattern:** `describe(...) / it(...) / expect(...)` Jasmine syntax; `TestBed.configureTestingModule({...})` for Angular dependency wiring.

**Coverage:** **25** `it()` specs across **4** `.spec.ts` files. The SPA is intentionally lean on unit tests — most behaviour is covered by the Playwright e2e suite (below) which exercises the real app against a live backend.

---

## Angular UI library — Karma + Jasmine

**Used in:** `esquire.ui.lib/` (the `@mir0n-pro/esquire.ui` package consumed by the frontend).

**What for:** the library carries the reusable Esquire UI primitives (tree explorer, entity dialogs, command-handler registry, field renderers, etc.). Karma + Jasmine specs validate this shared surface — same stack as the consuming SPA.

**How wired:** Same as the SPA — `ng test` via Karma + Jasmine; coverage reporter wired through `karma-coverage`.

**Coverage:** **146** `it()` specs across **23** `.spec.ts` files. The library is the testing-heaviest tier in the Esquire stack — shared code earns its own coverage.

---

## Browser end-to-end — Playwright

**Used in:** `explorer/e2e-test/` (separate Node project alongside the SPA).

**What for:** end-to-end browser tests driving the live BFF + SPA + KC + gateway + microservices + DB stack from a real browser. Validates the full user journey: login (OIDC code+PKCE through KC), tree navigation, context-menu actions, entity CRUD, accounting transactions (deposit / withdrawal / transfer), error handling.

**How wired:** `@playwright/test` v1.49+. `npm test` runs the suite headless; `npm run test:ui` opens the Playwright UI runner. Tests target `localhost`, `localhost:4200` (live SPA), and OKE prod URLs as needed.

**Coverage:** **32** `test()` cases across **16** `.spec.ts` files (01-prelogin through 15-system-entity-protection, plus 99-debug-login); the move / delete / withdrawal / transfer specs (09, 10, 12, 13) are present as placeholders but currently hold no `test()` cases. The suite runs green on Docker, local k8s, and OKE (`https://esquire.mir0n.pro`).

---

## Stack integration scenarios — Bash-driven matrices over the running stack

**Used in:** `services/test/` (the first scenario set is `audit-smoke/`).

**What for:** reproducible end-to-end integration scenarios that exercise the *running* stack across a grid of configurations and assert the outcome in the database. Distinct from the other tiers: unit tests mock collaborators with no stack, hauberk drives throughput / load, Playwright drives the browser UI — these scenarios push a small real workload through the gateway and then check that the data landed where the configuration says it should. They are the correctness counterpart to the hauberk performance matrices.

**How wired:** a Bash driver (`run.sh`) per scenario set. For each cell it sets the configuration via environment, recreates the affected stack pieces (`docker compose up --force-recreate` on docker, `helm upgrade` + rollout restart on local k8s), drives the workload (the hauberk `EntitySmoke`: create office -> update a parameter -> move -> delete, plus an account deposit), then validates row-count deltas in the target database via `psql` (docker uses the host Postgres pg18; local k8s the in-cluster Postgres), `sqlplus` (Oracle), or `kubectl exec`. Each run writes a PASS/FAIL table into `results-<stamp>.md`.

**Coverage:** the **audit-smoke matrix** — ~27 cells = audit sink (`a` DB triggers / `b` in-process shared+dedicated / `c` ActiveMQ / `ck` Kafka / `d` Redis stream / `dk` Kafka stream) x primary DB (Postgres / Oracle) x environment (docker / local k8s). Proves the audit log lands in the right place for every combination: the relational `*_log` tables for the consumed sinks (a / b / c / ck), the stream itself for the producer-only sinks (d / dk).

---

## Why this many frameworks

Each tier sits in a different runtime, so it gets the test framework its ecosystem natively supports:

- **Java + Spring** → JUnit 5 / Mockito / AssertJ (de-facto Spring Boot standard)
- **Java load / concurrency** → Gatling (the standard answer for high-throughput HTTP load with deterministic injection profiles; the hauberk sprint adopted it as the framework-wide standard for everything beyond pure unit scope)
- **Node.js / TypeScript** → Vitest (fast, ESM-native, drop-in replacement for Jest)
- **Angular** → Karma + Jasmine (the framework's default; switching would mean fighting the toolchain)
- **Browser flow** → Playwright (modern Selenium alternative; supports auto-wait, parallel workers, trace viewer)

The split is by *runtime + concern*, not by team — and that's the right axis: forcing one framework across runtimes always loses more than it saves.

## Cross-references

- [Testing.md](Testing.md) — Gatling-as-Esquire-standard policy (the narrower note that predates this overview).
- [Esquire.Haubergeon.md](Esquire.Haubergeon.md) — full reference for the Gatling harness.
- [Race.Conditions.Repro.md](Race.Conditions.Repro.md) — race-repro Simulations and their PASS/FAIL contract.

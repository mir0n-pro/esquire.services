# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Testing Stack

The Esquire framework spans Java microservices, a Node.js BFF, an Angular SPA + library, and a Java load harness. Each tier picks the test framework that fits its language and what's being verified. This document lists every framework currently in use, what it covers, and the approximate test count.

## At a glance

|                                                               | Tier                                          | Framework(s) | Project(s) | Tests |
|---------------------------------------------------------------|-----------------------------------------------|---|---|---|
| ![Alt text](media/junit.svg)                                  | Java unit + service                           | **JUnit 5** + **Mockito** + **AssertJ** | `services/*` | **610** `@Test` methods across **94** classes |
| ![Alt text](media/jacoco.png)                              | Java code coverage (a test of the tests)      | **JaCoCo** | `services/*` &rarr; `test/JaCoCo` | **15** per-module line / branch reports (unit + in-JVM ITs) |
| ![Alt text](media/hauberk.svg) ![Alt text](media/gatling.svg) | Running-stack load / stress / race-repro      | **Haubergeon** (on **Gatling 3.13** Java DSL) | `explorer/hauberk` | **23** self-validating Simulations (smoke / load / super / race-repro / message-loss / HA) + 3 JUnit catalog tests |
| ![Alt text](media/hauberk.svg) ![Alt text](media/gatling.svg) | Running-stack integration matrices            | **Bash** driver + `psql` / `sqlplus` / `kubectl` (drives the **hauberk** `EntitySmoke` workload) | `services/test` | **~27-cell** audit matrix (audit sink x primary DB x environment) + a **bus health** readiness/liveness chaos smoke |
| ![Alt text](media/vitest.svg)                                 | Node.js (BFF)                                 | **Vitest** + **Supertest** | `explorer/backend` | **47** specs across **5** files (config / cache / trace / tokens / W3C trace-id conformance) |
| ![Alt text](media/karma.svg) ![Alt text](media/jasmine.svg)   | Angular SPA                                   | **Karma** + **Jasmine** (`ng test`) | `explorer/frontend` | **25** `it()` specs in **4** files |
| ![Alt text](media/karma.svg) ![Alt text](media/jasmine.svg)   |  Angular UI library                           | **Karma** + **Jasmine** (`ng test`) | `esquire.ui.lib` | **146** `it()` specs in **23** files |
| ![Alt text](media/playwrite.svg)                              | Browser end-to-end                            | **Playwright** | `explorer/e2e-test` | **47** `test()` cases in **23** `.spec.ts` files |

---

## Java unit + service tests — JUnit 5 / Mockito / AssertJ

**Used in:** every `services/*` module (mir0n-utils, common, messaging, dataKeep, audit, bizTree, enyMan, pacMan, keySmith, kcMaster, gateway, auKeep, tp-activemq, tp-redis, tp-kafka) and in the hauberk module's catalog contract test.

**What for:** classic unit + service-layer tests. Mock repositories, transaction templates, JMS publishers, KC clients; assert behavior against the mocked collaborators. Test files end in `Test.java` and live next to the production code under `src/test/java/`.

**How wired:** picked up via `spring-boot-starter-test` (transitive dep through the parent `pom.xml`), which bundles JUnit 5 + Mockito + AssertJ. The hauberk module pulls JUnit 5 + AssertJ directly (no Spring there).

**Pattern:** `@ExtendWith(MockitoExtension.class)` on the class, `@Mock` fields for collaborators, `assertThat(...)` / `assertThatThrownBy(...)` for assertions.

**Coverage:**

| Module | `@Test` methods | Notes |
|---|---|---|
| mir0n-utils | 26 | base utilities split out of `common` — identity / string / numeric helpers |
| common | 201 | core framework: entity / field utils, roles storage, access profile, validators, Taijitu cache rigs, request-context guard, worker-pool |
| messaging | 112 | the messaging-bus + x-rod substrate (the old `XRodManager` dissolved into the facade): catalog / codec / transport, the facade (`MessagingBusTest`, `MessagingBusCatalogTest`, `RodEventCodecTest`, `XRodTest`), bus health (`BusHealthIndicatorTest`, `TransportHealthIndicatorTest`, `TransportHealthTest`, `AliveSessionTest`), role + config-bind validation (`XRodRoleSupportTest`, `XRodValidateTest`, `BusRefBindTest`, `XRodParamsTest`), broker-down resilience (`XRodBrokerDownTest`) |
| audit | 18 | the audit rules on the generic keep engine: `AuditSqlTest`, `AuditKeepDirectorTest`, `AuditBusBridgeTest`, `AuditKindsTest` |
| dataKeep | 3 | the generic keep-engine SQL / applier units |
| bizTree | 42 | — |
| enyMan | 70 | — |
| pacMan | 42 | — |
| keySmith | 22 | — |
| kcMaster | 31 | — |
| gateway | 35 | gateway typically light on JUnit; reactive WebFlux code is harder to mock-test cleanly |
| auKeep | 2 | the audit-bus consumer integration test |
| tp-activemq | 2 | transport-provider unit checks |
| tp-redis | 2 | transport-provider unit checks |
| tp-kafka | 2 | transport-provider unit checks |
| **total** | **610** | across **94** classes |

**Coverage tooling — JaCoCo.** Line / branch coverage of this Java tier is measured with **JaCoCo** (0.8.13, wired in the parent `pom.xml`: `prepare-agent` + `report`). It counts whatever runs in the forked test JVM — the unit tests **and** the in-JVM Testcontainers / `@SpringBootTest` integration tests; e2e and hauberk drive a separately deployed stack, so they fall outside its reach. Reports are written to `services/test/JaCoCo/<artifactId>` (deliberately outside module `target/`, so `mvn clean` keeps them). Run via `build-with-JaCoCo.bat` (`mvn clean test`) and browse `test/JaCoCo/framed.html`. Coverage is a signal, not a build gate; mutation testing (PIT) is not used.

---

## Running-stack load / stress / race-repro — Haubergeon (Gatling 3.13 harness)

**Used in:** `explorer/hauberk/` only.

**Harness for Gatling Engine.** **Haubergeon** is the Esquire-grown harness that wraps Gatling: picocli CLI (`hauberk.cmd`), fat-jar (`hauberk.jar`), per-run output layout, `PerformanceMatrix` capture, `@SimulationInfo` catalog contract, and a base `HauberkSimulation` class. Gatling does the load engine; Haubergeon makes it driveable and observable in this project. Full reference: [Esquire.Haubergeon.md](Esquire.Haubergeon.md).

**What for:** anything that exercises the running stack end-to-end with non-trivial concurrency: smoke (does the wire light up?), load (sustained throughput), stress (saturate a single hot operation), race-condition reproduction (deliberately concurrent shapes that surface known bugs).

**How wired:** Gatling Java DSL + picocli CLI launcher. One fat-jar (`hauberk.jar`) carries Gatling + every Simulation. The CLI provides `run`, `list`, `summary`, `diff` subcommands; `PerformanceMatrix` captures per-request CSVs via the gateway's `X-Capture-Metrics` header. Runs ship into a single per-run folder under `--output` (default `./output`) alongside Gatling's own report.

**Pattern:** Each Simulation extends `HauberkSimulation` (abstract base — pulls up lazy KC token, instrumented `httpProtocol`, perf-matrix flush). Reusable `ChainBuilder` atoms compose into `ScenarioBuilder` flows. `@SimulationInfo("...")` annotation supplies the catalog description; presence enforced by `SimulationCatalogContractTest` (JUnit 5).

**Coverage:** 23 Simulations + 32 reusable Chains + 3 JUnit catalog-contract tests. `@SimulationInfo` descriptions are held under a 90-char `hauberk list` cap enforced by `SimulationCatalogContractTest`.

**Esquire-org standard:** Gatling is the chosen framework for all integration / stress / load / race-repro testing across the project (see *Why this many frameworks* below for the rationale). See [Esquire.Haubergeon.md](Esquire.Haubergeon.md) for the harness reference — build / run / catalog / vocabulary.

---

## Running-stack integration matrices — Bash-driven over the running stack

Sits right beside the Haubergeon harness above — **same running stack, different question.** Haubergeon asks *how fast / does it survive concurrency*; these matrices ask *did the data land where the configuration says it should*. They reuse ONE hauberk workload (`EntitySmoke`) as the probe and assert the result in the database across a grid of configurations. (This is the tier that can look like a hauberk duplicate but is not: Haubergeon = 23 Gatling Simulations under `explorer/hauberk`; this = ~27 Bash-orchestrated **config cells** under `services/test`.)

**Used in:** `services/test/` — two scenario sets: `audit-smoke/` and `health-smoke/`.

**What for:** reproducible correctness scenarios over the *running* stack across a grid of configurations. Where unit tests mock collaborators, Haubergeon drives throughput, and Playwright drives the browser, these push a small real workload through the gateway and check the data landed where the configuration says — the correctness counterpart to Haubergeon's performance matrices.

**How wired:** a Bash driver (`run.sh`) per scenario set. For each cell it sets the configuration via environment, recreates the affected stack pieces (`docker compose up --force-recreate` on docker, `helm upgrade` + rollout restart on local k8s), drives the workload (the hauberk `EntitySmoke`: create office -> update a parameter -> move -> delete, plus an account deposit), then validates row-count deltas in the target database via `psql` (docker uses the host Postgres pg18; local k8s the in-cluster Postgres), `sqlplus` (Oracle), or `kubectl exec`. Each run writes a PASS/FAIL table into `results-<stamp>.md`.

**Coverage:**

- the **audit-smoke matrix** — ~27 cells = audit sink (`a` DB triggers / `b` in-process shared+dedicated / `c` ActiveMQ / `ck` Kafka / `d` Redis stream / `dk` Kafka stream) x primary DB (Postgres / Oracle) x environment (docker / local k8s). Proves the audit log lands in the right place for every combination: the relational `*_log` tables for the consumed sinks (a / b / c / ck), the stream itself for the producer-only sinks (d / dk).
- the **health-smoke** chaos smoke — drives the broker up / down / back and asserts every service forwards its bus connection health to `/actuator/health`, that the indicator sits in the **readiness** group only (a broker outage depools the pod but never restarts it), and that an ActiveMQ leg recovers on its own through the `failover:` transport. The readiness-DOWN edge is asserted on docker (a clean `docker stop`) and observed on local k8s (a graceful `scale --replicas=0`); a separate capture kills the keep database to check the `keepDatasource` health dimension.

---

## Node.js (BFF) — Vitest + Supertest

**Used in:** `explorer/backend/` (the BFF tier).

**What for:** unit and integration tests for the BFF — OIDC flow, session store, proxy + cache layers, log + trace utilities. Supertest provides HTTP-level assertions against the Express app without binding a real port.

**How wired:** dev deps in `explorer/backend/package.json`; `npm test` runs `vitest run`, `npm run test:watch` runs `vitest` in watch mode. Test files live under `explorer/backend/test/` mirroring `src/` layout; `tsconfig.json` excludes the `test/` tree so test code does not ship in the production image.

**Pattern:** `describe(...) / it(...) / expect(...)` Vitest syntax. `vi.mock('../../src/auth/openidClient.js')` stubs the OIDC seam without bringing in the real KC handshake. ASCII-only, no emojis, `.js` import suffix on relative paths.

**Coverage:**

| File | Specs | What it covers |
|---|---|---|
| `test/config.test.ts` | 6 | default fallback values; `ALLOWED_ORIGINS` parse + dedupe with `publicBaseUrl` first; blank entries skipped; numeric env parsing; `NODE_ENV=production` |
| `test/util/trace.test.ts` | 5 | UUID generation when `X-Request-ID` absent; preserves client-supplied id; `X-Correlation-ID` propagated only when client sets it; first-of-array headers; empty string treated as absent |
| `test/proxy/cache.test.ts` | 10 | `keyForRequest` shape for `/esq-kinds` + `/esq-dict` (incl. array-shaped kind); null for missing kind / non-dictionary paths; HIT/MISS round-trip; distinct keys per kind (no cross-pollination); size accounting; LRU eviction past `maxEntries`; TTL eviction past `ttlMs` |
| `test/auth/tokens.test.ts` | 12 | `NoSessionError` on missing tokens; returns current `access_token` when fresh; refresh within the 30s leeway window; refresh after expiry; `NoSessionError` when expiring without a `refresh_token`; `RefreshFailedError` wraps upstream KC failures; `RefreshFailedError` when the refresh response omits `access_token`; old `refresh_token` preserved when the response omits a new one; the session-expiry additions (`refreshExpiresAt` derived from `refresh_expires_in`) |
| **total** | **33** | |

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

**Coverage:** **47** `test()` cases across **23** `.spec.ts` files (01-prelogin through 20-token-relay, plus the `_disc` / `cycle` helpers and 99-debug-login); the move / delete / withdrawal / transfer specs (09, 10, 12, 13) remain placeholders with no `test()` cases. Specs 16-session-expiry (expiry notice + pre-empt), 17-login-cancel (the KeyCloak Cancel link), 18-details-esc-focus, 19-access-profile-sync, and 20-token-relay cover the newer flows. The mutating specs (08 entity lifecycle, 11 accounting) now build and tear down their OWN working data under the seeded Test House via the `/api` proxy instead of mutating the shared seed tree. The suite runs green on Docker, local k8s, and OKE (`https://esquire.mir0n.pro`); `timeout: 60s` + `retries: 2` and 30s login-path waits absorb cold-start latency after a (re)deploy.

---

## Why this many frameworks

Each tier sits in a different runtime, so it gets the test framework its ecosystem natively supports:

- **Java + Spring** → JUnit 5 / Mockito / AssertJ (de-facto Spring Boot standard)
- **Java load / concurrency** → Gatling (the standard answer for high-throughput HTTP load with deterministic injection profiles; adopted as the framework-wide standard for everything beyond pure unit scope)
- **Node.js / TypeScript** → Vitest (fast, ESM-native, drop-in replacement for Jest)
- **Angular** → Karma + Jasmine (the framework's default; switching would mean fighting the toolchain)
- **Browser flow** → Playwright (modern Selenium alternative; supports auto-wait, parallel workers, trace viewer)

The split is by *runtime + concern*, not by team — and that's the right axis: forcing one framework across runtimes always loses more than it saves.

## Cross-references

- [Esquire.Haubergeon.md](Esquire.Haubergeon.md) — full reference for the Gatling harness (build / run / catalog / vocabulary).
- [Race.Conditions.Repro.md](review/Race.Conditions.Repro.md) — race-repro Simulations and their PASS/FAIL contract.

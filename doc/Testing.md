| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|

# Testing

Esquire uses **[Gatling](https://gatling.io/) (Java DSL)** as the standard framework
for integration, stress, and load testing. One framework, one set of vocabulary, one
report style — across services, the BFF, and any future component. Sprints add new
Chains and Simulations rather than spawning parallel frameworks.

## Vocabulary

Gatling's terminology, used verbatim across the project:

| Term | Meaning |
|---|---|
| **Simulation** | One Java class (extends `Simulation`) — the top-level "test run". One Simulation = one `mvn gatling:test` invocation. |
| **Scenario** (`ScenarioBuilder`) | Sequence of actions a virtual user performs. Built by chaining `.exec(...)` calls. |
| **Chain** (`ChainBuilder`) | Reusable building block — one logical command. Composes into Scenarios. Each Chain lives in `.../chain/` and is a static `ChainBuilder` field or factory method. |
| **Virtual User (VU)** | An async actor running a Scenario. Cheap — single JVM scales to thousands. |
| **Injection profile** | How VUs are spawned: `atOnceUsers(N)`, `rampUsers(N).during(...)`, `constantUsersPerSec(N).during(...)`. |
| **`setUp(...)`** | One call per Simulation; wires Scenarios with their injection profiles. Multiple populations in one `setUp` run in parallel. |
| **`andThen(...)`** | Sequential populations: the next population starts only after the previous finishes. |
| **`before()` / `after()`** | Hooks that run once per Simulation — typically for token fetch / shared setup / cleanup. |
| **Session** | Per-VU state. Use `.exec(session -> session.set("k", v))` to write; `${k}` interpolation to read. |
| **Feeder** | Data source (CSV, JSON, in-memory) that injects values into Sessions. |
| **Check** | Response assertion: `check(status().is(200))`, `check(jsonPath("$.id").saveAs("entityId"))`. |

## Layout convention

For any module that contains Gatling Simulations:

```
<module>/
  pom.xml                               -- gatling-maven-plugin
  src/test/java/<package>/
    auth/
      KcTokenClient.java                -- OAuth2 helper (token fetch, refresh)
    config/
      <Module>Config.java               -- env-driven base URLs, secrets
    chain/
      CreateOffice.java
      CreateUser.java
      ...                               -- one Chain per logical command
    simulations/
      SmokeSimulation.java
      <Real>Simulation.java
      ...                               -- one Simulation per "show"
```

## Auth pattern

Esquire services validate JWT against the realm JWKS. For Gatling Simulations, the
token is fetched once per run and applied to every request:

```java
@Override
public void before() {
  System.setProperty("kc.token", KcTokenClient.fetchAccessToken());
}

HttpProtocolBuilder httpProtocol = http
    .baseUrl(Config.gatewayBase())
    .header("Authorization", "Bearer " + System.getProperty("kc.token"))
    .header("X-Request-ID", "${requestId}");
```

For Simulations longer than the token TTL (default 5 min), wrap requests in a
token-refresh helper that fetches lazily on 401.

The realm client is `esq-tshirt` (or another service-account client) with
`grant_type=client_credentials`, configured to emit the standard Esquire claims
(`esq_uid`, `esq_rootpath`, `realm_access.roles`).

## Run

```
cd <module>
mvn gatling:test                                                # run all Simulations in module
mvn gatling:test -Dgatling.simulationClass=<fqcn>               # run one Simulation
```

HTML report appears under `target/gatling/<simulation>-<timestamp>/index.html`.
Raw event log: `target/gatling/<simulation>-<timestamp>/simulation.log`.

## Cleanup

A Simulation that creates persistent state ships its cleanup as the last
injection-population:

```java
setUp(
  loadScn.injectOpen(atOnceUsers(10))
    .andThen(cleanupScn.injectOpen(atOnceUsers(1)))
).protocols(httpProtocol);
```

Service-side delete restrictions (connected user, non-`C`-status account) are relaxed
when the JWT carries the `TSHIRT_PURGE` realm role *or* the target entity's email is
under the `@mir0n.pro` domain.

## What Gatling is NOT for

- **Unit tests.** JUnit / Mockito stay where they are.
- **Browser e2e.** Playwright in `explorer/e2e-test/` covers the BFF + SPA flow.
- **Internal-only assertions** (e.g. is a Spring bean wired correctly). That's
  Spring's own test slices.

Gatling is the answer when the test crosses a network boundary, needs concurrent
load, or wants per-percentile latency reporting.

## Why Gatling

- Java DSL since 3.7; first-class JDK 21 support.
- Async actor model — thousands of concurrent VUs on a single JVM, no thread-pool
  tuning.
- Built-in HTML report with percentiles, throughput, error breakdown — saves us from
  building one.
- Maven plugin integrates with the existing project build with no friction.
- Free / open source (Apache 2.0); commercial Gatling Enterprise exists for
  cross-Simulation aggregation but isn't required.
- Same framework usable for first-pass smoke checks (1 VU, 1 request) and serious
  load tests (10k VUs over 30 min). One mental model across the spectrum.

# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Services — Configuration Reference

Every runtime-configurable parameter of the Esquire Spring services, grouped by service, with its
environment variable, default, and meaning. Also covers the logging model and the gateway route
table.

## How configuration works

- Each service reads config from its `application.yml`, where every knob is a Spring placeholder
  `${ENV_VAR:default}`. Set the `ENV_VAR` in the environment to override; otherwise the default
  applies.
- **Where to set it:** docker — the service's `environment:` block in `compose/compose.yaml`;
  k8s — the chart `values.yaml` / the rendered ConfigMap (`k8s/charts/esquire-<svc>/`).
- **Relaxed binding:** an env var maps to a dotted property by upper-casing and replacing `.`/`-`
  with `_`. So `biztree.queue.bulk-threshold` is set by `BIZTREE_QUEUE_BULK_THRESHOLD`.
- **DB vendor profiles:** the data services pick a Spring profile from `DB_<SVC>_VENDOR`
  (`dev-postgres` default, or `dev-oracle`); the profile chooses the datasource URL, driver,
  dialect and JPA mapping files. bizTree additionally activates `cache-h2`.
- Defaults below are the **code defaults** (what `application.yml` / `@Value` ship). Where the
  deployed compose/k8s value differs on purpose, it is called out as `code / deployed`.
- **Messaging is transport-agnostic.** A service never talks JMS/Kafka/Redis directly; it asks the
  **`MessagingBus`** facade for a bus's **x-rod**, built from the `role` it declares on that bus. A
  cross-service **bus catalog** (the "topology") defines every bus ONCE in an external file, imported
  by every service (see [Shared parameters](#shared-parameters-most-services)).

---

## Shared parameters (most services)

These follow the same pattern across services; `<SVC>` is the service token in caps
(`ENYMAN`, `PACMAN`, `KEYSMITH`, `KCMASTER`, `BIZTREE`, `DATAKEEP`). They are listed once here and referenced
from each service section rather than repeated. (The audit consumer service is named **auKeep** but its
env token is `DATAKEEP` -- a deferred rename, so the env prefix stays `DATAKEEP`.)

### Database (data services: enyMan, pacMan, keySmith, bizTree, auKeep — NOT kcMaster)

| Env var | Default | Description |
|---|---|---|
| `DB_<SVC>_VENDOR` | `dev-postgres` | Spring profile / DB vendor: `dev-postgres` or `dev-oracle`. |
| `DB_<SVC>_HOST` | `localhost` | Database host. |
| `DB_<SVC>_PORT` | `5432` (pg) / `1521` (ora) | Database port. |
| `DB_<SVC>_NAME` | `esq2025` (pg) / `MIR0N` (ora) | Database / service name. |
| `DB_<SVC>_USERNAME` | `esq2025` | Database user. |
| `DB_<SVC>_PASSWORD` | `q` | Database password. Set from a secret in real deployments. |

Hikari `max-lifetime=1800000` and `idle-timeout=600000` are fixed in the yml. Pool sizing
(`maximum-pool-size`, `minimum-idle`, `connection-timeout`) and the pgjdbc fail-fast properties are
env-overridable -- see [Resilience budget](#resilience-budget-timeouts-pool--thread-sizing) below.

### Resilience budget (timeouts, pool & thread sizing)

The request-path timeout cap, the pool/thread sizing, and the fail-fast DB properties. **Every default
here is the no-redundancy setting** -- the value the stack ran before HA work. The tuned budget is set
per service in the local-k8s chart overlays (`k8s/values/*.yaml`); the OKE deploy leaves these unset and
so inherits the pre-HA defaults below. Applies to the data services (enyMan, pacMan, keySmith, bizTree;
the keep cap also to auKeep).

| Env var | Default | Description |
|---|---|---|
| `ESQ_TX_TIMEOUT_S` | `-1` | Request-path query/transaction cap (seconds); `-1` = no cap (pre-HA). A stuck query frees its worker + connection instead of hanging. The long ops (a branch move, the bizTree full-tree cache load) opt out so they are never cut off. |
| `ESQ_KEEP_QUERY_TIMEOUT_S` | `0` | Per-apply keep statement cap (seconds), via JDBC `setQueryTimeout`; `0` = uncapped (pre-HA). A stuck `*_log` apply is cancelled instead of pinning a keep connection. |
| `ESQ_TOMCAT_MAX_THREADS` | `200` | Tomcat request worker threads max (pre-HA = Boot default). HA bounds the pool so a slow downstream cannot exhaust threads fleet-wide. |
| `ESQ_TOMCAT_ACCEPT_COUNT` | `100` | Tomcat accept-queue depth. |
| `ESQ_DB_POOL_MAX` | `20` | Hikari `maximum-pool-size`. |
| `ESQ_DB_POOL_MIN_IDLE` | `20` | Hikari `minimum-idle`. |
| `ESQ_DB_CONNECT_TIMEOUT_MS` | `30000` | Hikari `connection-timeout` (ms). |
| `ESQ_DB_SOCKET_TIMEOUT_S` | `0` | pgjdbc `socketTimeout` (s); `0` = off (pre-HA). Makes a vanished DB fail fast on a half-open socket instead of hanging a worker. |
| `ESQ_DB_TCP_KEEPALIVE` | `false` | pgjdbc `tcpKeepAlive`; `false` = off (pre-HA). |

### Messaging bus (the x-rod) + topology import

Messaging runs behind one facade, **`MessagingBus`**, which builds an **x-rod** per bus. A service references a logical bus and a role;
the actual transport (ActiveMQ / Kafka / Redis) and its endpoint live in the shared **topology**,
not in per-service env. The abstract bus framework -- the x-rod engine, the transport-driver SPI, the
catalog + parameter model -- is documented in `doc/Esquire.MessagingBus.md`; THIS section is the concrete
Esquire catalog: which buses exist and the env that drives them. The vocabulary:

- **bus** (`bus-id`): a logical channel (`esquire.entity`, `esquire.kc`, `audit-c`, ...).
- **slot** (`slot-id`): a leg a participant joins -- `entity`, `kc`, `audit`.
- **node** (`node-id`): request/response buses split a slot into `request` + `response` nodes (each
  its own destination); single-node buses just carry a `destination`.
- **x-rod**: the per-slot x-rod config -- `rod-class` + engine knobs (receiver-pool.size, receiver-pool.mode,
  feed-capacity, publisher-pool.size, publisher-pool.mode, concurrency) + alive-protocol knobs (alive, heartbeat-interval,
  alive-timeout, alive-fail-fast) + send-retry knobs (send-retry, send-retry-backoff-sec, send-retry-max-attempts)
  + a `transport` block.
  - **alive protocol** (an OPT-IN session-layer keep-alive; **OFF by default**): `alive` (default `false`) --
    turn on the FIX-style HeartBeat / TestRequest session on this leg. When OFF, the leg runs no session and its
    health is the transport's own connection signal alone; when ON, a producing leg quiet for
    `heartbeat-interval` (seconds, default 10) sends a keep-alive, `alive-timeout` (seconds, default 3x
    heartbeat-interval) is the age after which the leg reads DOWN, and `alive-fail-fast` (default true) flips it
    DOWN at once on a send error (a no-op on ActiveMQ `failover:`, which queues a send rather than throwing, so
    the timeout governs there). Keep these IN SYNC across all x-rods on a slot. **When to enable it -- see
    "Connection monitoring & the alive protocol" under Health checks below.**
  - **send-retry** (an OPT-IN producer messaging-path resilience pattern; **OFF by default**): `send-retry`
    (default `false`) -- when a dispatch fails on a transport-backed producer leg, HOLD the message on the feed
    (tx) worker and re-dispatch it over a backoff ladder until the broker recovers (the SAME `ApplMsgID` per
    resend, so a consumer can dedup). Holding the single worker is the back-pressure -- queued events wait
    behind it. `send-retry-backoff-sec` (a comma list of SECONDS, default `1,2,5,5` -- the last step repeats) is the
    ladder; `send-retry-max-attempts` (default `0`) is `0` = BLOCK mode (retry until recovery, never drop) or a
    positive N = FALLBACK mode (DROP after N attempts and move on). Only a transport publisher leg gets it (an
    in-process / non-transport leg has nothing to re-dispatch); a heartbeat is never retried. Keep these IN SYNC
    across the x-rods on a slot. The hold / recover / drop trail rides the leg's `msg` audit log.
  - **worker sizing & thread model** (the leg's thread budget): `receiver-pool.size` (default `4`) is the receive /
    apply pool -- the worker count, which is the concurrency cap; `receiver-pool.mode` (default `platform`) is that
    pool's thread model -- `platform` | `virtual` | `virtual-per-task`; `concurrency` (default `1`) is the transport
    listener's own threads; `publisher-pool.size` (default `0` = publish on the single feed worker, `>0` = an async
    publish pool) with its own `publisher-pool.mode` (default `platform`); `feed-capacity` (default `4096`) is the
    feed queue DEPTH (memory, not threads). The feed (tx) worker is ALWAYS one platform thread. Every pool is the
    common `WorkerPool` (`pro.mir0n.utils.concurrent`): `platform` / `virtual` = a fixed pool of `size` reused
    workers (OS threads, or `size` virtual threads); `virtual-per-task` = one virtual thread per event, capped by
    `Semaphore(size)` (uncapped when `size = 0`). The `mode` lever is wired in every environment and passes the full
    smoke + e2e matrix on both `platform` and `virtual`; the default is `platform` because Esquire's apply pools are
    small and DB-pool-capped, so virtual threads buy nothing here -- they pay off only when a pod would otherwise
    exhaust its OS file-handle / thread budget holding many concurrently-blocked waits. See the metal-vs-virtual
    budget in `Esquire.HighAvailability.md` section 5.5.
- **transport**: provider (`activemq` | `kafka` | `redis`, or a class name) + endpoint + destination +
  `params` (opaque per-vendor knobs, e.g. `jms.useAsyncSend` / `pubSubDomain` for ActiveMQ pub/sub-vs-queue
  / `noLocal` / `group-id` / `max-len`) + (R&R) `request-node` / `response-node` + a node list. The bus carries
  no queue-vs-topic notion of its own: that is a JMS concept, set as the ActiveMQ `pubSubDomain` param
  (`true` = topic, absent/`false` = queue) and read only by `tp-activemq`.
  - **noLocal** (`transport.params.noLocal`, default off): for a broadcast `CLIENT` that both publishes and
    listens on ONE topic when the fleet runs many instances. With it on, the x-rod runs both legs over a SINGLE
    broker connection and the broker drops that connection's own publications, so the leg receives only OTHER
    instances' events. Leave it off and the rod keeps two connections and excludes its own in code by rod-id
    instead -- same result, one extra connection.
- **rod-class**: `XRod` (standard transceiver), `XRodRR` (request/response, two-node, role-routed),
  `XRodInProcess` (a generic in-process relay that runs a worker applying events locally instead of sending
  them; `messaging.xrod.impl`), `XRodInProcessKeep` (the in-process KEEP that applies events to a DB via a
  `datasource` + `director`; FQCN `pro.mir0n.esquire.dataKeep.keep.XRodInProcessKeep`),
  `XRodInfo` (logs instead of sends), `XRodDisabled` (a no-op; selected EXPLICITLY to run without a bus).
- **role**: `CLIENT` / `SERVER`.

**Transport providers** are pluggable per-vendor modules (`tp-activemq` / `tp-kafka` / `tp-redis`)
implementing `ITransportProvider`, resolved by name via `TransportProviders`. A deployment carries
only the modules it uses; each ships an `AutoConfigurationImportFilter` that suppresses Boot's
matching auto-config so a service stays transport-agnostic.

The catalog is normally ONE shared external **topology file** loaded by every service (a service may
instead define `esquire.messaging-bus` INLINE in its own application.yml, or not use the bus at all).
Docker bind-mounts `compose/topology/esquire-topology.yml`; k8s mounts the `esquire-topology` ConfigMap
(chart `k8s/charts/esquire-topology`). The file is per-environment with concrete hostnames (no `${}`). The
import is OPTIONAL. The catalog is validated at startup: a duplicate `bus-id` (across the catalog),
`slot-id` (within a bus), or `node-id` (within an x-rod's `transport.nodes`) FAILS FAST -- the list is used
as a map, so its keys must be unique.

| Env var | Default | Description |
|---|---|---|
| `TOPOLOGY_IMPORT` | `file:/etc/esquire/topology.yml` | `spring.config.import` location of the shared bus catalog. Optional (a service may inline its catalog under `esquire.messaging-bus`, or not use the bus). |

A service references a bus by a logical KEY: `esquire.<key>.messaging-bus -> {bus-id, slot-id [,
x-rod overrides]}`. Keys: `kc-bus`, `entity-bus`, `audit-bus`. The catalog also MERGES a service-local
overlay under `<spring.application.name>.messaging-bus` BY ID (the service's own-namespace overlay of the
global `esquire.messaging-bus`, used for the audit-(b) in-process leg, whose datasource is service-specific):
a service bus/slot with a SAME id REPLACES the shared one, a NEW id is added. (A separate 2nd key is needed
because Spring binds lists by index, so one shared key across two sources would replace, not merge.) The
`bus-id` / `slot-id` values are env-overridable per service (`KC_BUS_ID` / `KC_SLOT_ID`,
`ENTITY_BUS_ID` / `ENTITY_SLOT_ID`, `AUDIT_BUS_ID` / `AUDIT_SLOT_ID`). A `bus-id`/`slot-id` the catalog does
NOT resolve (unset or mistyped) for a bus the service uses **FAILS FAST** at boot -- there is no silent
fallback. To run WITHOUT a bus, point it at an explicit `XRodDisabled` leg (e.g. the catalog's `audit-off` bus).

**Selectors:** `XRodRR` CLIENT consume filters `RodID = '<rod-id>'`; SERVER consume filters
`SlotID = '<slot-id>'`; a single-node bus has no selector. `rod-id` defaults to the per-instance id
`<app>.<instanceNo>` (`spring.application.name` + `EsqUtils.instanceNo()`, the instance number derived
from the host name) when unset or blank, so each sharded replica owns a distinct rod-id.

### Audit logging (producers: enyMan, pacMan, keySmith)

Producers buffer committed entity changes and, after commit, feed them off the request thread to the
audit `slot` (`slot-id` = `audit`) -- posting a UA. **Which sink runs is one config value:**
`AUDIT_BUS_ID` names the audit bus, and the topology leg for that bus carries the transport.
The sinks:

| `audit-bus` bus-id | Sink | rod-class |
|---|---|---|
| `audit-b` | in-process apply to the `*_log` tables (a SERVICE-LEVEL leg whose datasource block carries the datasource; the keep applier writes `*_log` via the generic keep engine) | `XRodInProcessKeep` |
| `audit-c` | ActiveMQ -> the standalone **auKeep** consumer -> `*_log` | `XRod` |
| `audit-ck` | Kafka -> auKeep -> `*_log` | `XRod` |
| `audit-d` | Redis stream IS the log (producer-only; no consumer service) | `XRod` |
| `audit-dk` | Kafka topic IS the log (producer-only; no consumer service) | `XRod` |
| `audit-off` | bus audit OFF -- an explicit no-op sink (e.g. DB triggers carry the audit instead) | `XRodDisabled` |

Docker and local k8s default `audit-c`; the code default is `audit-b`. Switching sink is the one env
flip + the matching topology leg (and infra) -- no code change, no rebuild.

| Env var | Default | Description |
|---|---|---|
| `AUDIT_BUS_ID` | `audit-b` code / `audit-c` deployed | Selects the audit bus (the sink) the producer posts to. |
| `AUDIT_SLOT_ID` | `audit` | The audit `slot-id`. |
| `AUDIT_POOL_SIZE` | `4` | Audit x-rod receive / apply-pool size; keep <= the keep datasource pool. |
| `AUDIT_FEED_CAPACITY` | `4096` | Producer feed depth (bounded; full -> back-pressures flush-after-commit). |
| `AUDIT_POOL_MODE` | `platform` | Apply-pool thread model: `platform` \| `virtual` \| `virtual-per-task`. |

**audit-(b) keep datasource** (only when the `audit-b` in-process leg is active -- it writes `*_log`
locally, so its datasource is service-specific and configured on the service-local topology key):

| Env var | Default | Description |
|---|---|---|
| `DB_DATAKEEP_URL` | *(empty)* | Keep JDBC URL. The SQL dialect is read from this URL's subprotocol (`jdbc:postgresql...` -> Postgres, `jdbc:oracle...` -> Oracle). |
| `DB_DATAKEEP_USERNAME` | `esq2025` | Keep DB user. |
| `DB_DATAKEEP_PASSWORD` | `q` | Keep DB password. |
| `DB_DATAKEEP_POOL_SIZE` | `2` | Keep Hikari pool size (maps to `datasource.hikari.maximum-pool-size`). |

These drive the producer in-process `x-rod.datasource` block: the keep gets its OWN dedicated pool from
`url`/`hikari`, isolated from the business queries (it can even target a different database than the service).
The keep reads its SQL dialect from the database URL -- `jdbc:postgresql...` means Postgres, `jdbc:oracle...`
means Oracle -- so the keep can run a different dialect than the service. The auKeep CONSUMER instead reads its keep
datasource from a separate `esquire.keep.datasource` group (same record shape: url / username /
password / hikari; on docker it points at `DB_DATAKEEP_*`), and excludes Boot's
`DataSourceAutoConfiguration` (no `spring.datasource`).

**Keep datasource -- connection settings so the health check actually detects a DB outage (recommended).** A
JDBC pool HANGS on a vanished database when the socket goes half-open (no FIN/RST -- the typical k8s
pod-deleted case): `Connection.isValid` / `getConnection` block on unacknowledged TCP, so `keepDatasource` never
flips DOWN. Give the keep pool short driver + pool timeouts -- set the standard Hikari way, under
`hikari.data-source-properties` (the same key Spring Boot exposes as `spring.datasource.hikari.data-source-properties`),
so the JDBC driver enforces them:

```yaml
esquire:
  keep:
    datasource:
      hikari:
        connection-timeout: 5000          # ms -- caps getConnection (the health probe), so DOWN is reported fast
        data-source-properties:           # forwarded verbatim to the JDBC driver
          socketTimeout: 5                # pgjdbc, SECONDS -- a socket read fails this fast, so isValid cannot hang
          connectTimeout: 5               # pgjdbc, SECONDS -- a new connect fails this fast
          tcpKeepAlive: true              # OS-level keepalive, a backstop for a vanished peer
```

- Keep the values SMALL but above the slowest real query (the keep applies single-row `*_log` INSERT/MERGE,
  sub-second -- 5s is safe), so detection is bounded (~`connection-timeout`) and easy to verify: kill the DB,
  watch `keepDatasource` flip DOWN, then recover.
- **Oracle** uses the SAME `data-source-properties` map with the Oracle names: `oracle.jdbc.ReadTimeout` (ms) +
  `oracle.net.CONNECT_TIMEOUT` (ms). They are driver PROPERTIES (not URL params), which is exactly why the
  passthrough -- not the URL -- is the portable place for them.
- The same applies to ANY health-gating DB pool, not just the keep: a pool whose health is read on
  `/actuator/health` needs a bounded socket/connect timeout, or the probe can hang on a half-open socket.

The `*_log` SQL each producer (and auKeep) can write is shipped as a deploy-time
`META-INF/audit/{dialect}.xml` spec set; omit those files to package the service with no audit SQL (the
loader tolerates the absence -> empty map, so the in-process leg silently no-ops). The `*_log` tables
are seeded on a fresh cluster by the `esquire-postgres` image (its `create/all.sql` chains to
`create.log/all.sql`); the `audit-b`/`audit-c`/`audit-ck` sinks need them, the Redis/Kafka-as-log
sinks (`audit-d` / `audit-dk`) do not. See [auKeep](#aukeep) for the bus consumer and
`doc/Esquire.AuditLoggingStack.md` for the delivery-semantics trade-offs.

### Server port

| Env var | Default | Description |
|---|---|---|
| `<SVC>_PORT` | `3000` (yml) | HTTP listen port. Compose/k8s assign per-service ports (see each service). |

### Health checks (readiness / liveness)

Each service forwards its **messaging-bus connection** health to `/actuator/health` -- a broker outage takes the
pod out of rotation (readiness) but never restarts it (liveness). The `management` block (in `application.yml`)
enables the probe groups and puts the bus indicator in the **readiness** group only:

```yaml
management:
  endpoint:
    health:
      probes: { enabled: true }            # expose /actuator/health/readiness + /liveness
      show-details: always
      validate-group-membership: false     # the bus indicator is registered dynamically (at app-ready), so the startup check is off
      group:
        readiness:
          include: readinessState, messagingBus   # bizTree adds cacheReadiness; auKeep adds keepDatasource
```

- **`messagingBus`** -- the per-bus connection map (every bus this service uses; DOWN if any is DOWN). Registered
  programmatically by the lifecycle registrar at app-ready (no `@Bean`). Each bus's health is the **worse of**
  its TRANSPORT connection signal (always) and -- only when the alive protocol is enabled on the leg -- the
  `alive-timeout`-bounded keep-alive. A transport that cannot observe its connection reports UNKNOWN, which is
  shown but does NOT fail readiness (only DOWN does). See "Connection monitoring & the alive protocol" below.
- **`keepDatasource`** (auKeep only) -- the keep `*_log` database connection (the apply side), beside the
  consumer's broker health. It reads DOWN within a few seconds of the DB going unreachable because the keep pool
  carries short socket/connect + connection timeouts (see the keep datasource settings above) -- without them the
  probe hangs on a half-open socket and never flips.
- **`cacheReadiness`** (bizTree only) -- the pre-existing cache-bootstrap gate, kept alongside `messagingBus`.

The **k8s charts** point `readinessProbe` at `/actuator/health/readiness` and `livenessProbe` at
`/actuator/health/liveness` (so a bus-down fails readiness, never liveness). The shared topology reaches
ActiveMQ through a **`failover:(tcp://...)?timeout=3000`** endpoint -- auto-reconnect (so the health recovers on
its own when the broker returns) + a 3s send timeout (a send during an outage fails fast instead of blocking).

#### Connection monitoring & the alive protocol (when to enable)

A bus's health comes from two sources, folded to the **worse** of the two:

**1. The transport's own connection monitoring (always on, the default).** Each vendor client already detects a
broker outage by itself -- no extra traffic needed. Tune it through the leg's `transport.params` (opaque
per-vendor knobs). The vendors differ -- some observe the connection actively, some only on send-outcome, some
not at all (then they report UNKNOWN, which is benign):

- **ActiveMQ** -- the `failover:(tcp://...)?timeout=N` endpoint auto-reconnects and a connection `TransportListener`
  reports interrupt/resume without any send; the send carries a bounded timeout. The richest native signal of
  the three.
- **Redis (Lettuce)** -- a connection-state listener (connected/disconnected) + auto-reconnect, plus **TCP
  keepalive** (`KeepAliveOptions` idle/interval/count) and a command timeout (set as URI options on the
  `transport.endpoint` / via `transport.params`). WITHOUT keepalive a silently dropped connection can hang
  undetected (the half-open-socket trap) -- set keepalive + a timeout on any leg whose health matters.
- **Kafka** -- the consumer's group heartbeat (`heartbeat.interval.ms` / `session.timeout.ms`), the producer's
  metadata refresh + request/delivery timeouts, and `connections.max.idle.ms` (all settable verbatim via
  `transport.params`). The client monitors the broker connection itself; no application ping is needed.

**2. The Esquire alive protocol (OPT-IN, `alive: true` -- OFF by default).** A session-layer HeartBeat /
TestRequest above the transport. Enable it only where the transport's own signal is not enough:

- **The round-trip case** ("can a message actually traverse the bus and come back from a peer") -- **R&R only**:
  a CLIENT learns its SERVER is reachable-but-dead, which no connection monitor can tell.
- **The idle case** -- with no traffic, a send-outcome-only transport (Redis/Kafka) reads UNKNOWN until the next
  real send; the alive protocol fills that gap and can flag a fault FASTER (bounded by `alive-timeout`) than the
  transport's own mechanism. **Optional, and recommended mainly for ActiveMQ** legs where a deterministic,
  bounded DOWN is wanted.

> **If you enable `alive` on a Redis or Kafka leg:** the heartbeats are routed to a SEPARATE `<destination>.admin`
> stream/topic (never the data log). **Keep that admin channel NON-DURABLE** -- tp-redis caps the admin stream
> (`MAXLEN`) and tp-kafka creates the admin topic with a short `retention.ms`, but if you pre-create or manage
> these yourself, set a tiny retention so the throwaway heartbeats self-purge.

**Role note:** a CLIENT (consumer) role over a produce-only transport (the XADD-only Redis) **fails fast at boot**
(`unsupported config ... cannot run ... for a CLIENT role`) -- Redis can be a SERVER (producer) but not a CLIENT.

**Supported vendor x role, and the `alive` effect** (verified on the docker stack):

| transport | SERVER (produces) | CLIENT (consumes) | with `alive: true` |
|---|---|---|---|
| **ActiveMQ** | yes | yes | heartbeats flow on the destination; the consumer's `isSession()` filter drops them |
| **Kafka** | yes | yes | heartbeats routed to `<topic>.admin` (short retention); the log topic stays clean |
| **Redis** | yes | **no -- FAILS FAST** (XADD-only) | heartbeats routed to `<stream>.admin` (capped); the log stream stays clean |

With `alive` **OFF** (the default) NO heartbeats are emitted on any leg, and a bus's health is its transport
indicator alone; with `alive` **ON**, a quiet producing leg self-heartbeats and the metric folds into the bus
health. A `<destination>.admin` channel is created only on the Redis/Kafka legs that carry session traffic.

### Instance identity (enyMan / common — entity-id minting)

Resolved by `EsqUtils.instanceNo()` from a single source — the trailing ordinal of the host name
(`EsqUtils.instanceHost()` = `HOSTNAME` → `POD_NAME` → local hostname):

| Source | Default | Description |
|---|---|---|
| trailing ordinal of the host name | *(derived)* | The StatefulSet pod-name ordinal (`enyman-3` → 3), or a `hostname: <app>-N` set on the Docker container, parsed from `instanceHost()`. |
| *(fallback)* | `0` | No ordinal in the name — a single, unsharded instance. |

The deployment carries the sequence in the name whenever it runs for resilience (a StatefulSet does
this by construction). The same number is the per-instance token of the default rod-id
(`<app>.<instanceNo>`). A plain Deployment / default Docker name has no parseable ordinal, so every
replica resolves to `0`; to shard (unique entity ids and unique rod-ids) run as a StatefulSet (or give
each container a `hostname: <app>-N`).

Used to keep minted entity ids unique across redundant enyMan instances; an instance number
outside `0..9` fails fast at first mint.

---

## gateway

Edge service: Spring Cloud Gateway, KC OIDC + resource-server, CORS, Token Relay, optional JWE,
and the route table (see [Gateway routes](#gateway-routes)).

**Port:** `GATEWAY_PORT` (default `8080`). **Shared:** messaging bus not used; logging (below).

| Env var | Default | Description |
|---|---|---|
| `KEYCLOAK_HOST` | `keycloak` | Keycloak host for the OIDC/JWKS URIs. |
| `KEYCLOAK_PORT` | `8080` | Keycloak port. |
| `KEYCLOAK_PATH` | *(empty)* | KC context path prefix (e.g. `/kc-auth` when KC runs under a relative path). |
| `KEYCLOAK_REALM` | `esquire` | Realm used in all KC endpoint URIs. |
| `KEYCLOAK_CLIENT_ID` | `esq-angular` | OAuth2 client registration id (authorization_code login). |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Allowed CORS origin (Spring Security CORS; globalcors is disabled). |
| `GW_SERVICE_METRICS_ENABLED` | `false` | Collect downstream per-service timing metrics. |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | *(derived)* | Resource-server JWKS URI. k8s sets it directly to bypass the empty-`KEYCLOAK_PATH` placeholder gotcha. |
| `jwt.secret` | *(required, no default)* | Symmetric JWT secret used by the gateway filters; must be provided. |

### Token Relay (gateway brokers/exchanges JWTs downstream)

Both variants are dormant when their `clients` allowlist is empty.

| Env var | Default | Description |
|---|---|---|
| `ESQ_GW_TOKEN_URI` | *(empty)* | KC `/token` endpoint shared by both relay variants. |
| `ESQ_GW_VANILLA_CLIENTS` | *(empty)* | Allowlist for **Vanilla Token Relay** (client sends HTTP Basic; gateway runs client_credentials, caches per client_id). |
| `ESQ_GW_PHANTOM_CLIENTS` | *(empty)* | Allowlist for **Phantom Token Relay** (client sends a stripped Bearer; gateway runs RFC 8693 token-exchange, caches per jti). |
| `ESQ_GW_EXCHANGE_CLIENT_ID` | *(empty)* | Confidential client id (`esq-gw-exchange`) authenticating the phantom token-exchange. |
| `ESQ_GW_EXCHANGE_CLIENT_SECRET` | *(empty)* | Secret for the exchange client. |

### JWE (optional access-token encryption)

| Env var | Default | Description |
|---|---|---|
| `ESQ_JWE_PRIVATE_KEY_PATH` | *(empty)* | In-container path to the JWE keypair. When set, the JWE-aware decoder is wired and `/jwe-jwks` serves the public key; blank → plain JWS decoder (the v1.2.3 baseline). |

### Resilience (circuit breaker, per-route timeout & retry, connection pool)

The gateway sheds load away from a failing backend (an open circuit breaker) and bounds each call with a
per-route timeout; an open/tripped breaker or a timed-out call surfaces as the `GatewayErrorWebExceptionHandler`
503/504 ProblemDetail. The per-route deadline is the breaker's TimeLimiter (NOT the Netty response-timeout,
which is a generous backstop above it). The slow-write breakers (`enyman-move-cb`, `pacman-acct-cb`,
`enyman-new-cb`) use the longer `slow-timeout`. Defaults below are the no-redundancy settings; the tuned
budget is in the local-k8s overlay.

| Env var | Default | Description |
|---|---|---|
| `GW_CB_TIMEOUT_S` | `10` | Default per-route call deadline (the breaker's TimeLimiter). |
| `GW_CB_SLOW_TIMEOUT_S` | `30` | Per-route deadline for the slow-write routes (move / acct / create). |
| `GW_CB_WINDOW` | `20` | Breaker sliding-window size (count-based). |
| `GW_CB_MIN_CALLS` | `10` | Minimum calls before the breaker computes its rates. |
| `GW_CB_FAILURE_RATE` | `50` | Failure-rate % that opens the breaker. |
| `GW_CB_SLOW_RATE` | `100` | Slow-call-rate % that opens the breaker. |
| `GW_CB_SLOW_DURATION_S` | `8` | A call slower than this counts as a slow call. |
| `GW_CB_OPEN_WAIT_S` | `10` | Time the breaker stays open before trialing half-open. |
| `GW_CB_HALFOPEN_CALLS` | `5` | Trial calls allowed in the half-open state. |
| `GW_RETRY_READ` | `3` | Retry attempts on read routes (connect-failure + timeout, GET only). |
| `GW_RETRY_WRITE` | `1` | Retry attempts on write routes (connect-failure ONLY -- a non-idempotent POST is never resent). |
| `GW_CONNECT_TIMEOUT_MS` | `2000` | Netty client connect-timeout (ms) -- fails fast if a pod is unreachable. |
| `GW_RESPONSE_TIMEOUT` | `35s` | Netty response-timeout backstop (keep above `GW_CB_SLOW_TIMEOUT_S`). |
| `GW_POOL_MAX_CONNECTIONS` | `16` | Backend connection pool max-connections. |
| `GW_POOL_ACQUIRE_TIMEOUT_MS` | `45000` | Pool acquire-timeout (ms). |

### Route upstream hosts/ports

The route table targets these (defaults are dev-quirky leftovers — always overridden by compose/k8s):
`KEYSMITH_HOST` (`biztree`) / `KEYSMITH_PORT` (`3002`), `BIZTREE_HOST` (`biztree`) / `BIZTREE_PORT`
(`3002`), `PACMAN_HOST` (`enyman`) / `PACMAN_PORT` (`3003`), `ENYMAN_HOST` (`enyman`) / `ENYMAN_PORT`
(`3003`).

---

## enyMan

Entity manager: org/user/account CREATE, save, move, the in-process move queue, entity-id minting.

**Port:** `ENYMAN_PORT` (`3000` code / `3003` deployed). **Shared:** DB token `ENYMAN`, messaging
bus, logging, instance identity, [audit logging](#audit-logging-producers-enyman-pacman-keysmith).

enyMan joins three buses: **entity-bus** (CLIENT, role both legs -- it broadcasts UE AND listens for its
peers' creates on one shared connection, v1.2.10 Goal-4), **kc-bus** (R&R CLIENT to kcMaster), and
**audit-bus** (UA producer). The bus-id / slot-id and x-rod knobs come from the shared topology; the env
overrides it actually uses:

| Env var | Default | Description |
|---|---|---|
| `ENTITY_BUS_ID` | `esquire.entity` | Entity-broadcast bus-id. enyMan runs both legs on it (publishes UE and listens for peer creates). |
| `ENTITY_SLOT_ID` | `entity` | Entity slot-id. |
| `ENTITY_RX_POOL_SIZE` | `2` | Entity-bus receive-leg listener pool size (Goal-4 peer-create receive). |
| `ENTITY_RX_CONCURRENCY` | `1` | Entity-bus receive-leg listener concurrency. |
| `ENTITY_BROADCAST_SEND_RETRY` | `false` | Producer [send-retry](#messaging-bus-the-x-rod--topology-import) on the entity broadcast leg; ON in docker + local-k8s, OFF on OKE. Ladder `ENTITY_BROADCAST_SEND_RETRY_BACKOFF_SEC` (`1,2,5,5`) + cap `ENTITY_BROADCAST_SEND_RETRY_MAX_ATTEMPTS` (`0` = block). |
| `KC_BUS_ID` | `esquire.kc` | KC request/response bus-id (R&R CLIENT). |
| `KC_SLOT_ID` | `kc` | KC slot-id. |
| `KC_SEND_RETRY` | `false` | Producer send-retry on the KC request leg (+ `KC_SEND_RETRY_BACKOFF_SEC` `1,2,5,5` / `KC_SEND_RETRY_MAX_ATTEMPTS` `0`); ON in docker + local-k8s. |
| `AUDIT_BUS_ID` | *(see [audit logging](#audit-logging-producers-enyman-pacman-keysmith))* | Audit sink bus-id (UA producer). |
| `ENYMAN_MOVE_QUEUE_CAPACITY` | `16384` | Move-queue depth (bounded; on full, `submitMove`/`submitReconcile` drop + log). |
| `ENYMAN_MOVE_TX_TIMEOUT_S` | `0` | Move-transaction cap (seconds); `0` = uncapped (pre-HA). The move opts OUT of the request-path cap ([`ESQ_TX_TIMEOUT_S`](#resilience-budget-timeouts-pool--thread-sizing)) -- set a positive value only to put a safety ceiling on a move. |
| `ENYMAN_VALIDATE_CREATE_DURING_MOVE` | `true` | v1.2.6 Goal 3: `true` runs CREATE-during-move path reconciliation (race-8b closed); `false` reproduces the race (negative test). |

---

## pacMan

Accounting: account balance / deposit / withdrawal / transfer and account DELETE.

**Port:** `PACMAN_PORT` (`3000` code / `3003` deployed). **Shared:** DB token `PACMAN`, messaging bus,
logging, [audit logging](#audit-logging-producers-enyman-pacman-keysmith).

pacMan joins **entity-bus** (SERVER -- broadcasts UE) and **audit-bus** (UA producer); no kc-bus.

| Env var | Default | Description |
|---|---|---|
| `ENTITY_BUS_ID` | `esquire.entity` | Entity-broadcast bus-id (SERVER producer). |
| `ENTITY_SLOT_ID` | `entity` | Entity slot-id. |
| `ENTITY_BROADCAST_SEND_RETRY` | `false` | Producer [send-retry](#messaging-bus-the-x-rod--topology-import) on the entity broadcast leg; ON in docker + local-k8s, OFF on OKE. Ladder `ENTITY_BROADCAST_SEND_RETRY_BACKOFF_SEC` (`1,2,5,5`) + cap `ENTITY_BROADCAST_SEND_RETRY_MAX_ATTEMPTS` (`0` = block). |
| `AUDIT_BUS_ID` | *(see [audit logging](#audit-logging-producers-enyman-pacman-keysmith))* | Audit sink bus-id (UA producer). |

---

## keySmith

Access-profile / credential routine; publishes KC-sync requests (JMS) to kcMaster.

**Port:** `KEYSMITH_PORT` (`3000` code / `3002` deployed). **Shared:** DB token `KEYSMITH`, messaging
bus, logging, [audit logging](#audit-logging-producers-enyman-pacman-keysmith).

keySmith joins **kc-bus** (R&R CLIENT to kcMaster, publishing KC-sync requests) and **audit-bus** (UA
producer).

| Env var | Default | Description |
|---|---|---|
| `KC_BUS_ID` | `esquire.kc` | KC request/response bus-id (R&R CLIENT). |
| `KC_SLOT_ID` | `kc` | KC slot-id. |
| `KC_SEND_RETRY` | `false` | Producer [send-retry](#messaging-bus-the-x-rod--topology-import) on the KC request leg; ON in docker + local-k8s, OFF on OKE (pre-HA). Ladder `KC_SEND_RETRY_BACKOFF_SEC` (`1,2,5,5`) + cap `KC_SEND_RETRY_MAX_ATTEMPTS` (`0` = block until recovery). |
| `AUDIT_BUS_ID` | *(see [audit logging](#audit-logging-producers-enyman-pacman-keysmith))* | Audit sink bus-id (UA producer). |
| `KEYSMITH_TEST_CONNECT_HOLD_MS` | `0` | **Test-only** race-8c hook: ms to sleep between the committed path read and the activation URQ publish. `0` = disabled; never set in production. |

---

## kcMaster

Owns all Keycloak synchronization (create/update/delete users, path sync). No application DB — it
talks to KC over the admin REST API.

**Port:** `KCMASTER_PORT` (`3006`). **Shared:** messaging bus, logging. (No DB.)

| Env var | Default | Description |
|---|---|---|
| `KC_BASE_URL` | `http://localhost:8080` | Keycloak base URL for the admin client (include `/kc-auth` when KC runs under a relative path). |
| `KC_REALM` | `esquire` | Realm kcMaster manages. |
| `KC_ADMIN_CLIENT_ID` | `admin-cli` | Admin client id. Deployments use the confidential service-account client `esq-kcMaster`. |
| `KC_ADMIN_CLIENT_SECRET` | *(empty)* | Secret for a confidential admin client. |
| `KC_BUS_ID` | `esquire.kc` | KC request/response bus-id (R&R SERVER; serves enyMan + keySmith). |
| `KC_SLOT_ID` | `kc` | KC slot-id (SERVER consume filters `SlotID = '<slot-id>'`). |
| `KC_SEND_RETRY` | `false` | Producer [send-retry](#messaging-bus-the-x-rod--topology-import) on the KC response leg; ON in docker + local-k8s, OFF on OKE. Ladder `KC_SEND_RETRY_BACKOFF_SEC` (`1,2,5,5`) + cap `KC_SEND_RETRY_MAX_ATTEMPTS` (`0` = block). |
| `ENTITY_BUS_ID` | `esquire.entity` | Entity-broadcast bus-id (CLIENT consumer, for KC path sync). |
| `ENTITY_SLOT_ID` | `entity` | Entity slot-id. |
| `KCMASTER_PATH_BUFFER_TTL_MS` | `60000` code / `10000` deployed | Race-8c path-buffer TTL. Buffered topic-side paths older than this are not applied. **Test:** `-1` disables recovery (reproduces the race). |
| `KCMASTER_PATH_BUFFER_PRUNE_MS` | `30000` | Interval of the scheduled buffer prune. |

KC admin client timeouts are fixed in the yml: `connect-timeout-ms=5000`, `read-timeout-ms=10000`.

---

## bizTree

Recoverable read cache (Taijitu two-monad H2). Consumes entity broadcasts, serves tree reads,
runs the night-watch anti-entropy sweep.

**Port:** `BIZTREE_PORT` (`3000` code / `3002` deployed). **Shared:** DB token `BIZTREE` (the source
DB read at cache load), messaging bus, logging.

| Env var | Default | Description |
|---|---|---|
| `BIZTREE_CACHE_VENDOR` | `cache-h2` | Cache backend profile (H2 in-memory). |
| `ENTITY_BUS_ID` | `esquire.entity` | Entity-broadcast bus-id (CLIENT consumer; updates the cache). |
| `ENTITY_SLOT_ID` | `entity` | Entity slot-id. |
| `BIZTREE_DIRECTOR` | `legacy` code / `taijitu` deployed | Cache director: `legacy` (single cache) or `taijitu` (two-monad night-watch). |
| `BIZTREE_CACHE_TABLE` | `ESQ_TREE` | Base cache table name; taijitu suffixes it per monad (`ESQ_TREE_MONAD` / `ESQ_TREE_DANOM`). |
| `BIZTREE_MONAD_QUEUE_CAPACITY` | `4096` | Per-monad event queue depth (back-pressure boundary). |
| `BIZTREE_QUEUE_BULK_THRESHOLD` | `10` | Backlog size above which the monad worker batches events into ONE cache transaction (the throughput win). Set very high to force one-by-one (A/B). |
| `BIZTREE_TAIJITU_ON_MISMATCH` | `LOG` code / `SWAP` deployed | Night-watch reaction when the two monads' checksums disagree: `LOG` \| `SWAP` \| `TERMINATE`. |
| `BIZTREE_TAIJITU_SWEEP_INTERVAL_MS` | `10000` code / `600000` deployed | Interval between night-watch sweeps. |
| `BIZTREE_TAIJITU_SWEEP_TIMEOUT_MS` | `10000` | Per-leg CHECKSUM deadline; a slower leg is cancelled (the sweep is inconclusive, not fatal). |
| `BIZTREE_CACHE_LOAD_TX_TIMEOUT_S` | `0` | Startup full-tree cache load transaction cap (seconds); `0` = uncapped (pre-HA). The whole-tree read opts OUT of the request-path cap ([`ESQ_TX_TIMEOUT_S`](#resilience-budget-timeouts-pool--thread-sizing)) -- set a positive value only to put a safety ceiling on a full-tree load. |

### H2 cache datasource / pool

| Env var | Default | Description |
|---|---|---|
| `BIZTREE_H2_URL` | `jdbc:h2:mem:biztree;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` | H2 cache JDBC URL (independent of the source DB). |
| `BIZTREE_H2_POOL_MAX` | `10` | Hikari max pool size. |
| `BIZTREE_H2_POOL_MIN_IDLE` | `10` | Hikari minimum idle. |
| `BIZTREE_H2_POOL_CONN_TIMEOUT` | `5000` | Connection timeout (ms). |
| `BIZTREE_H2_POOL_MAX_LIFETIME` | `1800000` | Connection max lifetime (ms). |
| `BIZTREE_H2_POOL_IDLE_TIMEOUT` | `600000` | Idle timeout (ms). |
| `BIZTREE_H2_QUERY_TIMEOUT_S` | `0` | Per-statement cap on the in-memory H2 cache (seconds); `0` = uncapped (pre-HA). The H2 cache surface of the request-path cap -- a guard against a pathological cache statement, not a tuning knob. |

---

## auKeep

Standalone **audit-bus consumer** (image `esquire.aukeep`): it drains whichever bus the `audit-bus`
ref names (`AUDIT_BUS_ID`, default `audit-c`) and applies each decoded event to its keep
datasource (`esquire.keep.datasource`) via the **generic keep engine** (`KeepApplier` /
`RodEventDbWriter` / `KeepSqlStore`, in the `esquire-dataKeep` library). The audit keep director
(`AuditKeepDirector`, an `IKeepDirector`) only DECLARES the kinds + the SQL group `audit`; the engine
does the DB apply. It carries all transport-provider modules, so it consumes the ActiveMQ (`audit-c`)
or Kafka (`audit-ck`) sink as the topology leg dictates; producer-only sinks (`audit-d` / `audit-dk`)
have no auKeep. Horizontally redundant (competing consumers; no clientId) -- it drains the bus audit
sink to the `*_log` tables. It ships the **full** `META-INF/audit/{dialect}.xml` SQL set (it writes
every kind). The in-process x-rod for producers (audit-(b)) is `XRodInProcess`, the same generic relay
backed by the same keep engine. See `doc/Esquire.AuditLoggingStack.md` section 4.7.

**Port:** `DATAKEEP_PORT` (`3007`). **Shared:** DB token `DATAKEEP` (the keep datastore it writes;
vendor from `DB_DATAKEEP_VENDOR`), messaging bus (consumes the audit bus), logging. (No producer audit
block -- auKeep is the consumer side.)

| Env var | Default | Description |
|---|---|---|
| `AUDIT_BUS_ID` | `audit-c` | The `audit-bus` ref -- which bus auKeep drains; the topology leg supplies the transport. |
| `AUDIT_SLOT_ID` | `audit` | The audit `slot-id`. |

The keep director is wired in code, not selected by config: one `IKeepDirector` = `AuditKeepDirector`,
which declares its kinds + SQL group `audit`. A future replication / doc-DB keep is a different
`IKeepDirector`, also wired in code.

The apply-pool sizing (`receiver-pool.size`, `receiver-pool.mode`, `concurrency`) is the audit leg's `x-rod` block
in the shared topology; keep `receiver-pool.size` <= the datasource Hikari pool. `receiver-pool.mode` stays
`platform` -- this apply pool is DB-pool-capped, so virtual threads buy nothing here (see HA 5.5).

---

## backend (BFF)

Browser-facing Backend-for-Frontend (image `esquire.backend`, Node/Express): it serves the Angular SPA,
owns the OIDC login (authorization-code + PKCE, session in an HttpOnly cookie), and proxies `/api/*` to the
gateway with the session's bearer injected. The SPA and the BFF ship in one image; the SPA's browser-side
runtime config is served at `/assets/config.json`. No messaging bus, no database.

**Port:** `PORT` (`3000`). Deployed behind ingress (local: `esquire.localhost`; OKE: `esquire.mir0n.pro`).

| Env var | Default | Description |
|---|---|---|
| `PORT` | `3000` | HTTP listen port. |
| `NODE_ENV` | `development` (image: `production`) | Node environment. |
| `PUBLIC_BASE_URL` | `http://localhost:3000` | Browser-visible base URL; the base for `redirect_uri` / post-login + post-logout locations. |
| `ALLOWED_ORIGINS` | *(empty)* | Comma-separated extra origins accepted on `/auth/*` (the request Origin/Referer is validated against this list; `PUBLIC_BASE_URL` is always included). |
| `KC_ISSUER` | `http://localhost:8080/kc-auth/realms/esquire` | Public, browser-facing realm issuer -- the token issuer and the base for authorize / end_session. |
| `KC_ISSUER_INTERNAL` | *(= `KC_ISSUER`)* | URL the BFF discovers KC through server-to-server. On local k8s the public host is loopback inside a pod, so this points at the in-cluster KC service; KC's backchannel-dynamic config keeps the issuer + browser endpoints public. |
| `KC_CLIENT_ID` | `esq-angular` | OIDC client registration id. |
| `KC_CLIENT_SECRET` | *(dev literal; required)* | OIDC client secret. Set from a secret in real deployments. |
| `GATEWAY_URL` | `http://localhost:7070` | In-cluster gateway base the `/api/*` proxy targets (skips the public hop). |
| `SESSION_SECRET` | *(dev literal; required)* | express-session signing secret. Set from a secret in real deployments. |
| `SESSION_MAX_AGE_MS` | `43200000` (12h) | Session cookie max age. |
| `REDIS_URL` | *(empty)* | Shared session store. Empty -> in-memory `MemoryStore` (correct only at a single replica). Set to the in-cluster redis to share sessions across replicas (required to run more than one BFF copy). |
| `ESQ_DICT_CACHE_TTL_MS` | `3600000` (1h) | Entity-dictionary proxy-cache TTL. |
| `ESQ_DICT_CACHE_MAX` | `64` | Entity-dictionary proxy-cache max entries. |
| `BFF_REQUEST_TIMEOUT_MS` | `0` | R1: whole-request deadline (`http.Server.requestTimeout`); `0` = Node default (none, pre-HA). |
| `BFF_PROXY_TIMEOUT_MS` | `0` | R1: `/api` upstream-proxy deadline (BFF -> gateway hop); `0` = none (pre-HA). |

### SPA runtime config (browser-fetched at `/assets/config.json`)

Served to the browser, not BFF process env. The image bakes pre-HA defaults; on local k8s a ConfigMap
(`{release}-backend-spaconfig`, rendered only when `spa.httpTimeoutMs` is set in the chart) is mounted over
the baked `config.json`.

| Key | Default | Description |
|---|---|---|
| `apiBasePath` | `/api` | Base path the SPA calls the BFF on. |
| `httpTimeoutMs` | `0` | R1 client-side request timeout (ms); `0` / absent = no timeout (pre-HA). A positive value bounds a hung request in the browser. |

---

## Logging configuration

Three log tiers (see `doc/Logging.md` for the full strategy):

- **console** (`log`) — operational, level `LOG_LEVEL_MIR0N`.
- **develop** (`devLog` → logger `develop.<class>`) — verbose diagnostics, written to a file.
- **msg audit** (`msgLog` → logger `msg.<class>`) — JMS publishers/listeners only.

Levels (all services unless noted):

| Env var | Default | Description |
|---|---|---|
| `LOG_LEVEL_ROOT` | `ERROR` | Root logger level. |
| `LOG_LEVEL_SF` | `ERROR` | `org.springframework` level. |
| `LOG_LEVEL_JMS` | `INFO` (enyMan/pacMan/kcMaster/auKeep) / `ERROR` (bizTree/keySmith) | `org.springframework.jms` level. (Not present in the gateway.) |
| `LOG_LEVEL_AMQ` | `INFO` / `ERROR` (as JMS above) | `org.apache.activemq` level. |
| `LOG_LEVEL_MIR0N` | `INFO` (enyMan/bizTree) / `ERROR` (gateway/pacMan/keySmith/kcMaster/auKeep) | Application (`pro.mir0n`) console level. |
| `LOG_LEVEL_DEVELOP` | `DEBUG` | `develop.*` (devLog) level. |
| `LOG_LEVEL_MSG` | `INFO` | `msg.*` (msgLog) level. (Not present in the gateway.) |

File paths:

| Env var | Default | Description |
|---|---|---|
| `DEVELOP_LOG_PATH` | `logs/<svc>-develop.log` | develop-tier log file. |
| `MSG_LOG_PATH` | `logs/<svc>-msg.log` | msg-audit log file. (Not present in the gateway.) |

---

## Gateway routes

All routes apply the same filters: `DedupeResponseHeader=... RETAIN_FIRST`, `RewritePath` (strip
nothing — identity), and `TokenRelay=`. `EntityKind=isAcct` is a custom predicate routing account
commands to pacMan; the same path without it falls through to enyMan.

| Route id | Path(s) | Method | Predicate | Upstream |
|---|---|---|---|---|
| `keysmith-route` | `/esq-key` | GET, POST | — | keySmith |
| `biztree-route` | `/esq`, `/esq-path`, `/esq-enode`, `/esq-tree`, `/esq-sweep` | GET, POST | — | bizTree |
| `pacman-route` | `/esq-cmd` | GET, POST | `EntityKind=isAcct` | pacMan |
| `enyman-route` | `/esq-cmd`, `/esq-dict`, `/esq-kinds` | GET, POST | — | enyMan |
| `pacman-save-route` | `/esq-cmd-save` | POST | `EntityKind=isAcct` | pacMan |
| `enyman-save-route` | `/esq-cmd-save` | POST | — | enyMan |
| `keysmith-save-route` | `/esq-key-save` | POST | — | keySmith |
| `enyman-new-route` | `/esq-cmd-new` | POST | — | enyMan *(account CREATE moved here in v1.2.6)* |
| `pacman-del-route` | `/esq-cmd-del` | POST | `EntityKind=isAcct` | pacMan |
| `enyman-del-route` | `/esq-cmd-del` | POST | — | enyMan |
| `pacman-acct-route` | `/esq-acct` | POST | `EntityKind=isAcct` | pacMan |
| `enyman-move-route` | `/esq-move` | POST | — | enyMan |
| `enyman-tree-route` | `/esq-cmd-tree` | GET | — | enyMan |

Upstream host/port for each is `${<SVC>_HOST}:${<SVC>_PORT}` (see the gateway route hosts above).

---

## Test-only hooks (default-safe; never set in production)

| Env var | Service | Default | Effect when set |
|---|---|---|---|
| `KEYSMITH_TEST_CONNECT_HOLD_MS` | keySmith | `0` | Holds between the path read and the activation URQ publish to open the race-8c window. |
| `KCMASTER_PATH_BUFFER_TTL_MS` | kcMaster | `10000` | `-1` disables path-buffer recovery so race-8c reproduces (buffer OFF). |
| `ENYMAN_VALIDATE_CREATE_DURING_MOVE` | enyMan | `true` | `false` skips CREATE-during-move reconciliation so race-8b reproduces. |
| `ENYMAN_TEST_CREATE_DELAY_MS` | enyMan | `0` | Holds the create transaction open between the parent-path read and the child insert, so a concurrent cross-instance move can rewrite the parent path in the gap (the race-8b multi-instance lever). |
| `ESQ_TEST_SLOW_QUERY_ENABLED` | enyMan | `false` | `true` wires the `/test/slow-query` (capped) + `/test/slow-query-optout` endpoints used by the R6 query-timeout smoke. |
| `BIZTREE_QUEUE_BULK_THRESHOLD` | bizTree | `10` | A very high value forces one-by-one cache processing (A/B against batched). |

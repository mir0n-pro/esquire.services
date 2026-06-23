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

Hikari pool settings are fixed in the yml (not env-driven): `maximum-pool-size=20`,
`minimum-idle=20`, `connection-timeout=30000`, `max-lifetime=1800000`, `idle-timeout=600000`.

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
- **x-rod**: the per-slot x-rod config -- `rod-class` + engine knobs (pool-size, feed-capacity,
  virtual-threads, publisher-pool-size, concurrency) + a `transport` block.
- **transport**: provider (`activemq` | `kafka` | `redis`, or a class name) + endpoint + destination +
  `params` (opaque per-vendor knobs, e.g. `jms.useAsyncSend` / `pubSubDomain` for ActiveMQ pub/sub-vs-queue
  / `group-id` / `max-len`) + (R&R) `request-node` / `response-node` + a node list. The bus carries no
  queue-vs-topic notion of its own: that is a JMS concept, set as the ActiveMQ `pubSubDomain` param
  (`true` = topic, absent/`false` = queue) and read only by `tp-activemq`.
- **rod-class**: `XRod` (standard transceiver), `XRodRR` (request/response, two-node, role-routed),
  `XRodInProcess` (a generic in-process relay that runs a worker applying events locally instead of sending
  them; `messaging.xrod.impl`), `XRodInProcessKeep` (the in-process KEEP that applies events to a DB via a
  `datasource` + `director`; FQCN `pro.mir0n.esquire.dataKeep.keep.XRodInProcessKeep`),
  `XRodInfo` (logs instead of sends), `XRodDisabled` (a no-op; selected EXPLICITLY to run without a bus).
- **role**: `CLIENT` / `SERVER` / `BOTH`.

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
| `AUDIT_POOL_SIZE` | `4` | Audit x-rod feed apply-pool size; keep <= the keep datasource pool. |
| `AUDIT_FEED_CAPACITY` | `4096` | Producer feed depth (bounded; full -> back-pressures flush-after-commit). |
| `AUDIT_VIRTUAL_THREADS` | `false` | Feed-pool workers on virtual threads. |

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

enyMan joins three buses: **entity-bus** (SERVER -- broadcasts UE), **kc-bus** (R&R CLIENT to
kcMaster), and **audit-bus** (UA producer). The bus-id / slot-id and x-rod knobs come from the shared
topology; the env overrides it actually uses:

| Env var | Default | Description |
|---|---|---|
| `ENTITY_BUS_ID` | `esquire.entity` | Entity-broadcast bus-id (SERVER producer). |
| `ENTITY_SLOT_ID` | `entity` | Entity slot-id. |
| `KC_BUS_ID` | `esquire.kc` | KC request/response bus-id (R&R CLIENT). |
| `KC_SLOT_ID` | `kc` | KC slot-id. |
| `AUDIT_BUS_ID` | *(see [audit logging](#audit-logging-producers-enyman-pacman-keysmith))* | Audit sink bus-id (UA producer). |
| `ENYMAN_MOVE_QUEUE_CAPACITY` | `16384` | Move-queue depth (bounded; on full, `submitMove`/`submitReconcile` drop + log). |
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

### H2 cache datasource / pool

| Env var | Default | Description |
|---|---|---|
| `BIZTREE_H2_URL` | `jdbc:h2:mem:biztree;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` | H2 cache JDBC URL (independent of the source DB). |
| `BIZTREE_H2_POOL_MAX` | `10` | Hikari max pool size. |
| `BIZTREE_H2_POOL_MIN_IDLE` | `10` | Hikari minimum idle. |
| `BIZTREE_H2_POOL_CONN_TIMEOUT` | `5000` | Connection timeout (ms). |
| `BIZTREE_H2_POOL_MAX_LIFETIME` | `1800000` | Connection max lifetime (ms). |
| `BIZTREE_H2_POOL_IDLE_TIMEOUT` | `600000` | Idle timeout (ms). |

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

The apply-pool sizing (`pool-size`, `virtual-threads`, `concurrency`) is the audit leg's `x-rod` block in
the shared topology, not a per-service env var; keep `pool-size` <= the datasource Hikari pool.

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
| `BIZTREE_QUEUE_BULK_THRESHOLD` | bizTree | `10` | A very high value forces one-by-one cache processing (A/B against batched). |

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

---

## Shared parameters (most services)

These follow the same pattern across services; `<SVC>` is the service token in caps
(`ENYMAN`, `PACMAN`, `KEYSMITH`, `KCMASTER`, `BIZTREE`, `XXROD`). They are listed once here and referenced
from each service section rather than repeated.

### Database (data services: enyMan, pacMan, keySmith, bizTree, xxRod — NOT kcMaster)

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

### ActiveMQ (all services)

| Env var | Default | Description |
|---|---|---|
| `AMQ_BROKER_URL` | `tcp://localhost:61616` | Broker URL. |
| `AMQ_USER` | *(empty)* | Broker user (no auth in current phase). |
| `AMQ_PASSWORD` | *(empty)* | Broker password. |

### Audit logging (producers: enyMan, pacMan, keySmith)

Opt-in audit (option-0 baseline: default OFF). The producer buffers committed entity changes and, after
commit, feeds them off the request thread to the audit sink: **(b)** in-process write to the `*_log`
tables, **(c)** publish to the audit queue for the standalone **xxRod** consumer, or **(d)** XADD to a
Redis Stream (the stream IS the audit log; no consumer service). Here `<SVC>` is `ENYMAN` / `PACMAN` /
`KEYSMITH`. See `doc/Esquire.AuditLogging.md`.

| Env var | Default | Description |
|---|---|---|
| `<SVC>_AUDIT_ENABLED` | `false` code / `true` deployed | Master switch; OFF -> `post()` is a no-op. |
| `<SVC>_AUDIT_MODE` | `in-process` code / `bus` deployed | `in-process` = (b) write `*_log` here; `bus` = (c) publish to the queue (xxRod writes); `redis` = (d) XADD to a Redis Stream. |
| `<SVC>_AUDIT_FEED_CAPACITY` | `4096` | xy-Rod producer feed depth (bounded; full -> back-pressures flush-after-commit). |
| `<SVC>_AUDIT_POOL_SIZE` | `4` | (b) in-process apply-pool size; keep <= the log datastore pool. |
| `<SVC>_AUDIT_VIRTUAL_THREADS` | `false` | (b)/(c) pool workers on virtual threads. |
| `<SVC>_AUDIT_PUBLISHER_POOL_SIZE` | `0` | (c)/(d) publisher pool: `0` = single feed-worker synchronous publish; `N>0` = N async sender threads (bus: a dedicated `useAsyncSend` connection; redis: N XADD workers). |
| `REDIS_HOST` | `localhost` code / `redis` deployed | (d) Redis host (`spring.data.redis.host`). Lettuce connects lazily, so it is ignored unless mode=redis. |
| `REDIS_PORT` | `6379` | (d) Redis port. |
| `<SVC>_AUDIT_REDIS_STREAM` | *(empty -> `esquire.rod.audit`)* | (d) the Redis Stream key to XADD each event to. |
| `<SVC>_AUDIT_REDIS_MAX_LEN` | `0` | (d) approximate MAXLEN cap on the stream (`0` = uncapped). |
| `<SVC>_AUDIT_LOG_DATASTORE` | `shared` | (b) where `*_log` is written: `shared` (service DB) or `dedicated` (separate pool/vendor below). |
| `<SVC>_AUDIT_DB_VENDOR` | `dev-postgres` | (b) dedicated-only: log-DB SQL dialect (may differ from the business DB). |
| `<SVC>_AUDIT_DB_URL` | *(empty)* | (b) dedicated-only: log-DB JDBC URL. |
| `<SVC>_AUDIT_DB_USERNAME` | `esq2025` | (b) dedicated-only: log-DB user. |
| `<SVC>_AUDIT_DB_PASSWORD` | `q` | (b) dedicated-only: log-DB password. |
| `<SVC>_AUDIT_DB_POOL_SIZE` | `8` | (b) dedicated-only: log-DB Hikari pool size. |

The `*_log` SQL each producer can write is shipped as a deploy-time `META-INF/audit/{vendor}.xml` spec set
(omit those files to package the service without audit SQL). See [xxRod](#xxrod) for the (c) consumer.

#### Selecting an audit option -- all external, no framework code change

The framework code is **option-agnostic**: which audit style runs is decided entirely by configuration and
deploy-time artifacts. Four external layers compose the choice -- **(1) app config** (env / yml
`<svc>.audit-logging.*`), **(2) DB deploy** (`db.seed`: the `*_log` tables, the triggers, the dedup
indexes), **(3) SQL spec artifacts** (`META-INF/audit/{vendor}.xml`, shipped or omitted at packaging), and
**(4) infra** (ActiveMQ + xxRod, or Redis). Each option is a recipe across those layers:

| Option | (1) App config (env) | (2) db.seed | (3) META-INF/audit SQL | (4) Infra |
|---|---|---|---|---|
| **(0) off** | `<SVC>_AUDIT_ENABLED=false` (default) | -- | -- | -- |
| **(a) triggers** | `<SVC>_AUDIT_ENABLED=false` (the app producer stays OFF; the DB logs) | `*_log` tables (`create.log`) **+ run the trigger DDL** (`<vendor>/triggers/all.sql`; base seed is trigger-free) | -- (SQL lives in the trigger) | -- |
| **(b) in-process** | `=true`, `_MODE=in-process`; `_POOL_SIZE` / `_VIRTUAL_THREADS` / `_FEED_CAPACITY`; `_LOG_DATASTORE=shared\|dedicated` (+ `_DB_*` if dedicated) | `*_log` tables (`create.log`) | **ship** the service's subset | -- |
| **(c) bus -> xxRod** | producer: `=true`, `_MODE=bus`, `_PUBLISHER_POOL_SIZE=0\|N`, `AMQ_BROKER_URL`. consumer: `XXROD_DIRECTOR=audit`, `XXROD_AUDIT_POOL_SIZE`, `XXROD_MESSAGING_CONCURRENCY`, `DB_XXROD_*` | `*_log` tables **+ dedup unique indexes** (`create.log`) | producers: **not required** (they don't write in bus mode); **xxRod ships the FULL set** | ActiveMQ broker + the xxRod service |
| **(d) redis** | `=true`, `_MODE=redis`; `_REDIS_STREAM` / `_REDIS_MAX_LEN`; `REDIS_HOST` / `REDIS_PORT` | -- (no `*_log`) | -- (no SQL) | Redis (`redis:8`); RedisInsight optional |

Notes:
- `<SVC>` is `ENYMAN` / `PACMAN` / `KEYSMITH`; the env prefix is `<SVC>_AUDIT_` (e.g. `ENYMAN_AUDIT_MODE`).
- **(a) is configured at the DB layer, not the app** -- you opt in by *running* the trigger scripts at
  deploy; the framework audit feature stays disabled.
- **Audit is opt-in at packaging too:** omit a service's `META-INF/audit/` files and it ships with no audit
  SQL (the loader tolerates the absence -> empty map), so (b)/(c) silently no-op on that service without any
  code or config change.
- Switching options at runtime is a **config flip + the matching infra** -- e.g. (b)->(c) is
  `_MODE=bus` + bring up ActiveMQ/xxRod; (c)->(d) is `_MODE=redis` + bring up Redis. No rebuild.
- Delivery-semantics trade-offs per option (loss / dup / dedup / zero-loss): see
  `doc/Esquire.AuditLogging.Design.md` section 13.

#### Audit Redis Stream (option d) -- the `redis` service + RedisInsight console

In `mode=redis` the producer XADDs each event to a Redis Stream; the stream IS the append-only audit log
(no consumer service -- read it with `XRANGE` / `XREVRANGE`). The dev stack ships a `redis:8` service
(`esq-redis`, port 6379, `maxmemory 384mb` + `noeviction` + AOF). The Redis health probe is disabled on the
producers (`management.health.redis.enabled=false`) so an unused Redis never marks a service DOWN.

Inspect the stream with **RedisInsight**, a web GUI gated behind the compose `tools` profile (so it does
NOT start with the default stack):

```
docker compose up -d redisinsight        # or: docker compose --profile tools up -d
```

Then open `http://localhost:5540` -- the audit DB is pre-added as `esq-audit` (host `redis`, port `6379`,
no auth); open the `esquire.rod.audit` key for the stream viewer. Or use the CLI directly:

```
docker exec -it esq-redis redis-cli XREVRANGE esquire.rod.audit + - COUNT 10
```

### Server port

| Env var | Default | Description |
|---|---|---|
| `<SVC>_PORT` | `3000` (yml) | HTTP listen port. Compose/k8s assign per-service ports (see each service). |

### Instance identity (enyMan / common — entity-id minting)

Resolved by `EsqUtils.instanceNo()` in priority order; the first one set wins:

| Env var / source | Default | Description |
|---|---|---|
| `ESQUIRE_INSTANCE_NO` | *(unset)* | Explicit instance number (0..9). Highest priority. |
| `POD_INDEX` | *(unset)* | k8s 1.28+ StatefulSet pod-index label via the downward API. |
| `POD_NAME` | *(unset)* | StatefulSet pod name; the trailing ordinal is parsed (`enyman-3` → 3). |
| `esquire.instance.no` (system property) | *(unset)* | JVM `-D` override. |
| *(fallback)* | `0` | Single, unsharded instance / local dev. |

Used to keep minted entity ids unique across redundant enyMan instances; an instance number
outside `0..9` fails fast at first mint.

---

## gateway

Edge service: Spring Cloud Gateway, KC OIDC + resource-server, CORS, Token Relay, optional JWE,
and the route table (see [Gateway routes](#gateway-routes)).

**Port:** `GATEWAY_PORT` (default `8080`). **Shared:** AMQ not used; logging (below).

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

**Port:** `ENYMAN_PORT` (`3000` code / `3003` deployed). **Shared:** DB token `ENYMAN`, AMQ,
logging, instance identity, [audit logging](#audit-logging-producers-enyman-pacman-keysmith).

| Env var | Default | Description |
|---|---|---|
| `ENYMAN_SERVICE_ID` | `entity-update-broadcast` | ServiceID in the outbound message envelope (entity-broadcast channel). |
| `ENYMAN_CTRL_ID` | `enyman.default` | Stable producer instance id (CtrlID); response listeners filter on it. |
| `ENYMAN_MESSAGING_CLIENT_ID` | `enyman` | JMS client-id base; composed per-pod as `<base>-${POD_INDEX:0}` for durable subscriptions. |
| `ENYMAN_MESSAGING_CONSUMER_ENABLED` | `false` | Activate the entity-broadcast consumer template. |
| `ENYMAN_MOVE_QUEUE_CAPACITY` | `16384` | Move-queue depth (bounded; on full, `submitMove`/`submitReconcile` drop + log). |
| `ENYMAN_VALIDATE_CREATE_DURING_MOVE` | `true` | v1.2.6 Goal 3: `true` runs CREATE-during-move path reconciliation (race-8b closed); `false` reproduces the race (negative test). |

---

## pacMan

Accounting: account balance / deposit / withdrawal / transfer and account DELETE.

**Port:** `PACMAN_PORT` (`3000` code / `3003` deployed). **Shared:** DB token `PACMAN`, AMQ, logging,
[audit logging](#audit-logging-producers-enyman-pacman-keysmith).

| Env var | Default | Description |
|---|---|---|
| `PACMAN_SERVICE_ID` | `entity-update-broadcast` | ServiceID in the outbound message envelope. |
| `PACMAN_CTRL_ID` | `pacman.default` | Stable producer instance id (CtrlID). |
| `PACMAN_MESSAGING_CLIENT_ID` | `pacman` | JMS client-id. |

---

## keySmith

Access-profile / credential routine; publishes KC-sync requests (JMS) to kcMaster.

**Port:** `KEYSMITH_PORT` (`3000` code / `3002` deployed). **Shared:** DB token `KEYSMITH`, AMQ, logging,
[audit logging](#audit-logging-producers-enyman-pacman-keysmith).

| Env var | Default | Description |
|---|---|---|
| `KEYSMITH_MESSAGING_CLIENT_ID` | `keysmith` | JMS client-id. |
| `KEYSMITH_CTRL_ID` | `keysmith.default` | Stable producer instance id (CtrlID); the response listener filters on it. |
| `KEYSMITH_TEST_CONNECT_HOLD_MS` | `0` | **Test-only** race-8c hook: ms to sleep between the committed path read and the activation URQ publish. `0` = disabled; never set in production. |

---

## kcMaster

Owns all Keycloak synchronization (create/update/delete users, path sync). No application DB — it
talks to KC over the admin REST API.

**Port:** `KCMASTER_PORT` (`3006`). **Shared:** AMQ, logging. (No DB.)

| Env var | Default | Description |
|---|---|---|
| `KC_BASE_URL` | `http://localhost:8080` | Keycloak base URL for the admin client (include `/kc-auth` when KC runs under a relative path). |
| `KC_REALM` | `esquire` | Realm kcMaster manages. |
| `KC_ADMIN_CLIENT_ID` | `admin-cli` | Admin client id. Deployments use the confidential service-account client `esq-kcMaster`. |
| `KC_ADMIN_CLIENT_SECRET` | *(empty)* | Secret for a confidential admin client. |
| `KCMASTER_SERVICE_ID` | `kc-sync` | ServiceID in the message envelope. |
| `KCMASTER_CTRL_ID` | `kcmaster.default` | Stable producer instance id (CtrlID). |
| `KCMASTER_MESSAGING_CLIENT_ID` | `kcmaster` | JMS client-id. |
| `KCMASTER_PATH_BUFFER_TTL_MS` | `60000` code / `10000` deployed | Race-8c path-buffer TTL. Buffered topic-side paths older than this are not applied. **Test:** `-1` disables recovery (reproduces the race). |
| `KCMASTER_PATH_BUFFER_PRUNE_MS` | `30000` | Interval of the scheduled buffer prune. |

KC admin client timeouts are fixed in the yml: `connect-timeout-ms=5000`, `read-timeout-ms=10000`.

---

## bizTree

Recoverable read cache (Taijitu two-monad H2). Consumes entity broadcasts, serves tree reads,
runs the night-watch anti-entropy sweep.

**Port:** `BIZTREE_PORT` (`3000` code / `3002` deployed). **Shared:** DB token `BIZTREE` (the source
DB read at cache load), AMQ, logging.

| Env var | Default | Description |
|---|---|---|
| `BIZTREE_CACHE_VENDOR` | `cache-h2` | Cache backend profile (H2 in-memory). |
| `BIZTREE_MESSAGING_CONSUMER_ENABLED` | `true` | Activate the entity-broadcast consumer. |
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

## xxRod

Standalone audit-bus consumer (option c) -- a generic **xRod host**: consumes the audit queue
(`esquire.rod.audit`) and hands each decoded event to the configured `IRodDirector` (audit = write the
`*_log` tables). Horizontally redundant (competing consumers on the queue; no clientId). It ships the
**full** `META-INF/audit/{vendor}.xml` SQL set (it writes every kind). See
`doc/Esquire.AuditLogging.Design.md` §12.

**Port:** `XXROD_PORT` (`3007`). **Shared:** DB token `XXROD` (the log datastore it writes; vendor from
`DB_XXROD_VENDOR`), AMQ (consumes the audit queue), logging. (No producer audit block -- xxRod is the
consumer side.)

| Env var | Default | Description |
|---|---|---|
| `XXROD_DIRECTOR` | `audit` | Which `IRodDirector` is active (`xxrod.director.type`); each impl is a gated `@Component` that reads its own `xxrod.director.<type>.*` config in `init()`. `audit` writes the `*_log` tables; future: replication / doc-DB. |
| `XXROD_AUDIT_POOL_SIZE` | `8` | Audit director's apply-pool (`XXRod`) size; keep <= the datasource Hikari pool. |
| `XXROD_AUDIT_VIRTUAL_THREADS` | `false` | Apply-pool workers on virtual threads. |
| `XXROD_MESSAGING_CONCURRENCY` | `1-1` | JMS listener concurrency (the apply pool provides the actual write parallelism). |

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
| `LOG_LEVEL_JMS` | `INFO` (enyMan/pacMan/kcMaster/xxRod) / `ERROR` (bizTree/keySmith) | `org.springframework.jms` level. (Not present in the gateway.) |
| `LOG_LEVEL_AMQ` | `INFO` / `ERROR` (as JMS above) | `org.apache.activemq` level. |
| `LOG_LEVEL_MIR0N` | `INFO` (enyMan/bizTree) / `ERROR` (gateway/pacMan/keySmith/kcMaster/xxRod) | Application (`pro.mir0n`) console level. |
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

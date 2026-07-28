<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire Messaging — how Esquire uses the Messaging Bus

This page is about **how Esquire applies the Messaging Bus** — the message buses that actually run in Esquire
today: what each one carries, who publishes and who consumes, over which transport — together with the
**current limitations** of the running topology.

It is the *instance* view (Esquire's use of the bus), NOT the generic bus framework. For how the bus works
underneath — the catalog, the x-rod frontend, rod-classes, and the transport-provider SPI — see
[Esquire Messaging Bus — Framework](Esquire.MessagingBus.md). For the wire format of each message, see
[Esquire.MessagingBus.MessageStructure.md](Esquire.MessagingBus.MessageStructure.md).

A bus is one logical conversation: a set of channels (a topic, a queue, or a stream) grouped to serve a
single purpose. A service never talks to ActiveMQ / Kafka / Redis directly — it joins a named bus through
the x-rod frontend, and the broker is a per-deployment choice. ActiveMQ is the transport deployed in the
cloud today; the local docker stack also runs Kafka and Redis.

---

## The buses at a glance

| Bus | Pattern | Destination(s) | Producers | Consumers |
|---|---|---|---|---|
| **Entity Broadcast** | broadcast (many-to-many) | topic `esquire.entity.broadcast` | enyMan, pacMan | bizTree, kcMaster |
| **IAM Request/Response** | request / response | queues `esquire.kc.request` + `esquire.kc.response` | enyMan, keySmith (client) → kcMaster (server) → back | — |
| **Audit** | broadcast / stream | `esquire.rod.audit` (queue / topic / stream, per sink) | enyMan, pacMan, keySmith | auKeep (bus sinks only) |

---

## Entity Broadcast Bus

**enyMan** (organizations and users) and **pacMan** (accounts) publish an **entity-update (`UE`)** event on
every committed mutation — create, update, delete, move — to one shared topic. **bizTree** and **kcMaster**
subscribe; more consumers can be added without touching the publishers.

- **bizTree** keeps its in-memory entity tree current, dispatching each `UE` by `(event type, kind)` to a
  create / update / delete / move handler.
- **kcMaster** watches for moves, so a relocated entity's Keycloak path stays in sync.

The event carries raw entity field values only (id, kind, name, path, status, …); each consumer interprets
them, and a field absent from a message is a no-op. The `UE` wire format is in
[Esquire.MessagingBus.MessageStructure.md](Esquire.MessagingBus.MessageStructure.md).

---

## IAM Request/Response Bus

**enyMan** and **keySmith** send identity commands to **kcMaster** — the only service that writes to
Keycloak — and wait for the reply. The bus has two destinations: a **request** queue and a **response**
queue. The requester is the *client* (publishes the request, reads its own reply); kcMaster is the *server*
(the inverse).

- **Request (`URQ`)** — create / update / delete / move a Keycloak user, carrying the login id, email,
  path, roles, and auth state.
- **Response** — **`URS`** on success, **`URR`** on failure (RFC 9457 problem details). The reply is told
  apart by its message type, not by inspecting the body.
- **Reply routing** — each client instance reads only its *own* responses (filtered by its instance id),
  so sharded replicas never steal each other's replies.

kcMaster dispatches each request to a create / update / delete / update-path Keycloak operation. The `URQ` /
`URS` / `URR` wire formats are in [Esquire.MessagingBus.MessageStructure.md](Esquire.MessagingBus.MessageStructure.md).

---

## Audit Bus

The entity producers (**enyMan**, **pacMan**, **keySmith**) post an **audit (`UA`)** event after commit,
off the request thread. Where the audit lands is a per-deployment choice flipped by one environment
variable (`AUDIT_BUS_ID`); the framework default is **off** — a fresh deploy audits nothing.

| Sink (`bus-id`) | Where the audit lands |
|---|---|
| in-process (`audit-b`) | the producing service writes the `*_log` tables itself — no broker |
| ActiveMQ (`audit-c`) | queue → **auKeep** consumes → `*_log` |
| Kafka (`audit-ck`) | topic → **auKeep** consumes → `*_log` |
| Redis stream (`audit-d`) | the stream itself is the append-only log (no consumer) |
| Kafka stream (`audit-dk`) | the topic itself is the log (no consumer) |

Audit is a thin layer on a generic "keep" engine (`esquire-dataKeep`) that applies incoming changes to a
database; **auKeep** is the standalone consumer service for the bus sinks. The full audit model, the `*_log`
schema, and the delivery analysis are in [Esquire.AuditLoggingStack.md](Esquire.AuditLoggingStack.md).

---

## Running as a fleet

Every Esquire service can run as more than one copy at once (see
[Esquire.HighAvailability.md](Esquire.HighAvailability.md) for the deployment side). Each copy gets a distinct
instance id (`<service>.<number>`, from its ordinal in the set), and all three buses keep working under the
duplication with no extra wiring:

- **Entity Broadcast** — enyMan and pacMan each run as several copies; every copy publishes its `UE`, and
  every OTHER copy receives — the consumers (bizTree, kcMaster) AND the publisher's own sibling copies. A copy
  never re-applies its own publication: on one shared broker connection the broker drops it, otherwise the copy
  filters its own out by instance id. This is what corrects the cross-copy case in enyMan — a copy busy moving a
  branch hears a SIBLING copy's create on the broadcast and fixes the new record's stale location, the gap a
  single copy's own "move in progress" check could not see.
- **IAM Request/Response** — kcMaster runs as several copies that all listen on the one request queue as
  competing consumers, so each request is handled by exactly ONE copy (the work spreads across them). The reply
  carries the asking copy's instance id and routes back only to it, so sharded requesters never read each
  other's replies.
- **Audit** — each producing copy posts its own `UA` independently; for the broker sinks auKeep also runs as
  several copies competing on the one queue, so each audit event lands once. A redelivered duplicate is
  collapsed by the `*_log` unique key (see Current limitations), not by the bus.

---

## Bus health

Each service forwards its bus connection health to `/actuator/health`: every bus it uses reports UP / DOWN.
Each connection sends a small **keep-alive** on a timer when it is otherwise quiet (a broadcast leg a HeartBeat,
a request/response client a TestRequest the server answers), and a connection whose sends stop getting through
reads DOWN -- so the signal works the SAME on every transport, not just where the broker offers a connection
callback (that callback stays as a fast diagnostic; the in-process keep instead reports its datasource). The
keep-alive runs from one shared timer per service. The indicator sits in the **readiness** group, so a broker
outage takes the pod out of rotation -- but it is **not** in liveness, so a blip never restarts the pod. auKeep
additionally reports its keep `*_log` database (the apply side). Wiring: [Esquire.MessagingBus.md](Esquire.MessagingBus.md)
(Health) + [services.configuring.md](services.configuring.md) (Health checks).

---

## Current limitations

- **ActiveMQ is the only transport in the cloud deployment.** The Kafka and Redis providers are implemented
  and validated on the local docker stack (every audit sink runs there), but the OKE / k8s deployment ships
  ActiveMQ only — the Kafka / Redis sinks (`audit-ck` / `audit-d` / `audit-dk`) are validated options, not
  part of the cloud topology. On k8s a bus pointed at an absent broker resolves to a disabled no-op, not a
  crash.

- **The stream sinks are producer-only.** `audit-d` (Redis) and `audit-dk` (Kafka) write the log but have
  no consumer back into the `*_log` tables — the stream itself *is* the record. Feeding it onward (e.g. into
  a Redis document store) needs an external component such as a Kafka Connect sink, not part of the framework.

- **A bus a service declares it uses must be defined — a missing one fails LOUD.** A service names the buses it
  uses; a named bus the catalog does not define (or a typo'd key) fails fast at boot, not silently. To run
  *without* a bus, point it at an explicit `XRodDisabled` leg (e.g. the catalog's `audit-off` bus) — disabling
  is always deliberate, never an accident.

- **Delivery is best-effort per transport; the bus adds no replay or de-duplication.** Idempotency where it
  matters (the audit `*_log`) rests on a unique key in the database, not on the bus. A consumer that is down
  misses broadcasts while it is gone — bizTree's recoverable cache reconciles that on its own (its periodic
  night-watch rebuild), but the bus itself does not redeliver.

- **The fleet size is fixed -- nothing autoscales.** Each service runs a set number of copies; no copies are
  added or removed automatically as load changes. At the scale this runs today the traffic is small and steady,
  so a fixed small fleet covers it, and the shared single-instance backends (Postgres, the broker, KeyCloak)
  are the real ceiling -- adding app copies against a fixed backend only pushes more load onto it. Autoscaling
  earns its keep only with real, spiky production traffic AND after those backends are in a scalable / HA mode.
  Where and how it would attach -- driven by REST request rate / duration, the copy count capped at ten -- is in
  [Esquire.HighAvailability.md](Esquire.HighAvailability.md) (section 3.7).

- **One broker per bus** (reached through a `failover:` endpoint). A bus names a single transport; the ActiveMQ
  legs use a `failover:(tcp://...)` endpoint, so a dropped connection auto-reconnects to that broker (and the
  bus health recovers with it). A `failover:` endpoint *can* list more than one broker, but multi-broker
  failover and partitioned routing are not configured at the bus layer.

- **The transport SPI is shaped around the async messaging model** — discrete messages sent to / received from
  a named target, one direction per leg (request/response is two paired legs correlated by rod-id). Queue, topic,
  and stream are just how today's providers realize a *destination*; the model is not tied to them. Any transport
  that reduces to async message exchange is a drop-in provider regardless of its wire — a synchronous RPC
  (the call becomes the request leg, its reply the response leg), a stateful session (FIX / WebSocket, with the
  session handled inside the x-rod), a brokerless or multicast peer (the "destination" is a group or endpoint,
  not a broker queue), a shared-table outbox (the in-process keep already is one). The SPI only has to grow for a
  transport whose *interaction model* is not async message-passing at all: a demand-driven / backpressured pull
  transport (Reactive Streams, RSocket) where the consumer throttles a remote producer end to end, or a
  continuous unframed byte / media stream with no message boundary.

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*

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

### The same buses in the compact shape

Grouping services into fewer programs does not change the catalog: the buses, the destinations and the roles
are the same file, read the same way. What changes is who is on which side of a leg.

- The **Entity Broadcast** bus keeps every participant. In compact, Mesnie publishes it (enyMan inside it)
  and gateWard consumes it (the tree cache inside it).
- The **IAM Request/Response** bus is defined and **carries nothing**: the requester and the server -- enyMan,
  keySmith and kcMaster -- sit in the same program, so identity commands are a call through the identity
  gateway instead of a queue round trip. The bus stays in the catalog because the catalog describes the
  framework, not one deployment.
- The **Audit** bus is unchanged, and auKeep remains its own program. In the cloud profile the audit trail is
  written by database triggers instead, and the audit bus id is set to the DEFINED `audit-off`.

## Entity Broadcast Bus

**enyMan** (organizations and users) and **pacMan** (accounts) publish an **entity-update (`UE`)** event on
every committed mutation — create, update, delete, move — to one shared topic. **bizTree** and **kcMaster**
subscribe; more consumers can be added without touching the publishers.

- **bizTree** keeps its in-memory entity tree current, dispatching each `UE` by `(event type, kind)` to a
  create / update / delete / move handler.
- **kcMaster** watches for moves, so a relocated entity's Keycloak path stays in sync.

The event carries raw entity field values only (id, kind, name, path, status, …); each consumer interprets
them, and a field absent from a message is a no-op. Nothing acknowledges an applied event; the tree
converges through the night-watch instead (see **Delivery** below). The `UE` wire format is in
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

## Delivery — what the bus holds, and what it does not

**A fan-out has no acknowledgement, by nature.** One publisher announces a fact to whoever subscribes; each
consumer does something different with it, at its own pace, and some of them are not running. There is no
single outcome to acknowledge, and asking for one would turn a broadcast into as many request/responses as
there are subscribers -- coupling the publisher to every consumer's success, which is the opposite of why a
broadcast exists. The publisher's job ends when the event is announced.

**And when an answer IS wanted, the bus already has the shape for it: R&R.** A request names one
replier and gets a `URS` / `URR` back, routed to the instance that asked by its rod-id. That is the
choice being made at the catalog: a broadcast is chosen precisely when the publisher does not wait, and
a request/response when it does. Neither one is the other missing a feature.

So Esquire's bus is **fire-and-forget at the application layer**. A publisher hands an event to its rod and
returns; a consumer applies it. **No acknowledgement lives in Esquire** — there is no `acknowledge()`, no
`CLIENT_ACKNOWLEDGE`, no transacted listener anywhere in the tree, and no consumer can withhold one.

Whether a message counts as delivered is the **transport provider's** business. On ActiveMQ the JMS container
acks when its listener returns, and that listener returns as soon as the rod's receive pool accepts the
event — so from the broker's side the message is done before the consumer's work is. That is a deliberate
line: the bus carries events, the vendor half decides delivery semantics, and a service is never written
against one broker's guarantees.

Each bus answers loss in its own way, and each answer is a property of the design rather than of the wire:

- **Entity broadcast** — the topic subscription is non-durable, so a consumer that is disconnected does not
  receive what was published in the gap. The **Taijitu night-watch** anti-entropy compares the cache against
  the database and heals it, which is the no-event-loss mechanism here. A consumer therefore needs no ack:
  the tree converges whether an event arrived, failed to apply, or never came.
- **Audit** — the event is posted after the business commit, so every bus sink is best-effort by
  construction; **(a) DB triggers** is the never-lose choice, inside the transaction. Making the broker sink
  **(c)** zero-loss is transport-provider work, not a setting — see
  [Esquire.AuditLoggingStack.md](Esquire.AuditLoggingStack.md).
- **IAM request/response** — the answer travels back to the instance that asked, by rod-id, and is recorded
  there. A reply that never comes surfaces as the caller's own request timeout.

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
